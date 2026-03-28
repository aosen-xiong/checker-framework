import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.util.Objects;
import java.util.function.Function;

public interface ElementDescriptor<T> {

    public static <T> ElementDescriptor<T> of(
            MemoryLayout layout,
            Function<MemorySegment, T> toElement,
            Function<T, MemorySegment> toAddress) {
        Objects.requireNonNull(layout);
        Objects.requireNonNull(toElement);
        Objects.requireNonNull(toAddress);
        return new ElementDescriptor<>() {
            @Override
            public MemoryLayout layout() {
                return layout;
            }

            @Override
            public T elementFrom(MemorySegment segment) {
                if (segment.equals(MemorySegment.NULL)) {
                    return null;
                }
                return toElement.apply(segment);
            }

            @Override
            public MemorySegment addressOf(T element) {
                if (element == null) {
                    return MemorySegment.NULL;
                }
                return toAddress.apply(element);
            }
        };
    }

    MemoryLayout layout();

    T elementFrom(MemorySegment segment);

    MemorySegment addressOf(T element);
}