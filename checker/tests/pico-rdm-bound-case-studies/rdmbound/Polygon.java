package rdmbound;

import org.checkerframework.checker.mutability.qual.Immutable;
import org.checkerframework.checker.mutability.qual.Mutable;
import org.checkerframework.checker.mutability.qual.Readonly;

/** Clients of ValueList: an editable polygon, and a frozen shape that can be shared. */
public class Polygon {
    /** The vertices of an editable polygon, which the editor moves in place. */
    private final @Mutable ValueList<@Mutable Point> editable =
            new @Mutable ValueList<@Mutable Point>();

    public void addVertex(@Mutable Polygon this, int x, int y) {
        editable.add(new @Mutable Point(x, y));
    }

    public void translate(@Mutable Polygon this, int dx, int dy) {
        for (int i = 0; i < editable.size(); i++) {
            editable.get(i).moveBy(dx, dy);
        }
    }

    public int perimeter(@Readonly Polygon this) {
        int total = 0;
        for (int i = 1; i < editable.size(); i++) {
            @Readonly Point a = editable.get(i - 1);
            @Readonly Point b = editable.get(i);
            total += Math.abs(a.x() - b.x()) + Math.abs(a.y() - b.y());
        }
        return total;
    }

    /** A frozen corner. Its point is immutable because the list is. */
    public static @Immutable ValueList<@Immutable Point> corner() {
        return new @Immutable ValueList<@Immutable Point>(new @Immutable Point(0, 0));
    }

    /** The x coordinate of the frozen corner, read through the immutable list. */
    public static int cornerX() {
        @Immutable Point p = corner().get(0);
        return p.x();
    }
}
