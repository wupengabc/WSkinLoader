package com.wupeng.wskinloader.client.skin;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

class MojangApiCheckerTest {
    @Test
    void parsesPremiumProfileWithoutNetwork() {
        MojangApiChecker.PlayerProfile profile = MojangApiChecker.parseProfile(
                "{\"id\":\"069a79f444e94726a5befca90e38aaf5\",\"name\":\"Notch\"}");

        assertEquals("069a79f4-44e9-4726-a5be-fca90e38aaf5", profile.uuid.toString());
        assertEquals("Notch", profile.name);
        assertTrue(profile.isPremium);
    }

    @Test
    void malformedProfileCannotBeParsedAsMissingAccount() {
        assertThrows(RuntimeException.class, () -> MojangApiChecker.parseProfile("{"));
        assertThrows(IllegalArgumentException.class, () -> MojangApiChecker.parseProfile("{}"));
        assertThrows(IllegalArgumentException.class,
                () -> MojangApiChecker.parseProfile("{\"id\":123,\"name\":\"Name\"}"));
    }

    @Test
    void positiveAndNegativeProfileEntriesHaveDifferentExpirations() {
        long now = System.currentTimeMillis();
        var premium = new MojangApiChecker.CachedProfile(
                new MojangApiChecker.PlayerProfile(java.util.UUID.randomUUID(), "Name", true), now);
        var missing = new MojangApiChecker.CachedProfile(
                new MojangApiChecker.PlayerProfile(null, "Name", false), now);

        assertTrue(premium.isFresh(now + 59 * 60 * 1000L));
        assertFalse(premium.isFresh(now + 60 * 60 * 1000L));
        assertTrue(missing.isFresh(now + 4 * 60 * 1000L));
        assertFalse(missing.isFresh(now + 5 * 60 * 1000L));
    }
}
