/*
 * Copyright 2012-2019 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.boot.autoconfigure.condition;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanClassLoaderAware;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.boot.autoconfigure.AutoConfigurationImportFilter;
import org.springframework.boot.autoconfigure.AutoConfigurationMetadata;
import org.springframework.util.ClassUtils;
import org.springframework.util.CollectionUtils;

/**
 * Abstract base class for a {@link SpringBootCondition} that also implements
 * {@link AutoConfigurationImportFilter}.
 *
 * @author Phillip Webb
 */
abstract class FilteringSpringBootCondition extends SpringBootCondition
		implements AutoConfigurationImportFilter, BeanFactoryAware, BeanClassLoaderAware {

	private BeanFactory beanFactory;

	private ClassLoader beanClassLoader;

	@Override
	public boolean[] match(String[] autoConfigurationClasses, AutoConfigurationMetadata autoConfigurationMetadata) {
		ConditionEvaluationReport report = ConditionEvaluationReport.find(this.beanFactory);
		// getOutcomes方法由子类实现
		ConditionOutcome[] outcomes = getOutcomes(autoConfigurationClasses, autoConfigurationMetadata);
		boolean[] match = new boolean[outcomes.length];
		for (int i = 0; i < outcomes.length; i++) {
			// outcomes对应下标的值为null，或者match值为ture则表示符合，设置对应下标为ture，表示该候选类可以自动装配
			match[i] = (outcomes[i] == null || outcomes[i].isMatch());
			if (!match[i] && outcomes[i] != null) {
				logOutcome(autoConfigurationClasses[i], outcomes[i]);
				if (report != null) {
					report.recordConditionEvaluation(autoConfigurationClasses[i], this, outcomes[i]);
				}
			}
		}
		return match;
	}

	protected abstract ConditionOutcome[] getOutcomes(String[] autoConfigurationClasses,
			AutoConfigurationMetadata autoConfigurationMetadata);

	@Override
	public void setBeanFactory(BeanFactory beanFactory) throws BeansException {
		this.beanFactory = beanFactory;
	}

	protected final BeanFactory getBeanFactory() {
		return this.beanFactory;
	}

	protected final ClassLoader getBeanClassLoader() {
		return this.beanClassLoader;
	}

	@Override
	public void setBeanClassLoader(ClassLoader classLoader) {
		this.beanClassLoader = classLoader;
	}

	protected final List<String> filter(Collection<String> classNames, ClassNameFilter classNameFilter,
			ClassLoader classLoader) {
		if (CollectionUtils.isEmpty(classNames)) {
			return Collections.emptyList();
		}
		List<String> matches = new ArrayList<>(classNames.size());
		// 遍历候选类@ConditionalOnBean，ConditionalOnClass等注解的value值，
		for (String candidate : classNames) {
			if (classNameFilter.matches(candidate, classLoader)) {
				// 表示不能能够加载到对应的类
				matches.add(candidate);
			}
		}
		return matches;
	}

	/**
	 * Slightly faster variant of {@link ClassUtils#forName(String, ClassLoader)} that
	 * doesn't deal with primitives, arrays or inner types.
	 * @param className the class name to resolve
	 * @param classLoader the class loader to use
	 * @return a resolved class
	 * @throws ClassNotFoundException if the class cannot be found
	 */
	protected static Class<?> resolve(String className, ClassLoader classLoader) throws ClassNotFoundException {
		if (classLoader != null) {
			return Class.forName(className, false, classLoader);
		}
		return Class.forName(className);
	}

	protected enum ClassNameFilter {

		PRESENT {

			@Override
			public boolean matches(String className, ClassLoader classLoader) {
				return isPresent(className, classLoader);
			}

		},

		MISSING {

			@Override
			public boolean matches(String className, ClassLoader classLoader) {
				// isPresent返回ture或者false，为ture则表示能加载到该类，false则表示加载不到
				return !isPresent(className, classLoader);
			}

		};

		abstract boolean matches(String className, ClassLoader classLoader);

		static boolean isPresent(String className, ClassLoader classLoader) {
			if (classLoader == null) {
				classLoader = ClassUtils.getDefaultClassLoader();
			}
			try {
				// 根据当前ClassLoader使用反射Class.forName()加载类
				resolve(className, classLoader);
				// 如果能加载到返回ture
				return true;
			}
			catch (Throwable ex) {
				// 如果加载不到返回false
				return false;
			}
		}

	}

}
