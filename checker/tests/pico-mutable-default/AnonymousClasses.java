import org.checkerframework.checker.mutability.qual.Immutable;
import org.checkerframework.checker.mutability.qual.Mutable;
import org.checkerframework.checker.mutability.qual.Readonly;
import org.checkerframework.checker.mutability.qual.ReceiverDependentMutable;

class AnonymousClasses {
    @Immutable static class ImmutableClass {}

    @Mutable static class MutableClass {}

    @ReceiverDependentMutable static class RDMClass {}

    void creationExpressionDeterminesAnonymousClassBound() {
        new @Immutable ImmutableClass() {};

        // :: error: (type.invalid.annotations.on.use) :: warning:
        // (cast.unsafe.constructor.invocation)
        new @Mutable ImmutableClass() {};

        new @Mutable MutableClass() {};

        // :: error: (type.invalid.annotations.on.use) :: warning:
        // (cast.unsafe.constructor.invocation)
        new @Immutable MutableClass() {};

        new @Mutable RDMClass() {};
        new @Immutable RDMClass() {};
        new @ReceiverDependentMutable RDMClass() {};

        // :: error: (constructor.invocation.invalid) :: error: (constructor.return.invalid)
        new @Readonly RDMClass() {};
    }

    // An anonymous class has no declaration of its own. Without an explicit qualifier on the
    // creation expression it must take the bound of the type it extends or implements;
    // otherwise an @Immutable supertype yields @Readonly, which no context accepts and which
    // cannot be corrected at the use site -- there is nowhere to write a qualifier on
    // `new Base() {}` once the creation expression is left bare.
    @Immutable ImmutableClass immutableSupertypeGivesImmutable() {
        return new ImmutableClass() {};
    }

    @Mutable MutableClass mutableSupertypeGivesMutable() {
        return new MutableClass() {};
    }

    // A @ReceiverDependentMutable supertype still yields the concrete creation qualifier.
    @Mutable RDMClass rdmSupertypeGivesMutable() {
        return new RDMClass() {};
    }
}
