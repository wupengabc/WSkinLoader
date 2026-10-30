package com.wupeng.wskinloader.client.config;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * ModConfig 单元测试
 */
public class ModConfigTest {

    @Test
    public void testAllowedApiUrl_ValidHttpsUrl() {
        assertTrue(ModConfig.isAllowedApiUrl("https://littleskin.cn/skin/%name%.png"));
        assertTrue(ModConfig.isAllowedApiUrl("https://example.com/api/skin/%name%"));
    }

    @Test
    public void testAllowedApiUrl_ValidHttpUrl() {
        // HTTP 应该也被允许（为了向后兼容）
        assertTrue(ModConfig.isAllowedApiUrl("http://example.com/skin/%name%.png"));
        assertTrue(ModConfig.isAllowedApiUrl("http://[64:ff9b::808:808]/skin/%name%.png"));
        assertFalse(ModConfig.isAllowedApiUrl("http://[64:ff9b::7f00:1]/skin/%name%.png"));
        assertFalse(ModConfig.isAllowedApiUrl("http://[2002:7f00:1::]/skin/%name%.png"));
    }

    @Test
    public void testAllowedApiUrl_RejectsLocalhost() {
        assertFalse(ModConfig.isAllowedApiUrl("http://localhost/skin/%name%.png"));
        assertFalse(ModConfig.isAllowedApiUrl("https://localhost:8080/skin/%name%.png"));
    }

    @Test
    public void testAllowedApiUrl_RejectsLoopback() {
        assertFalse(ModConfig.isAllowedApiUrl("http://127.0.0.1/skin/%name%.png"));
        assertFalse(ModConfig.isAllowedApiUrl("http://[::1]/skin/%name%.png"));
    }

    @Test
    public void testAllowedApiUrl_RejectsPrivateIpv6Ranges() {
        assertFalse(ModConfig.isAllowedApiUrl("http://[fc00::1]/skin/%name%.png"));
        assertFalse(ModConfig.isAllowedApiUrl("http://[fd12:3456::1]/skin/%name%.png"));
        assertFalse(ModConfig.isAllowedApiUrl("http://[fe80::1]/skin/%name%.png"));
        assertFalse(ModConfig.isAllowedApiUrl("http://[::ffff:127.0.0.1]/skin/%name%.png"));
        assertFalse(ModConfig.isAllowedApiUrl("http://[::ffff:10.0.0.1]/skin/%name%.png"));
    }

    @Test
    public void testAllowedApiUrl_RejectsAuthorityAmbiguity() {
        assertFalse(ModConfig.isAllowedApiUrl("http://user@example.com/skin/%name%.png"));
        assertFalse(ModConfig.isAllowedApiUrl("http://example.com/skin/%name%.png#fragment"));
        assertFalse(ModConfig.isAllowedApiUrl("http://example.com:70000/skin/%name%.png"));
        assertFalse(ModConfig.isAllowedApiUrl("http://host.local/skin/%name%.png"));
    }

    @Test
    public void testAllowedApiUrl_RejectsInvalidScheme() {
        assertFalse(ModConfig.isAllowedApiUrl("ftp://example.com/skin/%name%.png"));
        assertFalse(ModConfig.isAllowedApiUrl("file:///skin/%name%.png"));
        assertFalse(ModConfig.isAllowedApiUrl("javascript:alert(1)"));
    }

    @Test
    public void testAllowedApiUrl_RejectsNullOrBlank() {
        assertFalse(ModConfig.isAllowedApiUrl(null));
        assertFalse(ModConfig.isAllowedApiUrl(""));
        assertFalse(ModConfig.isAllowedApiUrl("   "));
    }

    @Test
    public void testAllowedApiUrl_RejectsTooLong() {
        String longUrl = "https://example.com/" + "a".repeat(600);
        assertFalse(ModConfig.isAllowedApiUrl(longUrl));
    }

    @Test
    public void testAllowedApiUrl_RejectsInvalidUrl() {
        assertFalse(ModConfig.isAllowedApiUrl("not a url"));
        assertFalse(ModConfig.isAllowedApiUrl("https://"));
    }

    @Test
    public void testApiConfigDeepCopy() {
        ModConfig.ApiConfig original = new ModConfig.ApiConfig(
            "https://example.com/skin/%name%.png",
            "Example"
        );

        ModConfig.ApiConfig copy = original.deepCopy();

        assertEquals(original.url, copy.url);
        assertEquals(original.alias, copy.alias);
        assertNotSame(original, copy);
    }

    @Test
    public void testPlayerOverrideDeepCopy() {
        ModConfig.PlayerOverride original = new ModConfig.PlayerOverride();
        original.skipPremiumCheck = true;
        original.modelType = "slim";
        original.skinSourcePlayer = "TestPlayer";
        original.skin.useCustomApi = true;
        original.skin.apiIndex = 1;

        ModConfig.PlayerOverride copy = original.deepCopy();

        assertEquals(original.skipPremiumCheck, copy.skipPremiumCheck);
        assertEquals(original.modelType, copy.modelType);
        assertEquals(original.skinSourcePlayer, copy.skinSourcePlayer);
        assertEquals(original.skin.useCustomApi, copy.skin.useCustomApi);
        assertEquals(original.skin.apiIndex, copy.skin.apiIndex);
        assertNotSame(original, copy);
    }

    @Test
    public void testTextureOverrideIsValid() {
        ModConfig.TextureOverride override = new ModConfig.TextureOverride();

        // apiIndex = -1 (尝试所有) 总是有效
        override.apiIndex = -1;
        assertTrue(override.isValid(5));
        assertTrue(override.isValid(0));

        // apiIndex 在范围内
        override.apiIndex = 0;
        assertTrue(override.isValid(5));

        override.apiIndex = 4;
        assertTrue(override.isValid(5));

        // apiIndex 超出范围
        override.apiIndex = 5;
        assertFalse(override.isValid(5));

        override.apiIndex = 10;
        assertFalse(override.isValid(5));

        override.apiIndex = -2;
        assertFalse(override.isValid(5));
    }

    @Test
    public void testConfigDataValidation() {
        ModConfig.ConfigData data = new ModConfig.ConfigData();

        // 空配置应该是有效的
        assertNull(data.validate());

        // 添加有效的 API
        data.skinApis.add(new ModConfig.ApiConfig("https://example.com/skin/%name%.png", "Example"));
        assertNull(data.validate());

        // 添加无效的 API（空 URL）
        data.skinApis.add(new ModConfig.ApiConfig("", "Invalid"));
        assertNotNull(data.validate());

        // 清理并测试玩家覆盖
        data.skinApis.clear();
        data.skinApis.add(new ModConfig.ApiConfig("https://example.com/skin/%name%.png", "Example"));

        // 添加有效的玩家覆盖
        ModConfig.PlayerOverride override = new ModConfig.PlayerOverride();
        data.playerOverrides.put("TestPlayer", override);
        assertNull(data.validate());

        // 添加无效的玩家覆盖（apiIndex 超出范围）
        override.skin.apiIndex = 10;
        assertNotNull(data.validate());
    }
}
