package com.wupeng.wskinloader.client.skin;

import com.google.gson.JsonSyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MojangSessionApiTest {
    @Test
    void successfulEmptyTexturesAreDistinctFromUnavailableResponse() {
        MojangSessionApi.ProfileTextures empty = MojangSessionApi.parseProfileTextures("{\"properties\":[]}");

        assertNotNull(empty);
        assertNull(empty.skinUrl);
        assertNull(empty.capeUrl);
        assertFalse(empty.isSlim);
    }

    @Test
    void selectsTexturesPropertyByNameRatherThanPosition() {
        String encoded = Base64.getEncoder().encodeToString(
                "{\"textures\":{\"SKIN\":{\"url\":\"https://textures.example/skin\",\"metadata\":{\"model\":\"slim\"}}}}"
                        .getBytes(StandardCharsets.UTF_8));
        String payload = "{\"properties\":[{\"name\":\"other\",\"value\":\"not-base64\"},"
                + "{\"name\":\"textures\",\"value\":\"" + encoded + "\"}]}";

        MojangSessionApi.ProfileTextures parsed = MojangSessionApi.parseProfileTextures(payload);

        assertEquals("https://textures.example/skin", parsed.skinUrl);
        assertNull(parsed.capeUrl);
        assertTrue(parsed.isSlim);
    }

    @Test
    void malformedSessionPayloadFailsInsteadOfBecomingEmptyTextures() {
        assertThrows(JsonSyntaxException.class, () -> MojangSessionApi.parseProfileTextures("{"));
        assertThrows(IllegalArgumentException.class,
                () -> MojangSessionApi.parseProfileTextures("{\"properties\":[{\"name\":\"textures\",\"value\":\"%%%\"}]}"));
    }
}
