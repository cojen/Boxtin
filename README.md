
Boxtin is a Java security manager agent, intended to replace the original security manager, which is now [disabled](https://openjdk.org/jeps/486).

Status: Project is experimental, and it doesn't fully work yet.

Boxtin provides an [instrumentation agent](https://docs.oracle.com/en/java/javase/24/docs/api/java.instrument/java/lang/instrument/package-summary.html) which modifies classes to include the necessary security checks. It's launched with a custom [controller](https://github.com/cojen/Boxtin/blob/main/src/main/java/org/cojen/boxtin/Controller.java), which decides what operations are allowed for a given module.

```
java -javaagent:Boxtin.jar=my.app.SecurityController ...
```

The design is intended for restricting operations for "plugins", much like the original security manager was intended for restricting applet permissions. There are a few key differences, however:

- Boxtin only checks the immediate caller in a stack trace, whereas the original security manager checked all frames within the trace. The original design seemed correct at the time, but in practice it wasn't a good idea. The [`AccessController`](https://docs.oracle.com/en/java/javase/24/docs/api/java.base/java/security/AccessController.html) was intended for handling special cases, but it was actually used over 1200 times in the JDK. What was expected to be an exceptional case ended up being the normal case.
- Rules are defined entirely by the host environment, and so the libraries it depends on aren't expected to require any modifications.
- The rules strictly allow or deny access to a constructor or method, and they cannot perform any special "filtering" operations. If a plugin wishes to open a file, but the operation is generally denied, then the plugin must ask the host environment to open the file on its behalf. The host environment is responsible for performing the necessary path filtering checks.

A _caller_ is the plugin code, represented by a [module](https://docs.oracle.com/en/java/javase/24/docs/api/java.base/java/lang/Module.html), possibly unnamed. A _target_ is the code which is being called by the caller, represented by a rule. A rule logically maps target methods or constructors to an "allow" or "deny" outcome.

The controller decides which set of rules apply for a given module. For convenience, a [`RulesApplier`](https://github.com/cojen/Boxtin/blob/main/src/main/java/org/cojen/boxtin/RulesApplier.java) can define a standard set of rules, by name or category: [java.base](https://github.com/cojen/Boxtin/blob/main/src/main/java/org/cojen/boxtin/JavaBaseApplier.java)

Rules cannot allow access to operations beyond the boundaries already established by modules. For example, a rule cannot be defined to allow access to the internal JDK classes. The existing Java `--add-exports` and `--add-opens` options must be used instead.

Rules cannot deny access to operations within a module. A caller is always allowed to call any target operation in its own module, restricted only by the usual private/protected checks. Any rule which would deny access within a module is ignored.

## Transforms

For a given rule, Boxtin transforms a caller class or a target class. In general, target class transformation is preferred. Here's an example transform applied to a target constructor:

```java
    public FileInputStream(String Path) {
        SecurityAgent.check(SecurityAgent.WALKER.getCallerClass(), FileInputStream.class, null, "(Ljava/lang/String;)V");
        ...
    }
```

The `check` method will call into the controller for determining if access is allowed or denied, caching the result. If denied, a `SecurityException` is thrown.

Here's an example transform applied to a caller method:

```java
    // original
    public void fail() {
        System.exit(1);
    }

    // transformed
    public void fail() {
        $3(1);
    }

    // synthetic method
    private static void $3(int param) {
        if (Caller.class.getModule() != System.class.getModule()) {
            throw new SecurityException();
        }
        System.exit(param);
    }
```

Although it might seem silly to include a module check which will obviously yield false, it greatly simplifies the code transformation because the exact module isn't known until after the class has been loaded. In practice, the module checking code will be optimized into oblivion by the JVM.

Although the caller transform is much more efficient, it doesn't support checks against inherited methods. Target transforms are preferred except in cases where they won't work (cyclic dependencies in the JDK) or where it's known that the rule doesn't apply to a method which is inherited or can be inherited. Care must be taken to not assume that a private constructor prevents inheritance. The JVM permits sub classing without a constructor, allowing access to inherited static methods.

- Native methods: TBD
- MethodHandle constants: TBD

