package rdmbound;

import org.checkerframework.checker.mutability.qual.Assignable;
import org.checkerframework.checker.mutability.qual.Mutable;
import org.checkerframework.checker.mutability.qual.Readonly;
import org.checkerframework.checker.mutability.qual.ReceiverDependentMutable;

/**
 * A growable list whose elements belong to it. Its bound couples the elements to the list: a
 * {@code @Mutable ValueList<@Mutable E>} is an ordinary list of mutable elements, and an
 * {@code @Immutable ValueList<@Immutable E>} is deeply immutable, so it can be shared freely. A
 * mutable list cannot be viewed as an immutable one.
 */
@ReceiverDependentMutable public class ValueList<E extends @ReceiverDependentMutable Object> {
    private @Assignable @Readonly Object @ReceiverDependentMutable [] elements;
    private @Assignable int size;

    public ValueList() {
        this.elements = new @Readonly Object @ReceiverDependentMutable [4];
        this.size = 0;
    }

    /** Creates a list holding {@code first}, as an immutable list when the caller asks for one. */
    public ValueList(E first) {
        this.elements = new @Readonly Object @ReceiverDependentMutable [] {first};
        this.size = 1;
    }

    public int size(@Readonly ValueList<E> this) {
        return size;
    }

    @SuppressWarnings({
        "unchecked",
        "mutability:cast.unsafe"
    }) // the backing array holds only E, as in java.util.ArrayList
    public E get(@Readonly ValueList<E> this, int index) {
        if (index < 0 || index >= size) {
            throw new IndexOutOfBoundsException("index " + index + ", size " + size);
        }
        return (E) elements[index];
    }

    public void add(@Mutable ValueList<E> this, E element) {
        if (size == elements.length) {
            @Readonly Object @Mutable [] grown = new @Readonly Object @Mutable [size * 2];
            System.arraycopy(elements, 0, grown, 0, size);
            elements = grown;
        }
        elements[size++] = element;
    }

    public void set(@Mutable ValueList<E> this, int index, E element) {
        if (index < 0 || index >= size) {
            throw new IndexOutOfBoundsException("index " + index + ", size " + size);
        }
        elements[index] = element;
    }

    public boolean contains(@Readonly ValueList<E> this, @Readonly Object o) {
        for (int i = 0; i < size; i++) {
            if (elements[i].equals(o)) {
                return true;
            }
        }
        return false;
    }
}
