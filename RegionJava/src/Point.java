import static java.lang.foreign.ValueLayout.JAVA_INT;

import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemoryLayout.PathElement;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SegmentAllocator;
import java.lang.foreign.StructLayout;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.Objects;

public final class Point {

    public static final StructLayout LAYOUT;
    public static final ElementDescriptor<Point> DESCRIPTOR;

    private static final VarHandle X;
    private static final VarHandle Y;

    static {
        LAYOUT = MemoryLayout.structLayout(JAVA_INT.withName("x"), JAVA_INT.withName("y"));

        var x = LAYOUT.varHandle(PathElement.groupElement("x"));
        X = MethodHandles.insertCoordinates(x, 1, 0L);

        var y = LAYOUT.varHandle(PathElement.groupElement("y"));
        Y = MethodHandles.insertCoordinates(y, 1, 0L);

        DESCRIPTOR = ElementDescriptor.of(LAYOUT, Point::new, Point::address);
    }

    private final MemorySegment segment;

    public Point(SegmentAllocator allocator) {
        segment = allocator.allocate(LAYOUT);
    }

    public Point(MemorySegment segment) {
        this.segment = Objects.requireNonNull(segment);
    }

    public MemorySegment address() {
        return MemorySegment.ofAddress(segment.address());
    }

    public int getX() {
        return (int) X.get(segment);
    }

    public void setX(int x) {
        X.set(segment, x);
    }

    public int getY() {
        return (int) Y.get(segment);
    }

    public void setY(int y) {
        Y.set(segment, y);
    }

    public void swap() {
        var temp = getX();
        X.set(segment, getY());
        Y.set(segment, temp);
    }

    @Override
    public String toString() {
        return "Point(x=" + getX() + ", y=" + getY() + ")";
    }
}