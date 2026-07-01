package checker_reconcile.constraints;

import java.util.ArrayList;
import java.util.List;

/** Nullness qualifier compatibility for V0 diagnosis. */
public final class Nullness {
    private Nullness() {}

    public static String qualifierOf(String annotatedType) {
        if (annotatedType.contains("@Nullable")) {
            return "@Nullable";
        }
        if (annotatedType.contains("@MonotonicNonNull")) {
            return "@MonotonicNonNull";
        }
        if (annotatedType.contains("@PolyNull")) {
            return "@PolyNull";
        }
        return "@NonNull";
    }

    public static boolean isSubtype(String gotType, String wantType) {
        String got = qualifierOf(gotType);
        String want = qualifierOf(wantType);
        if (got.equals(want)) {
            return true;
        }
        return (got.equals("@NonNull") || got.equals("@MonotonicNonNull"))
                && want.equals("@Nullable");
    }

    public static boolean isWeakening(String fromQualifier, String toQualifier) {
        return !fromQualifier.equals(toQualifier) && isSubtype(fromQualifier, toQualifier);
    }

    public static List<String> qualifiersOf(String annotatedType) {
        List<QualifierOccurrence> occurrences = new ArrayList<>();
        addOccurrences(occurrences, annotatedType, "@Nullable");
        addOccurrences(occurrences, annotatedType, "@MonotonicNonNull");
        addOccurrences(occurrences, annotatedType, "@PolyNull");
        addOccurrences(occurrences, annotatedType, "@NonNull");
        occurrences.sort((left, right) -> Integer.compare(left.offset, right.offset));
        List<String> result = new ArrayList<>();
        for (QualifierOccurrence occurrence : occurrences) {
            result.add(occurrence.qualifier);
        }
        return result;
    }

    public static boolean receiverNonNull(String receiverType) {
        return qualifierOf(receiverType).equals("@NonNull");
    }

    private static void addOccurrences(
            List<QualifierOccurrence> occurrences, String text, String qualifier) {
        int offset = text.indexOf(qualifier);
        while (offset >= 0) {
            occurrences.add(new QualifierOccurrence(offset, qualifier));
            offset = text.indexOf(qualifier, offset + qualifier.length());
        }
    }

    private static final class QualifierOccurrence {
        private final int offset;
        private final String qualifier;

        private QualifierOccurrence(int offset, String qualifier) {
            this.offset = offset;
            this.qualifier = qualifier;
        }
    }
}
