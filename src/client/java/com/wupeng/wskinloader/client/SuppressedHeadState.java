package com.wupeng.wskinloader.client;

import net.minecraft.resources.Identifier;

/**
 * Per-render-thread flag telling {@link com.wupeng.wskinloader.client.mixin.PlayerFaceExtractorMixin}
 * that the tab list is about to draw a head the server already rendered itself.
 *
 * <p>The flag is set while resolving the tab entry's skin and read immediately
 * afterwards by the face draw call in the same loop iteration, so it never
 * needs to survive across frames.
 */
public final class SuppressedHeadState {

    private static final ThreadLocal<Identifier> SUPPRESSED_TEXTURE = new ThreadLocal<>();

    private SuppressedHeadState() {
    }

    public static void set(Identifier texture) {
        SUPPRESSED_TEXTURE.set(texture);
    }

    public static void clear() {
        SUPPRESSED_TEXTURE.remove();
    }

    /** @return true when {@code texture} is the head the caller should not draw */
    public static boolean isSuppressed(Identifier texture) {
        Identifier suppressed = SUPPRESSED_TEXTURE.get();
        return suppressed != null && suppressed.equals(texture);
    }
}
