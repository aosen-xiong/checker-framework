// @below-java17-jdk-skip-test
// Optional source Javadoc: present values are nonnull; fallback values may be null.
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.framework.qual.AnnotatedFor;

import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.function.Supplier;

@AnnotatedFor("nullness")
class OptionalCoreNullness {
    void require(Object value) {}

    void valid(
            @Nullable String input,
            Optional<String> optional,
            Supplier<@Nullable String> nullableSupplier,
            Supplier<String> nonnullSupplier) {
        require(Optional.empty());
        require(Optional.ofNullable(input));
        require(Optional.of("value"));
        require(optional.get());
        require(optional.orElseThrow());
        require(optional.orElseThrow(IllegalStateException::new));
        require(optional.orElse("fallback"));
        require(optional.orElseGet(nonnullSupplier));
        String result = optional.orElseGet(nullableSupplier);
        if (result != null) require(result);
        require(optional.filter(value -> value.length() > 0));
        require(optional.map(value -> input));
        require(optional.flatMap(value -> Optional.ofNullable(input)));
        require(optional.or(Optional::empty));
        optional.ifPresent(value -> require(value));
        optional.ifPresentOrElse(value -> require(value), () -> {});
        optional.stream().forEach(value -> require(value));
        optional.equals(null);
        require(optional.toString());
        require(Optional.<@Nullable String>empty().orElseGet(() -> "value"));
        require(OptionalInt.empty());
        require(OptionalLong.of(1).stream());
        require(OptionalDouble.of(1).toString());
        OptionalInt.empty().equals(null);
        OptionalLong.empty().equals(null);
        OptionalDouble.empty().equals(null);
        OptionalInt.of(1).ifPresent(value -> {});
        OptionalLong.of(1).ifPresentOrElse(value -> {}, () -> {});
        OptionalDouble.empty().orElseGet(() -> 1);
    }

    void invalid(Optional<String> optional, Supplier<@Nullable String> nullableSupplier) {
        // :: error: (argument.type.incompatible)
        Optional.of(null);
        // :: error: (dereference.of.nullable)
        optional.orElse(null).length();
        // :: error: (dereference.of.nullable)
        optional.orElseGet(nullableSupplier).length();
        // :: error: (argument.type.incompatible)
        optional.map(null);
        // :: error: (argument.type.incompatible)
        optional.filter(null);
        // :: error: (return.type.incompatible)
        optional.flatMap(value -> null);
        // :: error: (return.type.incompatible)
        optional.or(() -> null);
        // :: error: (argument.type.incompatible)
        Optional.of("value").ifPresent(null);
        // :: error: (argument.type.incompatible)
        OptionalInt.of(1).ifPresent(null);
        // :: error: (argument.type.incompatible)
        OptionalLong.empty().orElseGet(null);
        // :: error: (argument.type.incompatible)
        OptionalDouble.empty().orElseThrow(null);
    }

    // Existing CF class bounds remain enforced despite the declaration-location exception.
    void invalidContainerTypes(
            // :: error: (type.invalid.annotations.on.use)
            @Nullable Optional<String> generic,
            // :: error: (type.invalid.annotations.on.use)
            @Nullable OptionalInt ints,
            // :: error: (type.invalid.annotations.on.use)
            @Nullable OptionalLong longs,
            // :: error: (type.invalid.annotations.on.use)
            @Nullable OptionalDouble doubles) {}
}
