[![Maven Central](https://img.shields.io/maven-central/v/org.cojen/boxtin.svg?label=Maven%20Central)](https://central.sonatype.com/artifact/org.cojen/boxtin)

Boxtin is a customizable Java security manager agent, intended to replace the original security manager, which is now [disabled](https://openjdk.org/jeps/486).

Boxtin provides an [instrumentation agent](https://docs.oracle.com/en/java/javase/24/docs/api/java.instrument/java/lang/instrument/package-summary.html) which modifies classes to include the necessary security checks. It's launched with a custom [controller](https://cojen.github.io/Boxtin/javadoc/org.cojen.boxtin/org/cojen/boxtin/Controller.html) which decides what operations are allowed for a given module.

```
java -javaagent:Boxtin.jar=my.app.SecurityController ...
```

If the controller is specified as "default", then a default is selected which only allows limited access to the [`java.base`](https://cojen.github.io/Boxtin/javadoc/org.cojen.boxtin/org/cojen/boxtin/RulesApplier.html#java_base()) module. If no controller is specified at all, then the [`activate`](https://cojen.github.io/Boxtin/javadoc/org.cojen.boxtin/org/cojen/boxtin/SecurityAgent.html#activate(org.cojen.boxtin.Controller)) method must be called later, preferably from the main method.

Boxtin is designed to restrict operations for "plugins", much like the original security manager was designed for restricting applet permissions. There are a few key differences, however:

- Boxtin is a shallow sandbox, which means it only checks the immediate caller to see if an operation is denied. It does this by modifying the caller class.
- The original Java security manager implemented a deep sandbox, by walking the stack. This seemed like a good idea at the time, but in practice it was too complicated and inefficient. The [`AccessController.doPrivileged`](https://docs.oracle.com/en/java/javase/23/docs/api/java.base/java/security/AccessController.html) methods were intended for handling special cases, but they were actually used over 1000 times in the JDK.
- With Boxtin, rules are defined entirely by the host environment, and so the libraries it depends on don't require any modifications.
- Rules are selected by module, not by code source or protection domain.
- The standard deny action is to throw a `SecurityException`, but alternative [deny actions](https://cojen.github.io/Boxtin/javadoc/org.cojen.boxtin/org/cojen/boxtin/DenyAction.html) can be configured instead.

A _caller_ is the plugin code, represented by a [module](https://docs.oracle.com/en/java/javase/24/docs/api/java.base/java/lang/Module.html), possibly unnamed. A _target_ is the code which is being called by the caller, represented by a rule. A rule logically maps target methods or constructors to an "allow" or "deny" outcome. Targets must be defined in modules &mdash; classes and interfaces loaded from the class path cannot have deniable operations.

Boxtin works by examining classes to see if any invocation of a constructor or method matches against a deny rule. If so, the class is transformed such that all suitable deny actions are applied. This type of transformation is strictly a "caller-side" check, and "target-side" checks are never performed — no stack trace is ever captured at runtime, and target classes are never modified unless they themselves call any denied operations.

The controller decides which set of rules apply for a given caller module. For convenience, a [`RulesApplier`](https://cojen.github.io/Boxtin/javadoc/org.cojen.boxtin/org/cojen/boxtin/RulesApplier.html) can define a standard set of rules, by name or category. For example: [`java.base`](https://github.com/cojen/Boxtin/blob/main/agent/src/main/java/org/cojen/boxtin/JavaBaseApplier.java)

Rules cannot allow access to operations beyond the boundaries already established by modules. As an example, a rule cannot be defined to allow access to the internal JDK classes. The existing Java `--add-exports` and `--add-opens` options must be used instead.

Rules cannot deny access to operations within a module. A caller is always allowed to call any target operation in its own module, restricted only by the usual private/protected checks. Any rule which would deny access within a module is ignored.

When a module exports a package P to a specific module B, then module B has access to all the public members of package P, regardless of what Boxtin rules have been defined. Although it might seem useful to define specific overrides, it makes configuration more confusing. A qualified export _is_ a type of access rule, which is why it's honored.

## Code transformations

The `SecurityAgent` installs a [`ClassFileTransformer`](https://docs.oracle.com/en/java/javase/24/docs/api/java.instrument/java/lang/instrument/ClassFileTransformer.html) which transforms classes and interfaces which have any denied operations. Classes and interfaces loaded by the bootstrap class loader are allowed to call anything, and so they're exempt from transformation.

The `Code` attribute of each method is scanned, searching for `invokevirtual`, `invokespecial`, `invokestatic` and `invokeinterface` bytecode operations. If it's determined that the invocation refers to the class itself, then the operation is allowed. Otherwise, a corresponding rule is selected from the [`rules`](https://cojen.github.io/Boxtin/javadoc/org.cojen.boxtin/org/cojen/boxtin/Rules.html) provided by the controller. If the rule indicates that the operation is denied, then a series of checks are logically inserted immediately before the invoke operation.

If multiple denial rules are applicable, then selection checks are performed to determine which rule to actually apply. These checks are necessary when a denied method signature exists in multiple classes, and the inheritance hierarchy isn't precisely known at the time the class is being transformed. An example is the `close()` method defined in the `URLClassLoader` and `ForkJoinPool` classes, both of which are denied by the `java.base` rules. For example:

```java
    // An object which has a `close()` method.
    SomeObject obj = ...

    invoke: {
        // These are the inserted selection checks...
        if (obj instanceof java.net.URLClassLoader) {
            // Denied.
            throw new java.lang.SecurityException();
        } else if (obj instanceof java.util.concurrent.ForkJoinPool) {
            // Denied. The deny action indicates that `close()` should do nothing.
            break invoke;
        }

        // Original invocation.
        obj.close();
    }
```

If the method being invoked is static, then the [`isAssignable`](https://docs.oracle.com/en/java/javase/24/docs/api/java.base/java/lang/Class.html#isAssignableFrom(java.lang.Class)) method is used instead of the `instanceof` operator. In either case, these checks are optimized by the JVM such that they are effectively eliminated. The resulting code will always perform a specific deny operation, or it will always allow the original invocation.

In the above example, no additional checks are made before performing a denial operation. Additional checks can be inserted to test the target module, or to perform a [`checked`](https://cojen.github.io/Boxtin/javadoc/org.cojen.boxtin/org/cojen/boxtin/DenyAction.html#checked(java.lang.invoke.MethodHandleInfo,org.cojen.boxtin.DenyAction)) deny action.

The purpose of the module check is to allow invocations within a module. As was stated earlier, a caller is always allowed to call any target operation in its own module. The module check is inserted because the exact module that the target is loaded into isn't known at the time the class is being transformed.

```java
    if (thisClass.getModule() != targetClass.getModule()) {
        // Perform the deny action.
        ...
    }
```

In practice, the module check will be optimized away by the JVM, and so there's no additional runtime cost. The invocation will always be denied or it will always be allowed.

If the class file transformer can prove that the caller class cannot be in the same module as the target class, then the module check is elided, and the operation is always denied. It currently only does this when the target class is in a "java.*" package, but the caller class isn't.

### Subtyping restrictions

When a class or interface is explicitly denied, the entire set of accessible methods it supports is expanded when the set of rules is built. This makes it possible for the `isAssignable` and `instanceof` checks to work, as described earlier. When a package (or module) is denied by default, all classes and interfaces contained within are implicitly denied. These classes and their methods aren't expanded, because it requires obtaining all of the classes in a package, and this cannot be performed reliably. Even if it were, the rules would become quite large, and the chain of `isAssignable` and `instanceof` checks would also become quite large. Without these checks, it's possible to extend a denied class and gain access to its inherited methods.

To guard against this, classes which are defined as subtypes of implicitly denied classes have special restrictions applied, when they're defined in different modules. If the subtype implements an implicitly denied interface, then the code is transformed such that construction isn't possible. If necessary, all of the constructors are replaced with ones which just throw a new `SecurityException`. As a result, access to the inherited instance methods is denied because instantiation is now impossible. If the subtype extends an implicitly denied class, then all of the constructors are already denied as a side-effect of having to call the denied superclass constructor.

Static methods require special attention, because they can be inherited and accessed without instantiation. Static methods defined in interfaces aren't inherited, and so nothing special needs to be done in that case. Special transformations are required for static methods which are inherited from an implicitly denied class.

For each accessible static method (which isn't already declared locally in the subtype), a synthetic override is generated which matches the signature of the inherited method. The override applies the necessary security checks against the inherited method, and only if the operation is actually allowed will the original method be called. Ordinarily the operation is denied, but the deny action for the package might be [`checked`](https://cojen.github.io/Boxtin/javadoc/org.cojen.boxtin/org/cojen/boxtin/DenyAction.html#checked(java.lang.invoke.MethodHandleInfo,org.cojen.boxtin.DenyAction)).

If the inherited static method is final, it still needs to be overridden to ensure that the access check is applied. Overriding such a method is perfectly legal as far as the JVM is concerned, and it's the Java compiler which restricts this.

Subtyping restrictions act a little bit differently than explicit class denials. For example, the [`java.base`](https://cojen.github.io/Boxtin/javadoc/org.cojen.boxtin/org/cojen/boxtin/RulesApplier.html#java_base()) rules deny access to the `java.nio.channels.spi` package. The `AbstractInterruptibleChannel` is defined in this package, and so it's implicitly denied. A subclass defined by a module which has these rules applied to it cannot be constructed &mdash; the subclass constructor always throws an exception.

However, the `java.nio.channels.SelectableChannel` class is a subclass of `AbstractInterruptibleChannel`, and this is allowed because both classes are in the same module. In addition, the `SelectableChannel` class is fully allowed because all classes in the `java.nio.channels` package are allowed by default, according to the `java.base` rules. The module which was denied the ability to subclass `AbstractInterruptibleChannel` directly can subclass `SelectableChannel`, thus extending `AbstractInterruptibleChannel` indirectly.

If `AbstractInterruptibleChannel` is explicitly denied and `SelectableChannel` is still allowed, subclassing `SelectableChannel` is still allowed, but the methods inherited from `AbstractInterruptibleChannel` are denied. If `SelectableChannel` overrides any of them, access is still denied. This inconsistency is undesirable, but it's not clear which behavior should be preferred.

### MethodHandle constants

The Java classfile format supports defining [`MethodHandle`](https://docs.oracle.com/javase/specs/jvms/se24/html/jvms-4.html#jvms-4.4.8) constants, which are primarily used by Java lambdas. When necessary, Boxtin transforms these constants such that a security check is put in place. It does this by replacing the original constant with one that calls a synthetic proxy method.

```java
    // original
    public void fail() {
        OptionalInt.of(1).ifPresent(System::exit);
    }

    // transformed
    public void fail() {
        OptionalInt.of(1).ifPresent(param -> $boxtin$5(param));
    }

    // synthetic proxy method
    private static void $boxtin$5(int param) {
        // deny actions go here
        ...
        // call the original method if the deny action actually allows it
        System.exit(param);
    }
```

### Hidden classes

The JVM doesn't pass hidden classes to instrumentation agents, and so this would permit hidden classes to completely bypass any rules. Boxtin could simply deny access to the `defineHiddenClass` and `defineHiddenClassWithClassData` methods, but instead it alters them such that hidden classes can be transformed just like any other class. Any class definition which is passed to these methods is first passed to the `SecurityAgent` for applying any necessary transformations. Code within the JDK itself is permitted to define hidden classes without going through these public methods, and so these classes won't be transformed. This isn't an issue because it doesn't permit arbitrary hidden classes to be defined.

### Fail secure behavior

When a `ClassFileTransformer` throws an exception, the instrumentation agent ignores it and continues on with the original untransformed class. If the Boxtin transformation fails in an unexpected way, then this could provide a rogue class a means to bypass the security checks. Instead, any unhandled exception during transformation is logged, and an empty class is produced instead. This prevents a rogue class from causing harm, but bugs in Boxtin can completely disable valid classes. Failing in all cases is the safer option.

## Reflection

The standard rules for the `java.base` module permit reflection operations, but with some restrictions. Access is checked when `Constructor` and `Method` instances are acquired, and not when they're invoked. See [`checkReflection`](https://cojen.github.io/Boxtin/javadoc/org.cojen.boxtin/org/cojen/boxtin/RulesApplier.html#checkReflection()) for more details.

## Object methods

Methods declared in the root `Object` class cannot be denied, even when done so explicitly. This makes it easier to deny all methods in a class without breaking these fundamental operations. Even if these operations could be denied, the caller check would be bypassed when any of these methods are invoked by any of the classes in the `java.base` module. For example: `String.valueOf(obj)` calls `toString()`.

## Limitations

The technique currently used to modify code is relatively simple and efficient, but it does have a limitation. The original invoke instruction for a denied operation is replaced with a `goto`, which branches to new code which is added at the end of the method. If the operation is allowed to continue, a `goto` or `goto_w` instruction branches back to the location just after the original invoke. 

Because the `goto` instruction is limited to a signed 16-bit offset, methods larger than 32767 bytes might not be transformable. When this happens, an error is logged and the entire class is replaced with an empty one. Ideally a `goto_w` instruction should be used, but it doesn't fit in the space occupied by the original invoke instruction. The exception is the `invokeinterface` instruction, which is large enough to be replaced with a `goto_w` instruction.

Even if the `goto` limitation was resolved, very large methods might still not be transformable, because the upper limit for a method is 65535 bytes.


