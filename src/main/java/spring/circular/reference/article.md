#Spring中的循环依赖与bean的构造过程
##依赖的定义
所谓依赖就是在一个类需要引用到另一个类，大致可以分为两类：
1. 构造依赖：在构造一个对象的时候需要使用到另一个类的对象。
```java
    class ClassB {    }
    class ClassA {
        private ClassB classB;
        public ClassA() {
            classB = new ClassB(); //ClassA --> ClassB (ClassA依赖于ClassB)
        }
    }
```
2. 属性依赖：在对象构造完毕之后，把另一个类的对象作为属性赋给第一个对象。
```java
    class ClassB {    }
    class ClassA {
        private ClassB classB;
        public void setClassB(final ClassB classB) {
            this.classB = classB;//ClassA --> ClassB (ClassA依赖于ClassB)
        }
    }
```
##循环依赖的定义
在上面的两个例子中，如果一个类依赖于另一个类，我们用-->表示，所谓的循环依赖就是依赖关系会形成一个环。
1. 构造依赖
```java
    class ClassB {
        private ClassA classA;
        public ClassB() {
            classA = new ClassA(); // ClassB --> ClassA
        }
    }
    class ClassA {
        private ClassB classB;
        public ClassA() {
            classB = new ClassB(); // ClassA --> ClassB (ClassA依赖于ClassB)
        }
    }
```
可以看出这里面的依赖关系构成了环，在代码运行的时候不管是构造ClassA还是ClassB都不会成功，运行的时候会导致
```Java
Exception in thread "main" java.lang.StackOverflowError
```
因为这里的逻辑会无限循环下去。
2. 属性依赖
```java
    class ClassB { 
        private ClassA classA;
        public void setClassA(final ClassA classA) {
            this.classA = classA; // ClassB --> ClassA
        }
    }
    class ClassA {
        private ClassB classB;
        public void setClassB(final ClassB classB) {
            this.classB = classB;// ClassA --> ClassB (ClassA依赖于ClassB)
        }
    }
```
这样的属性依赖就完全没有问题，因为可以首先实例化ClassA和ClassB，然后把对象通过set方法赋值。
##Spring中的循环依赖
在Spring中，第一种循环依赖是无法解决的，所以我说的Spring中的循环依赖特指第二种循环依赖，如下所示：
```java
@Service
public class AService {
    @Autowired
    private BService bService;
}
@Service
public class BService {
    @Autowired
    private AService aService;
}
```
Spring天然支持第二种循环依赖。有人可能有疑问，创建AService实例的时候需要用到BService实例，创建BService实例需要用到AService实例，这是怎么做到的呢？实际上很简单，步驟如下：
1. Spring首先会调用AService的构造函数获得其实例aService；
2. 把aService放入一个叫做earlySingletonObjects的map中；
3. Spring会尝试注入bService，发现earlySingletonObjects中没有，
4. Spring调用BService的构造函数获取其实例bService；
5. Spring尝试注入aService，发现aService已经存在，直接把它赋给bService.aService；
6. 现在bService构造完毕了，继续构造aService，把bService赋给给aService.bService。
至此，整个构造过程完毕。

这里将aService赋给bService.aService的过程通过的是反射机制，因此可以操作private member。

##增强后的循环依赖
现在我把Bean稍作修改：
```java
@Service
public class AService {
    @Autowired
    private BService bService;
    @Async
    public void testFun() { }
}
@Service
public class BService {
    @Autowired
    private AService aService;
    @Async
    public void testFun() { }
}
```
是不是还是一样可以编译呢？答案是不可以：
```java
Exception in thread "main" org.springframework.beans.factory.BeanCurrentlyInCreationException: Error creating bean with name 'AService': Bean with name 'AService' has been injected into other beans [BService] in its raw version as part of a circular reference, but has eventually been wrapped. This means that said other beans do not use the final version of the bean. This is often the result of over-eager type matching - consider using 'getBeanNamesOfType' with the 'allowEagerInit' flag turned off, for example.
	at org.springframework.beans.factory.support.AbstractAutowireCapableBeanFactory.doCreateBean(AbstractAutowireCapableBeanFactory.java:612)
	at org.springframework.beans.factory.support.AbstractAutowireCapableBeanFactory.createBean(AbstractAutowireCapableBeanFactory.java:502)
	at org.springframework.beans.factory.support.AbstractBeanFactory.lambda$doGetBean$0(AbstractBeanFactory.java:312)
	at org.springframework.beans.factory.support.DefaultSingletonBeanRegistry.getSingleton(DefaultSingletonBeanRegistry.java:228)
	at org.springframework.beans.factory.support.AbstractBeanFactory.doGetBean(AbstractBeanFactory.java:310)
	at org.springframework.beans.factory.support.AbstractBeanFactory.getBean(AbstractBeanFactory.java:200)
	at org.springframework.beans.factory.support.DefaultListableBeanFactory.preInstantiateSingletons(DefaultListableBeanFactory.java:760)
	at org.springframework.context.support.AbstractApplicationContext.finishBeanFactoryInitialization(AbstractApplicationContext.java:868)
	at org.springframework.context.support.AbstractApplicationContext.refresh(AbstractApplicationContext.java:549)
	at org.springframework.context.annotation.AnnotationConfigApplicationContext.<init>(AnnotationConfigApplicationContext.java:88)
	at com.andy.xiao.cn.Application.main(Application.java:10)
```
为什么呢？ 我们来看Spring源代码：
```Java
//org.springframework.beans.factory.support.AbstractAutowireCapableBeanFactory#doCreateBean
if (earlySingletonExposure) {
			Object earlySingletonReference = getSingleton(beanName, false);
			if (earlySingletonReference != null) {
				//由于@Async存在，导致了bean在上文中被Enhance，使得exposedObject和bean不相等。从而导致了再次判断循环依赖。最终抛出了循环依赖。
				if (exposedObject == bean) {
					exposedObject = earlySingletonReference;
				}
				else if (!this.allowRawInjectionDespiteWrapping && hasDependentBean(beanName)) {
					String[] dependentBeans = getDependentBeans(beanName);
					Set<String> actualDependentBeans = new LinkedHashSet<>(dependentBeans.length);
					for (String dependentBean : dependentBeans) {
						if (!removeSingletonIfCreatedForTypeCheckOnly(dependentBean)) {
							actualDependentBeans.add(dependentBean);
						}
					}
					if (!actualDependentBeans.isEmpty()) {
						throw new BeanCurrentlyInCreationException(beanName,
								"Bean with name '" + beanName + "' has been injected into other beans [" +
								StringUtils.collectionToCommaDelimitedString(actualDependentBeans) +
								"] in its raw version as part of a circular reference, but has eventually been " +
								"wrapped. This means that said other beans do not use the final version of the " +
								"bean. This is often the result of over-eager type matching - consider using " +
								"'getBeanNamesOfType' with the 'allowEagerInit' flag turned off, for example.");
					}
				}
			}
		}
```
至于为什么增强之后会导致重新解决循环依赖，我猜测是因为增强之后可能会增加一些原来不存在的依赖属性，因此需要再次寻找。一个证明有效的解决方式如下：

```Java
public class AService {
    private BService bService;
    @Autowired
    private ApplicationContext applicationContext;
    @PostConstruct
    public void init() {
        bService = applicationContext.getBean(BService.class);
    }
    @Async
    public void testFun() {
    }
}
@Service
public class BService {
    private AService aService;
    @Autowired
    private ApplicationContext applicationContext;
    @PostConstruct
    public void init() {
        aService = applicationContext.getBean(AService.class);
    }
    @Async
    public void testFun() {
    }
}
```

