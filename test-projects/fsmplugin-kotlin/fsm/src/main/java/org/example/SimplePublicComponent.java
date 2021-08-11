package org.example;

import com.espirit.moddev.components.annotations.PublicComponent;

@PublicComponent(name = "MySimplePublicComponent")
public class SimplePublicComponent {

    @PublicComponent(name = "MyOtherSimplePublicComponent")
    public static class AnotherSimplePublicComponent {
    }
}
