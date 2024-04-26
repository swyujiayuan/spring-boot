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

package org.springframework.boot;

import java.lang.reflect.Constructor;
import java.security.AccessControlException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.beans.BeanUtils;
import org.springframework.beans.CachedIntrospectionResults;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.groovy.GroovyBeanDefinitionReader;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanNameGenerator;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.xml.XmlBeanDefinitionReader;
import org.springframework.boot.Banner.Mode;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.convert.ApplicationConversionService;
import org.springframework.boot.web.reactive.context.StandardReactiveWebEnvironment;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ApplicationListener;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.AnnotatedBeanDefinitionReader;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.AnnotationConfigUtils;
import org.springframework.context.annotation.ClassPathBeanDefinitionScanner;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.GenericTypeResolver;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.convert.support.ConfigurableConversionService;
import org.springframework.core.env.CommandLinePropertySource;
import org.springframework.core.env.CompositePropertySource;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.SimpleCommandLinePropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.io.support.SpringFactoriesLoader;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ObjectUtils;
import org.springframework.util.ReflectionUtils;
import org.springframework.util.StopWatch;
import org.springframework.util.StringUtils;
import org.springframework.web.context.support.StandardServletEnvironment;

/**
 * Class that can be used to bootstrap and launch a Spring application from a Java main
 * method. By default class will perform the following steps to bootstrap your
 * application:
 *
 * <ul>
 * <li>Create an appropriate {@link ApplicationContext} instance (depending on your
 * classpath)</li>
 * <li>Register a {@link CommandLinePropertySource} to expose command line arguments as
 * Spring properties</li>
 * <li>Refresh the application context, loading all singleton beans</li>
 * <li>Trigger any {@link CommandLineRunner} beans</li>
 * </ul>
 *
 * In most circumstances the static {@link #run(Class, String[])} method can be called
 * directly from your {@literal main} method to bootstrap your application:
 *
 * <pre class="code">
 * &#064;Configuration
 * &#064;EnableAutoConfiguration
 * public class MyApplication  {
 *
 *   // ... Bean definitions
 *
 *   public static void main(String[] args) {
 *     SpringApplication.run(MyApplication.class, args);
 *   }
 * }
 * </pre>
 *
 * <p>
 * For more advanced configuration a {@link SpringApplication} instance can be created and
 * customized before being run:
 *
 * <pre class="code">
 * public static void main(String[] args) {
 *   SpringApplication application = new SpringApplication(MyApplication.class);
 *   // ... customize application settings here
 *   application.run(args)
 * }
 * </pre>
 *
 * {@link SpringApplication}s can read beans from a variety of different sources. It is
 * generally recommended that a single {@code @Configuration} class is used to bootstrap
 * your application, however, you may also set {@link #getSources() sources} from:
 * <ul>
 * <li>The fully qualified class name to be loaded by
 * {@link AnnotatedBeanDefinitionReader}</li>
 * <li>The location of an XML resource to be loaded by {@link XmlBeanDefinitionReader}, or
 * a groovy script to be loaded by {@link GroovyBeanDefinitionReader}</li>
 * <li>The name of a package to be scanned by {@link ClassPathBeanDefinitionScanner}</li>
 * </ul>
 *
 * Configuration properties are also bound to the {@link SpringApplication}. This makes it
 * possible to set {@link SpringApplication} properties dynamically, like additional
 * sources ("spring.main.sources" - a CSV list) the flag to indicate a web environment
 * ("spring.main.web-application-type=none") or the flag to switch off the banner
 * ("spring.main.banner-mode=off").
 *
 * @author Phillip Webb
 * @author Dave Syer
 * @author Andy Wilkinson
 * @author Christian Dupuis
 * @author Stephane Nicoll
 * @author Jeremy Rickard
 * @author Craig Burke
 * @author Michael Simons
 * @author Madhura Bhave
 * @author Brian Clozel
 * @author Ethan Rubinson
 * @since 1.0.0
 * @see #run(Class, String[])
 * @see #run(Class[], String[])
 * @see #SpringApplication(Class...)
 */
public class SpringApplication {

	/**
	 * The class name of application context that will be used by default for non-web
	 * environments.
	 */
	public static final String DEFAULT_CONTEXT_CLASS = "org.springframework.context."
			+ "annotation.AnnotationConfigApplicationContext";

	/**
	 * The class name of application context that will be used by default for web
	 * environments.
	 */
	public static final String DEFAULT_SERVLET_WEB_CONTEXT_CLASS = "org.springframework.boot."
			+ "web.servlet.context.AnnotationConfigServletWebServerApplicationContext";

	/**
	 * The class name of application context that will be used by default for reactive web
	 * environments.
	 */
	public static final String DEFAULT_REACTIVE_WEB_CONTEXT_CLASS = "org.springframework."
			+ "boot.web.reactive.context.AnnotationConfigReactiveWebServerApplicationContext";

	/**
	 * Default banner location.
	 */
	public static final String BANNER_LOCATION_PROPERTY_VALUE = SpringApplicationBannerPrinter.DEFAULT_BANNER_LOCATION;

	/**
	 * Banner location property key.
	 */
	public static final String BANNER_LOCATION_PROPERTY = SpringApplicationBannerPrinter.BANNER_LOCATION_PROPERTY;

	private static final String SYSTEM_PROPERTY_JAVA_AWT_HEADLESS = "java.awt.headless";

	private static final Log logger = LogFactory.getLog(SpringApplication.class);

	private Set<Class<?>> primarySources;

	private Set<String> sources = new LinkedHashSet<>();

	private Class<?> mainApplicationClass;

	private Banner.Mode bannerMode = Banner.Mode.CONSOLE;

	private boolean logStartupInfo = true;

	private boolean addCommandLineProperties = true;

	private boolean addConversionService = true;

	private Banner banner;

	private ResourceLoader resourceLoader;

	private BeanNameGenerator beanNameGenerator;

	private ConfigurableEnvironment environment;

	private Class<? extends ConfigurableApplicationContext> applicationContextClass;

	private WebApplicationType webApplicationType;

	private boolean headless = true;

	private boolean registerShutdownHook = true;

	private List<ApplicationContextInitializer<?>> initializers;

	private List<ApplicationListener<?>> listeners;

	private Map<String, Object> defaultProperties;

	private Set<String> additionalProfiles = new HashSet<>();

	private boolean allowBeanDefinitionOverriding;

	private boolean isCustomEnvironment = false;

	private boolean lazyInitialization = false;

	/**
	 * Create a new {@link SpringApplication} instance. The application context will load
	 * beans from the specified primary sources (see {@link SpringApplication class-level}
	 * documentation for details. The instance can be customized before calling
	 * {@link #run(String...)}.
	 * @param primarySources the primary bean sources
	 * @see #run(Class, String[])
	 * @see #SpringApplication(ResourceLoader, Class...)
	 * @see #setSources(Set)
	 */
	public SpringApplication(Class<?>... primarySources) {
		this(null, primarySources);
	}

	/**
	 * Create a new {@link SpringApplication} instance. The application context will load
	 * beans from the specified primary sources (see {@link SpringApplication class-level}
	 * documentation for details. The instance can be customized before calling
	 * {@link #run(String...)}.
	 * @param resourceLoader the resource loader to use
	 * @param primarySources the primary bean sources
	 * @see #run(Class, String[])
	 * @see #setSources(Set)
	 */
	@SuppressWarnings({ "unchecked", "rawtypes" })
	public SpringApplication(ResourceLoader resourceLoader, Class<?>... primarySources) {
		//首先初始化资源加载器，默认为nu
		this.resourceLoader = resourceLoader;
		Assert.notNull(primarySources, "PrimarySources must not be null");
		// 将主资源类primarySources存储到SpringApplication对象Set类型的primarySources属性中。
		this.primarySources = new LinkedHashSet<>(Arrays.asList(primarySources));
		//推断当前 WEB 应用类型，一共有三种：NONE,SERVLET,REACTIVE；默认是SERVLET。
		//Web应用类型可能在SpringApplication构造后及run方法之前，再通过setWebApplicatioinType(WebApplicationType)方法调整；
		// 又在推断Web应用类型的过程中，由于当前Spring应用上下文尚未准备，所以采用检查当前ClassLoader下基准Class的存在性来推断Web应用类型。
		this.webApplicationType = WebApplicationType.deduceFromClasspath();
		// 加载Spring应用上下文初始化器：从"META-INF/spring.factories"文件中读取ApplicationContextInitializer类的实例名称集合，
		// 然后进行Set去重、利用反射实例化对象，最后按照Order排序后，赋值到SpringApplication的List类型的initializers属性上，一共7个。
		setInitializers((Collection) getSpringFactoriesInstances(ApplicationContextInitializer.class));
		//加载Spring应用事件监听器：从"META-INF/spring.factories"文件中读取ApplicationListener类的实例名称集合，
		// 然后进行Set去重、利用反射实例化对象，最后按照Order排序后，赋值到SpringApplication的List类型的listeners属性上，一共11个
		setListeners((Collection) getSpringFactoriesInstances(ApplicationListener.class));
		// 推断主应用程序类，通过当前调用栈，获取Main方法所在类，并赋值给mainApplicationClass
		this.mainApplicationClass = deduceMainApplicationClass();
	}

	private Class<?> deduceMainApplicationClass() {
		try {
			StackTraceElement[] stackTrace = new RuntimeException().getStackTrace();
			for (StackTraceElement stackTraceElement : stackTrace) {
				if ("main".equals(stackTraceElement.getMethodName())) {
					return Class.forName(stackTraceElement.getClassName());
				}
			}
		}
		catch (ClassNotFoundException ex) {
			// Swallow and continue
		}
		return null;
	}

	/**
	 * Run the Spring application, creating and refreshing a new
	 * {@link ApplicationContext}.
	 * @param args the application arguments (usually passed from a Java main method)
	 * @return a running {@link ApplicationContext}
	 */
	public ConfigurableApplicationContext run(String... args) {
		// 第一步：开启计时器StopWatch,设置程序启动的开始时间
		// 就整个程序的启动时间而言，其实并不精确，因为构建SpringApplication的时间并没有算进去，其中也包含了一次从spring.factories文件中读取信息的IO操作。
		StopWatch stopWatch = new StopWatch();
		stopWatch.start();
		ConfigurableApplicationContext context = null;
		Collection<SpringBootExceptionReporter> exceptionReporters = new ArrayList<>();
		// 第二步：设置系统属性java.awt.headless
		// 设置一个系统属性java.awt.headless，默认为true；用于运行headless服务器，进行简单的图像处理；
		// 此外，其多用于在缺少显示屏、键盘或者鼠标时的系统配置，很多监控工具如jconsole，需要将该值设置为true。
		configureHeadlessProperty();
		//第三步：加载运行时监听器SpringApplicationRunListeners
		// 1. 从所有依赖jar包的META-INF/spring.factories文件中获取SpringApplicationRunListener接口所有的实现类。
		// 2. 将获取到的实现类通过构造函数赋值到SpringApplicationRunListeners类的List类型的成员变量listeners上。
		SpringApplicationRunListeners listeners = getRunListeners(args);
		//发布应用启动事件ApplicationStartingEvent
		listeners.starting();
		try {
			// 第四步：加载并解析命令行的参数到ApplicationArguments对象中,底层实现是基于Spring Framework中的命令行配置源SimpleCommandLinePropertySource的parse方法
			// SimpleCommandLinePropertySource将命令行参数分为两组，分别为：
			// 1. “选项参数”，选项参数必须以“--”为前缀。
			// 2. “非选项参数”，非选项参数是未包含“--”前缀的命令行参数。
			// 例如：根据以上规则，命令行参数--server.port=8088被SimpleCommandLineArgsParser解析为“server.port : 8088”键值属性，添加到DefaultApplicationArguments对象中。
			ApplicationArguments applicationArguments = new DefaultApplicationArguments(args);
			//第五步：准备当前应用程序的环境ConfigurableEnvironment
			//大致分为6步
			// 1. 获取当前环境，如果不存在，则根据应用类型来创建对应的环境。
			//   比如：SERVLET应用类型创建StandardServletEnvironment、REACTIVE应用类型创建StandardReactiveWebEnvironment，
			//   否者创建StandardEnvironment。另外：StandardReactiveWebEnvironment 继承自 StandardEnvironment，两者内容完全一样，
			//   只是StandardReactiveWebEnvironment又实现了ConfigurableReactiveWebEnvironment接口，虽然其中没做方法的重写。
			// 2. 配置当前环境，
			//    将类型转换器和格式化器添加到环境中、将命令行参数内容（SimpleCommandLinePropertySource {name=‘commandLineArgs’}）
			//    添加到环境的propertySources成员变量中、给环境设置activeProfiles。
			// 3. 如果propertySources中没有configurationProperties则将ConfigurationPropertySourcesPropertySource {name='configurationProperties'}
			//    加入到propertySources中，有的话先移除，然后再加。
			// 4. 广播ApplicationEnvironmentPreparedEvent事件，通知监听器，当前引用环境准备好了。
			//    在这里ConfigFileApplicationListener会解析我们的外部配置文件xx.properties、xxx.yml
			// 5. 绑定应用环境到spring.main，将应用环境ConfigurableEnvironment转换为相应应用类型的环境。
			// 6. 防止对环境转换时有问题，这里再重新配置当前环境。
			ConfigurableEnvironment environment = prepareEnvironment(listeners, applicationArguments);
			//第六步：设置系统属性spring.beaninfo.ignore,默认为ture,保证某些bean不会添加到准备的环境中
			configureIgnoreBeanInfo(environment);
			// 第七步：打印banner,这里可以自定义banner
			Banner printedBanner = printBanner(environment);
			// 第八步：创建Spring应用上下文，
			// 根据应用类型利用反射创建Spring应用上下文，可以理解为创建一个容器；就SERVLET而言：实例化AnnotationConfigServletWebServerApplicationContext。
			context = createApplicationContext();
			// 第九步：加载异常报告器SpringBootExceptionReporter,用来支持报告关于启动的错误
			// 从"META-INF/spring.factories"文件中读取SpringBootExceptionReporter类的实例名称集合，
			// 然后进行Set去重、利用反射实例化对象，最后按照Order排序。排序后的Set集合赋值到Collection类型的exceptionReporters对象上。
			exceptionReporters = getSpringFactoriesInstances(SpringBootExceptionReporter.class,
					new Class<?>[] { ConfigurableApplicationContext.class }, context);
			// 第十步：Spring应用上下文运行前准备,
			// 由SpringApplication#prepareContext方法完成，
			// 根据SpringApplicationRunListener的生命周期回调又分为“Spring应用上下文准备阶段” 和 “Spring应用上下文装载阶段”。
			prepareContext(context, environment, listeners, applicationArguments, printedBanner);
			// 第十一步： Spring应用上下文启动阶段,最核心，Spring的IOC，AOP在这里体现
			refreshContext(context);
			// 第十二步：Spring应用上下文启动后阶段，
			// 实际上，该afterRefresh方法并未给Spring应用上下文启动后阶段提供实现，而是交给开发人员自行扩展
			afterRefresh(context, applicationArguments);
			//计时监听器停止并统计,SpringBoot应用的启动时间为：StopWatch.stop()的当前时间 - StopWatch.start()的当前时间，默认统计的任务执行数量为1。
			stopWatch.stop();
			if (this.logStartupInfo) {
				//输出日志记录执行主类名和时间信息,
				new StartupInfoLogger(this.mainApplicationClass).logStarted(getApplicationLog(), stopWatch);
			}
			// 发布事件ApplicationStartedEvent，BackgroundPreinitializer、DelegatingApplicationListener监听到事件，但它们什么都不会做
			// 还会发布一个AvailabilityChangeEvent事件，状态：ReadinessState.CORRECT，表示应用已处于活动状态。
			listeners.started(context);
			//执行所有的ApplicationRunner和CommandLineRunner 两种运行器，默认情况下是没有运行器的，这里也是留给开发人员自己扩展的。例如Seata 就是扩展了一个Runner
			callRunners(context, applicationArguments);
		}
		catch (Throwable ex) {
			handleRunFailure(context, ex, exceptionReporters, listeners);
			throw new IllegalStateException(ex);
		}

		try {
			// 发布应用上下文就绪事件ApplicationReadyEvent
			// 1. SpringApplicationAdminMXBeanRegister将自己的状态设置为ready，可以对外提供服务。
			// 2. BackgroundPreinitializer中调用CountDownLatch.await()等待后台功能的初始化完成。
			// 3. DelegatingApplicationListener则什么都不做。
			// 除此之后，还会发布一个AvailabilityChangeEvent事件，状态：ReadinessState.ACCEPTING_TRAFFIC，表示应用可以开始准备接收请求了。
			listeners.running(context);
		}
		catch (Throwable ex) {
			handleRunFailure(context, ex, exceptionReporters, null);
			throw new IllegalStateException(ex);
		}
		return context;
	}

	private ConfigurableEnvironment prepareEnvironment(SpringApplicationRunListeners listeners,
			ApplicationArguments applicationArguments) {
		// Create and configure the environment
		// 1、获取或者创建应用环境（创建时会根据应用类型判断）
		ConfigurableEnvironment environment = getOrCreateEnvironment();
		// 2、配置应用环境，配置propertySource和activeProfiles
		configureEnvironment(environment, applicationArguments.getSourceArgs());
		// 如果propertySources中没有`configurationProperties`
		// 则将`ConfigurationPropertySourcesPropertySource {name='configurationProperties'}`加入到propertySources中。
		// 有的话先移除，然后再加。
		ConfigurationPropertySources.attach(environment);
		// 3、监听器环境准备，广播ApplicationEnvironmentPreparedEvent事件
		//   这里会处理我们自定义的配置文件内容--见ConfigFileApplicationListener
		listeners.environmentPrepared(environment);
		// 4、绑定应用环境
		bindToSpringApplication(environment);
		if (!this.isCustomEnvironment) {
			// 将应用环境转换为相应应用类型的环境，比如：StandardServletEnvironment、StandardReactiveWebEnvironment、StandardEnvironment
			environment = new EnvironmentConverter(getClassLoader()).convertEnvironmentIfNecessary(environment,
					deduceEnvironmentClass());
		}
		// 5、防止对环境转换时有问题，这里再重新配置propertySource和activeProfiles
		ConfigurationPropertySources.attach(environment);
		return environment;
	}

	private Class<? extends StandardEnvironment> deduceEnvironmentClass() {
		switch (this.webApplicationType) {
		case SERVLET:
			return StandardServletEnvironment.class;
		case REACTIVE:
			return StandardReactiveWebEnvironment.class;
		default:
			return StandardEnvironment.class;
		}
	}

	private void prepareContext(ConfigurableApplicationContext context, ConfigurableEnvironment environment,
			SpringApplicationRunListeners listeners, ApplicationArguments applicationArguments, Banner printedBanner) {
		// 设置应用上下文的environment
		context.setEnvironment(environment);
		// 应用上下文后置处理,默认只会将ConversionService添加到context的beanFactory中
		postProcessApplicationContext(context);
		// 应用一些初始化器，执行ApplicationContextInitializer，将所有的初始化对象放到Spring上下文context对象中。
		applyInitializers(context);
		// 发布 应用上下文初始化完成事件ApplicationContextInitializedEvent
		listeners.contextPrepared(context);
		if (this.logStartupInfo) {
			// 打印启动信息
			logStartupInfo(context.getParent() == null);
			// 打印active profile的信息
			logStartupProfileInfo(context);
		}
		// Add boot specific singleton beans
		ConfigurableListableBeanFactory beanFactory = context.getBeanFactory();
		// 注册启动参数bean，将容器指定的参数封装成bean，注入容器
		beanFactory.registerSingleton("springApplicationArguments", applicationArguments);
		// 设置banner
		if (printedBanner != null) {
			beanFactory.registerSingleton("springBootBanner", printedBanner);
		}
		if (beanFactory instanceof DefaultListableBeanFactory) {
			((DefaultListableBeanFactory) beanFactory)
					// 设置是否运行bean定义名成重复，默认为false
					.setAllowBeanDefinitionOverriding(this.allowBeanDefinitionOverriding);
		}
		if (this.lazyInitialization) {
			context.addBeanFactoryPostProcessor(new LazyInitializationBeanFactoryPostProcessor());
		}
		// Load the sources
		// 加载所有资源（指的是启动器指定的参数） 合并Spring应用上下文配置源
		Set<Object> sources = getAllSources();
		Assert.notEmpty(sources, "Sources must not be empty");
		// 核心所在！！将我们自己的启动类bean、自动装配的内容  加载到上下文中
		load(context, sources.toArray(new Object[0]));
		// EventPublishingRunListener发布一个ApplicationPreparedEvent事件
		// 和前面发布事件的方式一样EventPublishingRunListener将Spring事件的发布委托给它SimpleApplicationEventMulticaster类型的成员initialMulticaster
		// 在这个阶段，各个监听器做的内容如下：
		listeners.contextLoaded(context);
	}

	private void refreshContext(ConfigurableApplicationContext context) {
		// Spring 入口，Spring应用上下文进入Spring生命周期，Spring Boot核心特性随之启动，如：自动装配、嵌入式容器启动Production-Ready特性。
		refresh((ApplicationContext) context);
		if (this.registerShutdownHook) {
			try {
				context.registerShutdownHook();
			}
			catch (AccessControlException ex) {
				// Not allowed in some environments.
			}
		}
	}

	private void configureHeadlessProperty() {
		//如果设置了java.awt.headless参数，则将其赋值给java.awt.headless系统属性，否者将true赋值给java.awt.headless系统属性。
		System.setProperty(SYSTEM_PROPERTY_JAVA_AWT_HEADLESS,
				System.getProperty(SYSTEM_PROPERTY_JAVA_AWT_HEADLESS, Boolean.toString(this.headless)));
	}

	private SpringApplicationRunListeners getRunListeners(String[] args) {
		Class<?>[] types = new Class<?>[] { SpringApplication.class, String[].class };
		//SpringApplicationRunListener 属于 组合模式的实现，其内部关联了SpringApplicationRunListner集合
		return new SpringApplicationRunListeners(logger,
				//这里只会获取到一个EventPublishingRunListeners对象，此对象会贯穿整个应用程序启动的过程，用于发布各种事件给到各个Spring事件监听器
				getSpringFactoriesInstances(SpringApplicationRunListener.class, types, this, args));
	}

	private <T> Collection<T> getSpringFactoriesInstances(Class<T> type) {
		return getSpringFactoriesInstances(type, new Class<?>[] {});
	}

	private <T> Collection<T> getSpringFactoriesInstances(Class<T> type, Class<?>[] parameterTypes, Object... args) {
		ClassLoader classLoader = getClassLoader();
		// Use names and ensure unique to protect against duplicates
		//从Spring.factories文件中获取类全路径名，此时的type就是具体需要加载的类的类型，比如ApplicationContextInitializer，ApplicationListener
		//SpringFactoriesLoader.loadFactoryName(Class,ClassLoader)中首先会根据类加载器加载出所有spring.factories中的所有内容
		//会解析所有加载的jar包中 META-INF/spring.factories配置文件的配置内容，并组装为Map<String, List>数据结构，方法返回,具体流程步骤：
		//首先，去缓冲中查询是否有入参classLoader对应的配置信息（仅第一次加载spring.factories文件时不走缓存），如果存在，则表明服务之前解析过配置文件 并 方法返回。如果不存在，则进行解析操作。
		//其次，获得所有依赖jar包中，具有META-INF/spring.factories配置文件的jar文件URI，并依次进行遍历。
		//接着，将spring.factories配置的内容转化成properties实例；遍历properties实例，将key和value维护到Map<String, List<String>> result数据结构中，如果多个spring.factories中的key相同，则value取合集。
		//最后，将result维护到缓冲cache中——key=ClassLoader value=result；并将result作为返回值返回。
		Set<String> names = new LinkedHashSet<>(SpringFactoriesLoader.loadFactoryNames(type, classLoader));
		// 反射创建相对应类的实例
		// 要实例化的类必须要有无参构造函数
		List<T> instances = createSpringFactoriesInstances(type, parameterTypes, classLoader, args, names);
		//根据order排序
		//AnnotationAwareOrderComparator类继承自OrderComparator，排序规则体现在OrderComparator类中的compare()方法；
		//每个排序对象都会通过OrderComparator#getOrder()方法获取排列的序列值。
		//getOrder()方法中首先通过findOrder()方法查找序列值，而AnnotationAwareOrderComparator重写了findOrder()方法。所以调用findOrder()方法会先进入到AnnotationAwareOrderComparator#findOrder()方法。
		//在AnnotationAwareOrderComparator#findOrder()方法中，会先调用其父类OrderComparator#findOrder()方法判断对象是否实现Ordered接口 并 重写了getOrder()方法，有则返回getOrder()的值。
		//否则通过findOrderFromAnnotation()方法从对象的@Order注解中获取具体的值，如果对象没有被@Order注解标注，则返回null。
		//最后到OrderComparator#getOrder()层面，如果findOrder()返回了具体的Integer值，则返回，否者返回Integer.MAX_VALUE。
		AnnotationAwareOrderComparator.sort(instances);
		return instances;
	}

	@SuppressWarnings("unchecked")
	private <T> List<T> createSpringFactoriesInstances(Class<T> type, Class<?>[] parameterTypes,
			ClassLoader classLoader, Object[] args, Set<String> names) {
		List<T> instances = new ArrayList<>(names.size());
		for (String name : names) {
			try {
				//加载class文件到内存
				Class<?> instanceClass = ClassUtils.forName(name, classLoader);
				Assert.isAssignable(type, instanceClass);
				//获取构造器
				Constructor<?> constructor = instanceClass.getDeclaredConstructor(parameterTypes);
				// 利用反射实例化对象
				T instance = (T) BeanUtils.instantiateClass(constructor, args);
				instances.add(instance);
			}
			catch (Throwable ex) {
				throw new IllegalArgumentException("Cannot instantiate " + type + " : " + name, ex);
			}
		}
		return instances;
	}

	private ConfigurableEnvironment getOrCreateEnvironment() {
		if (this.environment != null) {
			//首先尝试获取应用环境，如果环境不存在，则根据应用类型来创建对应的环境。
			return this.environment;
		}
		//根据不同的应用类型创建环境
		switch (this.webApplicationType) {
		case SERVLET:
			return new StandardServletEnvironment();
		case REACTIVE:
			// StandardReactiveWebEnvironment 继承自 StandardEnvironment，两者内容完全一样，
			// 只是StandardReactiveWebEnvironment又实现了ConfigurableReactiveWebEnvironment接口，虽然其中没做方法的重写
			return new StandardReactiveWebEnvironment();
		default:
			return new StandardEnvironment();
		}
	}

	/**
	 * Template method delegating to
	 * {@link #configurePropertySources(ConfigurableEnvironment, String[])} and
	 * {@link #configureProfiles(ConfigurableEnvironment, String[])} in that order.
	 * Override this method for complete control over Environment customization, or one of
	 * the above for fine-grained control over property sources or profiles, respectively.
	 * @param environment this application's environment
	 * @param args arguments passed to the {@code run} method
	 * @see #configureProfiles(ConfigurableEnvironment, String[])
	 * @see #configurePropertySources(ConfigurableEnvironment, String[])
	 */
	protected void configureEnvironment(ConfigurableEnvironment environment, String[] args) {
		if (this.addConversionService) {
			//加载类型转换器和初始化器
			ConversionService conversionService = ApplicationConversionService.getSharedInstance();
			environment.setConversionService((ConfigurableConversionService) conversionService);
		}
		//配置propertySources
		configurePropertySources(environment, args);
		// 获取所有的additionalProfiles和当前环境active的Profile,最后合并添加到environment的activeProfiles成员变量上
		configureProfiles(environment, args);
	}

	/**
	 * Add, remove or re-order any {@link PropertySource}s in this application's
	 * environment.
	 * @param environment this application's environment
	 * @param args arguments passed to the {@code run} method
	 * @see #configureEnvironment(ConfigurableEnvironment, String[])
	 */
	protected void configurePropertySources(ConfigurableEnvironment environment, String[] args) {
		MutablePropertySources sources = environment.getPropertySources();
		if (this.defaultProperties != null && !this.defaultProperties.isEmpty()) {
			sources.addLast(new MapPropertySource("defaultProperties", this.defaultProperties));
		}
		if (this.addCommandLineProperties && args.length > 0) {
			//如果存在命令行参数，则将命令行参数封装为SimpleCommandLinePropertySource添加到环境的propertySources成员变量中
			String name = CommandLinePropertySource.COMMAND_LINE_PROPERTY_SOURCE_NAME;
			if (sources.contains(name)) {
				PropertySource<?> source = sources.get(name);
				CompositePropertySource composite = new CompositePropertySource(name);
				composite.addPropertySource(
						new SimpleCommandLinePropertySource("springApplicationCommandLineArgs", args));
				composite.addPropertySource(source);
				sources.replace(name, composite);
			}
			else {
				sources.addFirst(new SimpleCommandLinePropertySource(args));
			}
		}
	}

	/**
	 * Configure which profiles are active (or active by default) for this application
	 * environment. Additional profiles may be activated during configuration file
	 * processing via the {@code spring.profiles.active} property.
	 * @param environment this application's environment
	 * @param args arguments passed to the {@code run} method
	 * @see #configureEnvironment(ConfigurableEnvironment, String[])
	 * @see org.springframework.boot.context.config.ConfigFileApplicationListener
	 */
	protected void configureProfiles(ConfigurableEnvironment environment, String[] args) {
		// 获取所有的additionalProfiles和当前环境active的Profile,最后合并添加到environment的activeProfiles成员变量上
		Set<String> profiles = new LinkedHashSet<>(this.additionalProfiles);
		profiles.addAll(Arrays.asList(environment.getActiveProfiles()));
		environment.setActiveProfiles(StringUtils.toStringArray(profiles));
	}

	private void configureIgnoreBeanInfo(ConfigurableEnvironment environment) {
		//默认设置spring.beaninfo.ignore系统属性为true（一般不会该这个，无需关注）
		if (System.getProperty(CachedIntrospectionResults.IGNORE_BEANINFO_PROPERTY_NAME) == null) {
			Boolean ignore = environment.getProperty("spring.beaninfo.ignore", Boolean.class, Boolean.TRUE);
			System.setProperty(CachedIntrospectionResults.IGNORE_BEANINFO_PROPERTY_NAME, ignore.toString());
		}
	}

	/**
	 * Bind the environment to the {@link SpringApplication}.
	 * @param environment the environment to bind
	 */
	protected void bindToSpringApplication(ConfigurableEnvironment environment) {
		try {
			Binder.get(environment).bind("spring.main", Bindable.ofInstance(this));
		}
		catch (Exception ex) {
			throw new IllegalStateException("Cannot bind to SpringApplication", ex);
		}
	}

	private Banner printBanner(ConfigurableEnvironment environment) {
		// bannerMode默认为CONSOLE
		if (this.bannerMode == Banner.Mode.OFF) {
			return null;
		}
		// 获取resourceLoader，默认为：DefaultResourceLoader
		ResourceLoader resourceLoader = (this.resourceLoader != null) ? this.resourceLoader
				: new DefaultResourceLoader(getClassLoader());
		SpringApplicationBannerPrinter bannerPrinter = new SpringApplicationBannerPrinter(resourceLoader, this.banner);
		if (this.bannerMode == Mode.LOG) {
			return bannerPrinter.print(environment, this.mainApplicationClass, logger);
		}
		// 打印Banner日志，然后返回
		return bannerPrinter.print(environment, this.mainApplicationClass, System.out);
	}

	/**
	 * Strategy method used to create the {@link ApplicationContext}. By default this
	 * method will respect any explicitly set application context or application context
	 * class before falling back to a suitable default.
	 * @return the application context (not yet refreshed)
	 * @see #setApplicationContextClass(Class)
	 */
	protected ConfigurableApplicationContext createApplicationContext() {
		Class<?> contextClass = this.applicationContextClass;
		if (contextClass == null) {
			try {
				switch (this.webApplicationType) {
				case SERVLET:
					// AnnotationConfigServletWebServerApplicationContext
					contextClass = Class.forName(DEFAULT_SERVLET_WEB_CONTEXT_CLASS);
					break;
				case REACTIVE:
					// AnnotationConfigReactiveWebServerApplicationContext
					contextClass = Class.forName(DEFAULT_REACTIVE_WEB_CONTEXT_CLASS);
					break;
				default:
					// AnnotationConfigApplicationContext
					contextClass = Class.forName(DEFAULT_CONTEXT_CLASS);
				}
			}
			catch (ClassNotFoundException ex) {
				throw new IllegalStateException(
						"Unable create a default ApplicationContext, please specify an ApplicationContextClass", ex);
			}
		}
		return (ConfigurableApplicationContext) BeanUtils.instantiateClass(contextClass);
	}

	/**
	 * Apply any relevant post processing the {@link ApplicationContext}. Subclasses can
	 * apply additional processing as required.
	 * @param context the application context
	 */
	protected void postProcessApplicationContext(ConfigurableApplicationContext context) {
		// 这里没有做任何的处理，因为beanNameGenerator和resourceLoader默认为空，可以重写该方法做扩展处理
		if (this.beanNameGenerator != null) {
			context.getBeanFactory().registerSingleton(AnnotationConfigUtils.CONFIGURATION_BEAN_NAME_GENERATOR,
					this.beanNameGenerator);
		}
		if (this.resourceLoader != null) {
			if (context instanceof GenericApplicationContext) {
				((GenericApplicationContext) context).setResourceLoader(this.resourceLoader);
			}
			if (context instanceof DefaultResourceLoader) {
				((DefaultResourceLoader) context).setClassLoader(this.resourceLoader.getClassLoader());
			}
		}
		// 默认会直接来到这里
		if (this.addConversionService) {
			context.getBeanFactory().setConversionService(ApplicationConversionService.getSharedInstance());
		}
	}

	/**
	 * Apply any {@link ApplicationContextInitializer}s to the context before it is
	 * refreshed.
	 * @param context the configured ApplicationContext (not refreshed yet)
	 * @see ConfigurableApplicationContext#refresh()
	 */
	@SuppressWarnings({ "rawtypes", "unchecked" })
	protected void applyInitializers(ConfigurableApplicationContext context) {
		// getInitializers方法获取所有的初始器
		// 遍历初始器，调用initialize方法，应用到Context中
		for (ApplicationContextInitializer initializer : getInitializers()) {
			Class<?> requiredType = GenericTypeResolver.resolveTypeArgument(initializer.getClass(),
					ApplicationContextInitializer.class);
			Assert.isInstanceOf(requiredType, context, "Unable to call initializer.");
			// ConfigurationWarningsApplicationContextInitializer 将添加ConfigurationWarningsPostProcessor到context的beanFactoryPostProcessors中
			// ContextIdApplicationContextInitializer 给applicationContext设置程序名，默认application，可以通过spring.application 设置
			// DelegatingApplicationContextInitializer  什么都没有做
			// RSocketPortInfoApplicationContextInitializer 将AnnotationConfigServletWebApplicationContext添加到applicationListeners中
			// ServerPortInfoApplicationContextInitializer 将自己添加到applicationListeners中
			// SharedMetadataReaderFactoryContextInitializer 将添加CachingMetadataReaderFactoryPostProcessor到context的beanFactoryPostProcessors中
			// ConditionEvaluationReportLoggingListener 将ConditionEvaluationReportListener添加到applicationListeners中
			initializer.initialize(context);
		}
	}

	/**
	 * Called to log startup information, subclasses may override to add additional
	 * logging.
	 * @param isRoot true if this application is the root of a context hierarchy
	 */
	protected void logStartupInfo(boolean isRoot) {
		if (isRoot) {
			new StartupInfoLogger(this.mainApplicationClass).logStarting(getApplicationLog());
		}
	}

	/**
	 * Called to log active profile information.
	 * @param context the application context
	 */
	protected void logStartupProfileInfo(ConfigurableApplicationContext context) {
		Log log = getApplicationLog();
		if (log.isInfoEnabled()) {
			String[] activeProfiles = context.getEnvironment().getActiveProfiles();
			if (ObjectUtils.isEmpty(activeProfiles)) {
				String[] defaultProfiles = context.getEnvironment().getDefaultProfiles();
				log.info("No active profile set, falling back to default profiles: "
						+ StringUtils.arrayToCommaDelimitedString(defaultProfiles));
			}
			else {
				log.info("The following profiles are active: "
						+ StringUtils.arrayToCommaDelimitedString(activeProfiles));
			}
		}
	}

	/**
	 * Returns the {@link Log} for the application. By default will be deduced.
	 * @return the application log
	 */
	protected Log getApplicationLog() {
		if (this.mainApplicationClass == null) {
			return logger;
		}
		return LogFactory.getLog(this.mainApplicationClass);
	}

	/**
	 * Load beans into the application context.
	 * @param context the context to load beans into
	 * @param sources the sources to load
	 */
	protected void load(ApplicationContext context, Object[] sources) {
		if (logger.isDebugEnabled()) {
			logger.debug("Loading source " + StringUtils.arrayToCommaDelimitedString(sources));
		}
		// 获取bean对象定义的加载器
		BeanDefinitionLoader loader = createBeanDefinitionLoader(getBeanDefinitionRegistry(context), sources);
		if (this.beanNameGenerator != null) {
			loader.setBeanNameGenerator(this.beanNameGenerator);
		}
		if (this.resourceLoader != null) {
			loader.setResourceLoader(this.resourceLoader);
		}
		if (this.environment != null) {
			loader.setEnvironment(this.environment);
		}
		// 实际执行load的是BeanDefinitionLoader中的load方法
		loader.load();
	}

	/**
	 * The ResourceLoader that will be used in the ApplicationContext.
	 * @return the resourceLoader the resource loader that will be used in the
	 * ApplicationContext (or null if the default)
	 */
	public ResourceLoader getResourceLoader() {
		return this.resourceLoader;
	}

	/**
	 * Either the ClassLoader that will be used in the ApplicationContext (if
	 * {@link #setResourceLoader(ResourceLoader) resourceLoader} is set, or the context
	 * class loader (if not null), or the loader of the Spring {@link ClassUtils} class.
	 * @return a ClassLoader (never null)
	 */
	public ClassLoader getClassLoader() {
		if (this.resourceLoader != null) {
			return this.resourceLoader.getClassLoader();
		}
		return ClassUtils.getDefaultClassLoader();
	}

	/**
	 * Get the bean definition registry.
	 * @param context the application context
	 * @return the BeanDefinitionRegistry if it can be determined
	 */
	private BeanDefinitionRegistry getBeanDefinitionRegistry(ApplicationContext context) {
		if (context instanceof BeanDefinitionRegistry) {
			return (BeanDefinitionRegistry) context;
		}
		if (context instanceof AbstractApplicationContext) {
			return (BeanDefinitionRegistry) ((AbstractApplicationContext) context).getBeanFactory();
		}
		throw new IllegalStateException("Could not locate BeanDefinitionRegistry");
	}

	/**
	 * Factory method used to create the {@link BeanDefinitionLoader}.
	 * @param registry the bean definition registry
	 * @param sources the sources to load
	 * @return the {@link BeanDefinitionLoader} that will be used to load beans
	 */
	protected BeanDefinitionLoader createBeanDefinitionLoader(BeanDefinitionRegistry registry, Object[] sources) {
		// 初始化了一些类定义加载器，AnnotatedBeanDefinitionReader，XmlBeanDefinitionReader
		// 扫描器：ClassPathBeanDefinitionScanner
		return new BeanDefinitionLoader(registry, sources);
	}

	/**
	 * Refresh the underlying {@link ApplicationContext}.
	 * @param applicationContext the application context to refresh
	 * @deprecated since 2.3.0 in favor of
	 * {@link #refresh(ConfigurableApplicationContext)}
	 */
	@Deprecated
	protected void refresh(ApplicationContext applicationContext) {
		Assert.isInstanceOf(ConfigurableApplicationContext.class, applicationContext);
		refresh((ConfigurableApplicationContext) applicationContext);
	}

	/**
	 * Refresh the underlying {@link ApplicationContext}.
	 * @param applicationContext the application context to refresh
	 */
	protected void refresh(ConfigurableApplicationContext applicationContext) {
		// ConfigurableApplicationContext是一个AnnotationConfigServletWebServerApplicationContext
		// 会调用父类AbstractApplicationContext的refresh方法
		// 在前面的准备阶段，已经提前准备了一些BFPP，比如SharedMetadataReaderFactoryContextInitializer、ConfigurationWarningsPostProcessor、ConfigFileApplicationListener，
		// 所以在refresh方法里面的invokeBeanFactoryPostProcessors方法会执行这些BFPP，SpringBoot自动装配
		applicationContext.refresh();
	}

	/**
	 * Called after the context has been refreshed.
	 * @param context the application context
	 * @param args the application arguments
	 */
	protected void afterRefresh(ConfigurableApplicationContext context, ApplicationArguments args) {
	}

	private void callRunners(ApplicationContext context, ApplicationArguments args) {
		List<Object> runners = new ArrayList<>();
		runners.addAll(context.getBeansOfType(ApplicationRunner.class).values());
		runners.addAll(context.getBeansOfType(CommandLineRunner.class).values());
		AnnotationAwareOrderComparator.sort(runners);
		for (Object runner : new LinkedHashSet<>(runners)) {
			if (runner instanceof ApplicationRunner) {
				callRunner((ApplicationRunner) runner, args);
			}
			if (runner instanceof CommandLineRunner) {
				callRunner((CommandLineRunner) runner, args);
			}
		}
	}

	private void callRunner(ApplicationRunner runner, ApplicationArguments args) {
		try {
			(runner).run(args);
		}
		catch (Exception ex) {
			throw new IllegalStateException("Failed to execute ApplicationRunner", ex);
		}
	}

	private void callRunner(CommandLineRunner runner, ApplicationArguments args) {
		try {
			(runner).run(args.getSourceArgs());
		}
		catch (Exception ex) {
			throw new IllegalStateException("Failed to execute CommandLineRunner", ex);
		}
	}

	private void handleRunFailure(ConfigurableApplicationContext context, Throwable exception,
			Collection<SpringBootExceptionReporter> exceptionReporters, SpringApplicationRunListeners listeners) {
		try {
			try {
				handleExitCode(context, exception);
				if (listeners != null) {
					listeners.failed(context, exception);
				}
			}
			finally {
				reportFailure(exceptionReporters, exception);
				if (context != null) {
					context.close();
				}
			}
		}
		catch (Exception ex) {
			logger.warn("Unable to close ApplicationContext", ex);
		}
		ReflectionUtils.rethrowRuntimeException(exception);
	}

	private void reportFailure(Collection<SpringBootExceptionReporter> exceptionReporters, Throwable failure) {
		try {
			for (SpringBootExceptionReporter reporter : exceptionReporters) {
				if (reporter.reportException(failure)) {
					registerLoggedException(failure);
					return;
				}
			}
		}
		catch (Throwable ex) {
			// Continue with normal handling of the original failure
		}
		if (logger.isErrorEnabled()) {
			logger.error("Application run failed", failure);
			registerLoggedException(failure);
		}
	}

	/**
	 * Register that the given exception has been logged. By default, if the running in
	 * the main thread, this method will suppress additional printing of the stacktrace.
	 * @param exception the exception that was logged
	 */
	protected void registerLoggedException(Throwable exception) {
		SpringBootExceptionHandler handler = getSpringBootExceptionHandler();
		if (handler != null) {
			handler.registerLoggedException(exception);
		}
	}

	private void handleExitCode(ConfigurableApplicationContext context, Throwable exception) {
		int exitCode = getExitCodeFromException(context, exception);
		if (exitCode != 0) {
			if (context != null) {
				context.publishEvent(new ExitCodeEvent(context, exitCode));
			}
			SpringBootExceptionHandler handler = getSpringBootExceptionHandler();
			if (handler != null) {
				handler.registerExitCode(exitCode);
			}
		}
	}

	private int getExitCodeFromException(ConfigurableApplicationContext context, Throwable exception) {
		int exitCode = getExitCodeFromMappedException(context, exception);
		if (exitCode == 0) {
			exitCode = getExitCodeFromExitCodeGeneratorException(exception);
		}
		return exitCode;
	}

	private int getExitCodeFromMappedException(ConfigurableApplicationContext context, Throwable exception) {
		if (context == null || !context.isActive()) {
			return 0;
		}
		ExitCodeGenerators generators = new ExitCodeGenerators();
		Collection<ExitCodeExceptionMapper> beans = context.getBeansOfType(ExitCodeExceptionMapper.class).values();
		generators.addAll(exception, beans);
		return generators.getExitCode();
	}

	private int getExitCodeFromExitCodeGeneratorException(Throwable exception) {
		if (exception == null) {
			return 0;
		}
		if (exception instanceof ExitCodeGenerator) {
			return ((ExitCodeGenerator) exception).getExitCode();
		}
		return getExitCodeFromExitCodeGeneratorException(exception.getCause());
	}

	SpringBootExceptionHandler getSpringBootExceptionHandler() {
		if (isMainThread(Thread.currentThread())) {
			return SpringBootExceptionHandler.forCurrentThread();
		}
		return null;
	}

	private boolean isMainThread(Thread currentThread) {
		return ("main".equals(currentThread.getName()) || "restartedMain".equals(currentThread.getName()))
				&& "main".equals(currentThread.getThreadGroup().getName());
	}

	/**
	 * Returns the main application class that has been deduced or explicitly configured.
	 * @return the main application class or {@code null}
	 */
	public Class<?> getMainApplicationClass() {
		return this.mainApplicationClass;
	}

	/**
	 * Set a specific main application class that will be used as a log source and to
	 * obtain version information. By default the main application class will be deduced.
	 * Can be set to {@code null} if there is no explicit application class.
	 * @param mainApplicationClass the mainApplicationClass to set or {@code null}
	 */
	public void setMainApplicationClass(Class<?> mainApplicationClass) {
		this.mainApplicationClass = mainApplicationClass;
	}

	/**
	 * Returns the type of web application that is being run.
	 * @return the type of web application
	 * @since 2.0.0
	 */
	public WebApplicationType getWebApplicationType() {
		return this.webApplicationType;
	}

	/**
	 * Sets the type of web application to be run. If not explicitly set the type of web
	 * application will be deduced based on the classpath.
	 * @param webApplicationType the web application type
	 * @since 2.0.0
	 */
	public void setWebApplicationType(WebApplicationType webApplicationType) {
		Assert.notNull(webApplicationType, "WebApplicationType must not be null");
		this.webApplicationType = webApplicationType;
	}

	/**
	 * Sets if bean definition overriding, by registering a definition with the same name
	 * as an existing definition, should be allowed. Defaults to {@code false}.
	 * @param allowBeanDefinitionOverriding if overriding is allowed
	 * @since 2.1.0
	 * @see DefaultListableBeanFactory#setAllowBeanDefinitionOverriding(boolean)
	 */
	public void setAllowBeanDefinitionOverriding(boolean allowBeanDefinitionOverriding) {
		this.allowBeanDefinitionOverriding = allowBeanDefinitionOverriding;
	}

	/**
	 * Sets if beans should be initialized lazily. Defaults to {@code false}.
	 * @param lazyInitialization if initialization should be lazy
	 * @since 2.2
	 * @see BeanDefinition#setLazyInit(boolean)
	 */
	public void setLazyInitialization(boolean lazyInitialization) {
		this.lazyInitialization = lazyInitialization;
	}

	/**
	 * Sets if the application is headless and should not instantiate AWT. Defaults to
	 * {@code true} to prevent java icons appearing.
	 * @param headless if the application is headless
	 */
	public void setHeadless(boolean headless) {
		this.headless = headless;
	}

	/**
	 * Sets if the created {@link ApplicationContext} should have a shutdown hook
	 * registered. Defaults to {@code true} to ensure that JVM shutdowns are handled
	 * gracefully.
	 * @param registerShutdownHook if the shutdown hook should be registered
	 */
	public void setRegisterShutdownHook(boolean registerShutdownHook) {
		this.registerShutdownHook = registerShutdownHook;
	}

	/**
	 * Sets the {@link Banner} instance which will be used to print the banner when no
	 * static banner file is provided.
	 * @param banner the Banner instance to use
	 */
	public void setBanner(Banner banner) {
		this.banner = banner;
	}

	/**
	 * Sets the mode used to display the banner when the application runs. Defaults to
	 * {@code Banner.Mode.CONSOLE}.
	 * @param bannerMode the mode used to display the banner
	 */
	public void setBannerMode(Banner.Mode bannerMode) {
		this.bannerMode = bannerMode;
	}

	/**
	 * Sets if the application information should be logged when the application starts.
	 * Defaults to {@code true}.
	 * @param logStartupInfo if startup info should be logged.
	 */
	public void setLogStartupInfo(boolean logStartupInfo) {
		this.logStartupInfo = logStartupInfo;
	}

	/**
	 * Sets if a {@link CommandLinePropertySource} should be added to the application
	 * context in order to expose arguments. Defaults to {@code true}.
	 * @param addCommandLineProperties if command line arguments should be exposed
	 */
	public void setAddCommandLineProperties(boolean addCommandLineProperties) {
		this.addCommandLineProperties = addCommandLineProperties;
	}

	/**
	 * Sets if the {@link ApplicationConversionService} should be added to the application
	 * context's {@link Environment}.
	 * @param addConversionService if the application conversion service should be added
	 * @since 2.1.0
	 */
	public void setAddConversionService(boolean addConversionService) {
		this.addConversionService = addConversionService;
	}

	/**
	 * Set default environment properties which will be used in addition to those in the
	 * existing {@link Environment}.
	 * @param defaultProperties the additional properties to set
	 */
	public void setDefaultProperties(Map<String, Object> defaultProperties) {
		this.defaultProperties = defaultProperties;
	}

	/**
	 * Convenient alternative to {@link #setDefaultProperties(Map)}.
	 * @param defaultProperties some {@link Properties}
	 */
	public void setDefaultProperties(Properties defaultProperties) {
		this.defaultProperties = new HashMap<>();
		for (Object key : Collections.list(defaultProperties.propertyNames())) {
			this.defaultProperties.put((String) key, defaultProperties.get(key));
		}
	}

	/**
	 * Set additional profile values to use (on top of those set in system or command line
	 * properties).
	 * @param profiles the additional profiles to set
	 */
	public void setAdditionalProfiles(String... profiles) {
		this.additionalProfiles = new LinkedHashSet<>(Arrays.asList(profiles));
	}

	/**
	 * Sets the bean name generator that should be used when generating bean names.
	 * @param beanNameGenerator the bean name generator
	 */
	public void setBeanNameGenerator(BeanNameGenerator beanNameGenerator) {
		this.beanNameGenerator = beanNameGenerator;
	}

	/**
	 * Sets the underlying environment that should be used with the created application
	 * context.
	 * @param environment the environment
	 */
	public void setEnvironment(ConfigurableEnvironment environment) {
		this.isCustomEnvironment = true;
		this.environment = environment;
	}

	/**
	 * Add additional items to the primary sources that will be added to an
	 * ApplicationContext when {@link #run(String...)} is called.
	 * <p>
	 * The sources here are added to those that were set in the constructor. Most users
	 * should consider using {@link #getSources()}/{@link #setSources(Set)} rather than
	 * calling this method.
	 * @param additionalPrimarySources the additional primary sources to add
	 * @see #SpringApplication(Class...)
	 * @see #getSources()
	 * @see #setSources(Set)
	 * @see #getAllSources()
	 */
	public void addPrimarySources(Collection<Class<?>> additionalPrimarySources) {
		this.primarySources.addAll(additionalPrimarySources);
	}

	/**
	 * Returns a mutable set of the sources that will be added to an ApplicationContext
	 * when {@link #run(String...)} is called.
	 * <p>
	 * Sources set here will be used in addition to any primary sources set in the
	 * constructor.
	 * @return the application sources.
	 * @see #SpringApplication(Class...)
	 * @see #getAllSources()
	 */
	public Set<String> getSources() {
		return this.sources;
	}

	/**
	 * Set additional sources that will be used to create an ApplicationContext. A source
	 * can be: a class name, package name, or an XML resource location.
	 * <p>
	 * Sources set here will be used in addition to any primary sources set in the
	 * constructor.
	 * @param sources the application sources to set
	 * @see #SpringApplication(Class...)
	 * @see #getAllSources()
	 */
	public void setSources(Set<String> sources) {
		Assert.notNull(sources, "Sources must not be null");
		this.sources = new LinkedHashSet<>(sources);
	}

	/**
	 * Return an immutable set of all the sources that will be added to an
	 * ApplicationContext when {@link #run(String...)} is called. This method combines any
	 * primary sources specified in the constructor with any additional ones that have
	 * been {@link #setSources(Set) explicitly set}.
	 * @return an immutable set of all sources
	 */
	public Set<Object> getAllSources() {
		Set<Object> allSources = new LinkedHashSet<>();
		// primarySources来自于SpringApplication构造器参数
		if (!CollectionUtils.isEmpty(this.primarySources)) {
			allSources.addAll(this.primarySources);
		}
		// sources来自于setSource(Set<Object>)方法
		if (!CollectionUtils.isEmpty(this.sources)) {
			allSources.addAll(this.sources);
		}
		return Collections.unmodifiableSet(allSources);
	}

	/**
	 * Sets the {@link ResourceLoader} that should be used when loading resources.
	 * @param resourceLoader the resource loader
	 */
	public void setResourceLoader(ResourceLoader resourceLoader) {
		Assert.notNull(resourceLoader, "ResourceLoader must not be null");
		this.resourceLoader = resourceLoader;
	}

	/**
	 * Sets the type of Spring {@link ApplicationContext} that will be created. If not
	 * specified defaults to {@link #DEFAULT_SERVLET_WEB_CONTEXT_CLASS} for web based
	 * applications or {@link AnnotationConfigApplicationContext} for non web based
	 * applications.
	 * @param applicationContextClass the context class to set
	 */
	public void setApplicationContextClass(Class<? extends ConfigurableApplicationContext> applicationContextClass) {
		this.applicationContextClass = applicationContextClass;
		this.webApplicationType = WebApplicationType.deduceFromApplicationContext(applicationContextClass);
	}

	/**
	 * Sets the {@link ApplicationContextInitializer} that will be applied to the Spring
	 * {@link ApplicationContext}.
	 * @param initializers the initializers to set
	 */
	public void setInitializers(Collection<? extends ApplicationContextInitializer<?>> initializers) {
		this.initializers = new ArrayList<>(initializers);
	}

	/**
	 * Add {@link ApplicationContextInitializer}s to be applied to the Spring
	 * {@link ApplicationContext}.
	 * @param initializers the initializers to add
	 */
	public void addInitializers(ApplicationContextInitializer<?>... initializers) {
		this.initializers.addAll(Arrays.asList(initializers));
	}

	/**
	 * Returns read-only ordered Set of the {@link ApplicationContextInitializer}s that
	 * will be applied to the Spring {@link ApplicationContext}.
	 * @return the initializers
	 */
	public Set<ApplicationContextInitializer<?>> getInitializers() {
		//直接从SpringApplication的initializers成员中获取7个初始化器，而initializers的初始化发生在SpringApplication构建阶段，并且根据Order排序
		return asUnmodifiableOrderedSet(this.initializers);
	}

	/**
	 * Sets the {@link ApplicationListener}s that will be applied to the SpringApplication
	 * and registered with the {@link ApplicationContext}.
	 * @param listeners the listeners to set
	 */
	public void setListeners(Collection<? extends ApplicationListener<?>> listeners) {
		this.listeners = new ArrayList<>(listeners);
	}

	/**
	 * Add {@link ApplicationListener}s to be applied to the SpringApplication and
	 * registered with the {@link ApplicationContext}.
	 * @param listeners the listeners to add
	 */
	public void addListeners(ApplicationListener<?>... listeners) {
		this.listeners.addAll(Arrays.asList(listeners));
	}

	/**
	 * Returns read-only ordered Set of the {@link ApplicationListener}s that will be
	 * applied to the SpringApplication and registered with the {@link ApplicationContext}
	 * .
	 * @return the listeners
	 */
	public Set<ApplicationListener<?>> getListeners() {
		return asUnmodifiableOrderedSet(this.listeners);
	}

	/**
	 * Static helper that can be used to run a {@link SpringApplication} from the
	 * specified source using default settings.
	 * @param primarySource the primary source to load
	 * @param args the application arguments (usually passed from a Java main method)
	 * @return the running {@link ApplicationContext}
	 */
	public static ConfigurableApplicationContext run(Class<?> primarySource, String... args) {
		//这里是Spring boot项目启动的入口
		return run(new Class<?>[] { primarySource }, args);
	}

	/**
	 * Static helper that can be used to run a {@link SpringApplication} from the
	 * specified sources using default settings and user supplied arguments.
	 * @param primarySources the primary sources to load
	 * @param args the application arguments (usually passed from a Java main method)
	 * @return the running {@link ApplicationContext}
	 */
	public static ConfigurableApplicationContext run(Class<?>[] primarySources, String[] args) {
		// 先初始化一个SpringApplication，加载11个Spring个应用上下文初始化器（ApplicationContextInitializer）、
		// 加载7个Spring应用事件监听器（ApplicationListener）；
		// ApplicationContextInitializer和 ApplicationListener内建的实现类预置在spring-boot jar包的META-INF/spring.factories文件
		// 和spring-boot-autoconfigure jar包的META-INF/spring.factories文件中

		// primarySources参数实际为Spring Boot应用上下文的Configuration Class。
		// 换言之，Spring Boot应用上下文的Configuration Class也不一定非得是引导类（启动类），
		// 我们可以把Configuration Class抽出来，使用@EnableAutoConfiguration注解标注，
		// 然后将引导类（启动类）中的primarySources参数设置为抽出来的Configuration Class。

		// 然后调用其run方法
		return new SpringApplication(primarySources).run(args);
	}

	/**
	 * A basic main that can be used to launch an application. This method is useful when
	 * application sources are defined via a {@literal --spring.main.sources} command line
	 * argument.
	 * <p>
	 * Most developers will want to define their own main method and call the
	 * {@link #run(Class, String...) run} method instead.
	 * @param args command line arguments
	 * @throws Exception if the application cannot be started
	 * @see SpringApplication#run(Class[], String[])
	 * @see SpringApplication#run(Class, String...)
	 */
	public static void main(String[] args) throws Exception {
		SpringApplication.run(new Class<?>[0], args);
	}

	/**
	 * Static helper that can be used to exit a {@link SpringApplication} and obtain a
	 * code indicating success (0) or otherwise. Does not throw exceptions but should
	 * print stack traces of any encountered. Applies the specified
	 * {@link ExitCodeGenerator} in addition to any Spring beans that implement
	 * {@link ExitCodeGenerator}. In the case of multiple exit codes the highest value
	 * will be used (or if all values are negative, the lowest value will be used)
	 * @param context the context to close if possible
	 * @param exitCodeGenerators exist code generators
	 * @return the outcome (0 if successful)
	 */
	public static int exit(ApplicationContext context, ExitCodeGenerator... exitCodeGenerators) {
		Assert.notNull(context, "Context must not be null");
		int exitCode = 0;
		try {
			try {
				ExitCodeGenerators generators = new ExitCodeGenerators();
				Collection<ExitCodeGenerator> beans = context.getBeansOfType(ExitCodeGenerator.class).values();
				generators.addAll(exitCodeGenerators);
				generators.addAll(beans);
				exitCode = generators.getExitCode();
				if (exitCode != 0) {
					context.publishEvent(new ExitCodeEvent(context, exitCode));
				}
			}
			finally {
				close(context);
			}
		}
		catch (Exception ex) {
			ex.printStackTrace();
			exitCode = (exitCode != 0) ? exitCode : 1;
		}
		return exitCode;
	}

	private static void close(ApplicationContext context) {
		if (context instanceof ConfigurableApplicationContext) {
			ConfigurableApplicationContext closable = (ConfigurableApplicationContext) context;
			closable.close();
		}
	}

	private static <E> Set<E> asUnmodifiableOrderedSet(Collection<E> elements) {
		List<E> list = new ArrayList<>(elements);
		list.sort(AnnotationAwareOrderComparator.INSTANCE);
		return new LinkedHashSet<>(list);
	}

}
