package org.checkerframework.checker.test.junit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import checker_reconcile.corpus.CorpusAttempt;
import checker_reconcile.corpus.CorpusReportJson;
import checker_reconcile.corpus.CorpusRunner;
import checker_reconcile.corpus.CorpusSummary;
import checker_reconcile.repair.RepairKind;
import checker_reconcile.repair.RiskLevel;
import checker_reconcile.trace.TraceEvent;

/** End-to-end corpus-report smoke tests using the real Nullness Checker. */
public class NullnessCorpusReportTest {
    @Test
    public void syntheticCorpusReportsDecreaseAndUnsupportedAttempts() throws Exception {
        Path root = Files.createTempDirectory("nullness-corpus-root");
        Path accepted = root.resolve("Accepted.java");
        Path fullPass = root.resolve("FullPass.java");
        Path unsupported = root.resolve("Unsupported.java");
        Files.write(
                accepted,
                List.of(
                        "import org.checkerframework.checker.nullness.qual.NonNull;",
                        "import org.checkerframework.checker.nullness.qual.Nullable;",
                        "class Accepted {",
                        "  void f(@Nullable String s) {",
                        "    // :: error: (assignment.type.incompatible)",
                        "    @NonNull String first = s;",
                        "    // :: error: (assignment.type.incompatible)",
                        "    @NonNull String second = s;",
                        "  }",
                        "}"),
                StandardCharsets.UTF_8);
        Files.write(
                fullPass,
                List.of(
                        "import org.checkerframework.checker.nullness.qual.NonNull;",
                        "import org.checkerframework.checker.nullness.qual.Nullable;",
                        "class FullPass {",
                        "  void f(@Nullable String s) {",
                        "    // :: error: (assignment.type.incompatible)",
                        "    @NonNull String value = s;",
                        "  }",
                        "}"),
                StandardCharsets.UTF_8);
        Files.write(
                unsupported,
                List.of(
                        "import org.checkerframework.checker.nullness.qual.Nullable;",
                        "class Unsupported {",
                        "  void f(@Nullable String s) {",
                        "    // :: error: (dereference.of.nullable)",
                        "    s.length();",
                        "  }",
                        "}"),
                StandardCharsets.UTF_8);
        Set<RiskLevel> risks = new LinkedHashSet<>();
        risks.add(RiskLevel.LOCAL_ONLY);
        risks.add(RiskLevel.API_CHANGE);
        Path workDir = Files.createTempDirectory("nullness-corpus-work");

        List<CorpusAttempt> attempts =
                new CorpusRunner()
                        .run(
                                new CorpusRunner.Options(
                                        root,
                                        workDir,
                                        javac(),
                                        checkerAllJar(),
                                        "decrease",
                                        risks,
                                        1,
                                        100,
                                        4,
                                        false));
        CorpusSummary summary = new CorpusSummary(attempts);
        CorpusReportJson json = new CorpusReportJson();
        Path report = Files.createTempFile("nullness-corpus-report", ".jsonl");
        Files.write(
                report,
                List.of(
                        json.attempt(attempts.get(0)),
                        json.attempt(attempts.get(1)),
                        json.attempt(attempts.get(2)),
                        json.attempt(attempts.get(3)),
                        json.summary(summary)),
                StandardCharsets.UTF_8);
        List<TraceEvent> events = json.parse(report);

        assertEquals(4, attempts.size());
        assertTrue(summary.accepted() >= 1);
        assertTrue(summary.decreased() >= 1);
        assertTrue(summary.fullPass() >= 1);
        assertFalse(summary.byFailureReason().isEmpty());
        assertEquals("corpus_summary", events.get(events.size() - 1).stringField("event"));
        assertTrue(Files.readString(accepted).contains("@NonNull String first = s;"));
        assertTrue(Files.readString(fullPass).contains("@NonNull String value = s;"));
        assertTrue(Files.readString(unsupported).contains("s.length();"));

        Set<RiskLevel> bodyRisks = new LinkedHashSet<>();
        bodyRisks.add(RiskLevel.BODY_CHANGE);
        List<CorpusAttempt> bodyAttempts =
                new CorpusRunner()
                        .run(
                                new CorpusRunner.Options(
                                        root,
                                        Files.createTempDirectory("nullness-corpus-body-work"),
                                        javac(),
                                        checkerAllJar(),
                                        "decrease",
                                        bodyRisks,
                                        1,
                                        100,
                                        4,
                                        true));
        CorpusSummary bodySummary = new CorpusSummary(bodyAttempts);

        assertTrue(bodySummary.accepted() >= 1);
        assertTrue(
                bodyAttempts.stream()
                        .anyMatch(
                                attempt ->
                                        attempt.accepted()
                                                && attempt.acceptedRepairKind()
                                                        == RepairKind.ADD_NULL_CHECK));
    }

    private String javac() {
        return Paths.get(System.getProperty("java.home"), "bin", "javac").toString();
    }

    private Path checkerAllJar() throws Exception {
        Path libs = checkerLibs();
        try (Stream<Path> jars = Files.list(libs)) {
            return jars.filter(path -> path.getFileName().toString().endsWith("-all.jar"))
                    .max(Comparator.comparing(path -> path.getFileName().toString()))
                    .orElseThrow(
                            () ->
                                    new AssertionError(
                                            "missing Checker Framework all jar under " + libs));
        }
    }

    private Path checkerLibs() {
        Path moduleLibs = Paths.get("build", "libs");
        if (Files.isDirectory(moduleLibs)) {
            return moduleLibs;
        }
        Path repoLibs = Paths.get("checker", "build", "libs");
        if (Files.isDirectory(repoLibs)) {
            return repoLibs;
        }
        throw new AssertionError("missing Checker Framework all jar directory");
    }
}
