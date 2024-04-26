/*
 * Copyright 2012-2020 the original author or authors.
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

import java.security.AccessControlException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.springframework.boot.autoconfigure.AutoConfigurationImportFilter;
import org.springframework.boot.autoconfigure.AutoConfigurationMetadata;
import org.springframework.boot.autoconfigure.condition.ConditionMessage.Style;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;

/**
 * {@link Condition} and {@link AutoConfigurationImportFilter} that checks for the
 * presence or absence of specific classes.
 *
 * @author Phillip Webb
 * @see ConditionalOnClass
 * @see ConditionalOnMissingClass
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
class OnClassCondition extends FilteringSpringBootCondition {

	@Override
	protected final ConditionOutcome[] getOutcomes(String[] autoConfigurationClasses,
			AutoConfigurationMetadata autoConfigurationMetadata) {
		// Split the work and perform half in a background thread if more than one
		// processor is available. Using a single additional thread seems to offer the
		// best performance. More threads make things worse.
		if (Runtime.getRuntime().availableProcessors() > 1) {
			return resolveOutcomesThreaded(autoConfigurationClasses, autoConfigurationMetadata);
		}
		else {
			OutcomesResolver outcomesResolver = new StandardOutcomesResolver(autoConfigurationClasses, 0,
					autoConfigurationClasses.length, autoConfigurationMetadata, getBeanClassLoader());
			return outcomesResolver.resolveOutcomes();
		}
	}

	/**
	 * 它首先将所有的自动装配类一分为二，前半部分开启一个线程进行处理、后半部分当前线程处理，后半部分处理完之后，
	 * 调用thread.join()等待thread.join()方法等待上面开启的线程run()执行完毕（即：处理完前半部分自动装配类）。
	 * 最后合并处理结果并返回。
	 *
	 * @param autoConfigurationClasses
	 * @param autoConfigurationMetadata
	 * @return
	 */
	private ConditionOutcome[] resolveOutcomesThreaded(String[] autoConfigurationClasses,
			AutoConfigurationMetadata autoConfigurationMetadata) {
		// 将自动装配类的条件装配一分为2进行处理，
		int split = autoConfigurationClasses.length / 2;
		// 这里会开启一个线程处理前半部分的自动装配类
		OutcomesResolver firstHalfResolver = createOutcomesResolver(autoConfigurationClasses, 0, split,
				autoConfigurationMetadata);
		// 当前线程处理后半部分的自动装配类
		OutcomesResolver secondHalfResolver = new StandardOutcomesResolver(autoConfigurationClasses, split,
				autoConfigurationClasses.length, autoConfigurationMetadata, getBeanClassLoader());
		// 当前线程处理后半部分的自动装配类
		ConditionOutcome[] secondHalf = secondHalfResolver.resolveOutcomes();
		// 这里调用thread.join()方法等待上面开启的线程run()执行完毕（即：处理完前半部分自动装配类）
		ConditionOutcome[] firstHalf = firstHalfResolver.resolveOutcomes();
		// 最后将处理结果合并 并 返回。
		ConditionOutcome[] outcomes = new ConditionOutcome[autoConfigurationClasses.length];
		System.arraycopy(firstHalf, 0, outcomes, 0, firstHalf.length);
		System.arraycopy(secondHalf, 0, outcomes, split, secondHalf.length);
		return outcomes;
	}

	private OutcomesResolver createOutcomesResolver(String[] autoConfigurationClasses, int start, int end,
			AutoConfigurationMetadata autoConfigurationMetadata) {
		OutcomesResolver outcomesResolver = new StandardOutcomesResolver(autoConfigurationClasses, start, end,
				autoConfigurationMetadata, getBeanClassLoader());
		try {
			return new ThreadedOutcomesResolver(outcomesResolver);
		}
		catch (AccessControlException ex) {
			return outcomesResolver;
		}
	}

	@Override
	public ConditionOutcome getMatchOutcome(ConditionContext context, AnnotatedTypeMetadata metadata) {
		ClassLoader classLoader = context.getClassLoader();
		ConditionMessage matchMessage = ConditionMessage.empty();
		// 获取@ConditionalOnClass注解指定的类名
		List<String> onClasses = getCandidates(metadata, ConditionalOnClass.class);
		if (onClasses != null) {
			// 使用类加载机制判断Class是否存在
			List<String> missing = filter(onClasses, ClassNameFilter.MISSING, classLoader);
			if (!missing.isEmpty()) {
				// 表示不存在，不能够加载到所对应的类，返回ConditionOutcome对象，match字段为false，表示不符合
				return ConditionOutcome.noMatch(ConditionMessage.forCondition(ConditionalOnClass.class)
						.didNotFind("required class", "required classes").items(Style.QUOTE, missing));
			}
			matchMessage = matchMessage.andCondition(ConditionalOnClass.class)
					.found("required class", "required classes")
					.items(Style.QUOTE, filter(onClasses, ClassNameFilter.PRESENT, classLoader));
		}
		// 获取ConditionalOnMissingClass注解指定的类名
		List<String> onMissingClasses = getCandidates(metadata, ConditionalOnMissingClass.class);
		if (onMissingClasses != null) {
			List<String> present = filter(onMissingClasses, ClassNameFilter.PRESENT, classLoader);
			if (!present.isEmpty()) {
				// 表示存在，能够加载到所对应的类，返回ConditionOutcome对象，match字段为false，表示不符合
				return ConditionOutcome.noMatch(ConditionMessage.forCondition(ConditionalOnMissingClass.class)
						.found("unwanted class", "unwanted classes").items(Style.QUOTE, present));
			}
			matchMessage = matchMessage.andCondition(ConditionalOnMissingClass.class)
					.didNotFind("unwanted class", "unwanted classes")
					.items(Style.QUOTE, filter(onMissingClasses, ClassNameFilter.MISSING, classLoader));
		}
		// 来到这里表示符合，返回ConditionOutcome对象，match字段为ture
		return ConditionOutcome.match(matchMessage);
	}

	private List<String> getCandidates(AnnotatedTypeMetadata metadata, Class<?> annotationType) {
		MultiValueMap<String, Object> attributes = metadata.getAllAnnotationAttributes(annotationType.getName(), true);
		if (attributes == null) {
			return null;
		}
		List<String> candidates = new ArrayList<>();
		addAll(candidates, attributes.get("value"));
		addAll(candidates, attributes.get("name"));
		return candidates;
	}

	private void addAll(List<String> list, List<Object> itemsToAdd) {
		if (itemsToAdd != null) {
			for (Object item : itemsToAdd) {
				Collections.addAll(list, (String[]) item);
			}
		}
	}

	private interface OutcomesResolver {

		ConditionOutcome[] resolveOutcomes();

	}

	private static final class ThreadedOutcomesResolver implements OutcomesResolver {

		private final Thread thread;

		private volatile ConditionOutcome[] outcomes;

		private ThreadedOutcomesResolver(OutcomesResolver outcomesResolver) {
			// 解析所有的候选类，看下哪些符合，哪些不符合
			this.thread = new Thread(() -> this.outcomes = outcomesResolver.resolveOutcomes());
			// 线程开始执行
			this.thread.start();
		}

		@Override
		public ConditionOutcome[] resolveOutcomes() {
			try {
				// 等待线程的run方法执行完毕
				this.thread.join();
			}
			catch (InterruptedException ex) {
				Thread.currentThread().interrupt();
			}
			return this.outcomes;
		}

	}

	private static final class StandardOutcomesResolver implements OutcomesResolver {

		private final String[] autoConfigurationClasses;

		private final int start;

		private final int end;

		private final AutoConfigurationMetadata autoConfigurationMetadata;

		private final ClassLoader beanClassLoader;

		private StandardOutcomesResolver(String[] autoConfigurationClasses, int start, int end,
				AutoConfigurationMetadata autoConfigurationMetadata, ClassLoader beanClassLoader) {
			this.autoConfigurationClasses = autoConfigurationClasses;
			this.start = start;
			this.end = end;
			this.autoConfigurationMetadata = autoConfigurationMetadata;
			this.beanClassLoader = beanClassLoader;
		}

		@Override
		public ConditionOutcome[] resolveOutcomes() {
			return getOutcomes(this.autoConfigurationClasses, this.start, this.end, this.autoConfigurationMetadata);
		}

		/**
		 * 1. 首先获取自动装配类autoConfigurationClass上@ConditionalOnClass注解中的值；如果获取不到，继续下一个循环。
		 * 2. 获取到相应的类，则通过类加载机制判断其是否存在，如果能加载到则返回。。。。 否则返回null。这里使用类加载机制判断Class是否存在时，不会对Class执行初始化操作。
		 *
		 * @param autoConfigurationClasses
		 * @param start
		 * @param end
		 * @param autoConfigurationMetadata
		 * @return
		 */
		private ConditionOutcome[] getOutcomes(String[] autoConfigurationClasses, int start, int end,
				AutoConfigurationMetadata autoConfigurationMetadata) {
			ConditionOutcome[] outcomes = new ConditionOutcome[end - start];
			//根据下标遍历传入的所有自动装配类名称
			for (int i = start; i < end; i++) {
				// 取出对应下标的候选类
				String autoConfigurationClass = autoConfigurationClasses[i];
				if (autoConfigurationClass != null) {
					// 取出候选类的@ConditionalOnClass注解的属性，
					// 例如JmxAutoConfiguration类上被@ConditionalOnClass({ MBeanExporter.class })注解标注，所以获取到的candidates局部变量值为：MBeanExporter。
					String candidates = autoConfigurationMetadata.get(autoConfigurationClass, "ConditionalOnClass");
					if (candidates != null) {
						//设置该后续类所对应下标的值，如果匹配则不为null，不匹配则为null
						outcomes[i - start] = getOutcome(candidates);
					}
				}
			}
			return outcomes;
		}

		private ConditionOutcome getOutcome(String candidates) {
			try {
				// @ConditionalOnClass注解只有一个Class
				if (!candidates.contains(",")) {
					return getOutcome(candidates, this.beanClassLoader);
				}
				// 如果@ConditionalOnClass注解包含多个Class，则遍历
				for (String candidate : StringUtils.commaDelimitedListToStringArray(candidates)) {
					ConditionOutcome outcome = getOutcome(candidate, this.beanClassLoader);
					if (outcome != null) {
						// 有不满足则返回ConditionOutcome，表示不符合，该候选类不能够进行自动装配
						return outcome;
					}
				}
			}
			catch (Exception ex) {
				// We'll get another chance later
			}
			return null;
		}

		private ConditionOutcome getOutcome(String className, ClassLoader classLoader) {
			if (ClassNameFilter.MISSING.matches(className, classLoader)) {
				// 返回ture则表示加载不到该类，返回一个ConditionOutcome对象，match值为false
				return ConditionOutcome.noMatch(ConditionMessage.forCondition(ConditionalOnClass.class)
						.didNotFind("required class").items(Style.QUOTE, className));
			}
			// 来到这里标识能够加载到类，返回null，表示符合，该候选类能够进行自动装配
			return null;
		}

	}

}
