package org.gradle.plugins.fsm.util;

import de.espirit.firstspirit.module.Configuration;
import de.espirit.firstspirit.module.ServerEnvironment;

import javax.swing.*;
import java.awt.*;
import java.util.Set;

public class BaseConfiguration implements Configuration<ServerEnvironment> {
    @Override
    public boolean hasGui() {
        return false;
    }

    @Override
    public JComponent getGui(Frame frame) {
        return null;
    }

    @Override
    public void load() {

    }

    @Override
    public void store() {

    }

    @Override
    public Set<String> getParameterNames() {
        return null;
    }

    @Override
    public String getParameter(String s) {
        return null;
    }

    @Override
    public void init(String s, String s1, ServerEnvironment t) {

    }

    @Override
    public ServerEnvironment getEnvironment() {
        return null;
    }
}
