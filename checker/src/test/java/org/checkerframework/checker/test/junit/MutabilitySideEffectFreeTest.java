package org.checkerframework.checker.test.junit;

import org.checkerframework.framework.test.CheckerFrameworkPerDirectoryTest;
import org.junit.runners.Parameterized;

import java.io.File;
import java.util.List;

/** JUnit tests for the Mutability Checker with -AsideEffectFreeAsTransitiveState. */
public class MutabilitySideEffectFreeTest extends CheckerFrameworkPerDirectoryTest {
    /**
     * Create a MutabilitySideEffectFreeTest.
     *
     * @param testFiles the files containing test code, which will be type-checked
     */
    public MutabilitySideEffectFreeTest(List<File> testFiles) {
        super(
                testFiles,
                org.checkerframework.checker.mutability.MutabilityChecker.class,
                "pico-side-effect-free",
                "-AsideEffectFreeAsTransitiveState");
    }

    /**
     * Returns the test files. They are in the "tests/pico-side-effect-free" directory.
     *
     * @return the test directories
     */
    @Parameterized.Parameters
    public static String[] getTestDirs() {
        return new String[] {"pico-side-effect-free"};
    }
}
