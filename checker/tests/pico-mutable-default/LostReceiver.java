import org.checkerframework.checker.mutability.qual.Mutable;
import org.checkerframework.checker.mutability.qual.Readonly;
import org.checkerframework.checker.mutability.qual.ReceiverDependentMutable;

// A @ReceiverDependentMutable method receiver adapts uniformly, including through
// @MutabilityLost: @MutabilityLost |> @ReceiverDependentMutable = @MutabilityLost. The call is
// then rejected because the adapted receiver type contains @MutabilityLost, the same rule that
// rejects a parameter whose adapted type contains it.
@ReceiverDependentMutable class LostReceiverCell {
    int get(@ReceiverDependentMutable LostReceiverCell this) {
        return 0;
    }
}

@ReceiverDependentMutable class LostReceiverHolder {
    @ReceiverDependentMutable LostReceiverCell cell;

    LostReceiverHolder(@ReceiverDependentMutable LostReceiverCell cell) {
        this.cell = cell;
    }
}

@Mutable class LostReceiver {
    int throughMutable(@Mutable LostReceiverHolder h) {
        return h.cell.get();
    }

    int throughReadonly(@Readonly LostReceiverHolder h) {
        // h.cell is @MutabilityLost, so get()'s receiver adapts to @MutabilityLost.
        // :: error: (mutability.lost.receiver)
        return h.cell.get();
    }

    static int readonlyReceiver(@Readonly LostReceiverCell c) {
        // @Readonly |> @ReceiverDependentMutable = @Readonly for a receiver: allowed.
        return c.get();
    }
}
