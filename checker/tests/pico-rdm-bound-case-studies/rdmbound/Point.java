package rdmbound;

import org.checkerframework.checker.mutability.qual.Mutable;
import org.checkerframework.checker.mutability.qual.Readonly;
import org.checkerframework.checker.mutability.qual.ReceiverDependentMutable;

/** A point that is mutable or immutable depending on the reference that creates it. */
@ReceiverDependentMutable public class Point {
    private int x;
    private int y;

    public Point(int x, int y) {
        this.x = x;
        this.y = y;
    }

    public int x(@Readonly Point this) {
        return x;
    }

    public int y(@Readonly Point this) {
        return y;
    }

    public void moveBy(@Mutable Point this, int dx, int dy) {
        x += dx;
        y += dy;
    }
}
