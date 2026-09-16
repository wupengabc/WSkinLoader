package com.wupeng.wskinloader.client.config.screen.render.shader;

import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;
import org.junit.jupiter.api.Assertions;

/**
 * Smoke test that verifies the jqwik + JUnit Platform test runner is wired up
 * correctly for the shader-msaa-rendering feature's test source set.
 *
 * <p>This intentionally asserts a trivial invariant ({@code 1 + 1 == 2}) so a
 * failure here signals a test infrastructure problem rather than a product bug.
 * It is the companion to task 1.2 of the {@code shader-msaa-rendering} spec.
 */
class TestRunnerSmokeTest {

    @Property(tries = 10)
    void onePlusOneEqualsTwo(@ForAll @IntRange(min = -1000, max = 1000) int ignoredSeed) {
        Assertions.assertEquals(2, 1 + 1);
    }
}
