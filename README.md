
Boxtin is a customizable Java security manager agent, intended to replace the original security manager, which is now [disabled](https://openjdk.org/jeps/486).

Project status: Under heavy development, and many more tests are needed.

Boxtin provides an [instrumentation agent](https://docs.oracle.com/en/java/javase/24/docs/api/java.instrument/java/lang/instrument/package-summary.html) which modifies classes to include the necessary security checks. It's launched with a custom [controller](https://cojen.github.io/Boxtin/javadoc/org.cojen.boxtin/org/cojen/boxtin/Controller.html) which decides what operations are allowed for a given module.

```
java -javaagent:Boxtin.jar=my.app.SecurityController ...
```

If the controller is specified as "default", then a default is selected which only allows limited access to the [`java.base`](https://cojen.github.io/Boxtin/javadoc/org.cojen.boxtin/org/cojen/boxtin/RulesApplier.html#java_base()) module. If no controller is specified, then the [`activate`](https://cojen.github.io/Boxtin/javadoc/org.cojen.boxtin/org/cojen/boxtin/SecurityAgent.html#activate(org.cojen.boxtin.Controller)) method must be called later, preferably from the main method.

Boxtin is designed to restrict operations for "plugins", much like the original security manager was designed for restricting applet permissions. There are a few key differences, however:

- Boxtin is a shallow sandbox, which means it only checks the immediate caller to see if an operation is denied. The original Java security manager checked all frames within the trace, which seemed like a good idea at the time, but in practice it was too complicated. The [`AccessController.doPrivileged`](https://docs.oracle.com/en/java/javase/23/docs/api/java.base/java/security/AccessController.html) methods were intended for handling special cases, but it they were actually used over 1200 times in the JDK. What was expected to be an exceptional case ended up being the normal case.
- With Boxtin, rules are defined entirely by the host environment, and so the libraries it depends on aren't expected to require any modifications.
- Rules are selected by module, not by code source or protection domain.
- The standard deny action is to throw a `SecurityException`, but alternative [deny actions](https://cojen.github.io/Boxtin/javadoc/org.cojen.boxtin/org/cojen/boxtin/DenyAction.html) can be configured instead.

A _caller_ is the plugin code, represented by a [module](https://docs.oracle.com/en/java/javase/24/docs/api/java.base/java/lang/Module.html), possibly unnamed. A _target_ is the code which is being called by the caller, represented by a rule. A rule logically maps target methods or constructors to an "allow" or "deny" outcome.

Boxtin works by examining classes to see if any invocation of a constructor or method matches against a deny rule. If so, the class is transformed such that all suitable deny actions are applied. This type of transformation is strictly a "caller-side" check, and "target-side" checks are never performed — no stack trace is ever captured at runtime, and target classes are never modified unless they themselves call any denied operations.

The controller decides which set of rules apply for a given module. For convenience, a [`RulesApplier`](https://cojen.github.io/Boxtin/javadoc/org.cojen.boxtin/org/cojen/boxtin/RulesApplier.html) can define a standard set of rules, by name or category. For example: [`java.base`](https://github.com/cojen/Boxtin/blob/main/agent/src/main/java/org/cojen/boxtin/JavaBaseApplier.java)

Rules cannot allow access to operations beyond the boundaries already established by modules. As an example, a rule cannot be defined to allow access to the internal JDK classes. The existing Java `--add-exports` and `--add-opens` options must be used instead.

Rules cannot deny access to operations within a module. A caller is always allowed to call any target operation in its own module, restricted only by the usual private/protected checks. Any rule which would deny access within a module is ignored.

When a module exports a package P to a specific module B, then module B has access to all the public members of package P, regardless of what Boxtin rules have been defined. Although it might seem useful to define specific overrides, it makes configuration more confusing. A qualified export _is_ a type of access rule, which is why it's honored.

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

### Reflection

The standard rules for the `java.base` module permit reflection operations, but with some restrictions. Access is guarded when `Constructor` and `Method` instances are acquired, and not when they're invoked. Custom deny rules perform an access check is performed at that time, possibly resulting in an exception being thrown. For methods which return an array, (example: `Class.getMethods()`), a filtering step is applied which removes elements which cannot be accessed.

The following methods in `java.lang.Class` have custom deny actions:

- `getConstructor` - can throw a `NoSuchMethodException`
- `getConstructors` - can filter the results
- `getDeclaredConstructor` - can throw a `NoSuchMethodException`
- `getDeclaredConstructors` - can filter the results
- `getDeclaredMethod` - can throw a `NoSuchMethodException`
- `getDeclaredMethods`- can filter the results
- `getEnclosingConstructor` - can throw a `NoSuchMethodError`
- `getEnclosingMethod` - can throw a `NoSuchMethodError`
- `getMethod` - can throw a `NoSuchMethodException`
- `getMethods`- can filter the results
- `getRecordComponents`- can filter the results

Methods which return `MethodHandle` instances are checked using the same strategy as for reflection. Custom deny actions are defined for the following `Lookup` methods, which can throw a `NoSuchMethodException`:

- `bind`
- `findConstructor`
- `findSpecial`
- `findStatic`
- `findVirtual`

Methods defined by `AccessibleObject` which enable access to class members are denied by the standard rules. Attempting to call `setAccessible` causes an `InaccessibleObjectException` to be thrown. Calling `trySetAccessible` does nothing, and instead the caller gets a result of `false`.

## Object methods

Methods declared in the root `Object` class cannot be denied, even when done so explicitly. This makes it easier to deny all methods in a class without breaking these fundamental operations. Even if these operations could be denied, the caller check would be bypassed when they're invoked by any of the classes in the `java.base` module.

