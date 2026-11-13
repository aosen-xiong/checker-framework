package rdmbound;

import org.checkerframework.checker.mutability.qual.Immutable;
import org.checkerframework.checker.mutability.qual.Mutable;
import org.checkerframework.checker.mutability.qual.Readonly;
import org.checkerframework.checker.mutability.qual.ReceiverDependentMutable;

/**
 * A tree whose immutability extends to its nodes and their values. The children have the tree's
 * qualifier through the field type, and the values through the bound of V, so an immutable tree is
 * immutable all the way down.
 */
@ReceiverDependentMutable public class TreeNode<V extends @ReceiverDependentMutable Object> {
    private final V value;
    private final @ReceiverDependentMutable ValueList<@ReceiverDependentMutable TreeNode<V>>
            children;

    public TreeNode(V value) {
        this.value = value;
        this.children =
                new @ReceiverDependentMutable ValueList<@ReceiverDependentMutable TreeNode<V>>();
    }

    @SuppressWarnings("mutability:return.type.incompatible") // the checker defaults the field to
    // @ReceiverDependentMutable V, which reads as
    // @MutabilityLost V through @Readonly; the paper
    // accepts this getter
    public V value(@Readonly TreeNode<V> this) {
        return value;
    }

    @SuppressWarnings({
        "mutability:type.argument.type.incompatible",
        "mutability:argument.type.incompatible"
    }) // WF-Meth: inside a method whose receiver is @Mutable, the paper adapts the bound of V to
    // @Mutable, but the checker keeps @ReceiverDependentMutable
    public void addChild(@Mutable TreeNode<V> this, @Mutable TreeNode<V> child) {
        children.add(child);
    }

    public int childCount(@Readonly TreeNode<V> this) {
        return children.size();
    }

    /**
     * Returns a child for reading. A child of an immutable tree is immutable and one of a mutable
     * tree is mutable, but a @Readonly TreeNode is not well formed, so the reader gets the child as
     * a @Readonly Object of the class and casts it back on a concrete tree.
     */
    public @Readonly Object child(@Readonly TreeNode<V> this, int i) {
        return children.get(i);
    }

    @SuppressWarnings({
        "mutability:type.argument.type.incompatible",
        "mutability:return.type.incompatible"
    }) // WF-Meth: inside a method whose receiver is @Immutable, the paper adapts the bound of V to
    // @Immutable, but the checker keeps @ReceiverDependentMutable
    public @Immutable TreeNode<V> immutableChild(@Immutable TreeNode<V> this, int i) {
        return children.get(i);
    }

    @SuppressWarnings({
        "mutability:type.argument.type.incompatible",
        "mutability:return.type.incompatible"
    }) // WF-Meth: inside a method whose receiver is @Mutable, the paper adapts the bound of V to
    // @Mutable, but the checker keeps @ReceiverDependentMutable
    public @Mutable TreeNode<V> mutableChild(@Mutable TreeNode<V> this, int i) {
        return children.get(i);
    }

    public int size(@Readonly TreeNode<V> this) {
        int n = 1;
        for (int i = 0; i < children.size(); i++) {
            n += children.get(i).size();
        }
        return n;
    }

    public boolean containsValue(@Readonly TreeNode<V> this, @Readonly Object v) {
        if (value.equals(v)) {
            return true;
        }
        for (int i = 0; i < children.size(); i++) {
            if (children.get(i).containsValue(v)) {
                return true;
            }
        }
        return false;
    }
}
