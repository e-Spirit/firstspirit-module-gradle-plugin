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
import org.gradle.plugins.fsm.tasks.verification.IsolationLevel

class FSMPluginExtension {
	/**
	 * The name of the directory containing the module.xml, relative to the project directory.
	 */
	String moduleDirName = 'src/main/resources'
	String archivePath
	String archiveName

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
    ModuleInfo.Mode resourceMode

    /**
     * If set, this URL is used to connect to the FSM Dependency Detector
     */
    String isolationDetectorUrl

    /**
     * Isolation level to check for if {#link isolationDetectorUrl} is set.
     * Defaults to {@link IsolationLevel#RUNTIME_USAGE}
     */
    IsolationLevel isolationLevel

    /**
     * The FirstSpirit version to check against with the isolation detector service.
     */
    String firstSpiritVersion

}
