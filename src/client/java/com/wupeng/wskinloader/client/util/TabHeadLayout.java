package com.wupeng.wskinloader.client.util;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.network.chat.Component;

/** Head visibility computed from the names actually selected for one Tab frame. */
public final class TabHeadLayout {
    private final Set<UUID> serverFaces = new HashSet<>();
    private boolean enabled;
    private boolean deduplicate;
    private boolean hasEntries;
    private boolean allHaveFaces;

    public void begin(boolean enabled, boolean deduplicate) {
        serverFaces.clear();
        this.enabled = enabled;
        this.deduplicate = enabled && deduplicate;
        hasEntries = false;
        allHaveFaces = true;
    }

    public void record(UUID player, Component name) {
        if (!deduplicate) {
            return;
        }
        boolean hasFace = ComponentFaceDetector.containsPlayerFace(name);
        hasEntries = true;
        allHaveFaces &= hasFace;
        if (hasFace) {
            serverFaces.add(player);
        }
    }

    public boolean showHeads(boolean vanilla) {
        return enabled ? !(deduplicate && hasEntries && allHaveFaces) : vanilla;
    }

    public boolean suppressHead(UUID player) {
        return deduplicate && serverFaces.contains(player);
    }
}
