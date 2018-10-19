package org.example;

import com.espirit.moddev.components.annotations.WebAppComponent;
import de.espirit.firstspirit.module.AbstractWebApp;
import de.espirit.firstspirit.module.*;

import javax.swing.*;
import java.awt.*;
import java.util.Set;

@WebAppComponent(name = "SimpleWebApp",
        displayName = "SimpleWebApp",
        description = "This is a simple WebApp",
        configurable = SimpleWebApp.WebAppConfigurable.class,
        webXml = "/lib/web.xml"
//        TODO: This doesn't work out currently
//        webResources = "<resource>web/abtesting.tld</resource>\n" +
//            "<resource target=\"web/images/\">web/images/abtest_icon_selected.png</resource>\n" +
//            "<resource target=\"web/images/\">web/images/abtest_icon.png</resource>\n" +
//            "<resource target=\"web/images/\">web/images/client_abtest_icon_selected.png</resource>\n" +
//            "<resource target=\"web/images/\">web/images/client_abtest_icon.png</resource>\n"
)
public class SimpleWebApp extends AbstractWebApp {
    public static class WebAppConfigurable implements Configuration<ServerEnvironment> {

        @Override
        public boolean hasGui(){
            return false;
        }

        @Override
        public JComponent getGui(Frame var1){
            return null;
        }

        @Override
        public void load(){

        }

        @Override
        public void store(){

        }

        @Override
        public Set<String> getParameterNames(){
            return null;
        }

        @Override
        public String getParameter(String var1){
            return null;
        }

        @Override
        public void init(String var1, String var2, ServerEnvironment var3) {

        }
        @Override
        public ServerEnvironment getEnvironment() {
            return null;
        }
    }
}
