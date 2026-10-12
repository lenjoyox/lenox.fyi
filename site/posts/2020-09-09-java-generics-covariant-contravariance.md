---
title: "Java 泛型协变、逆变与 PECS 原则"

tags:
  - Java
  - Generics
  - Contravariance
  - Covariant
---

> 这里讨论一下 Java 中的泛型（Generics），重点说明协变（covariant）和逆变（contravariance）。

首先，在 Java 中，泛型类型是不变（invariant）的，这意味着 `List<String>` 并不是 `List<Object>` 的子类。并且使用有界通配符（bounded wildcards）可以增加 API 的灵活性。
为了方便下面讨论，我们这里定义 3 个有继承关系的类：`Animal`、`Cat`、`Dog`。

```java
class Animal {}
class Cat extends Animal {}
class Dog extends Animal {}
```

### 协变（covariant）

首先我们查看一下 `Collection` 的 `addAll` 的方法签名：

```java
class Collection<E> {
    boolean addAll(Collection<? extends E> c);
}
```

将 `addAll` 的参数类型定义成 `Collection<? extends E>` 而非 `Collection<E>` 的好处在于，可以让这个方法接受类型为 `E` 或 `E` 子类型的 `Collection`，这样极大地增加了 API 的灵活性。

这样我们有个清晰的理解：虽然 `Collection<Cat>` 不是 `Collection<Animal>` 的子类型，但 `Collection<Cat>` 是 `Collection<? extends Animal>` 的子类型，换句话说，带有上边界的通配符使这个类型发生了协变（the wildcard with an extends-bound (upper bound) makes the type covariant）。

```java
void covariant(List<? extends Animal> items) {
    Animal first = items.get(0);
}
```

### 逆变（contravariance）

`Collection<? super Dog>` 这种类型匹配所有泛型类型是 `Dog` 及其超类的集合类型，如 `Collection<Dog>` ， `Collection<Animal>` ， `Collection<Object>` 。一个很重要的点是，对于发生逆变(contravariance)的类型，我们既可以对其 *读（read）* 也可以对其 *写（write）* ，但是：读（read）的类型是 `Object` ，而不是 `Dog` ，因为我们并不知具体是 `Dog` ， `Animal` 还是 `Object` ，我们仅仅知道这些类的共同点，它们有一个共同的超类- `Object` ；写（write）的类型只支持 `Dog` 类型，因为我们并不知具体是 `Dog` ， `Animal` 还是 `Object` ，我们仅仅知道这些类的共同点，它们有一个子类- `Dog`。

```java
void contravariance(List<? super Dog> items) {
    items.add(new Dog());
    Object first = items.get(0);
}
```

### PECS

这是 *Producer-Extends, Consumer-Super* 的缩写，表示从生产者里读（协变，covariant），写入到消费者（逆变，contravariance）。

Joshua Bloch recommends：*For maximum flexibility, use wildcard types on input parameters that represent producers or consumers, and proposes the following mnemonic: PECS*
