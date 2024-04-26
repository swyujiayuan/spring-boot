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

package org.springframework.boot;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.apache.commons.logging.Log;

import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.util.ReflectionUtils;

/**
 * A collection of {@link SpringApplicationRunListener}.
 *
 * @author Phillip Webb
 */
class SpringApplicationRunListeners {

	private final Log log;

	private final List<SpringApplicationRunListener> listeners;

	SpringApplicationRunListeners(Log log, Collection<? extends SpringApplicationRunListener> listeners) {
		this.log = log;
		this.listeners = new ArrayList<>(listeners);
	}

	void starting() {
		// 遍历this.listeners集合，里面只有一个对象EventPublishingRunListeners
		for (SpringApplicationRunListener listener : this.listeners) {
			//这里调用EventPublishingRunListeners#starting方法
			// EventPublishingRunListeners通过其内部的initialMulticaster成员广播ApplicationStartingEvent事件
			listener.starting();
		}
	}

	void environmentPrepared(ConfigurableEnvironment environment) {
		// 遍历this.listeners集合，里面只有一个对象EventPublishingRunListeners
		for (SpringApplicationRunListener listener : this.listeners) {
			//这里调用EventPublishingRunListeners#environmentPrepared方法
			// EventPublishingRunListeners通过其内部的initialMulticaster成员广播ApplicationEnvironmentPreparedEvent事件
			//在这个阶段 ,ConfigFileApplicationListener事件监听器会进行yaml/properties配置文件的加载；LoggingApplicationListener事件监听器会进行日志系统的初始化
			listener.environmentPrepared(environment);
		}
	}

	void contextPrepared(ConfigurableApplicationContext context) {
		// 遍历this.listeners集合，里面只有一个对象EventPublishingRunListeners
		for (SpringApplicationRunListener listener : this.listeners) {
			//这里调用EventPublishingRunListeners#environmentPrepared方法
			// 此处只有BackgroundPreinitializer和DelegatingApplicationListener两个事件监听器会处理ApplicationContextInitializedEvent事件，然而它俩处理逻辑中什么都没做
			listener.contextPrepared(context);
		}
	}

	void contextLoaded(ConfigurableApplicationContext context) {
		for (SpringApplicationRunListener listener : this.listeners) {
			listener.contextLoaded(context);
		}
	}

	void started(ConfigurableApplicationContext context) {
		for (SpringApplicationRunListener listener : this.listeners) {
			listener.started(context);
		}
	}

	void running(ConfigurableApplicationContext context) {
		for (SpringApplicationRunListener listener : this.listeners) {
			listener.running(context);
		}
	}

	void failed(ConfigurableApplicationContext context, Throwable exception) {
		for (SpringApplicationRunListener listener : this.listeners) {
			callFailedListener(listener, context, exception);
		}
	}

	private void callFailedListener(SpringApplicationRunListener listener, ConfigurableApplicationContext context,
			Throwable exception) {
		try {
			listener.failed(context, exception);
		}
		catch (Throwable ex) {
			if (exception == null) {
				ReflectionUtils.rethrowRuntimeException(ex);
			}
			if (this.log.isDebugEnabled()) {
				this.log.error("Error handling failed", ex);
			}
			else {
				String message = ex.getMessage();
				message = (message != null) ? message : "no error message";
				this.log.warn("Error handling failed (" + message + ")");
			}
		}
	}

}
