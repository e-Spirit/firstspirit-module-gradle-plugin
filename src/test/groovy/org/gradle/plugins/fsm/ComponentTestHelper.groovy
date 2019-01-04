package org.gradle.plugins.fsm

import com.espirit.moddev.components.annotations.ModuleComponent
import com.espirit.moddev.components.annotations.ProjectAppComponent
import com.espirit.moddev.components.annotations.PublicComponent
import com.espirit.moddev.components.annotations.Resource
import com.espirit.moddev.components.annotations.ScheduleTaskComponent
import com.espirit.moddev.components.annotations.ServiceComponent
import com.espirit.moddev.components.annotations.UrlFactoryComponent
import com.espirit.moddev.components.annotations.WebAppComponent
import com.espirit.moddev.components.annotations.WebResource
import de.espirit.firstspirit.access.Language
import de.espirit.firstspirit.access.project.Resolution
import de.espirit.firstspirit.access.project.TemplateSet
import de.espirit.firstspirit.access.store.ContentProducer
import de.espirit.firstspirit.access.store.PageParams
import de.espirit.firstspirit.access.store.mediastore.Media
import de.espirit.firstspirit.agency.SpecialistsBroker
import de.espirit.firstspirit.generate.PathLookup
import de.espirit.firstspirit.generate.UrlFactory
import de.espirit.firstspirit.module.Configuration
import de.espirit.firstspirit.module.Module
import de.espirit.firstspirit.module.ServerEnvironment
import de.espirit.firstspirit.module.descriptor.ModuleDescriptor
import de.espirit.firstspirit.module.descriptor.WebAppDescriptor
import de.espirit.firstspirit.scheduling.ScheduleTaskForm
import de.espirit.firstspirit.scheduling.ScheduleTaskFormFactory
import de.espirit.firstspirit.server.module.ModuleInfo
import org.gradle.plugins.fsm.tasks.bundling.FSM
import org.gradle.plugins.fsm.util.BaseConfiguration
import org.gradle.plugins.fsm.util.BaseProjectApp
import org.gradle.plugins.fsm.util.BaseService
import org.gradle.plugins.fsm.util.BaseWebApp

import javax.swing.JComponent
import java.awt.Frame
import java.lang.annotation.Annotation

class ComponentHelper {
    static Resource createResource(String path, String name, String version) {
        return new Resource() {
            @Override
            String path() {
                return path
            }

            @Override
            String name() {
                return name
            }

            @Override
            String version() {
                return version
            }

            @Override
            String minVersion() {
                return "myMinVersion"
            }

            @Override
            String maxVersion() {
                return "myMaxVersion"
            }

            @Override
            ModuleInfo.Scope scope() {
                return ModuleInfo.Scope.MODULE
            }

            @Override
            ModuleInfo.Mode mode() {
                return ModuleInfo.Mode.ISOLATED
            }

            @Override
            Class<? extends Annotation> annotationType() {
                return Resource.class
            }
        }
    }

    static WebResource createWebResource(String path, String name, String version) {
        return new WebResource() {

            @Override
            String path() {
                return path
            }

            @Override
            String name() {
                return name
            }

            @Override
            String version() {
                return version
            }

            @Override
            String minVersion() {
                return "myMinVersion"
            }

            @Override
            String maxVersion() {
                return "myMaxVersion"
            }

            @Override
            String targetPath() {
                return "myTargetPath"
            }

            @Override
            ModuleInfo.Scope scope() {
                return ModuleInfo.Scope.MODULE
            }

            @Override
            ModuleInfo.Mode mode() {
                return ModuleInfo.Mode.ISOLATED
            }

            @Override
            Class<? extends Annotation> annotationType() {
                return WebResource
            }
        }
    }

    static boolean addTestModulesToBlacklist(FSM fsmTask) {
        fsmTask.moduleBlacklist.addAll(TestModuleImpl.class.name, TestModuleImplWithConfiguration.class.name)
    }
}
class CustomProjectAppComponent implements ProjectAppComponent {
    @Override
    String name() {
        return "CustomProjectAppComponent"
    }

    @Override
    String displayName() {
        return "CustomProjectAppComponent"
    }

    @Override
    String description() {
        return "CustomProjectAppComponent"
    }

    @Override
    Class<? extends Configuration> configurable() {
        return null
    }

    @Override
    Resource[] resources() {
        return new Resource[0]
    }

    @Override
    Class<? extends Annotation> annotationType() {
        return ProjectAppComponent
    }
}

class CustomWebAppComponent implements WebAppComponent {
    @Override String name() { return "MyWebApp" }

    @Override String displayName() { return "MyWebApp" }

    @Override String description() { return "MyDescription" }

    @Override Class<? extends Configuration> configurable() { return null }

    @Override String webXml() { return "web.xml" }

    @Override WebAppDescriptor.WebAppScope[] scope() { return new WebAppDescriptor.WebAppScope[0] }

    @Override
    WebResource[] webResources() {
        return []
    }

    @Override Class<? extends Annotation> annotationType() { return WebAppComponent }
}

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

@WebAppComponent(name = "TestWebAppComponentName",
        displayName = "TestDisplayName",
        description = "TestDescription",
        webXml = "web0.xml",  // Please don't add leading slash, we want to test web.xml file handling without leading slash as well
        webResources = [@WebResource(path = "lib/guava-24.0.jar", name = "com.google.guava:guava", version = "24.0"),
                @WebResource(path = "lib/commons-lang-3.0.jar", name = "org.apache.commons:commons-lang3", version = "3.0", minVersion = "2.9", maxVersion = "3.1")])
class TestWebAppComponentWithoutConfiguration extends BaseWebApp {
    static class TestConfigurable extends BaseConfiguration { }
}

@PublicComponent(name = "TestPublicComponentName", displayName = "TestDisplayName")
class TestPublicComponent {
}

@PublicComponent(name = "TestPublicComponentWithConfigName", displayName = "TestDisplayName", configurable = TestConfigurable.class)
class TestPublicComponentWithConfiguration {
}

@ScheduleTaskComponent(taskName = "Test task", description = "A task for test purpose")
class TestScheduleTaskComponentWithoutForm {
}

@ScheduleTaskComponent(taskName = "Test task", description = "A task for test purpose", configurable = TestConfigurable.class)
class TestScheduleTaskComponentWithConfigurable {
}

@ScheduleTaskComponent(taskName = "Test task", description = "A task for test purpose", formClass = TestScheduleTaskFormFactory.class )
class TestScheduleTaskComponentWithForm {

}

class TestScheduleTaskFormFactory implements ScheduleTaskFormFactory {

    @Override
    ScheduleTaskForm createForm(final SpecialistsBroker specialistsBroker) {
        return null
    }
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

@UrlFactoryComponent(name = "TestUrlFactoryComponentName",
        displayName = "TestDisplayName",
        description = "TestDescription",
        useRegistry = true)
class TestUrlFactoryComponent implements UrlFactory {
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
