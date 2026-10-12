---
title: "Dart 泛型协变及其运行时风险"

tags:
  - Dart
  - Generics
  - Covariant
---

> 这篇文章讨论一下 Dart 泛型中的协变（covariant）。

### 与 Java 的不同之处

熟悉 Java 的人都知道，泛型类型在 Java 中是不变（invariant）的，这意味着 `List<Cat>` 并不是 `List<Animal>` 的子类。比如以下代码在 Java 中会在编译期报错（`Cat` 是 `Animal` 的子类）：

```java
List<Cat> cats = new ArrayList<>();
List<Animal> animals = cats; // error
```

需要让此类型发生协变（covariant）：

```java
List<Cat> cats = new ArrayList<>();
List<? extends Animal> animals = cats; // correct
```

但在 Dart 中，`List<Cat>` 是 `List<Animal>` 的子类，这意味着以下代码是正常运行的：

```dart
List<Animal> animals = <Cat>[];
```

我们此时再定义一个 `Animal` 的子类——`Dog`，并往 `animals` 添加一只 `Dog`：

```dart
animals.add(Dog());
```

以上代码在编译期通过，但是在运行时会发生错误：

```text
Unhandled exception:
type 'Dog' is not a subtype of type 'Cat' of 'value'
#0      List.add (dart:core-patch/growable_array.dart)
#1      main (file:///home/xianxueliang/IdeaProjects/dart_app/bin/dart_app.dart:11:12)
#2      _startIsolate.<anonymous closure> (dart:isolate-patch/isolate_patch.dart:299:32)
#3      _RawReceivePortImpl._handleMessage (dart:isolate-patch/isolate_patch.dart:168:12)
```

Dart 语言的泛型系统没有 Java 语言那么完善，协变（covariant，关键字 `covariant`）在 Dart 中也比较鸡肋，限制也比较多，也没有逆变一说，所以在进行程序设计的时候需要格外注意。

```dart
abstract class Animal {
  void chase(covariant Animal animal);
}

class Cat extends Animal {
  @override
  void chase(Cat animal) {}
}

class Dog extends Animal {
  @override
  void chase(Dog animal) {}
}
```
