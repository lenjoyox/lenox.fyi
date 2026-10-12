---
title: "Java ClassLoader：类路径、双亲委派与自定义加载"

tags:
  - Java
  - ClassLoader
---

> 这篇文章聊一下 Java 中的 `ClassLoader`。

### 前言

假如现在有一个 class file 叫做 `cn.nikeo.Square.class`，位于 `/home/lenox/IdeaProjects/kotlin-starter/` 目录下，如下所示：

```java
// IntelliJ API Decompiler stub source generated from a class file
// Implementation of methods is not available

package cn.nikeo;

public class Square {
  private final int left;
  private final int top;
  private final int right;
  private final int bottom;

  public Square(int left, int top, int right, int bottom) { /* compiled code */ }

  public int width() { /* compiled code */ }

  public int height() { /* compiled code */ }

  public int perimeter() { /* compiled code */ }

  public int area() { /* compiled code */ }
}
```

我现在需要加载并使用它，有以下几种方式。

### 通过 classpath 加载类

#### Gradle

如果你使用 Gradle，可以使用如下方式：

```kotlin
// build.gradle.kts

dependencies {
    runtimeOnly(files("/home/lenox/IdeaProjects/kotlin-starter"))
}
```

#### `java -classpath`

在使用 `java` 执行 main class 文件的时候，添加 `-classpath /home/lenox/IdeaProjects/kotlin-starter` 选项，如：

```bash
java -Dfile.encoding=UTF-8 \
  -classpath /home/lenox/.sdkman/candidates/java/8.0.275.open-adpt/jre/lib/rt.jar:/home/lenox/IdeaProjects/kotlin-starter \
  Main
```

其实这两种方式都是将 `/home/lenox/IdeaProjects/kotlin-starter` 添加到 classpath，此时你就可以使用 `/home/lenox/IdeaProjects/kotlin-starter` 下的 class files 了。

#### 查看 `java.class.path`

你可以通过 `System.getProperty("java.class.path")` 查看当前运行环境下所有的 classpath。比如：

```text
// println(System.getProperty("java.class.path").split(":").joinToString("\n"))

/home/lenox/.sdkman/candidates/java/8.0.275.open-adpt/jre/lib/charsets.jar
/home/lenox/.sdkman/candidates/java/8.0.275.open-adpt/jre/lib/ext/cldrdata.jar
/home/lenox/.sdkman/candidates/java/8.0.275.open-adpt/jre/lib/ext/dnsns.jar
/home/lenox/.sdkman/candidates/java/8.0.275.open-adpt/jre/lib/ext/jaccess.jar
/home/lenox/.sdkman/candidates/java/8.0.275.open-adpt/jre/lib/ext/localedata.jar
/home/lenox/.sdkman/candidates/java/8.0.275.open-adpt/jre/lib/ext/nashorn.jar
/home/lenox/.sdkman/candidates/java/8.0.275.open-adpt/jre/lib/ext/sunec.jar
/home/lenox/.sdkman/candidates/java/8.0.275.open-adpt/jre/lib/ext/sunjce_provider.jar
/home/lenox/.sdkman/candidates/java/8.0.275.open-adpt/jre/lib/ext/sunpkcs11.jar
/home/lenox/.sdkman/candidates/java/8.0.275.open-adpt/jre/lib/ext/zipfs.jar
/home/lenox/.sdkman/candidates/java/8.0.275.open-adpt/jre/lib/jce.jar
/home/lenox/.sdkman/candidates/java/8.0.275.open-adpt/jre/lib/jfr.jar
/home/lenox/.sdkman/candidates/java/8.0.275.open-adpt/jre/lib/jsse.jar
/home/lenox/.sdkman/candidates/java/8.0.275.open-adpt/jre/lib/management-agent.jar
/home/lenox/.sdkman/candidates/java/8.0.275.open-adpt/jre/lib/resources.jar
/home/lenox/.sdkman/candidates/java/8.0.275.open-adpt/jre/lib/rt.jar
/home/lenox/IdeaProjects/kotlin-starter/build/classes/java/main
/home/lenox/IdeaProjects/kotlin-starter/build/classes/kotlin/main
/home/lenox/.gradle/caches/modules-2/files-2.1/org.jetbrains.kotlin/kotlin-stdlib-jdk8/1.4.21/3ad7f99fb330947a12451ea16767d192d763600a/kotlin-stdlib-jdk8-1.4.21.jar
/home/lenox/.gradle/caches/modules-2/files-2.1/com.squareup.okhttp3/okhttp/4.9.0/8e17601d3bdc8cf57902c154de021931d2c27c1/okhttp-4.9.0.jar
/home/lenox/.gradle/caches/modules-2/files-2.1/io.reactivex.rxjava3/rxjava/3.0.8/b9ff4d4d216a088b337fde628a49d0f3233962e7/rxjava-3.0.8.jar
/home/lenox/.gradle/caches/modules-2/files-2.1/org.jetbrains.kotlin/kotlin-stdlib-jdk7/1.4.21/26b6082f9296911bdcb8e72a7cc68692c7025a03/kotlin-stdlib-jdk7-1.4.21.jar
/home/lenox/.gradle/caches/modules-2/files-2.1/org.jetbrains.kotlin/kotlin-stdlib/1.4.21/4a668382d7c38688d3490afde93b6a113ed46698/kotlin-stdlib-1.4.21.jar
/home/lenox/.gradle/caches/modules-2/files-2.1/com.squareup.okio/okio/2.8.0/49b64e09d81c0cc84b267edd0c2fd7df5a64c78c/okio-jvm-2.8.0.jar
/home/lenox/.gradle/caches/modules-2/files-2.1/org.reactivestreams/reactive-streams/1.0.3/d9fb7a7926ffa635b3dcaa5049fb2bfa25b3e7d0/reactive-streams-1.0.3.jar
/home/lenox/.gradle/caches/modules-2/files-2.1/org.jetbrains.kotlin/kotlin-stdlib-common/1.4.21/7f48a062aa4b53215998780f7c245a4276828e1d/kotlin-stdlib-common-1.4.21.jar
/home/lenox/.gradle/caches/modules-2/files-2.1/org.jetbrains/annotations/13.0/919f0dfe192fb4e063e7dacadee7f8bb9a2672a9/annotations-13.0.jar
/home/lenox/IdeaProjects/kotlin-starter
/home/lenox/.local/share/JetBrains/Toolbox/apps/IDEA-U/ch-0/203.5981.155/lib/idea_rt.
```

#### 系统 `ClassLoader`

现在你可以使用系统的 `ClassLoader` 去加载位于 `/home/lenox/IdeaProjects/kotlin-starter/` 目录下的 `cn.nikeo.Square.class` 了：

```kotlin
fun main() {
    val clazz = ClassLoader.getSystemClassLoader().loadClass("cn.nikeo.Square")
    val constructor = clazz.getConstructor(Int::class.java, Int::class.java, Int::class.java, Int::class.java)
    val c = constructor.newInstance(10, 10, 100, 100)

    println(clazz.getMethod("width").invoke(c)) // output: 90
    println(clazz.getMethod("height").invoke(c)) // output: 90
    println(clazz.getMethod("perimeter").invoke(c)) // output: 360
    println(clazz.getMethod("area").invoke(c)) // output: 8100
}
```

### 自定义 `ClassLoader`

现在介绍第三种方式——自定义 `ClassLoader`。

```kotlin
class LocalClassLoader(private val classPath: String) : ClassLoader() {
    override fun findClass(name: String): Class<*> {
        val className = name.replace(".", "/") + ".class"
        val classFile = File(classPath, className)
        require(classFile.exists() && classFile.isFile) {
            "$classFile is not exist."
        }

        val b = loadClassData(classFile)
        return defineClass(name, b, 0, b.size)
    }

    private fun loadClassData(classFile: File): ByteArray {
        return classFile.inputStream().readBytes()
    }
}
```

然后我们可以使用这个 `LocalClassLoader` 去加载位于 `/home/lenox/IdeaProjects/kotlin-starter/` 目录下的 `cn.nikeo.Square.class` 了：

```kotlin
fun main() {
    val clazz = LocalClassLoader("/home/lenox/IdeaProjects/kotlin-starter").loadClass("cn.nikeo.Square")
    val constructor = clazz.getConstructor(Int::class.java, Int::class.java, Int::class.java, Int::class.java)
    val c = constructor.newInstance(10, 10, 100, 100)

    println(clazz.getMethod("width").invoke(c)) // output: 90
    println(clazz.getMethod("height").invoke(c)) // output: 90
    println(clazz.getMethod("perimeter").invoke(c)) // output: 360
    println(clazz.getMethod("area").invoke(c)) // output: 8100
}
```

### `ClassLoader` 简介

`ClassLoader` 是用来将 Java 类加载到 JVM 中，Java 源代码经过 Java 编译器（`javac` 命令）编译成 Java 字节码文件后，该字节码文件被加载进 JVM 中解释执行。

具体来说，该字节码文件由 `ClassLoader` 负责读取，然后将其转换成 `java.lang.Class` 类的一个实例（Java 类），然后我们可以通过 `getConstructor()` 和 `newInstance` 创建出该类的一个对象。

### `ClassLoader` 分类

总的来说有四类 `ClassLoader`。

#### Bootstrap class loader

这个 `ClassLoader` 主要用来加载 Java 的核心库，需要注意的是这个 `ClassLoader` 存在于 native，所以它并没有继承 `java.lang.ClassLoader`。

```java
// java.lang.ClassLoader.java

/**
 * Returns a class loaded by the bootstrap class loader;
 * or return null if not found.
 */
private Class<?> findBootstrapClassOrNull(String name)
{
    if (!checkName(name)) return null;

    return findBootstrapClass(name);
}

// return null if not found
private native Class<?> findBootstrapClass(String name);
```

这个 `ClassLoader` 加载哪个路径下的资源呢，我们可以查看以下源码：

```java
// sun.misc.Launcher.java

public class Launcher {
    ...

    private static String bootClassPath =
        System.getProperty("sun.boot.class.path");

    ...

    private static class BootClassPathHolder {
        static final URLClassPath bcp;
        static {
            URL[] urls;
            if (bootClassPath != null) {
                urls = AccessController.doPrivileged(
                    new PrivilegedAction<URL[]>() {
                        public URL[] run() {
                            File[] classPath = getClassPath(bootClassPath);
                            int len = classPath.length;
                            Set<File> seenDirs = new HashSet<File>();
                            for (int i = 0; i < len; i++) {
                                File curEntry = classPath[i];
                                // Negative test used to properly handle
                                // nonexistent jars on boot class path
                                if (!curEntry.isDirectory()) {
                                    curEntry = curEntry.getParentFile();
                                }
                                if (curEntry != null && seenDirs.add(curEntry)) {
                                    MetaIndex.registerDirectory(curEntry);
                                }
                            }
                            return pathToURLs(classPath);
                        }
                    }
                );
            } else {
                urls = new URL[0];
            }
            bcp = new URLClassPath(urls, factory, null);
            bcp.initLookupCache(null);
        }
    }

    public static URLClassPath getBootstrapClassPath() {
        return BootClassPathHolder.bcp;
    }
}
```

由上面的源码可知，路径定义在 `sun.boot.class.path` 这个系统属性下，我们可以打印一下：

```text
// println(System.getProperty("sun.boot.class.path").split(":").joinToString("\n"))

/home/lenox/.sdkman/candidates/java/8.0.275.open-adpt/jre/lib/resources.jar
/home/lenox/.sdkman/candidates/java/8.0.275.open-adpt/jre/lib/rt.jar
/home/lenox/.sdkman/candidates/java/8.0.275.open-adpt/jre/lib/sunrsasign.jar
/home/lenox/.sdkman/candidates/java/8.0.275.open-adpt/jre/lib/jsse.jar
/home/lenox/.sdkman/candidates/java/8.0.275.open-adpt/jre/lib/jce.jar
/home/lenox/.sdkman/candidates/java/8.0.275.open-adpt/jre/lib/charsets.jar
/home/lenox/.sdkman/candidates/java/8.0.275.open-adpt/jre/lib/jfr.jar
/home/lenox/.sdkman/candidates/java/8.0.275.open-adpt/jre/classes
```

#### ExtClassLoader

这个 `ClassLoader` 主要用来加载 Java 的拓展库，它是 `java.lang.ClassLoader` 的字类。

```java
/*
 * The class loader used for loading installed extensions.
 */
static class ExtClassLoader extends URLClassLoader {
    ...

    private static File[] getExtDirs() {
        String s = System.getProperty("java.ext.dirs");
        File[] dirs;
        if (s != null) {
            StringTokenizer st =
                new StringTokenizer(s, File.pathSeparator);
            int count = st.countTokens();
            dirs = new File[count];
            for (int i = 0; i < count; i++) {
                dirs[i] = new File(st.nextToken());
            }
        } else {
            dirs = new File[0];
        }
        return dirs;
    }

    ...
}
```

由上面的源码可知，路径定义在 `java.ext.dirs` 这个系统属性下，我们可以打印一下：

```text
// println(System.getProperty("java.ext.dirs").split(":").joinToString("\n"))

/home/lenox/.sdkman/candidates/java/8.0.275.open-adpt/jre/lib/ext
/usr/java/packages/lib/ext
```

#### AppClassLoader

这个 `ClassLoader` 主要根据 Java 类路径（CLASSPATH）用来加载 Java 类，它是 `java.lang.ClassLoader` 的字类。

```java
/**
 * The class loader used for loading from java.class.path.
 * runs in a restricted security context.
 */
static class AppClassLoader extends URLClassLoader {

    ...

    public static ClassLoader getAppClassLoader(final ClassLoader extcl)
        throws IOException
    {
        final String s = System.getProperty("java.class.path");
        final File[] path = (s == null) ? new File[0] : getClassPath(s);

        // Note: on bugid 4256530
        // Prior implementations of this doPrivileged() block supplied
        // a rather restrictive ACC via a call to the private method
        // AppClassLoader.getContext(). This proved overly restrictive
        // when loading  classes. Specifically it prevent
        // accessClassInPackage.sun.* grants from being honored.
        //
        return AccessController.doPrivileged(
            new PrivilegedAction<AppClassLoader>() {
                public AppClassLoader run() {
                URL[] urls =
                    (s == null) ? new URL[0] : pathToURLs(path);
                return new AppClassLoader(urls, extcl);
            }
        });
    }

    ...
}
```

由上面的源码可知，路径定义在 `java.class.path` 这个系统属性下，我们可以打印一下：

```text
// println(System.getProperty("java.class.path").split(":").joinToString("\n"))

/home/lenox/.sdkman/candidates/java/8.0.275.open-adpt/jre/lib/charsets.jar
/home/lenox/.sdkman/candidates/java/8.0.275.open-adpt/jre/lib/ext/cldrdata.jar
/home/lenox/.sdkman/candidates/java/8.0.275.open-adpt/jre/lib/ext/dnsns.jar
/home/lenox/.sdkman/candidates/java/8.0.275.open-adpt/jre/lib/ext/jaccess.jar
/home/lenox/.sdkman/candidates/java/8.0.275.open-adpt/jre/lib/ext/localedata.jar
/home/lenox/.sdkman/candidates/java/8.0.275.open-adpt/jre/lib/ext/nashorn.jar
/home/lenox/.sdkman/candidates/java/8.0.275.open-adpt/jre/lib/ext/sunec.jar
/home/lenox/.sdkman/candidates/java/8.0.275.open-adpt/jre/lib/ext/sunjce_provider.jar
/home/lenox/.sdkman/candidates/java/8.0.275.open-adpt/jre/lib/ext/sunpkcs11.jar
/home/lenox/.sdkman/candidates/java/8.0.275.open-adpt/jre/lib/ext/zipfs.jar
/home/lenox/.sdkman/candidates/java/8.0.275.open-adpt/jre/lib/jce.jar
/home/lenox/.sdkman/candidates/java/8.0.275.open-adpt/jre/lib/jfr.jar
/home/lenox/.sdkman/candidates/java/8.0.275.open-adpt/jre/lib/jsse.jar
/home/lenox/.sdkman/candidates/java/8.0.275.open-adpt/jre/lib/management-agent.jar
/home/lenox/.sdkman/candidates/java/8.0.275.open-adpt/jre/lib/resources.jar
/home/lenox/.sdkman/candidates/java/8.0.275.open-adpt/jre/lib/rt.jar
/home/lenox/IdeaProjects/kotlin-starter/build/classes/java/main
/home/lenox/IdeaProjects/kotlin-starter/build/classes/kotlin/main
/home/lenox/.gradle/caches/modules-2/files-2.1/org.jetbrains.kotlin/kotlin-stdlib-jdk8/1.4.21/3ad7f99fb330947a12451ea16767d192d763600a/kotlin-stdlib-jdk8-1.4.21.jar
/home/lenox/.gradle/caches/modules-2/files-2.1/com.squareup.okhttp3/okhttp/4.9.0/8e17601d3bdc8cf57902c154de021931d2c27c1/okhttp-4.9.0.jar
/home/lenox/.gradle/caches/modules-2/files-2.1/io.reactivex.rxjava3/rxjava/3.0.8/b9ff4d4d216a088b337fde628a49d0f3233962e7/rxjava-3.0.8.jar
/home/lenox/.gradle/caches/modules-2/files-2.1/org.jetbrains.kotlin/kotlin-stdlib-jdk7/1.4.21/26b6082f9296911bdcb8e72a7cc68692c7025a03/kotlin-stdlib-jdk7-1.4.21.jar
/home/lenox/.gradle/caches/modules-2/files-2.1/org.jetbrains.kotlin/kotlin-stdlib/1.4.21/4a668382d7c38688d3490afde93b6a113ed46698/kotlin-stdlib-1.4.21.jar
/home/lenox/.gradle/caches/modules-2/files-2.1/com.squareup.okio/okio/2.8.0/49b64e09d81c0cc84b267edd0c2fd7df5a64c78c/okio-jvm-2.8.0.jar
/home/lenox/.gradle/caches/modules-2/files-2.1/org.reactivestreams/reactive-streams/1.0.3/d9fb7a7926ffa635b3dcaa5049fb2bfa25b3e7d0/reactive-streams-1.0.3.jar
/home/lenox/.gradle/caches/modules-2/files-2.1/org.jetbrains.kotlin/kotlin-stdlib-common/1.4.21/7f48a062aa4b53215998780f7c245a4276828e1d/kotlin-stdlib-common-1.4.21.jar
/home/lenox/.gradle/caches/modules-2/files-2.1/org.jetbrains/annotations/13.0/919f0dfe192fb4e063e7dacadee7f8bb9a2672a9/annotations-13.0.jar
/home/lenox/IdeaProjects/kotlin-starter
/home/lenox/.local/share/JetBrains/Toolbox/apps/IDEA-U/ch-0/203.5981.155/lib/idea_rt.jar
```

通过 `ClassLoader.getSystemClassLoader()` 获取到的就是 `AppClassLoader`：

```java
// java.lang.ClassLoader.java

public abstract class ClassLoader {
    public static ClassLoader getSystemClassLoader() {
        initSystemClassLoader();
        if (scl == null) {
            return null;
        }
        SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            checkClassLoaderPermission(scl, Reflection.getCallerClass());
        }
        return scl;
    }

    private static synchronized void initSystemClassLoader() {
        if (!sclSet) {
            if (scl != null)
                throw new IllegalStateException("recursive invocation");
            sun.misc.Launcher l = sun.misc.Launcher.getLauncher();
            if (l != null) {
                Throwable oops = null;
                scl = l.getClassLoader();
                try {
                    scl = AccessController.doPrivileged(
                        new SystemClassLoaderAction(scl));
                } catch (PrivilegedActionException pae) {
                    oops = pae.getCause();
                    if (oops instanceof InvocationTargetException) {
                        oops = oops.getCause();
                    }
                }
                if (oops != null) {
                    if (oops instanceof Error) {
                        throw (Error) oops;
                    } else {
                        // wrap the exception
                        throw new Error(oops);
                    }
                }
            }
            sclSet = true;
        }
    }
}

// sun.misc.Launcher.java

public class Launcher {
    private static URLStreamHandlerFactory factory = new Factory();
    private static Launcher launcher = new Launcher();
    private static String bootClassPath =
        System.getProperty("sun.boot.class.path");

    public static Launcher getLauncher() {
        return launcher;
    }

    private ClassLoader loader;

    public Launcher() {
        // Create the extension class loader
        ClassLoader extcl;
        try {
            extcl = ExtClassLoader.getExtClassLoader();
        } catch (IOException e) {
            throw new InternalError(
                "Could not create extension class loader", e);
        }

        // Now create the class loader to use to launch the application
        try {
            loader = AppClassLoader.getAppClassLoader(extcl);
        } catch (IOException e) {
            throw new InternalError(
                "Could not create application class loader", e);
        }

        // Also set the context class loader for the primordial thread.
        Thread.currentThread().setContextClassLoader(loader);

        // Finally, install a security manager if requested
        String s = System.getProperty("java.security.manager");
        if (s != null) {
            // init FileSystem machinery before SecurityManager installation
            sun.nio.fs.DefaultFileSystemProvider.create();

            SecurityManager sm = null;
            if ("".equals(s) || "default".equals(s)) {
                sm = new java.lang.SecurityManager();
            } else {
                try {
                    sm = (SecurityManager)loader.loadClass(s).newInstance();
                } catch (IllegalAccessException e) {
                } catch (InstantiationException e) {
                } catch (ClassNotFoundException e) {
                } catch (ClassCastException e) {
                }
            }
            if (sm != null) {
                System.setSecurityManager(sm);
            } else {
                throw new InternalError(
                    "Could not create SecurityManager: " + s);
            }
        }
    }

    /*
     * Returns the class loader used to launch the main application.
     */
    public ClassLoader getClassLoader() {
        return loader;
    }
}
```

#### Custom ClassLoader

这个 `ClassLoader` 是用户根据实际需求自定义的 `ClassLoader`，如上面提到的 `LocalClassLoader`。

### 双亲委派机制

按照 `java.lang.ClassLoader` 类的构造器，可以说明每个 `ClassLoader` 都有一个 Parent ClassLoader，当通过 `loadClass` 方法加载字节码文件的时候，会先代理给其 Parent ClassLoader，然后一层一层地传递。

```java
// java.lang.ClassLoader.java

public abstract class ClassLoader {

    protected Class<?> loadClass(String name, boolean resolve)
        throws ClassNotFoundException
    {
        synchronized (getClassLoadingLock(name)) {
            // First, check if the class has already been loaded
            Class<?> c = findLoadedClass(name);
            if (c == null) {
                long t0 = System.nanoTime();
                try {
                    if (parent != null) {
                        c = parent.loadClass(name, false);
                    } else {
                        c = findBootstrapClassOrNull(name);
                    }
                } catch (ClassNotFoundException e) {
                    // ClassNotFoundException thrown if class not found
                    // from the non-null parent class loader
                }

                if (c == null) {
                    // If still not found, then invoke findClass in order
                    // to find the class.
                    long t1 = System.nanoTime();
                    c = findClass(name);

                    // this is the defining class loader; record the stats
                    sun.misc.PerfCounter.getParentDelegationTime().addTime(t1 - t0);
                    sun.misc.PerfCounter.getFindClassTime().addElapsedTimeFrom(t1);
                    sun.misc.PerfCounter.getFindClasses().increment();
                }
            }
            if (resolve) {
                resolveClass(c);
            }
            return c;
        }
    }
}
```

由上分析源码可知几个 `ClassLoader` 有以下关系：

```text
BootstrapClassLoader(Native)
    ^
    | parent（loadClass()）
ExtClassLoader(Java) --> (findClass()) --> (defineClass())
    ^
    | parent（loadClass()）
AppClassLoader(Java) --> (findClass()) --> (defineClass())
```

当我们使用 `ClassLoader.getSystemClassLoader()`（`AppClassLoader`）去 `loadClass()` 的时候，会先调用 `ExtClassLoader` 的 `loadClass()`，由于 `ExtClassLoader` 没有 `parent`，所以会使用 BootstrapClassLoader 去加载 class file，如果在引导类路径中没有找到 class file，则调用 `ExtClassLoader` 的 `findClass()` 去寻找，找到了就调用 `defineClass` 去将 class file 转化为 `java.lang.Class`；如果 `ExtClassLoader` 没有加载成功，则会向下继续调用 `AppClassLoader` 的 `findClass()` 去寻找，找到了就调用 `defineClass` 去将 class file 转化为 `java.lang.Class`；加载是个从上到下的过程。

### `findLoadedClass`

查找名称为 `name` 的已经被加载过的类，这样不用每次重新去加载：

```java
try {
    Class<?> aClass = ClassLoader.getSystemClassLoader().loadClass("cn.nikeo.Rect");
    Class<?> bClass = ClassLoader.getSystemClassLoader().loadClass("cn.nikeo.Rect"); // findLoadedClass
    System.out.println(aClass);
} catch (ClassNotFoundException e) {
    e.printStackTrace();
}
```

### `Class.forName`

```java
public static Class<?> forName(String name, boolean initialize, ClassLoader loader)
```

- `name`：类的全限定名。
- `initialize`：是否初始化，**如果初始化，则会调用该类的静态代码块**。
- `loader`：类加载器，**默认是 bootstrap class loader**。

使用 `ClassLoader.getSystemClassLoader().loadClass()` **不会调用该类的静态代码块**。
