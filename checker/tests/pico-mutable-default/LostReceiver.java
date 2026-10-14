import org.checkerframework.checker.mutability.qual.Mutable;
import org.checkerframework.checker.mutability.qual.Readonly;
import org.checkerframework.checker.mutability.qual.ReceiverDependentMutable;

// A @ReceiverDependentMutable method receiver adapts uniformly, including through
// @MutabilityLost: @MutabilityLost |> @ReceiverDependentMutable = @MutabilityLost. As in Generic
// Universe Types, a receiver that contains @MutabilityLost does not block the call. Only the
// adapted parameter types and method type-variable bounds must be free of @MutabilityLost.
@ReceiverDependentMutable class LostReceiverCell {
    int get(@ReceiverDependentMutable LostReceiverCell this) {
        return 0;
    }

    void set(@ReceiverDependentMutable LostReceiverCell this, @ReceiverDependentMutable Object o) {}
}

@ReceiverDependentMutable class LostReceiverBox<E extends @Readonly Object> {
    int size(@Readonly LostReceiverBox<E> this) {
        return 0;
    }
}

@ReceiverDependentMutable class LostReceiverHolder {
    @ReceiverDependentMutable LostReceiverCell cell;
    @ReceiverDependentMutable LostReceiverBox<@ReceiverDependentMutable LostReceiverCell> box;

    LostReceiverHolder(
            @ReceiverDependentMutable LostReceiverCell cell,
            @ReceiverDependentMutable LostReceiverBox<@ReceiverDependentMutable LostReceiverCell> box) {
        this.cell = cell;
        this.box = box;
    }
}

@Mutable class LostReceiver {
    int throughMutable(@Mutable LostReceiverHolder h) {
        return h.cell.get();
    }

    int throughReadonly(@Readonly LostReceiverHolder h) {
        // h.cell is @MutabilityLost, so get()'s receiver adapts to @MutabilityLost. Allowed.
        return h.cell.get();
    }

    int typeArgumentThroughReadonly(@Readonly LostReceiverHolder h) {
        // h.box is @MutabilityLost LostReceiverBox<@MutabilityLost LostReceiverCell>. Allowed.
        return h.box.size();
    }

    void parameterThroughReadonly(@Readonly LostReceiverHolder h) {
        // set's parameter adapts to @MutabilityLost, so the call is rejected even with null.
        // :: error: (mutability.lost.parameter)
        h.cell.set(null);
    }

    static int readonlyReceiver(@Readonly LostReceiverCell c) {
        // @Readonly |> @ReceiverDependentMutable = @Readonly for a receiver: allowed.
        return c.get();
    }
}
