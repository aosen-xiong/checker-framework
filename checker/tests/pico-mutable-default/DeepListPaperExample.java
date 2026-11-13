import org.checkerframework.checker.mutability.qual.Immutable;
import org.checkerframework.checker.mutability.qual.Mutable;
import org.checkerframework.checker.mutability.qual.Readonly;
import org.checkerframework.checker.mutability.qual.ReceiverDependentMutable;

// The example of GAIT paper Section 2.4. DeepList couples its element to the list, so an
// @Immutable DeepList holds an @Immutable Data. increment views @Immutable X as
// @Immutable DecoupledList<@MutabilityLost Data> (ST-Qual-Class), so the write is rejected.
@ReceiverDependentMutable class PaperData {
    int value;
}

@ReceiverDependentMutable class PaperDecoupledList<E extends @Readonly PaperData> {
    E elem;

    PaperDecoupledList(E elem) {
        this.elem = elem;
    }
}

@ReceiverDependentMutable class PaperDeepList extends PaperDecoupledList<@ReceiverDependentMutable PaperData> {
    PaperDeepList(@ReceiverDependentMutable PaperData elem) {
        super(elem);
    }
}

class DeepListPaperExample {
    static <X extends @Mutable PaperDecoupledList<@Mutable PaperData>> void increment(
            @Immutable X list) {
        // :: error: (illegal.field.write)
        list.elem.value++;
    }

    static <X extends @Readonly PaperDecoupledList<@Mutable PaperData>> void incrementReadonlyBound(
            @Immutable X list) {
        // :: error: (illegal.field.write)
        list.elem.value++;
        // :: error: (assignment.type.incompatible)
        @Mutable PaperData m = list.elem;
    }

    static void caller() {
        @Immutable PaperDeepList list = new @Immutable PaperDeepList(new @Immutable PaperData());
        DeepListPaperExample.<@Mutable PaperDeepList>increment(list);
    }
}
