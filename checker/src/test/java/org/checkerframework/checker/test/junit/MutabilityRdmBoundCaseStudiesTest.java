package org.checkerframework.checker.test.junit;

import org.checkerframework.framework.test.CheckerFrameworkPerDirectoryTest;
import org.junit.runners.Parameterized;

import java.io.File;
import java.util.List;

/**
 * Small case studies of classes whose type parameters have a {@code @ReceiverDependentMutable}
 * bound, which couples the elements to the container. They must type-check with no error.
 */
public class MutabilityRdmBoundCaseStudiesTest extends CheckerFrameworkPerDirectoryTest {
    /**
     * Create a MutabilityRdmBoundCaseStudiesTest.
     *
     * @param testFiles the files containing test code, which will be type-checked
     */
    public MutabilityRdmBoundCaseStudiesTest(List<File> testFiles) {
        super(
                testFiles,
                org.checkerframework.checker.mutability.MutabilityChecker.class,
                "pico-rdm-bound-case-studies");
    }

    /**
     * Returns the test files. They are in the "tests/pico-rdm-bound-case-studies" directory.
     *
     * @return the test directories
     */
    @Parameterized.Parameters
    public static String[] getTestDirs() {
        return new String[] {"pico-rdm-bound-case-studies"};
    }
}
