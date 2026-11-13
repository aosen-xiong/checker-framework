import org.checkerframework.checker.mutability.qual.AbstractState;
import org.checkerframework.checker.mutability.qual.Mutable;
import org.checkerframework.checker.mutability.qual.Readonly;
import org.checkerframework.checker.mutability.qual.ReceiverDependentMutable;

// Rocq: gait-organized/ExamplesOverride.v. The file proves one well-typed program (ov_ct_wf,
// ov_main_typed) and proves no program ill typed. It exercises an override with dynamic dispatch
// and a field read through a qualified use @Readonly X of a method type variable whose bound has
// no type arguments. The program is translated to check that the checker accepts it.
@ReceiverDependentMutable class OvxCell {
    @Readonly Object value;

    OvxCell(@Readonly Object value) {
        this.value = value;
    }
}

@ReceiverDependentMutable class OvxBase {
    OvxBase() {}

    // wf_base_id (well typed).
    @AbstractState
    @Readonly Object id(@Readonly OvxBase this, @Readonly Object a) {
        return a;
    }

    // wf_base_look (well typed): the members of @Readonly X are looked up at @Readonly Cell.
    @AbstractState
    <X extends @Readonly OvxCell> @Readonly Object look(@Readonly OvxBase this, @Readonly X c) {
        @Readonly Object r = c.value;
        return r;
    }
}

@ReceiverDependentMutable class OvxDerived extends OvxBase {
    OvxDerived() {}

    // wf_derived_id (well typed): the override.
    @Override
    @AbstractState
    @Readonly Object id(@Readonly OvxDerived this, @Readonly Object a) {
        @Readonly Object r = null;
        return r;
    }
}

class OvxMain {
    // ov_main_typed (well typed).
    @AbstractState
    void main() {
        @Mutable Object o = new @Mutable Object();
        @Mutable OvxCell c = new @Mutable OvxCell(o);
        @Readonly OvxBase b = new @Mutable OvxDerived();
        @Readonly Object x = b.id(o);
        @Readonly Object y = b.<@Mutable OvxCell>look(c);
    }
}
