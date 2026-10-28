import org.checkerframework.checker.mutability.qual.Mutable;
import org.checkerframework.checker.mutability.qual.PolyMutable;
import org.checkerframework.checker.mutability.qual.Readonly;
import org.checkerframework.checker.mutability.qual.ReadonlyState;
import org.checkerframework.checker.mutability.qual.ReceiverDependentMutable;

// A @ReceiverDependentMutable value read through a @PolyMutable receiver adapts to
// @MutabilityLost in every mode (GAIT paper, Section 2.4). Propagating @PolyMutable would let the
// nested type argument of a returned container become a writable contract. In readonly-state and
// transitive-state, a declared @Mutable member read through @PolyMutable is lost too. A method
// receiver is adapted separately, so a @PolyMutable receiver may still call a
// @ReceiverDependentMutable method.
@ReceiverDependentMutable class PolyItem {}

@ReceiverDependentMutable class PolyBox<E extends @Readonly PolyItem> {}

@ReceiverDependentMutable class PolyHolder {
    @ReceiverDependentMutable PolyBox<@ReceiverDependentMutable PolyItem> box;
    @Mutable PolyItem mutableItem;

    PolyHolder(
            @ReceiverDependentMutable PolyBox<@ReceiverDependentMutable PolyItem> box,
            @Mutable PolyItem mutableItem) {
        this.box = box;
        this.mutableItem = mutableItem;
    }

    int size(@ReceiverDependentMutable PolyHolder this) {
        return 0;
    }

    @PolyMutable PolyBox<@PolyMutable PolyItem> expose(@PolyMutable PolyHolder this) {
        // :: error: (return.type.incompatible)
        return box;
    }

    @Readonly Object readThroughPoly(@PolyMutable PolyHolder this) {
        @Readonly Object lost = box;
        return lost;
    }

    int receiverKeepsPoly(@PolyMutable PolyHolder this) {
        return size();
    }

    @Mutable PolyItem abstractStateMutableMember(@PolyMutable PolyHolder this) {
        return mutableItem;
    }

    @ReadonlyState
    @Readonly Object readonlyStateMutableMember(@PolyMutable PolyHolder this) {
        // :: error: (assignment.type.incompatible)
        @Mutable PolyItem item = mutableItem;
        return item;
    }
}
