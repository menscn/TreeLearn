package com.treelearn.web;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.treelearn.core.domain.PromptPurpose;
import org.junit.jupiter.api.Test;

/**
 * T-2026-10-10-003: proves the shared pure-JVM modules are consumable from this
 * build. The {@code PromptPurpose} reference is a compile-time dependency on
 * the shared :core module (a missing or wrong projectDir reference fails
 * compilation); the assertion adds the runtime check that the class also loads
 * and initialises on the test classpath.
 */
class SharedClasspathTest {

    @Test
    void sharedCoreModuleIsOnTheCompileAndRuntimeClasspath() {
        assertTrue(PromptPurpose.values().length > 0,
                "the shared :core domain enum must be reachable from the web backend");
    }
}
