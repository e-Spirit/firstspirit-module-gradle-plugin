/*
 * Copyright 2013 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.gradle.plugins.fsm

import de.espirit.firstspirit.server.module.ModuleInfo
import de.espirit.mavenplugins.fsmchecker.ComplianceLevel

class FSMPluginExtension {

	/**
	 * The name of the module that should be used in the module.xml instead of the project name
	 */
	String moduleName

	/**
	 * The name of the directory containing the module.xml, relative to the project directory.
	 */
	String moduleDirName

	/**
	 * Human-readable display name of the module
	 */
	String displayName

	/**
	 * Responsible vendor of the module
	 */
	String vendor

    /**
     * If set, this classloader isolation mode is used for all resources
     */
    ModuleInfo.Mode resourceMode = ModuleInfo.Mode.ISOLATED

    /**
     * If set, this URL is used to connect to the FSM Dependency Detector
     */
    String isolationDetectorUrl

    /**
     * Resource identifiers of the form 'groupId:artifactId:version' of resources
     * which should not be scanned for external dependencies
     */
    Collection<String> isolationDetectorWhitelist

	/**
	 * Names of web components to be deployed as part of a ContentCreator web-app.
	 */
	Collection<String> contentCreatorComponents

	/**
	 * The compliance level to check for if {#link isolationDetectorUrl} is set. Defaults to
	 * {@link ComplianceLevel#DEFAULT}
	 */
	ComplianceLevel complianceLevel = ComplianceLevel.DEFAULT

    /**
     * The FirstSpirit version to check against with the isolation detector service.
     */
    String firstSpiritVersion

	/**
	 * Whether to append the artifact version as the minVersion attribute to resources.
	 */
	boolean appendDefaultMinVersion = true

	/**
	 * The dependencies of this FS-Module (FSM) to other FSM's. Will at least be displayed in the UI,
	 * when a user adds this Module.
	 */
	Collection<String> fsmDependencies = new ArrayList<String>()

}
