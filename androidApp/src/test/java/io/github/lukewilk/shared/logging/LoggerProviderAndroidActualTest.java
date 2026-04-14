package io.github.lukewilk.shared.logging;

import static org.junit.Assert.assertNull;

import org.junit.Test;

/**
 * Host-side Android unit test for the shared Android logger configuration actual.
 */
public class LoggerProviderAndroidActualTest {
    @Test
    public void androidActualLoggerConfigLookupReturnsNull() throws Exception {
        // Documents that the Android target does not read a desktop-style config file for log severity.
        assertNull(readLogLevelReflectively());
    }

    /** Resolves the generated top-level Kotlin logger function class for the Android variant at runtime. */
    private static Object readLogLevelReflectively() throws Exception {
        String[] classNames = {
            "io.github.lukewilk.shared.logging.LoggerProviderKt",
            "io.github.lukewilk.shared.logging.LoggerProvider_androidKt"
        };

        for (String className : classNames) {
            try {
                Class<?> klass = Class.forName(className);
                return klass.getMethod("readLogLevelFromConfig").invoke(null);
            } catch (ClassNotFoundException | NoSuchMethodException ignored) {
                // Try the next generated class name variant.
            }
        }

        throw new ClassNotFoundException("Could not find the generated Android logger config class");
    }
}




