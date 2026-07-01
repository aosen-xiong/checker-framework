package checker_reconcile.repair;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import checker_reconcile.constraints.TraceModel.DiagnosticSlice;

/** Compatibility facade from legacy repair sketches to typed repair plans and source edits. */
public final class Patcher {
    public void writePatched(Path source, Path out, List<RepairSketch> sketches)
            throws IOException {
        Files.copy(source, out, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    }

    public void writePatched(
            Path source, Path out, DiagnosticSlice slice, List<RepairSketch> sketches)
            throws IOException {
        writePatched(source, out, slice);
    }

    public void writePatched(Path source, Path out, DiagnosticSlice slice) throws IOException {
        writePlanned(source, out, new RepairPlanner().plan(source, slice));
    }

    public void writePlanned(Path source, Path out, List<SuggestedRepair> repairs)
            throws IOException {
        List<SourceEdit> edits = new ArrayList<>();
        for (SuggestedRepair repair : repairs) {
            if (repair.automatic() && !repair.edits().isEmpty()) {
                edits.addAll(repair.edits());
            }
        }
        if (edits.isEmpty()) {
            Files.copy(source, out, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } else {
            new PatchApplier().writePatched(source, out, edits);
        }
    }
}
