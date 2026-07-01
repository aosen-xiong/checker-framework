package checker_reconcile.repair;

import java.util.ArrayList;
import java.util.List;

import checker_reconcile.constraints.TraceModel.DiagnosticSlice;
import checker_reconcile.trace.TraceEvent;

/** Rule-based V0 repair sketches. */
public final class Sketches {
    public List<RepairSketch> forSlice(DiagnosticSlice slice) {
        List<RepairSketch> result = new ArrayList<>();
        for (TraceEvent assumption : slice.assumptions().values()) {
            String slot = assumption.stringField("slot");
            String type = assumption.stringField("type");
            boolean editable = Boolean.parseBoolean(assumption.stringField("editable"));
            if (!editable) {
                continue;
            }
            if (slot.startsWith("local:") && type.contains("@NonNull")) {
                result.add(
                        new RepairSketch(
                                "change_local_annotation",
                                assumption.id,
                                true,
                                "Change local annotation from @NonNull to @Nullable."));
            } else if (slot.startsWith("field:") && type.contains("@NonNull")) {
                result.add(
                        new RepairSketch(
                                "change_field_annotation",
                                assumption.id,
                                true,
                                "Change field annotation from @NonNull to @Nullable; review API risk."));
            } else if (slot.equals("parameter") && type.contains("@NonNull")) {
                result.add(
                        new RepairSketch(
                                "change_parameter_annotation",
                                assumption.id,
                                false,
                                "Parameter weakening can affect callers; report risk before patching."));
            } else if (slot.equals("return") && type.contains("@NonNull")) {
                result.add(
                        new RepairSketch(
                                "change_return_annotation",
                                assumption.id,
                                false,
                                "Return weakening can affect callers; report risk before patching."));
            }
        }
        result.add(
                new RepairSketch(
                        "add_null_check", slice.obligation().id, false, "Sketch only in V0."));
        result.add(
                new RepairSketch(
                        "introduce_suppression",
                        slice.diagnostic().id,
                        false,
                        "Suppression is rejected by default."));
        return result;
    }
}
