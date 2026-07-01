package checker_reconcile.corpus;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Parses lightweight Checker Framework test-file directives needed by corpus runs. */
public final class CfTestMetadata {
    private CfTestMetadata() {}

    public static boolean isJavaTestFile(Path file) throws IOException {
        return Files.isRegularFile(file)
                && file.getFileName().toString().endsWith(".java")
                && skipReason(file).isEmpty();
    }

    public static String skipReason(Path file) throws IOException {
        int javaVersion = javaVersion();
        for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
            if (!line.contains("skip-test")) {
                continue;
            }
            if (line.contains("@skip-test")) {
                return "@skip-test";
            }
            String versionSkip = versionSkipReason(line, javaVersion);
            if (!versionSkip.isEmpty()) {
                return versionSkip;
            }
        }
        return "";
    }

    private static String versionSkipReason(String line, int javaVersion) {
        int[] versions = {9, 10, 11, 14, 16, 17, 18, 21, 22};
        for (int version : versions) {
            String below = "@below-java" + version + "-jdk-skip-test";
            if (line.contains(below) && javaVersion < version) {
                return below;
            }
            String above = "@above-java" + version + "-jdk-skip-test";
            if (line.contains(above) && javaVersion > version) {
                return above;
            }
        }
        return "";
    }

    private static int javaVersion() {
        String version = System.getProperty("java.specification.version", "8");
        if (version.startsWith("1.")) {
            version = version.substring(2);
        }
        int dot = version.indexOf('.');
        if (dot >= 0) {
            version = version.substring(0, dot);
        }
        return Integer.parseInt(version);
    }
}
