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
package org.gradle.plugins.fsm.util;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import org.gradle.api.artifacts.Configuration;

public final class TestUtil {
	private TestUtil() {
		
	}
	
	/**
	 * Copied from {@link org.gradle.api.internal.artifacts.configurations.Configurations#getNames}
	 */
	public static Set<String> getNames(Collection<? extends Configuration> configurations) {
		Set<Configuration> allConfigurations = new HashSet<Configuration>(configurations);
		Set<String> names = new HashSet<String>();
		for (Configuration configuration : allConfigurations) {
			names.add(configuration.getName());
		}
		return names;
	}
}
