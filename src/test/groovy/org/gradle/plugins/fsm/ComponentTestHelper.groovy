package org.gradle.plugins.fsm

import com.espirit.moddev.components.annotations.*
import com.espirit.moddev.components.annotations.params.gadget.Scope
import de.espirit.common.base.ui.Gadget
import de.espirit.firstspirit.access.Language
import de.espirit.firstspirit.access.project.Resolution
import de.espirit.firstspirit.access.project.TemplateSet
import de.espirit.firstspirit.access.store.ContentProducer
import de.espirit.firstspirit.access.store.PageParams
import de.espirit.firstspirit.access.store.mediastore.Media
import de.espirit.firstspirit.access.store.templatestore.gom.GomElement
import de.espirit.firstspirit.agency.SpecialistsBroker
import de.espirit.firstspirit.client.access.editor.ValueEngineer
import de.espirit.firstspirit.client.access.editor.ValueEngineerContext
import de.espirit.firstspirit.client.access.editor.ValueEngineerFactory
import de.espirit.firstspirit.generate.FilenameFactory
import de.espirit.firstspirit.generate.PathLookup
import de.espirit.firstspirit.generate.UrlFactory
import de.espirit.firstspirit.module.*
import de.espirit.firstspirit.module.descriptor.ModuleDescriptor
import de.espirit.firstspirit.scheduling.ScheduleTaskForm
import de.espirit.firstspirit.scheduling.ScheduleTaskFormFactory
import org.gradle.plugins.fsm.util.BaseConfiguration
import org.gradle.plugins.fsm.util.BaseProjectApp
import org.gradle.plugins.fsm.util.BaseService
import org.gradle.plugins.fsm.util.BaseWebApp

import javax.swing.*
import java.awt.*


@ModuleComponent
class TestModuleImpl implements Module {

    @Override
    void init(ModuleDescriptor moduleDescriptor, ServerEnvironment serverEnvironment) {

    }

    @Override
    void installed() {

    }

    @Override
    void uninstalling() {

    }

    @Override
    void updated(String s) {

    }

}

@ModuleComponent(configurable = TestConfigurable.class)
class TestModuleImplWithConfiguration implements Module {
    @Override
    void init(ModuleDescriptor moduleDescriptor, ServerEnvironment serverEnvironment) {

    }

    @Override
    void installed() {

    }

    @Override
    void uninstalling() {

    }

    @Override
    void updated(String s) {

    }
}

class TestConfigurable implements Configuration<ServerEnvironment> {

    @Override
    boolean hasGui() {
        return false
    }

    @Override
    JComponent getGui(Frame applicationFrame) {
        return null
    }

    @Override
    void load() {

    }

    @Override
    void store() {

    }

    @Override
    Set<String> getParameterNames() {
        return null
    }

    @Override
    String getParameter(String s) {
        return null
    }

    @Override
    void init(String moduleName, String componentName, ServerEnvironment env) {

    }

    @Override
    ServerEnvironment getEnvironment() {
        return null
    }
}

@ProjectAppComponent(name = "TestMinimalProjectAppComponentName")
class TestMinimalProjectAppComponent extends BaseProjectApp {
}

@ProjectAppComponent(name = "TestProjectAppComponentName",
        displayName = "TestDisplayName",
        description = "TestDescription",
        configurable = TestConfigurable,
        resources = [@Resource(path = "lib/guava-24.0.jar", name = "com.google.guava:guava", version = "24.0")])
class TestProjectAppComponent extends BaseProjectApp {
    static class TestConfigurable extends BaseConfiguration { }
}

@ProjectAppComponent(name = "TestProjectAppComponentWithoutConfigurableName",
        displayName = "TestDisplayName",
        description = "TestDescription")
class TestProjectAppComponentWithoutConfigurable extends BaseProjectApp {

}

@ProjectAppComponent(name = "TestProjectAppComponentWithProperties",
        displayName = "TestDisplayName",
        description = "TestDescription",
        configurable = TestConfigurable,
        resources = [@Resource(path = "\$path", name = "\${project.jodaConvertDependency}", version = "\$version")])
class TestProjectAppComponentWithProperties extends BaseProjectApp {
}

@WebAppComponent(name = "TestMinimalWebAppComponentName", webXml = "/web.xml")
class TestMinimalWebAppComponent extends BaseWebApp {
}

@WebAppComponent(name = "TestWebAppComponentName",
        displayName = "TestDisplayName",
        description = "TestDescription",
        configurable = TestConfigurable,
        webXml = "/web.xml",
        webResources = [@WebResource(path = "lib/guava-24.0.jar", name = "com.google.guava:guava", version = "24.0"),
                @WebResource(targetPath = "targetPath", path = "lib/commons-lang-3.0.jar", name = "org.apache.commons:commons-lang3", version = "3.0", minVersion = "2.9", maxVersion = "3.1")])
class TestWebAppComponent extends BaseWebApp {
    static class TestConfigurable extends BaseConfiguration { }
}

@WebAppComponent(name = "TestWebAppComponentWithoutConfigurationName",
        displayName = "TestDisplayName",
        description = "TestDescription",
        webXml = "web0.xml",  // Please don't add leading slash, we want to test web.xml file handling without leading slash as well
        webResources = [@WebResource(path = "lib/guava-24.0.jar", name = "com.google.guava:guava", version = "24.0"),
                @WebResource(path = "lib/commons-lang-3.0.jar", name = "org.apache.commons:commons-lang3", version = "3.0", minVersion = "2.9", maxVersion = "3.1")])
class TestWebAppComponentWithoutConfiguration extends BaseWebApp {
    static class TestConfigurable extends BaseConfiguration { }
}

@PublicComponent(name = "TestMinimalPublicComponentName")
class TestMinimalPublicComponent {
}

@PublicComponent(name = "TestPublicComponentName", displayName = "TestDisplayName", description = "Component Description")
class TestPublicComponent {
}

@PublicComponent(name = "TestPublicComponentWithConfigName", displayName = "TestDisplayName", configurable = TestConfigurable.class)
class TestPublicComponentWithConfiguration {
}

@ScheduleTaskComponent(taskName = "Test task without display name")
class TestMinimalScheduleTaskComponent {
}

@ScheduleTaskComponent(taskName = "Test task without form", displayName = "Test Task Display Name", description = "A task for test purposes")
class TestScheduleTaskComponentWithoutForm {
}

@ScheduleTaskComponent(taskName = "Test task with configurable", description = "A task for test purposes", configurable = TestConfigurable.class)
class TestScheduleTaskComponentWithConfigurable {
}

@ScheduleTaskComponent(taskName = "Test task with form", description = "A task for test purposes", formClass = TestScheduleTaskFormFactory.class)
class TestScheduleTaskComponentWithForm {

}

@GadgetComponent(name = "Test gadget")
class TestMinimalGadgetComponent {

}

class TestGadgetFactoryOne implements GadgetFactory<Gadget, GomElement, GadgetContext> {

    @Override
    Gadget create(final GadgetContext gadgetContext) {
        return null
    }
}

class TestGadgetFactoryTwo implements GadgetFactory<Gadget, GomElement, GadgetContext> {

    @Override
    Gadget create(final GadgetContext gadgetContext) {
        return null
    }
}

class TestValueEngineerFactory implements ValueEngineerFactory {

    @Override
    Class getType() {
        return null
    }

    @Override
    ValueEngineer create(final ValueEngineerContext valueEngineerContext) {
        return null
    }
}

@GadgetComponent(name = "Test gadget with unimplemented factory", factories = [GadgetFactory.class])
class TestGadgetComponentWithUnimplementedFactory {

}

@GadgetComponent(name = "Test gadget with one factory", factories = [TestGadgetFactoryOne.class])
class TestGadgetComponentWithOneFactory {

}

@GadgetComponent(name = "Test gadget with more than one factory", factories = [TestGadgetFactoryOne.class, TestGadgetFactoryTwo.class])
class TestGadgetComponentWithMoreThanOneFactory {

}

@GadgetComponent(name = "Test gadget with all attributes", description = "The description", factories = [TestGadgetFactoryOne],
        valueEngineerFactory = TestValueEngineerFactory.class, scopes = Scope.DATA)
class TestGadgetComponentWithAllAttributes {

}

class TestScheduleTaskFormFactory implements ScheduleTaskFormFactory {

    @Override
    ScheduleTaskForm createForm(final SpecialistsBroker specialistsBroker) {
        return null
    }
}

@ServiceComponent(name = "TestMinimalServiceComponentName")
class TestMinimalServiceComponent extends BaseService {
}

@ServiceComponent(name = "TestServiceComponentName",
        displayName = "TestDisplayName",
        description = "TestDescription",
        configurable = TestConfigurable)
class TestServiceComponent extends BaseService {
    static class TestConfigurable extends BaseConfiguration { }

    static class ServiceResource {}
}

@ServiceComponent(name = "TestServiceComponentWithoutConfigurableName",
        displayName = "TestDisplayName",
        description = "TestDescription")
class TestServiceComponentWithoutConfigurable extends BaseService {

}

@UrlFactoryComponent(name = "TestMinimalUrlFactoryComponentName")
class TestMinimalUrlFactoryComponent implements UrlFactory {
    @Override
    void init(Map<String, String> map, PathLookup pathLookup) {

    }

    @Override
    String getUrl(ContentProducer contentProducer, TemplateSet templateSet, Language language, PageParams pageParams) {
        return null
    }

    @Override
    String getUrl(Media media, Language language, Resolution resolution) {
        return null
    }
}

@UrlFactoryComponent(name = "TestUrlFactoryComponentName",
        displayName = "TestDisplayName",
        description = "TestDescription",
        useRegistry = true)
class TestUrlFactoryComponent extends TestMinimalUrlFactoryComponent {
}


@UrlFactoryComponent(name = "TestUrlFactoryWithFilenameFactoryComponentName",
        displayName = "TestDisplayName",
        description = "TestDescription",
        filenameFactory = TestFilenameFactory.class,
        useRegistry = true)
class TestUrlFactoryWithFilenameFactory extends TestMinimalUrlFactoryComponent {
}

class TestFilenameFactory implements FilenameFactory {
    @Override
    String getFilename(final String s, final ContentProducer contentProducer, final TemplateSet templateSet, final Language language, final PageParams pageParams) {
        return null
    }

    @Override
    String getFilename(final String s, final Media media, final Language language, final Resolution resolution) {
        return null
    }
}
