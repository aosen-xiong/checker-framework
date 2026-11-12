package rdmbound;

import org.checkerframework.checker.mutability.qual.Immutable;
import org.checkerframework.checker.mutability.qual.Mutable;

/** An org chart built mutably, then published as an immutable snapshot. */
public class Org {
    public static @Mutable TreeNode<@Mutable Employee> draft() {
        @Mutable TreeNode<@Mutable Employee> ceo =
                new @Mutable TreeNode<@Mutable Employee>(new @Mutable Employee("Ada", 100));
        ceo.addChild(new @Mutable TreeNode<@Mutable Employee>(new @Mutable Employee("Grace", 90)));
        ceo.mutableChild(0).value().raise(5);
        return ceo;
    }

    public static @Immutable TreeNode<@Immutable Employee> published() {
        return new @Immutable TreeNode<@Immutable Employee>(new @Immutable Employee("Ada", 100));
    }

    /** The payroll of a published chart. A draft is published before it is totalled. */
    public static int payroll(@Immutable TreeNode<@Immutable Employee> root) {
        int total = root.value().salary();
        for (int i = 0; i < root.childCount(); i++) {
            total += payroll(root.immutableChild(i));
        }
        return total;
    }
}
