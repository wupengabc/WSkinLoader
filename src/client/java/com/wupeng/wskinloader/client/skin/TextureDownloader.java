package com.wupeng.wskinloader.client.skin;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.MemoryCacheImageInputStream;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.Proxy;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Iterator;
import java.util.zip.CRC32;

final class TextureDownloader {
    private static final int MAX_BYTES = 4 * 1024 * 1024;
    private static final int TIMEOUT_MILLIS = 10000;

    private TextureDownloader() {
    }

    static File download(String url, File file, boolean skin, Proxy proxy) throws IOException {
        Path target = file.toPath().toAbsolutePath();
        if (Files.exists(target)) {
            try {
                validatePng(target, skin);
                return file;
            } catch (IOException invalidCache) {
                Files.delete(target);
            }
        }

        URLConnection opened = new URL(url).openConnection(proxy);
        if (!(opened instanceof HttpURLConnection)) {
            throw new IOException("Texture URL must use HTTP or HTTPS");
        }
        HttpURLConnection connection = (HttpURLConnection) opened;
        Path temporary = null;
        try {
            connection.setConnectTimeout(TIMEOUT_MILLIS);
            connection.setReadTimeout(TIMEOUT_MILLIS);
            int status = connection.getResponseCode();
            if (status < 200 || status >= 300) {
                throw new IOException("Texture HTTP status: " + status);
            }
            long contentLength = connection.getContentLengthLong();
            if (contentLength > MAX_BYTES) {
                throw new IOException("Texture exceeds 4 MiB");
            }

            Files.createDirectories(target.getParent());
            temporary = Files.createTempFile(target.getParent(), "texture-", ".tmp");
            int total = 0;
            try (InputStream input = connection.getInputStream();
                 OutputStream output = Files.newOutputStream(temporary)) {
                byte[] buffer = new byte[8192];
                int count;
                while ((count = input.read(buffer)) != -1) {
                    total += count;
                    if (total > MAX_BYTES) {
                        throw new IOException("Texture exceeds 4 MiB");
                    }
                    output.write(buffer, 0, count);
                }
            }
            if (contentLength >= 0 && contentLength != total) {
                throw new IOException("Incomplete texture response");
            }
            validatePng(temporary, skin);
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            return file;
        } finally {
            connection.disconnect();
            if (temporary != null) {
                Files.deleteIfExists(temporary);
            }
        }
    }

    private static void validatePng(Path file, boolean skin) throws IOException {
        if (Files.size(file) > MAX_BYTES) {
            throw new IOException("Texture exceeds 4 MiB");
        }
        byte[] bytes = Files.readAllBytes(file);
        try (ImageInputStream input = new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                throw new IOException("Texture is not a PNG");
            }
            ImageReader reader = readers.next();
            try {
                if (!"PNG".equalsIgnoreCase(reader.getFormatName())) {
                    throw new IOException("Texture is not a PNG");
                }
                reader.setInput(input);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (skin ? width != 64 || (height != 64 && height != 32)
                        : width < 1 || height < 1 || width > 1024 || height > 1024) {
                    throw new IOException("Invalid texture dimensions: " + width + "x" + height);
                }
                validateChunks(bytes);
                if (reader.read(0) == null) {
                    throw new IOException("Cannot decode PNG texture");
                }
            } finally {
                reader.dispose();
            }
        } catch (RuntimeException malformedImage) {
            throw new IOException("Invalid PNG texture", malformedImage);
        }
    }

    private static void validateChunks(byte[] bytes) throws IOException {
        // ImageIO can accept missing IEND chunks and does not check every chunk's CRC.
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(bytes))) {
            if (input.readLong() != 0x89504e470d0a1a0aL) {
                throw new IOException("Invalid PNG signature");
            }
            boolean first = true;
            boolean imageData = false;
            while (input.available() > 0) {
                int length = input.readInt();
                if (length < 0 || length > input.available() - 8) {
                    throw new IOException("Truncated PNG chunk");
                }
                int typeOffset = bytes.length - input.available();
                int type = input.readInt();
                if (first && (type != 0x49484452 || length != 13)) {
                    throw new IOException("Invalid PNG header");
                }
                first = false;
                CRC32 crc = new CRC32();
                crc.update(bytes, typeOffset, length + 4);
                input.skipBytes(length);
                if (input.readInt() != (int) crc.getValue()) {
                    throw new IOException("Invalid PNG chunk CRC");
                }
                if (type == 0x49444154) {
                    imageData = true;
                }
                if (type == 0x49454e44) {
                    if (length != 0 || !imageData || input.available() != 0) {
                        throw new IOException("Invalid PNG end chunk");
                    }
                    return;
                }
            }
            throw new IOException("Missing PNG end chunk");
        }
    }
}
