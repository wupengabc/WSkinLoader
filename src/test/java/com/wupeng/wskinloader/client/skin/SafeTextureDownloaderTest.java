package com.wupeng.wskinloader.client.skin;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class SafeTextureDownloaderTest {
    @Test
    void failedRefreshPreservesExistingDiskCache(@org.junit.jupiter.api.io.TempDir java.nio.file.Path directory) throws Exception {
        var path = directory.resolve("cached-skin");
        byte[] previous = new byte[] {9, 8, 7, 6};
        java.nio.file.Files.write(path, previous);
        assertThrows(java.util.concurrent.CompletionException.class, () -> SafeTextureDownloader.download(
                null, net.minecraft.resources.Identifier.fromNamespaceAndPath("wskinloader", "skins/test_1"),
                path, "file:///invalid-refresh", true, true, () -> true).join());
        assertArrayEquals(previous, java.nio.file.Files.readAllBytes(path));
    }

    @Test
    void modelDetectionUsesTheOriginalImageBytes() throws Exception {
        var skin = new java.awt.image.BufferedImage(64, 64, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        ByteArrayOutputStream data = new ByteArrayOutputStream();
        javax.imageio.ImageIO.write(skin, "png", data);
        assertEquals(net.minecraft.world.entity.player.PlayerModelType.SLIM, SafeTextureDownloader.detectModel(data.toByteArray()));
        skin.setRGB(50, 16, 0xFFFFFFFF);
        data.reset();
        javax.imageio.ImageIO.write(skin, "png", data);
        assertEquals(net.minecraft.world.entity.player.PlayerModelType.WIDE, SafeTextureDownloader.detectModel(data.toByteArray()));
        data.reset();
        javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(64, 32, 2), "png", data);
        assertEquals(net.minecraft.world.entity.player.PlayerModelType.WIDE, SafeTextureDownloader.detectModel(data.toByteArray()));
    }

    @Test
    void httpRemainsSupportedIncludingRelativeAndHttpsRedirects() throws Exception {
        URI from = URI.create("http://example.com/skins/a.png");
        assertEquals(URI.create("http://example.com/new/a.png"), SafeTextureDownloader.redirectTarget(from, "/new/a.png"));
        assertEquals(URI.create("https://example.com/a.png"), SafeTextureDownloader.redirectTarget(from, "https://example.com/a.png"));
        assertEquals("http://example.com/Player.png", CustomSkinLoader.buildUrl("http://example.com/%name%.png", "Player"));
    }

    @Test
    void nat64UsesEmbeddedPublicIpv4Policy() throws Exception {
        assertTrue(SafeTextureDownloader.isPublicAddress(InetAddress.getByName("64:ff9b::808:808")));
        assertFalse(SafeTextureDownloader.isPublicAddress(InetAddress.getByName("64:ff9b::7f00:1")));
        assertFalse(SafeTextureDownloader.isPublicAddress(InetAddress.getByName("64:ff9b::a00:1")));
        assertFalse(SafeTextureDownloader.isPublicAddress(InetAddress.getByName("2002:7f00:1::")));
        assertFalse(SafeTextureDownloader.isPublicAddress(InetAddress.getByName("2001::1")));
    }

    @Test
    void rejectsNonImageBytesAndWrongSkinDimensions() {
        assertThrows(IOException.class, () -> SafeTextureDownloader.validateImage(new byte[] {1, 2, 3}, true));
        assertThrows(IOException.class, () -> SafeTextureDownloader.validateImage(new byte[] {1, 2, 3}, false));
    }

    @Test
    void boundsDecodedImageDimensionsBeforeNativeAllocation() throws Exception {
        ByteArrayOutputStream data = new ByteArrayOutputStream();
        javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(64, 64, 2), "png", data);
        assertDoesNotThrow(() -> SafeTextureDownloader.validateImage(data.toByteArray(), true));
        data.reset();
        javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(128, 64, 2), "png", data);
        assertThrows(IOException.class, () -> SafeTextureDownloader.validateImage(data.toByteArray(), true));
    }

    @Test
    void redirectsRejectInternalTargetsAndTlsDowngrade() throws Exception {
        URI from = URI.create("https://example.com/skin/a.png");
        assertEquals(URI.create("https://example.com/cdn/a.png"),
                SafeTextureDownloader.redirectTarget(from, "/cdn/a.png"));
        assertThrows(IOException.class, () -> SafeTextureDownloader.redirectTarget(from, "http://example.com/a"));
        assertThrows(IOException.class, () -> SafeTextureDownloader.redirectTarget(from, "https://127.0.0.1/a"));
        assertThrows(IOException.class, () -> SafeTextureDownloader.redirectTarget(from, "https://[fd00::1]/a"));
    }

    @Test
    void httpFramingBoundsAndTruncationAreEnforced() throws Exception {
        long deadline = Long.MAX_VALUE;
        assertArrayEquals(new byte[] {1, 2}, SafeTextureDownloader.readBody(
                new ByteArrayInputStream(new byte[] {1, 2, 3}), 2, deadline));
        assertThrows(IOException.class, () -> SafeTextureDownloader.readBody(
                new ByteArrayInputStream(new byte[] {1}), 2, deadline));
        assertThrows(IOException.class, () -> SafeTextureDownloader.readBody(
                new ByteArrayInputStream(new byte[1_048_577]), -1, deadline));
        assertArrayEquals("test".getBytes(StandardCharsets.US_ASCII), SafeTextureDownloader.readChunked(
                bytes("4\r\ntest\r\n0\r\n\r\n"), deadline));
        assertThrows(IOException.class, () -> SafeTextureDownloader.readChunked(bytes("4\r\nte"), deadline));
    }

    @Test
    void invalidFirstApiDoesNotAbortRemainingCandidates() {
        var apis = java.util.List.of(new com.wupeng.wskinloader.client.config.ModConfig.ApiConfig(
                "file:///bad", "bad"), new com.wupeng.wskinloader.client.config.ModConfig.ApiConfig(
                "http://127.0.0.1/bad", "bad"));
        java.util.concurrent.atomic.AtomicInteger checks = new java.util.concurrent.atomic.AtomicInteger();
        var result = CustomSkinLoader.load(apis, "Player", null, java.nio.file.Path.of("cache/skins/00/base"),
                null, com.mojang.authlib.minecraft.MinecraftProfileTexture.Type.SKIN, -1, false,
                () -> { checks.incrementAndGet(); return true; }).join();
        assertNull(result);
        assertEquals(3, checks.get());
    }

    private static ByteArrayInputStream bytes(String value) {
        return new ByteArrayInputStream(value.getBytes(StandardCharsets.US_ASCII));
    }

    @Test
    void addressPolicyRejectsPrivateAndSpecialUseAddresses() throws Exception {
        for (String ip : new String[] {"127.0.0.1", "10.1.2.3", "169.254.1.1", "192.168.1.1", "100.64.0.2",
                "224.0.0.1", "::1", "fc00::1", "fd12::1234", "fe80::1", "ff02::1", "::ffff:127.0.0.1"}) {
            assertFalse(SafeTextureDownloader.isPublicAddress(InetAddress.getByName(ip)), ip);
        }
    }

    @Test
    void allowsPublicAddressAndKeepsUrlCachesDistinctAndStable() throws Exception {
        assertTrue(SafeTextureDownloader.isPublicAddress(InetAddress.getByName("1.1.1.1")));
        assertTrue(SafeTextureDownloader.isPublicAddress(InetAddress.getByName("2606:4700:4700::1111")));
        var root = java.nio.file.Path.of("cache", "wskinloader", "skins", "00", "base");
        var first = CustomSkinLoader.sourceCachePath(root, "https://one.example/skin/a.png");
        var again = CustomSkinLoader.sourceCachePath(root, "https://one.example/skin/a.png");
        var other = CustomSkinLoader.sourceCachePath(root, "https://two.example/skin/a.png");
        assertEquals(first, again);
        assertNotEquals(first, other);
        assertEquals(first, CustomSkinLoader.sourceCachePath(
                java.nio.file.Path.of("cache", "wskinloader", "skins", "ff", "another-player"),
                "https://one.example/skin/a.png"));
    }
}
