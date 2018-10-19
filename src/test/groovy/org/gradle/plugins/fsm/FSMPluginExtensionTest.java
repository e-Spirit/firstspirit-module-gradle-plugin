package org.gradle.plugins.fsm;

import de.espirit.firstspirit.server.module.ModuleInfo;
import org.junit.Before;
import org.junit.Test;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.*;

public class FSMPluginExtensionTest {

    FSMPluginExtension testling;

    @Before
    public void setUp() {
        testling = new FSMPluginExtension();
    }

    @Test
    public void testIsolatedModeIsDefault() {
        assertThat(testling.getResourceMode(), is(ModuleInfo.Mode.ISOLATED));
    }
    @Test
    public void testMinVersionIsDefault() {
        assertThat(testling.getAppendDefaultMinVersion(), is(true));
    }

}