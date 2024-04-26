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

import java.util.Map;

import org.springframework.boot.autoconfigure.AutoConfigurationMetadata;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication.Type;
import org.springframework.boot.web.reactive.context.ConfigurableReactiveWebEnvironment;
import org.springframework.boot.web.reactive.context.ReactiveWebApplicationContext;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.util.ClassUtils;
import org.springframework.util.ObjectUtils;
import org.springframework.web.context.ConfigurableWebEnvironment;
import org.springframework.web.context.WebApplicationContext;

/**
 * {@link Condition} that checks for the presence or absence of
 * {@link WebApplicationContext}.
 *
 * @author Dave Syer
 * @author Phillip Webb
 * @see ConditionalOnWebApplication
 * @see ConditionalOnNotWebApplication
 */
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
class OnWebApplicationCondition extends FilteringSpringBootCondition {

	private static final String SERVLET_WEB_APPLICATION_CLASS = "org.springframework.web.context.support.GenericWebApplicationContext";

	private static final String REACTIVE_WEB_APPLICATION_CLASS = "org.springframework.web.reactive.HandlerResult";

	// OnWebApplicationCondition的执行逻辑和OnClassCondition一样，区别在于
	// 1. OnClassCondition判断的是@ConditionalOnClass注解，OnWebApplicationCondition判断的是@ConditionalOnWebApplication注解
	// 2. OnClassCondition依靠类加载机制判断@ConditionalOnClass注解value属性中的Class是否存在；
	// OnWebApplicationCondition同样也是依靠类加载机制，但是判断的内容要少很多，其仅仅判断<ANY、SERVLET、REACTIVE>三种应用类型对应的类是否存在。
	@Override
	protected ConditionOutcome[] getOutcomes(String[] autoConfigurationClasses,
			AutoConfigurationMetadata autoConfigurationMetadata) {
		ConditionOutcome[] outcomes = new ConditionOutcome[autoConfigurationClasses.length];
		for (int i = 0; i < outcomes.length; i++) {
			String autoConfigurationClass = autoConfigurationClasses[i];
			if (autoConfigurationClass != null) {
				// 看下后续类是否符合自动装配，返回null则表示符合自动装配
				outcomes[i] = getOutcome(
						autoConfigurationMetadata.get(autoConfigurationClass, "ConditionalOnWebApplication"));
			}
		}
		return outcomes;
	}

	private ConditionOutcome getOutcome(String type) {
		if (type == null) {
			 // type为null，则表示该候选类没有标注@ConditionalOnWebApplication注解，返回null，表示可以自动装配
			return null;
		}
		// 这里根据Web的类型判断是否存在对应的类
		ConditionMessage.Builder message = ConditionMessage.forCondition(ConditionalOnWebApplication.class);
		if (ConditionalOnWebApplication.Type.SERVLET.name().equals(type)) {
			if (!ClassNameFilter.isPresent(SERVLET_WEB_APPLICATION_CLASS, getBeanClassLoader())) {
				// 如果没有org.springframework.web.context.support.GenericWebApplicationContext类则返回ConditionOutcome对象，表示候选类不符合自动装配
				return ConditionOutcome.noMatch(message.didNotFind("servlet web application classes").atAll());
			}
		}
		if (ConditionalOnWebApplication.Type.REACTIVE.name().equals(type)) {
			if (!ClassNameFilter.isPresent(REACTIVE_WEB_APPLICATION_CLASS, getBeanClassLoader())) {
				// 如果没有org.springframework.web.reactive.HandlerResult类则返回ConditionOutcome对象，表示候选类不符合自动装配
				return ConditionOutcome.noMatch(message.didNotFind("reactive web application classes").atAll());
			}
		}
		if (!ClassNameFilter.isPresent(SERVLET_WEB_APPLICATION_CLASS, getBeanClassLoader())
				&& !ClassUtils.isPresent(REACTIVE_WEB_APPLICATION_CLASS, getBeanClassLoader())) {
			// 没有org.springframework.web.reactive.HandlerResult类有没有org.springframework.web.context.support.GenericWebApplicationContext
			// 则返回ConditionOutcome对象，表示候选类不符合自动装配
			return ConditionOutcome.noMatch(message.didNotFind("reactive or servlet web application classes").atAll());
		}
		return null;
	}

	@Override
	public ConditionOutcome getMatchOutcome(ConditionContext context, AnnotatedTypeMetadata metadata) {
		boolean required = metadata.isAnnotated(ConditionalOnWebApplication.class.getName());
		ConditionOutcome outcome = isWebApplication(context, metadata, required);
		if (required && !outcome.isMatch()) {
			return ConditionOutcome.noMatch(outcome.getConditionMessage());
		}
		if (!required && outcome.isMatch()) {
			return ConditionOutcome.noMatch(outcome.getConditionMessage());
		}
		return ConditionOutcome.match(outcome.getConditionMessage());
	}

	private ConditionOutcome isWebApplication(ConditionContext context, AnnotatedTypeMetadata metadata,
			boolean required) {
		switch (deduceType(metadata)) {
		case SERVLET:
			return isServletWebApplication(context);
		case REACTIVE:
			return isReactiveWebApplication(context);
		default:
			return isAnyWebApplication(context, required);
		}
	}

	private ConditionOutcome isAnyWebApplication(ConditionContext context, boolean required) {
		ConditionMessage.Builder message = ConditionMessage.forCondition(ConditionalOnWebApplication.class,
				required ? "(required)" : "");
		ConditionOutcome servletOutcome = isServletWebApplication(context);
		if (servletOutcome.isMatch() && required) {
			return new ConditionOutcome(servletOutcome.isMatch(), message.because(servletOutcome.getMessage()));
		}
		ConditionOutcome reactiveOutcome = isReactiveWebApplication(context);
		if (reactiveOutcome.isMatch() && required) {
			return new ConditionOutcome(reactiveOutcome.isMatch(), message.because(reactiveOutcome.getMessage()));
		}
		return new ConditionOutcome(servletOutcome.isMatch() || reactiveOutcome.isMatch(),
				message.because(servletOutcome.getMessage()).append("and").append(reactiveOutcome.getMessage()));
	}

	private ConditionOutcome isServletWebApplication(ConditionContext context) {
		ConditionMessage.Builder message = ConditionMessage.forCondition("");
		if (!ClassNameFilter.isPresent(SERVLET_WEB_APPLICATION_CLASS, context.getClassLoader())) {
			return ConditionOutcome.noMatch(message.didNotFind("servlet web application classes").atAll());
		}
		if (context.getBeanFactory() != null) {
			String[] scopes = context.getBeanFactory().getRegisteredScopeNames();
			if (ObjectUtils.containsElement(scopes, "session")) {
				return ConditionOutcome.match(message.foundExactly("'session' scope"));
			}
		}
		if (context.getEnvironment() instanceof ConfigurableWebEnvironment) {
			return ConditionOutcome.match(message.foundExactly("ConfigurableWebEnvironment"));
		}
		if (context.getResourceLoader() instanceof WebApplicationContext) {
			return ConditionOutcome.match(message.foundExactly("WebApplicationContext"));
		}
		return ConditionOutcome.noMatch(message.because("not a servlet web application"));
	}

	private ConditionOutcome isReactiveWebApplication(ConditionContext context) {
		ConditionMessage.Builder message = ConditionMessage.forCondition("");
		if (!ClassNameFilter.isPresent(REACTIVE_WEB_APPLICATION_CLASS, context.getClassLoader())) {
			return ConditionOutcome.noMatch(message.didNotFind("reactive web application classes").atAll());
		}
		if (context.getEnvironment() instanceof ConfigurableReactiveWebEnvironment) {
			return ConditionOutcome.match(message.foundExactly("ConfigurableReactiveWebEnvironment"));
		}
		if (context.getResourceLoader() instanceof ReactiveWebApplicationContext) {
			return ConditionOutcome.match(message.foundExactly("ReactiveWebApplicationContext"));
		}
		return ConditionOutcome.noMatch(message.because("not a reactive web application"));
	}

	private Type deduceType(AnnotatedTypeMetadata metadata) {
		Map<String, Object> attributes = metadata.getAnnotationAttributes(ConditionalOnWebApplication.class.getName());
		if (attributes != null) {
			return (Type) attributes.get("type");
		}
		return Type.ANY;
	}

}
