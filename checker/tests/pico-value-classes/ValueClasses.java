import org.checkerframework.checker.mutability.qual.Assignable;
import org.checkerframework.checker.mutability.qual.Derived;
import org.checkerframework.checker.mutability.qual.Immutable;
import org.checkerframework.checker.mutability.qual.Mutable;

// Only abstract state.
// :: warning: (value.class)
@Immutable class Point {
    final int x;
    final int y;

    Point(int x, int y) {
        this.x = x;
        this.y = y;
    }
}

// Abstract state and a derived hash, as in java.lang.String.
// :: warning: (value.class)
@Immutable class Hashed {
    final int a;
    private @Assignable @Derived int hash;

    Hashed(int a) {
        this.a = a;
    }

    int hash() {
        int h = hash;
        if (h == 0) {
            int r = 31 * a + 7;
            hash = h = r;
        }
        return h;
    }
}

// The derived field is read twice.
// :: warning: (value.class.not)
@Immutable class DoubleRead {
    final int a;
    private @Assignable @Derived int hash;

    DoubleRead(int a) {
        this.a = a;
    }

    int hash() {
        if (hash == 0) {
            hash = 31 * a + 7;
        }
        // :: error: (derived.field.multiple.reads)
        // :: error: (derived.field.result.unchecked)
        return hash;
    }
}

// An assignable field that is not derived is extra mutable state.
// :: warning: (value.class.not)
@Immutable class Counter {
    final int a;
    @Assignable int hits;

    Counter(int a) {
        this.a = a;
    }
}

// A field that refers to mutable state is not abstract state.
// :: warning: (value.class.not)
@Immutable class Holder {
    final @Mutable Object box;

    Holder(@Mutable Object box) {
        this.box = box;
    }
}

// The elements of an array are reachable state.
// :: warning: (value.class.not)
@Immutable class ArrayOfMutable {
    final @Mutable Object @Immutable [] items;

    ArrayOfMutable(@Mutable Object @Immutable [] items) {
        this.items = items;
    }
}

// :: warning: (value.class)
@Immutable class ArrayOfImmutable {
    final @Immutable Object @Immutable [] items;

    ArrayOfImmutable(@Immutable Object @Immutable [] items) {
        this.items = items;
    }
}

// So are the objects named by a type argument.
// :: warning: (value.class.not)
@Immutable class ListOfMutable {
    final java.util.@Immutable List<@Mutable Object> items;

    ListOfMutable(java.util.@Immutable List<@Mutable Object> items) {
        this.items = items;
    }
}

// A class that is not immutable is not reported.
class Plain {
    int x;
}
