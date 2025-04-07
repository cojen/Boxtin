
Boxtin is a Java security manager agent.

Status: Project is experimental, and it doesn't actually work yet.

A major snag I've run into is a peculiar behavior of the new JDK classfile API. In order to build a stack map table, it needs to resolve the class hierarchy for some reason. In order to do this, it ends up loading classes and examines them. When implementing an instrumentation agent, loading additional classes is generally not recommended. It can lead to linkage errors of the form, "loader 'app' attempted duplicate class definition for...".  I'm observing this problem when using the classfile API, because it sometimes loads the class which is currently being transformed.

I need to figure why the classfile API is doing this, possibly disable this "feature", or use a different library instead.
