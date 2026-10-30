package com.wupeng.wskinloader.client.skin;

import net.minecraft.core.ClientAsset;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.PlayerModelType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executor;
import static org.junit.jupiter.api.Assertions.*;

class TextureOwnershipTest {
    private final ArrayDeque<Runnable> queued = new ArrayDeque<>();
    private final List<Identifier> released = new ArrayList<>();

    @AfterEach
    void cleanup() {
        SafeTextureDownloader.releaseAll();
        while (!queued.isEmpty()) queued.removeFirst().run();
    }

    private ClientAsset.Texture pending(UUID uuid, long token, String folder) {
        Identifier id = Identifier.fromNamespaceAndPath("wskinloader", folder + uuid + "_" + token);
        ClientAsset.Texture asset = new ClientAsset.DownloadedTexture(id, "http://example.com/skin.png");
        SafeTextureDownloader.registerPending(new SafeTextureDownloader.Registered(id, queued::add,
                () -> released.add(id), "http://example.com/skin.png", Path.of("cache/skin"),
                new SafeTextureDownloader.DownloadedTexture(asset, PlayerModelType.SLIM)));
        return asset;
    }

    @Test
    void invalidationCannotReleaseAReplacementAcceptedBeforeQueuedCleanupRuns() {
        UUID uuid = UUID.randomUUID();
        var old = pending(uuid, 1, "skins/");
        SafeTextureDownloader.accept(old);
        SafeTextureDownloader.release(uuid);
        var replacement = pending(uuid, 2, "skins/");
        SafeTextureDownloader.accept(replacement);
        queued.removeFirst().run();
        assertEquals(List.of(old.texturePath()), released);
        var reused = SafeTextureDownloader.downloadDetailed(null,
                Identifier.fromNamespaceAndPath("wskinloader", "skins/" + uuid + "_3"),
                Path.of("cache/skin"), "http://example.com/skin.png", true, true, () -> true).join();
        assertSame(replacement, reused.asset());
        assertTrue(queued.isEmpty());
    }

    @Test
    void invalidationReclaimsAcceptedAndPendingSkinAndCapeExactlyOnce() {
        UUID uuid = UUID.randomUUID();
        var skin = pending(uuid, 1, "skins/");
        SafeTextureDownloader.accept(skin);
        var cape = pending(uuid, 1, "capes/");
        SafeTextureDownloader.release(uuid);
        SafeTextureDownloader.accept(cape);
        SafeTextureDownloader.discard(cape);
        while (!queued.isEmpty()) queued.removeFirst().run();
        assertEquals(2, released.size());
        assertTrue(released.contains(skin.texturePath()));
        assertTrue(released.contains(cape.texturePath()));
    }

    @Test
    void replacementAndDuplicateDiscardDoNotReleaseTheCurrentTexture() {
        UUID uuid = UUID.randomUUID();
        var old = pending(uuid, 1, "skins/");
        SafeTextureDownloader.accept(old);
        var current = pending(uuid, 2, "skins/");
        SafeTextureDownloader.accept(current);
        SafeTextureDownloader.discard(current);
        SafeTextureDownloader.accept(current);
        while (!queued.isEmpty()) queued.removeFirst().run();
        assertEquals(List.of(old.texturePath()), released);
    }

    @Test
    void stoppedRenderExecutorDoesNotBreakInvalidation() {
        UUID uuid = UUID.randomUUID();
        var id = Identifier.fromNamespaceAndPath("wskinloader", "skins/" + uuid + "_1");
        Executor stopped = task -> { throw new java.util.concurrent.RejectedExecutionException(); };
        var asset = new ClientAsset.DownloadedTexture(id, "http://example.com/skin.png");
        SafeTextureDownloader.registerPending(new SafeTextureDownloader.Registered(id, stopped,
                () -> fail("Cleanup must stay on render executor"), "", Path.of("cache/skin"),
                new SafeTextureDownloader.DownloadedTexture(asset, PlayerModelType.WIDE)));
        assertDoesNotThrow(() -> SafeTextureDownloader.release(uuid));
        assertDoesNotThrow(() -> SafeTextureDownloader.discard(asset));
    }
}
