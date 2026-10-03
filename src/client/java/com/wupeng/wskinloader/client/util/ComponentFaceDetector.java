package com.wupeng.wskinloader.client.util;

import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.Style;
import net.minecraft.world.scores.PlayerTeam;

/**
 * Detects whether a component already renders a player skin face.
 *
 * <p>Some servers put a player head into the tab list display name as a
 * {@code player} object component instead of relying on the vanilla head that
 * the client draws to the left of the name. When both are present the player
 * shows up with two heads, so the vanilla one is skipped instead.
 */
public final class ComponentFaceDetector {

    private ComponentFaceDetector() {
    }

    /**
     * Replicates the name the tab list actually draws:
     * {@code PlayerTabOverlay.getNameForDisplay} falls back to the team-formatted
     * name when no custom display name is set, and many servers embed the head
     * glyph in the team prefix instead. Spectator italics (the other decoration
     * applied there) do not affect face detection.
     */
    public static Component renderableTabName(PlayerInfo info) {
        Component displayName = info.getTabListDisplayName();
        return displayName != null
                ? displayName
                : PlayerTeam.formatNameForTeam(
                        info.getTeam(),
                        Component.literal(info.getProfile().name()));
    }

    /**
     * @return true when any style in {@code component} uses a player sprite font
     */
    public static boolean containsPlayerFace(Component component) {
        if (component == null) {
            return false;
        }
        Boolean found = component.visit((style, contents) -> isPlayerFaceStyle(style) ? java.util.Optional.of(true) : java.util.Optional.empty(), Style.EMPTY)
                .orElse(false);
        return Boolean.TRUE.equals(found);
    }

    private static boolean isPlayerFaceStyle(Style style) {
        return style != null && style.getFont() instanceof FontDescription.PlayerSprite;
    }
}
