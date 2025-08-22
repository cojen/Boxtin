
/**
 * Boxtin is a Java security manager agent.
 */
module org.cojen.boxtin {
    exports org.cojen.boxtin;

    requires java.instrument;

    requires static java.logging;

    // For testing only.
    requires static java.desktop;
}
