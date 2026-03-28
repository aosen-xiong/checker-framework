package org.checkerframework.checker.test.junit;

import org.checkerframework.framework.test.CheckerFrameworkPerDirectoryTest;
import org.junit.runners.Parameterized.Parameters;

import java.io.File;
import java.util.List;

/** JUnit tests for the region checker. */
public class RegionTest extends CheckerFrameworkPerDirectoryTest {

    /**
     * Create tests for arena checker.
     *
     * @param testFiles the files containing test code, which will be type-checked
     */
    public RegionTest(List<File> testFiles) {
        super(testFiles, org.checkerframework.checker.region.UniquityChecker.class, "region");
    }

    @Parameters
    public static String[] getTestDirs() {
        return new String[] {"region"};
    }
}
