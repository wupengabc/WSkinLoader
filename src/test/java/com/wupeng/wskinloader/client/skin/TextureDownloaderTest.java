package com.wupeng.wskinloader.client.skin;

import com.sun.net.httpserver.HttpServer;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.CRC32;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;

public class TextureDownloaderTest {
    private static final int MAX_BYTES = 4 * 1024 * 1024;

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    private HttpServer server;
    private File directory;
    private File target;
    private byte[] skinPng;
    private final AtomicInteger requests = new AtomicInteger();

    @Before
    public void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0), 0);
        server.start();
        directory = temporaryFolder.newFolder("textures");
        target = new File(directory, "texture.png");
        skinPng = png(64, 64);
    }

    @After
    public void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    public void downloadsSkinPng() throws IOException {
        String url = serve(200, skinPng, false);
        assertSame(target, TextureDownloader.download(url, target, true, Proxy.NO_PROXY));
        assertArrayEquals(skinPng, Files.readAllBytes(target.toPath()));
        assertEquals(1, requests.get());
        assertDirectoryContainsOnlyTarget();
    }

    @Test
    public void acceptsOtherSuccessfulStatus() throws IOException {
        TextureDownloader.download(serve(201, skinPng, false), target, true, Proxy.NO_PROXY);
        assertArrayEquals(skinPng, Files.readAllBytes(target.toPath()));
    }

    @Test
    public void rejectsNotFound() throws IOException {
        assertRejected(serve(404, skinPng, false), true);
    }

    @Test
    public void rejectsHtmlWithSuccessfulStatus() throws IOException {
        assertRejected(serve(200, "<html>error</html>".getBytes(StandardCharsets.UTF_8), false), true);
    }

    @Test
    public void rejectsOtherImageFormats() throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(64, 64, BufferedImage.TYPE_INT_RGB), "JPEG", output);
        assertRejected(serve(200, output.toByteArray(), false), true);
    }

    @Test
    public void rejectsTruncatedImageData() throws IOException {
        assertRejected(serve(200, Arrays.copyOf(skinPng, skinPng.length / 2), false), true);
    }

    @Test
    public void rejectsMissingEndChunk() throws IOException {
        assertRejected(serve(200, Arrays.copyOf(skinPng, skinPng.length - 12), false), true);
    }

    @Test
    public void rejectsCorruptChunkCrc() throws IOException {
        byte[] damaged = skinPng.clone();
        damaged[damaged.length - 1] ^= 1;
        assertRejected(serve(200, damaged, false), true);
    }

    @Test
    public void rejectsCorruptImageDataWithValidCrc() throws IOException {
        byte[] damaged = skinPng.clone();
        ByteBuffer chunks = ByteBuffer.wrap(damaged);
        chunks.position(8);
        while (chunks.hasRemaining()) {
            int length = chunks.getInt();
            int typeOffset = chunks.position();
            int type = chunks.getInt();
            if (type == 0x49444154) {
                damaged[chunks.position()] = 0; // Invalid zlib compression method.
                CRC32 crc = new CRC32();
                crc.update(damaged, typeOffset, length + 4);
                chunks.putInt(chunks.position() + length, (int) crc.getValue());
                break;
            }
            chunks.position(chunks.position() + length + 4);
        }
        assertRejected(serve(200, damaged, false), true);
    }

    @Test
    public void rejectsWrongSkinWidth() throws IOException {
        assertRejected(serve(200, png(32, 64), false), true);
    }

    @Test
    public void rejectsWrongSkinHeight() throws IOException {
        assertRejected(serve(200, png(64, 128), false), true);
    }

    @Test
    public void acceptsLegacySkin() throws IOException {
        byte[] legacy = png(64, 32);
        TextureDownloader.download(serve(200, legacy, false), target, true, Proxy.NO_PROXY);
        assertArrayEquals(legacy, Files.readAllBytes(target.toPath()));
    }

    @Test
    public void acceptsCape() throws IOException {
        byte[] cape = png(128, 64);
        TextureDownloader.download(serve(200, cape, false), target, false, Proxy.NO_PROXY);
        assertArrayEquals(cape, Files.readAllBytes(target.toPath()));
    }

    @Test
    public void acceptsMaximumCapeDimensions() throws IOException {
        byte[] cape = png(1024, 1024);
        TextureDownloader.download(serve(200, cape, false), target, false, Proxy.NO_PROXY);
        assertArrayEquals(cape, Files.readAllBytes(target.toPath()));
    }

    @Test
    public void rejectsOversizedCapeWidth() throws IOException {
        assertRejected(serve(200, png(1025, 32), false), false);
    }

    @Test
    public void rejectsOversizedCapeHeight() throws IOException {
        assertRejected(serve(200, png(32, 1025), false), false);
    }

    @Test
    public void usesValidLocalCacheWithoutRequest() throws IOException {
        Files.write(target.toPath(), skinPng);
        assertSame(target, TextureDownloader.download(serve(404, new byte[0], false), target, true, Proxy.NO_PROXY));
        assertEquals(0, requests.get());
        assertArrayEquals(skinPng, Files.readAllBytes(target.toPath()));
        assertDirectoryContainsOnlyTarget();
    }

    @Test
    public void downloadsAgainForCorruptLocalCache() throws IOException {
        Files.write(target.toPath(), Arrays.copyOf(skinPng, skinPng.length - 12));
        TextureDownloader.download(serve(200, skinPng, false), target, true, Proxy.NO_PROXY);
        assertEquals(1, requests.get());
        assertArrayEquals(skinPng, Files.readAllBytes(target.toPath()));
        assertDirectoryContainsOnlyTarget();
    }

    @Test
    public void downloadsAgainForWrongLocalDimensions() throws IOException {
        Files.write(target.toPath(), png(32, 32));
        TextureDownloader.download(serve(200, skinPng, false), target, true, Proxy.NO_PROXY);
        assertEquals(1, requests.get());
        assertArrayEquals(skinPng, Files.readAllBytes(target.toPath()));
    }

    @Test
    public void removesInvalidCacheWhenDownloadFails() throws IOException {
        Files.write(target.toPath(), new byte[] {1, 2, 3});
        assertRejected(serve(404, skinPng, false), true);
        assertEquals(1, requests.get());
    }

    @Test
    public void rejectsOversizedDeclaredBody() throws IOException {
        assertRejected(serve(200, new byte[MAX_BYTES + 1], false), true);
    }

    @Test
    public void rejectsOversizedChunkedBody() throws IOException {
        assertRejected(serve(200, new byte[MAX_BYTES + 1], true), true);
    }

    @Test
    public void createsMissingParentDirectories() throws IOException {
        target = new File(directory, "nested/cache/skin.png");
        TextureDownloader.download(serve(200, skinPng, false), target, true, Proxy.NO_PROXY);
        assertArrayEquals(skinPng, Files.readAllBytes(target.toPath()));
        assertEquals(1, target.getParentFile().list().length);
    }

    @Test
    public void usesSuppliedHttpProxy() throws IOException {
        serve(200, skinPng, false);
        Proxy proxy = new Proxy(Proxy.Type.HTTP, server.getAddress());
        TextureDownloader.download("http://texture.invalid/texture", target, true, proxy);
        assertEquals(1, requests.get());
        assertArrayEquals(skinPng, Files.readAllBytes(target.toPath()));
    }

    private String serve(int status, byte[] body, boolean chunked) {
        server.createContext("/texture", exchange -> {
            requests.incrementAndGet();
            try {
                exchange.getResponseHeaders().set("Content-Type", "application/octet-stream");
                exchange.sendResponseHeaders(status, chunked || body.length == 0 ? 0 : body.length);
                exchange.getResponseBody().write(body);
            } catch (IOException disconnectedClient) {
                // Expected when the downloader rejects a response before consuming its body.
            } finally {
                exchange.close();
            }
        });
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/texture";
    }

    private void assertRejected(String url, boolean skin) {
        assertThrows(IOException.class, () -> TextureDownloader.download(url, target, skin, Proxy.NO_PROXY));
        assertFalse(target.exists());
        assertEquals(0, directory.list().length);
    }

    private void assertDirectoryContainsOnlyTarget() {
        assertArrayEquals(new String[] {target.getName()}, directory.list());
    }

    private static byte[] png(int width, int height) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0, 0, 0xff123456);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "PNG", output);
        return output.toByteArray();
    }
}
