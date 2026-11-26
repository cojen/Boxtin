Changelog
=========

v1.0.3 (2026-11-26)
------
* Fixed a failure to transform a hidden class from halting the JVM.
* Fixed the "value" deny action for methods which return an array of primitives, which caused a
  VerifyError.
* Fixed proxies for inherited static methods, which copied the native flag, which were
  duplicated when a static method is redeclared by a super class, and which used the wrong kind
  of method reference when the class is an interface.
* Fixed SecurityAgent initialization when the agent is loaded by the boot loader but isn't
  activated.
* Deny access to InetAddress classes, NetworkInterface, and ProxySelector.
* Support Java 28.

v1.0.2 (2026-09-15)
------
* Support Java 27.

v1.0.1 (2025-12-08)
------
* Support Java 26.

v1.0.0 (2025-09-01)
------
* First released version.
