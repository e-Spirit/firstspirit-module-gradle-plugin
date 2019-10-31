package org.gradle.plugins.fsm;

import de.espirit.firstspirit.server.module.ModuleInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;

public class FSMPluginExtensionTest {

    FSMPluginExtension testling;

    @BeforeEach
    void setUp() {
        testling = new FSMPluginExtension();
    }

    @Test
    void testIsolatedModeIsDefault() {
        assertThat(testling.getResourceMode()).isEqualTo(ModuleInfo.Mode.ISOLATED);
    }

    @Test
    void testMinVersionIsDefault() {
        assertThat(testling.getAppendDefaultMinVersion()).isTrue();
    }


    @Test
    void testCanSetFsmDependencies() {
        final String fsmModuleName = "fsmModuleName";
        testling.setFsmDependencies(Collections.singletonList(fsmModuleName));
        assertThat(testling.getFsmDependencies()).contains(fsmModuleName);
    }


}