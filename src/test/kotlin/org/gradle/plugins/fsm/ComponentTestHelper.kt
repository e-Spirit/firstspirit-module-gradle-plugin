package org.gradle.plugins.fsm

import com.espirit.moddev.components.annotations.*
import com.espirit.moddev.components.annotations.GadgetComponent
import com.espirit.moddev.components.annotations.params.gadget.Scope
import de.espirit.common.base.ui.Gadget
import de.espirit.firstspirit.access.Language
import de.espirit.firstspirit.access.project.Resolution
import de.espirit.firstspirit.access.project.TemplateSet
import de.espirit.firstspirit.access.store.ContentProducer
import de.espirit.firstspirit.access.store.PageParams
import de.espirit.firstspirit.access.store.mediastore.Media
import de.espirit.firstspirit.access.store.templatestore.gom.GomElement
import de.espirit.firstspirit.access.store.templatestore.gom.GomFormElement
import de.espirit.firstspirit.agency.SpecialistsBroker
import de.espirit.firstspirit.client.access.editor.ValueEngineer
import de.espirit.firstspirit.client.access.editor.ValueEngineerContext
import de.espirit.firstspirit.client.access.editor.ValueEngineerFactory
import de.espirit.firstspirit.generate.FilenameFactory
import de.espirit.firstspirit.generate.PathLookup
import de.espirit.firstspirit.generate.UrlFactory
import de.espirit.firstspirit.module.*
import de.espirit.firstspirit.module.descriptor.ModuleDescriptor
import de.espirit.firstspirit.scheduling.ScheduleTaskData
import de.espirit.firstspirit.scheduling.ScheduleTaskForm
import de.espirit.firstspirit.scheduling.ScheduleTaskFormFactory
import org.gradle.plugins.fsm.util.BaseConfiguration
import org.gradle.plugins.fsm.util.BaseProjectApp
import org.gradle.plugins.fsm.util.BaseService
import org.gradle.plugins.fsm.util.BaseWebApp
import java.awt.Frame
import javax.swing.JComponent


@ModuleComponent
class TestModuleImpl : Module {

    override fun init(moduleDescriptor: ModuleDescriptor, serverEnvironment: ServerEnvironment) {}

    override fun installed() {}

    override fun uninstalling() {}

    override fun updated(s: String) {}

}

@ModuleComponent(configurable = TestConfigurable::class)
class TestModuleImplWithConfiguration : Module {
    override fun init(moduleDescriptor: ModuleDescriptor, serverEnvironment: ServerEnvironment) {}

    override fun installed() {}

    override fun uninstalling() {}

    override fun updated(s: String) {}
}

class TestConfigurable : Configuration<ServerEnvironment> {

    override fun hasGui(): Boolean {
        return false
    }

    override fun getGui(applicationFrame: Frame): JComponent? {
        return null
    }

    override fun load() {}

    override fun store() {}

    override fun getParameterNames(): Set<String>? {
        return null
    }

    override fun getParameter(s: String): String? {
        return null
    }

    override fun init(moduleName: String, componentName: String, env: ServerEnvironment) {}

    override fun getEnvironment(): ServerEnvironment? {
        return null
    }
}

@ProjectAppComponent(name = "TestMinimalProjectAppComponentName")
class TestMinimalProjectAppComponent : BaseProjectApp()

@ProjectAppComponent(
    name = "TestProjectAppComponentName",
    displayName = "TestDisplayName",
    description = "TestDescription",
    configurable = TestProjectAppComponent.TestConfigurable::class,
    resources = [Resource(path = "lib/guava-24.0.jar", name = "com.google.guava:guava", version = "24.0")]
)
class TestProjectAppComponent : BaseProjectApp() {
    class TestConfigurable : BaseConfiguration()
}

@ProjectAppComponent(
    name = "TestProjectAppComponentWithoutConfigurableName",
    displayName = "TestDisplayName",
    description = "TestDescription"
)
class TestProjectAppComponentWithoutConfigurable : BaseProjectApp()

@ProjectAppComponent(
    name = "TestProjectAppComponentWithProperties",
    displayName = "TestDisplayName",
    description = "TestDescription",
    configurable = TestConfigurable::class,
    resources = [Resource(path = "\$path", name = "\${project.jodaConvertDependency}", version = "\$version")]
)
class TestProjectAppComponentWithProperties : BaseProjectApp()

@WebAppComponent(name = "TestMinimalWebAppComponentName", webXml = "/web.xml")
class TestMinimalWebAppComponent : BaseWebApp()

@WebAppComponent(
    name = "TestWebAppComponentName",
    displayName = "TestDisplayName",
    description = "TestDescription",
    configurable = TestWebAppComponent.TestConfigurable::class,
    webXml = "/web.xml",
    xmlSchemaVersion = "5.0",
    webResources = [WebResource(path = "lib/guava-24.0.jar", name = "com.google.guava:guava", version = "24.0"),
        WebResource(
            targetPath = "targetPath",
            path = "lib/commons-lang-3.0.jar",
            name = "org.apache.commons:commons-lang3",
            version = "3.0",
            minVersion = "2.9",
            maxVersion = "3.1"
        )]
)
class TestWebAppComponent : BaseWebApp() {
    class TestConfigurable : BaseConfiguration()
}

@WebAppComponent(
    name = "TestWebAppComponentWithoutConfigurationName",
    displayName = "TestDisplayName",
    description = "TestDescription",
    webXml = "web0.xml",  // Please don't add leading slash, we want to test web.xml file handling without leading slash as well
    webResources = [WebResource(path = "lib/guava-24.0.jar", name = "com.google.guava:guava", version = "24.0"),
        WebResource(
            path = "lib/commons-lang-3.0.jar",
            name = "org.apache.commons:commons-lang3",
            version = "3.0",
            minVersion = "2.9",
            maxVersion = "3.1"
        )]
)
class TestWebAppComponentWithoutConfiguration : BaseWebApp() {
    class TestConfigurable : BaseConfiguration()
}

@PublicComponent(name = "TestMinimalPublicComponentName")
class TestMinimalPublicComponent

@PublicComponent(
    name = "TestPublicComponentName",
    displayName = "TestDisplayName",
    description = "Component Description"
)
class TestPublicComponent

@PublicComponent(
    name = "TestPublicComponentWithConfigName",
    displayName = "TestDisplayName",
    configurable = TestConfigurable::class
)
class TestPublicComponentWithConfiguration

@ScheduleTaskComponent(taskName = "Test task without display name")
class TestMinimalScheduleTaskComponent

@ScheduleTaskComponent(
    taskName = "Test task without form",
    displayName = "Test Task Display Name",
    description = "A task for test purposes"
)
class TestScheduleTaskComponentWithoutForm

@ScheduleTaskComponent(
    taskName = "Test task with configurable",
    description = "A task for test purposes",
    configurable = TestConfigurable::class
)
class TestScheduleTaskComponentWithConfigurable

@ScheduleTaskComponent(
    taskName = "Test task with form",
    description = "A task for test purposes",
    formClass = TestScheduleTaskFormFactory::class
)
class TestScheduleTaskComponentWithForm

@GadgetComponent(name = "Test gadget")
class TestMinimalGadgetComponent

class TestGadgetFactoryOne : GadgetFactory<Gadget, GomElement, GadgetContext<*>> {

    override fun create(gadgetContext: GadgetContext<*>): Gadget {
        error("Gadget not available")
    }
}

class TestGadgetFactoryTwo : GadgetFactory<Gadget, GomElement, GadgetContext<*>> {

    override fun create(gadgetContext: GadgetContext<*>): Gadget {
        error("Gadget not available")
    }
}

class TestValueEngineerFactory : ValueEngineerFactory<Any, GomFormElement> {

    override fun getType(): Class<Any>? {
        return null
    }

    override fun create(valueEngineerContext: ValueEngineerContext<GomFormElement>): ValueEngineer<Any>? {
        return null
    }
}

@GadgetComponent(name = "Test gadget with unimplemented factory", factories = [GadgetFactory::class])
class TestGadgetComponentWithUnimplementedFactory

@GadgetComponent(name = "Test gadget with one factory", factories = [TestGadgetFactoryOne::class])
class TestGadgetComponentWithOneFactory

@GadgetComponent(
    name = "Test gadget with more than one factory",
    factories = [TestGadgetFactoryOne::class, TestGadgetFactoryTwo::class]
)
class TestGadgetComponentWithMoreThanOneFactory

@GadgetComponent(
    name = "Test gadget with all attributes",
    description = "The description",
    factories = [TestGadgetFactoryOne::class],
    valueEngineerFactory = TestValueEngineerFactory::class,
    scopes = [Scope.DATA]
)
class TestGadgetComponentWithAllAttributes

class TestScheduleTaskFormFactory : ScheduleTaskFormFactory<ScheduleTaskData> {
    override fun createForm(specialistsBroker: SpecialistsBroker): ScheduleTaskForm<ScheduleTaskData>? {
        return null
    }
}

@ServiceComponent(name = "TestMinimalServiceComponentName")
class TestMinimalServiceComponent : BaseService()

@ServiceComponent(
    name = "TestServiceComponentName",
    displayName = "TestDisplayName",
    description = "TestDescription",
    configurable = TestServiceComponent.TestConfigurable::class
)
class TestServiceComponent : BaseService() {
    class TestConfigurable : BaseConfiguration()

    class ServiceResource
}

@ServiceComponent(
    name = "TestServiceComponentWithoutConfigurableName",
    displayName = "TestDisplayName",
    description = "TestDescription"
)
class TestServiceComponentWithoutConfigurable : BaseService()

@UrlFactoryComponent(name = "TestMinimalUrlFactoryComponentName")
open class TestMinimalUrlFactoryComponent : UrlFactory {
    override fun init(map: Map<String, String>, pathLookup: PathLookup) {

    }

    override fun getUrl(
        contentProducer: ContentProducer,
        templateSet: TemplateSet,
        language: Language,
        pageParams: PageParams
    ): String {
        return ""
    }

    override fun getUrl(media: Media, language: Language, resolution: Resolution?): String {
        return ""
    }
}

@UrlFactoryComponent(
    name = "TestUrlFactoryComponentName",
    displayName = "TestDisplayName",
    description = "TestDescription",
    useRegistry = true
)
class TestUrlFactoryComponent : TestMinimalUrlFactoryComponent()


@UrlFactoryComponent(
    name = "TestUrlFactoryWithFilenameFactoryComponentName",
    displayName = "TestDisplayName",
    description = "TestDescription",
    filenameFactory = TestFilenameFactory::class,
    useRegistry = true
)
class TestUrlFactoryWithFilenameFactory : TestMinimalUrlFactoryComponent()

class TestFilenameFactory : FilenameFactory {
    override fun getFilename(
        s: String,
        contentProducer: ContentProducer,
        templateSet: TemplateSet,
        language: Language,
        pageParams: PageParams
    ): String? {
        return null
    }

    override fun getFilename(s: String, media: Media, language: Language?, resolution: Resolution?): String? {
        return null
    }
}
