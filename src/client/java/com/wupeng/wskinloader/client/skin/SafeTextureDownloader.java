package com.wupeng.wskinloader.client.skin;

import com.mojang.blaze3d.platform.NativeImage;
import com.wupeng.wskinloader.client.config.ModConfig;
import com.wupeng.wskinloader.client.mixin.SkinTextureDownloaderAccessor;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.SkinTextureDownloader;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.core.ClientAsset;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.PlayerModelType;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.SSLSocket;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

/** Bounded HTTP(S) texture fetcher with DNS pinning and strict image validation. */
public final class SafeTextureDownloader {
    private static final int MAX_BYTES = 1_048_576;
    private static final int MAX_HEADER_BYTES = 16_384;
    private static final int CONNECT_TIMEOUT_MS = 5_000;
    private static final int READ_TIMEOUT_MS = 5_000;
    private static final long TOTAL_TIMEOUT_NANOS = TimeUnit.SECONDS.toNanos(12);
    private static final Object[] CACHE_LOCKS = new Object[64];
    private static final ThreadPoolExecutor EXECUTOR = new ThreadPoolExecutor(3, 3, 0L, TimeUnit.MILLISECONDS,
        new ArrayBlockingQueue<>(1024), runnable -> {
        Thread thread = new Thread(runnable, "WSkinLoader-SafeDownload");
        thread.setDaemon(true);
        return thread;
    }, new ThreadPoolExecutor.AbortPolicy());
    public record DownloadedTexture(ClientAsset.Texture asset, PlayerModelType model) {}
    record Registered(Identifier id, Executor executor, Runnable release,
                      String url, Path path, DownloadedTexture result) {}
    private static final Object REGISTRATION_LOCK = new Object();
    private static final Map<String, Registered> REGISTERED = new ConcurrentHashMap<>();
    private static final Map<Identifier, Registered> PENDING = new ConcurrentHashMap<>();

    private SafeTextureDownloader() {}

    static {
        Arrays.setAll(CACHE_LOCKS, ignored -> new Object());
    }

    public static CompletableFuture<ClientAsset.Texture> download(SkinTextureDownloader downloader,
            Identifier textureId, Path cachePath, String url, boolean skin, boolean refresh,
            BooleanSupplier active) {
        return downloadDetailed(downloader, textureId, cachePath, url, skin, refresh, active)
                .thenApply(result -> result == null ? null : result.asset());
    }

    public static CompletableFuture<DownloadedTexture> downloadDetailed(SkinTextureDownloader downloader,
            Identifier textureId, Path cachePath, String url, boolean skin, boolean refresh,
            BooleanSupplier active) {
        Registered existing = REGISTERED.get(owner(textureId));
        // Explicit invalidation removes the owner first. A remaining registration
        // is a successful part of this load and can survive retries of the other part.
        if (existing != null && existing.url.equals(url) && existing.path.equals(cachePath)
                && active.getAsBoolean()) return CompletableFuture.completedFuture(existing.result);
        try {
            return CompletableFuture.supplyAsync(() -> {
            NativeImage image = null;
            try {
                if (!active.getAsBoolean()) return null;
                Object cacheLock = CACHE_LOCKS[(cachePath.toAbsolutePath().normalize().hashCode() & 0x7fffffff) % CACHE_LOCKS.length];
                byte[] bytes;
                synchronized (cacheLock) {
                    bytes = null;
                    if (!refresh && Files.isRegularFile(cachePath)) {
                        try {
                            byte[] cached = readBounded(cachePath);
                            validateImage(cached, skin);
                            try (NativeImage decoded = NativeImage.read(cached)) {
                                if (decoded.getWidth() <= 0 || decoded.getHeight() <= 0) throw new IOException("Invalid texture image");
                            }
                            bytes = cached;
                        } catch (IOException | RuntimeException ignored) {
                            if (active.getAsBoolean()) Files.deleteIfExists(cachePath);
                        }
                    }
                    if (bytes == null) {
                        bytes = fetch(url);
                        if (bytes == null) {
                            if (refresh && active.getAsBoolean()) Files.deleteIfExists(cachePath);
                            return null;
                        }
                        validateImage(bytes, skin);
                        try (NativeImage decoded = NativeImage.read(bytes)) {
                            if (decoded.getWidth() <= 0 || decoded.getHeight() <= 0) throw new IOException("Invalid texture image");
                        }
                        if (!active.getAsBoolean()) return null;
                        writeAtomically(cachePath, bytes);
                    }
                }
                PlayerModelType model = skin ? detectModel(bytes) : null;
                image = NativeImage.read(bytes);
                if (skin) image = processSkin(image, url);
                if (!active.getAsBoolean()) {
                    image.close();
                    return null;
                }
                NativeImage ready = image;
                SkinTextureDownloaderAccessor accessor = (SkinTextureDownloaderAccessor) downloader;
                var registered = register(accessor, textureId, ready, url, cachePath, model, active);
                image = null;
                return registered.join();
            } catch (Exception | Error e) {
                if (image != null) image.close();
                throw new java.util.concurrent.CompletionException(e);
            }
            }, EXECUTOR);
        } catch (RuntimeException e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    public static void shutdown() {
        // Queued futures must run to completion instead of being silently abandoned.
        EXECUTOR.shutdown();
    }

    public static void release(UUID uuid) {
        java.util.List<Registered> retired = new java.util.ArrayList<>();
        synchronized (REGISTRATION_LOCK) {
            for (String folder : new String[] { "skins/", "capes/" }) {
                String owner = "wskinloader:" + folder + uuid;
                Registered entry = REGISTERED.remove(owner);
                if (entry != null) retired.add(entry);
                PENDING.values().removeIf(pending -> {
                    if (!owner(pending.id).equals(owner)) return false;
                    retired.add(pending);
                    return true;
                });
            }
        }
        retired.forEach(SafeTextureDownloader::retire);
    }

    public static void releaseAll() {
        java.util.List<Registered> retired;
        synchronized (REGISTRATION_LOCK) {
            retired = new java.util.ArrayList<>(REGISTERED.values());
            retired.addAll(PENDING.values());
            REGISTERED.clear();
            PENDING.clear();
        }
        retired.forEach(SafeTextureDownloader::retire);
    }

    private static void retire(Registered entry) {
        try {
            entry.executor.execute(entry.release);
        } catch (java.util.concurrent.RejectedExecutionException ignored) {
            // Once the render executor stops, TextureManager owns final resource cleanup.
        }
    }

    /** Release the previous GPU texture only after the replacement is published. */
    public static void accept(ClientAsset.Texture asset) {
        Registered old;
        synchronized (REGISTRATION_LOCK) {
            Registered entry = PENDING.remove(asset.texturePath());
            if (entry == null) return;
            old = REGISTERED.put(owner(entry.id), entry);
        }
        if (old != null) retire(old);
    }

    public static void discard(ClientAsset.Texture asset) {
        Registered entry;
        synchronized (REGISTRATION_LOCK) {
            entry = PENDING.remove(asset.texturePath());
        }
        if (entry != null) retire(entry);
    }

    private static String owner(Identifier id) {
        String path = id.toString();
        int token = path.lastIndexOf('_');
        return token < 0 ? path : path.substring(0, token);
    }

    static void registerPending(Registered entry) {
        synchronized (REGISTRATION_LOCK) {
            PENDING.put(entry.id, entry);
        }
    }

    private static CompletableFuture<DownloadedTexture> register(SkinTextureDownloaderAccessor accessor,
            Identifier id, NativeImage image, String url, Path path, PlayerModelType model, BooleanSupplier active) {
        Executor mainThread = accessor.wskinloader$getMainThreadExecutor();
        TextureManager manager = accessor.wskinloader$getTextureManager();
        CompletableFuture<DownloadedTexture> result;
        try {
            result = CompletableFuture.supplyAsync(() -> {
            if (!active.getAsBoolean()) {
                image.close();
                return null;
            }
            final DynamicTexture texture;
            try {
                texture = new DynamicTexture(id::toString, image);
            } catch (RuntimeException | Error e) {
                image.close();
                throw e;
            }
            try {
                manager.register(id, texture);
            } catch (RuntimeException | Error e) {
                texture.close();
                throw e;
            }
            if (!active.getAsBoolean()) {
                manager.release(id);
                return null;
            }
            DownloadedTexture downloaded = new DownloadedTexture(new ClientAsset.DownloadedTexture(id, "wskinloader:validated"), model);
            registerPending(new Registered(id, mainThread, () -> manager.release(id), url, path, downloaded));
            // Invalidation may have occurred between the active check and registration.
            if (!active.getAsBoolean()) {
                discard(downloaded.asset());
                return null;
            }
            return downloaded;
            }, mainThread);
        } catch (RuntimeException e) {
            image.close();
            return CompletableFuture.failedFuture(e);
        }
        return result;
    }

    static PlayerModelType detectModel(byte[] bytes) throws IOException {
        var image = ImageIO.read(new ByteArrayInputStream(bytes));
        if (image == null) throw new IOException("Unsupported skin image");
        return image.getHeight() == 64 && (image.getRGB(50, 16) >>> 24) == 0
                ? PlayerModelType.SLIM : PlayerModelType.WIDE;
    }

    private static NativeImage processSkin(NativeImage original, String url) {
        if (original.getHeight() != 32) return SkinTextureDownloaderAccessor.wskinloader$processLegacySkin(original, url);
        NativeImage converted = new NativeImage(64, 64, true);
        try {
            converted.copyFrom(original);
            converted.fillRect(0, 32, 64, 32, 0);
            converted.copyRect(4, 16, 16, 32, 4, 4, true, false);
            converted.copyRect(8, 16, 16, 32, 4, 4, true, false);
            converted.copyRect(0, 20, 24, 32, 4, 12, true, false);
            converted.copyRect(4, 20, 16, 32, 4, 12, true, false);
            converted.copyRect(8, 20, 8, 32, 4, 12, true, false);
            converted.copyRect(12, 20, 16, 32, 4, 12, true, false);
            converted.copyRect(44, 16, -8, 32, 4, 4, true, false);
            converted.copyRect(48, 16, -8, 32, 4, 4, true, false);
            converted.copyRect(40, 20, 0, 32, 4, 12, true, false);
            converted.copyRect(44, 20, -8, 32, 4, 12, true, false);
            converted.copyRect(48, 20, -16, 32, 4, 12, true, false);
            converted.copyRect(52, 20, -8, 32, 4, 12, true, false);
            boolean opaque = true;
            for (int x = 32; x < 64; x++) for (int y = 0; y < 32; y++) {
                opaque &= (converted.getPixel(x, y) >>> 24) >= 128;
            }
            if (opaque) for (int x = 32; x < 64; x++) for (int y = 0; y < 32; y++) {
                converted.setPixel(x, y, converted.getPixel(x, y) & 0x00FFFFFF);
            }
            SkinTextureDownloaderAccessor.wskinloader$processLegacySkin(converted, url);
            original.close();
            return converted;
        } catch (RuntimeException | Error e) {
            converted.close();
            throw e;
        }
    }

    private static byte[] fetch(String value) throws IOException {
        return fetch(value, 0, System.nanoTime() + TOTAL_TIMEOUT_NANOS);
    }

    private static byte[] fetch(String value, int redirects, long deadline) throws IOException {
        checkDeadline(deadline);
        if (!ModConfig.isAllowedApiUrl(value)) throw new IOException("Texture URL is not allowed");
        URI uri = URI.create(value);
        String host = uri.getHost();
        int port = uri.getPort() < 0 ? (uri.getScheme().equalsIgnoreCase("https") ? 443 : 80) : uri.getPort();
        InetAddress[] addresses = InetAddress.getAllByName(host);
        try (Socket raw = connectPublic(addresses, port, deadline)) {
            raw.setSoTimeout(READ_TIMEOUT_MS);
            Socket socket = raw;
            SSLSocket tlsSocket = null;
            if (uri.getScheme().equalsIgnoreCase("https")) {
                SSLSocket tls = (SSLSocket) ((SSLSocketFactory) SSLSocketFactory.getDefault()).createSocket(raw, host, port, true);
                javax.net.ssl.SSLParameters parameters = tls.getSSLParameters();
                parameters.setEndpointIdentificationAlgorithm("HTTPS");
                tls.setSSLParameters(parameters);
                tls.startHandshake();
                socket = tls;
                tlsSocket = tls;
            }
            String target = uri.getRawPath();
            if (target == null || target.isEmpty()) target = "/";
            if (uri.getRawQuery() != null) target += "?" + uri.getRawQuery();
            OutputStream output = socket.getOutputStream();
            String request = "GET " + target + " HTTP/1.1\r\nHost: " + host + (uri.getPort() >= 0 ? ":" + port : "")
                    + "\r\nConnection: close\r\nAccept: image/png,image/jpeg,image/webp,image/*\r\nAccept-Encoding: identity\r\n\r\n";
            output.write(request.getBytes(StandardCharsets.US_ASCII));
            output.flush();
            InputStream input = socket.getInputStream();
            String status = readLine(input, MAX_HEADER_BYTES, deadline);
            if (status == null || !status.matches("HTTP/1\\.[01] [0-9]{3}(?: .*)?")) throw new IOException("Invalid HTTP status");
            int code = Integer.parseInt(status.substring(9, 12));
            int headerBytes = status.length() + 2;
            int contentLength = -1;
            boolean chunked = false;
            String location = null;
            while (true) {
                String line = readLine(input, MAX_HEADER_BYTES - headerBytes, deadline);
                if (line == null) throw new IOException("Truncated HTTP headers");
                if (line.isEmpty()) break;
                headerBytes += line.length() + 2;
                if (headerBytes > MAX_HEADER_BYTES) throw new IOException("Texture response headers are too large");
                int colon = line.indexOf(':');
                if (colon < 1) throw new IOException("Malformed HTTP header");
                String name = line.substring(0, colon).trim();
                String header = line.substring(colon + 1).trim();
                if (name.equalsIgnoreCase("Content-Length")) {
                    if (contentLength >= 0) throw new IOException("Duplicate Content-Length");
                    contentLength = Integer.parseInt(header);
                    if (contentLength < 0 || contentLength > MAX_BYTES) throw new IOException("Texture response is too large");
                } else if (name.equalsIgnoreCase("Transfer-Encoding")) {
                    if (chunked || !header.equalsIgnoreCase("chunked")) throw new IOException("Unsupported Transfer-Encoding");
                    chunked = true;
                } else if (name.equalsIgnoreCase("Content-Encoding") && !header.equalsIgnoreCase("identity")) {
                    throw new IOException("Encoded texture responses are not supported");
                } else if (name.equalsIgnoreCase("Location")) {
                    if (location != null) throw new IOException("Duplicate redirect location");
                    location = header;
                }
            }
            if (chunked && contentLength >= 0) throw new IOException("Ambiguous HTTP response framing");
            if (code == 404 || code == 204) return null;
            if (code == 301 || code == 302 || code == 303 || code == 307 || code == 308) {
                if (redirects >= 5 || location == null) throw new IOException("Invalid or excessive redirects");
                URI next = redirectTarget(uri, location);
                if (tlsSocket != null) tlsSocket.close();
                raw.close();
                return fetch(next.toString(), redirects + 1, deadline);
            }
            if (code != 200) throw new IOException("Texture server returned HTTP " + code);
            byte[] result = chunked ? readChunked(input, deadline) : readBody(input, contentLength, deadline);
            if (result.length == 0) throw new IOException("Empty texture response");
            if (tlsSocket != null) tlsSocket.close();
            return result;
        }
    }

    private static Socket connectPublic(InetAddress[] addresses, int port, long deadline) throws IOException {
        IOException failure = new IOException("No reachable public address for texture host");
        for (InetAddress address : addresses) {
            if (!isPublicAddress(address)) continue;
            checkDeadline(deadline);
            Socket socket = new Socket();
            try {
                int timeout = (int) Math.max(1, Math.min(CONNECT_TIMEOUT_MS,
                        TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime())));
                socket.connect(new InetSocketAddress(address, port), timeout);
                return socket;
            } catch (IOException e) {
                socket.close();
                failure.addSuppressed(e);
            }
        }
        throw failure;
    }

    static URI redirectTarget(URI from, String location) throws IOException {
        try {
            URI target = from.resolve(location);
            if (!ModConfig.isAllowedApiUrl(target.toString()) || ("https".equalsIgnoreCase(from.getScheme())
                    && !"https".equalsIgnoreCase(target.getScheme()))) throw new IOException("Unsafe texture redirect");
            return target;
        } catch (IllegalArgumentException e) {
            throw new IOException("Invalid redirect URL", e);
        }
    }

    static byte[] readBody(InputStream input, int contentLength, long deadline) throws IOException {
        if (contentLength > MAX_BYTES || contentLength < -1) throw new IOException("Invalid content length");
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(Math.max(1024, Math.max(0, contentLength)));
        byte[] buffer = new byte[8192];
        int read;
        while (contentLength < 0 || bytes.size() < contentLength) {
            checkDeadline(deadline);
            read = input.read(buffer, 0, contentLength < 0 ? buffer.length : Math.min(buffer.length, contentLength - bytes.size()));
            if (read == -1) break;
            checkDeadline(deadline);
            if (bytes.size() + read > MAX_BYTES) throw new IOException("Texture response is too large");
            bytes.write(buffer, 0, read);
        }
        if (contentLength >= 0 && bytes.size() != contentLength) throw new IOException("Truncated texture response");
        return bytes.toByteArray();
    }

    static byte[] readChunked(InputStream input, long deadline) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        while (true) {
            String sizeLine = readLine(input, 128, deadline);
            if (sizeLine == null) throw new IOException("Truncated chunked response");
            int extension = sizeLine.indexOf(';');
            int size = Integer.parseUnsignedInt(extension < 0 ? sizeLine : sizeLine.substring(0, extension).trim(), 16);
            if (size == 0) {
                int trailers = 0;
                while (true) {
                    String trailer = readLine(input, MAX_HEADER_BYTES - trailers, deadline);
                    if (trailer == null) throw new IOException("Truncated HTTP trailers");
                    trailers += trailer.length() + 2;
                    if (trailers > MAX_HEADER_BYTES) throw new IOException("HTTP trailers too large");
                    if (trailer.isEmpty()) break;
                }
                return bytes.toByteArray();
            }
            if (size < 0 || bytes.size() + size > MAX_BYTES) throw new IOException("Texture response is too large");
            byte[] chunk = readBody(input, size, deadline);
            checkDeadline(deadline);
            if (chunk.length != size || input.read() != '\r' || input.read() != '\n') throw new IOException("Malformed chunked response");
            bytes.write(chunk);
        }
    }

    private static String readLine(InputStream input, int limit, long deadline) throws IOException {
        ByteArrayOutputStream line = new ByteArrayOutputStream();
        int previous = -1;
        for (int b; (b = input.read()) != -1;) {
            checkDeadline(deadline);
            if (previous == '\r' && b == '\n') {
                byte[] bytes = line.toByteArray();
                return new String(bytes, 0, Math.max(0, bytes.length - 1), StandardCharsets.ISO_8859_1);
            }
            line.write(b);
            if (line.size() > limit) throw new IOException("HTTP line is too long");
            previous = b;
        }
        return null;
    }

    static void validateImage(byte[] bytes, boolean skin) throws IOException {
        if (bytes.length == 0 || bytes.length > MAX_BYTES) throw new IOException("Invalid image byte length");
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            if (input == null) throw new IOException("Invalid image data");
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw new IOException("Unsupported image format");
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                int width = reader.getWidth(0), height = reader.getHeight(0);
                if (skin ? width != 64 || (height != 32 && height != 64)
                        : width < 1 || height < 1 || width > 4096 || height > 4096 || (long) width * height > 4_194_304L) {
                    throw new IOException("Invalid texture dimensions");
                }
            } finally {
                reader.dispose();
            }
        }
    }

    private static void writeAtomically(Path path, byte[] bytes) throws IOException {
        Files.createDirectories(path.getParent());
        Path temp = Files.createTempFile(path.getParent(), path.getFileName().toString(), ".tmp");
        try {
            Files.write(temp, bytes);
            try {
                Files.move(temp, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
                Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    private static byte[] readBounded(Path path) throws IOException {
        try (InputStream input = Files.newInputStream(path); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) {
                if (output.size() + read > MAX_BYTES) throw new IOException("Cached texture is too large");
                output.write(buffer, 0, read);
            }
            if (output.size() == 0) throw new IOException("Empty cached texture");
            return output.toByteArray();
        }
    }

    static boolean isPublicAddress(InetAddress address) {
        return ModConfig.isPublicAddress(address.getAddress());
    }

    private static void checkDeadline(long deadline) throws IOException {
        if (System.nanoTime() > deadline) throw new IOException("Texture download timed out");
    }
}
