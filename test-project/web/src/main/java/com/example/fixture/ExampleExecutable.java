package com.example.fixture;

import com.espirit.moddev.components.annotations.PublicComponent;
import de.espirit.firstspirit.access.script.Executable;

import java.io.Writer;
import java.util.Map;

@PublicComponent(name = "ExampleExecutable")
public class ExampleExecutable implements Executable {

    @Override
    public Object execute(Map<String, Object> parameters, Writer out, Writer err) {
        return null;
    }
}
