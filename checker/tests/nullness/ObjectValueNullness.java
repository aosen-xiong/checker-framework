// Object and enum contracts, including null comparison and nonnull normal results.

import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.dataflow.qual.Pure;
import org.checkerframework.dataflow.qual.SideEffectFree;
import org.checkerframework.framework.qual.AnnotatedFor;

@AnnotatedFor("nullness")
class ObjectValueNullness {
    enum Choice {
        FIRST,
        SECOND
    }

    int equality(Object receiver, @Nullable Object other) {
        receiver.equals(null);
        if (receiver.equals(other)) {
            return other.toString().length();
        }
        return receiver.getClass().getName().length() + receiver.toString().length();
    }

    int values(Choice value, Number number) {
        value.equals(null);
        Choice selected = Enum.valueOf(Choice.class, "FIRST");
        Class<Choice> declaring = value.getDeclaringClass();
        Class<Void> noValue = Void.TYPE;
        return selected.name().length()
                + declaring.getName().length()
                + noValue.getName().length()
                + value.toString().length()
                + number.byteValue()
                + number.shortValue()
                + number.intValue();
    }

    void invalid(Choice value) {
        // :: error: (argument.type.incompatible)
        Enum.valueOf(Choice.class, null);
        // :: error: (argument.type.incompatible)
        Enum.<Choice>valueOf(null, "FIRST");
        // :: error: (argument.type.incompatible)
        value.compareTo(null);
    }

    static class InvalidObject {
        @Override
        @Pure
        // :: error: (override.param.invalid)
        public boolean equals(Object other) {
            return this == other;
        }

        @Override
        @SideEffectFree
        // :: error: (override.return.invalid)
        public @Nullable String toString() {
            return null;
        }
    }

    static class Copyable implements Cloneable {
        @SideEffectFree
        Object copy() throws CloneNotSupportedException {
            return super.clone();
        }
    }
}
