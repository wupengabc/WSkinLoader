package com.wupeng.wskinloader.client.skin;

import com.wupeng.wskinloader.client.util.ComponentFaceDetector;
import com.wupeng.wskinloader.client.util.TabHeadLayout;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.contents.objects.AtlasSprite;
import net.minecraft.network.chat.contents.objects.PlayerSprite;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.component.ResolvableProfile;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class TabHeadLayoutTest {
    private final UUID alice = UUID.randomUUID();
    private final UUID bob = UUID.randomUUID();
    private final TabHeadLayout layout = new TabHeadLayout();

    private Component face() {
        return Component.object(new PlayerSprite(ResolvableProfile.createUnresolved("Alice"), true));
    }

    @Test
    public void detectsRealPlayerObjectInNestedName() {
        assertTrue(ComponentFaceDetector.containsPlayerFace(face()));
        assertTrue(ComponentFaceDetector.containsPlayerFace(
                Component.literal("[Team] ").append(Component.empty().append(face())).append(" Alice")));
    }

    @Test
    public void detectsTranslatedFaceOnlyWhenArgumentIsRendered() {
        assertTrue(ComponentFaceDetector.containsPlayerFace(
                Component.translatableWithFallback("wskinloader.test.name", "%s Alice", face())));
        assertFalse(ComponentFaceDetector.containsPlayerFace(
                Component.translatableWithFallback("wskinloader.test.name", "Alice", face())));
    }

    @Test
    public void detectsFlattenedPlayerFontButNotEmptyStyledText() {
        for (Component component : face().toFlatList()) {
            assertTrue(ComponentFaceDetector.containsPlayerFace(component));
        }
        assertFalse(ComponentFaceDetector.containsPlayerFace(Component.empty().withStyle(style ->
                style.withFont(new FontDescription.PlayerSprite(ResolvableProfile.createUnresolved("Alice"), true)))));
    }

    @Test
    public void ignoresPlainNamesCustomFontsAndAtlasIcons() {
        assertFalse(ComponentFaceDetector.containsPlayerFace(null));
        assertFalse(ComponentFaceDetector.containsPlayerFace(Component.literal("Alice")));
        Identifier icon = Identifier.fromNamespaceAndPath("server", "head_icon");
        assertFalse(ComponentFaceDetector.containsPlayerFace(Component.literal("A")
                .withStyle(style -> style.withFont(new FontDescription.Resource(icon)))));
        assertFalse(ComponentFaceDetector.containsPlayerFace(Component.object(
                new AtlasSprite(AtlasSprite.DEFAULT_ATLAS, icon))));
    }

    @Test
    public void mixedListSuppressesOnlyMatchingPlayerAndKeepsColumn() {
        layout.begin(true, true);
        layout.record(alice, face());
        layout.record(bob, Component.literal("Bob"));
        assertTrue(layout.suppressHead(alice));
        assertFalse(layout.suppressHead(bob));
        assertTrue(layout.showHeads(false));
        assertTrue(layout.showHeads(true));
    }

    @Test
    public void allFacesRemoveColumnEvenOnEncryptedServers() {
        layout.begin(true, true);
        layout.record(alice, face());
        layout.record(bob, face());
        assertFalse(layout.showHeads(false));
        assertFalse(layout.showHeads(true));
    }

    @Test
    public void disablingDeduplicationKeepsHeadsWithServerFaces() {
        layout.begin(true, false);
        layout.record(alice, face());
        assertFalse(layout.suppressHead(alice));
        assertTrue(layout.showHeads(false));
        assertTrue(layout.showHeads(true));
    }

    @Test
    public void disablingModHeadsPreservesBothVanillaVisibilityOperands() {
        layout.begin(false, true);
        layout.record(alice, face());
        assertFalse(layout.suppressHead(alice));
        assertFalse(layout.showHeads(false));
        assertTrue(layout.showHeads(true));
    }

    @Test
    public void emptyListDoesNotTriggerAllFacesSuppression() {
        layout.begin(true, true);
        assertTrue(layout.showHeads(false));
    }

    @Test
    public void nextFrameDoesNotLatchFacesOrSuppressNewNames() {
        layout.begin(true, true);
        layout.record(alice, face());
        assertFalse(layout.showHeads(true));
        layout.begin(true, true);
        layout.record(alice, Component.literal("Alice"));
        layout.record(bob, Component.literal("Bob"));
        assertTrue(layout.showHeads(false));
        assertFalse(layout.suppressHead(alice));
        assertFalse(layout.suppressHead(bob));
    }
}
