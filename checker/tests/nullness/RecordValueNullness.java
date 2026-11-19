// @below-java17-jdk-skip-test
// Record's nullable equals input does not impose nonnull record components.

import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.framework.qual.AnnotatedFor;

import java.lang.constant.ClassDesc;

@AnnotatedFor("nullness")
class RecordValueNullness {
    record Item(@Nullable String name) {}

    enum Choice {
        FIRST
    }

    int valid() {
        Item item = new Item(null);
        Record record = item;
        record.equals(null);
        String name = item.name();
        return (name == null ? 0 : name.length()) + record.toString().length();
    }

    // Generated equals inherits Record.equals' nullable input contract.
    void generatedEquals(Item item) {
        item.equals(null);
    }

    void invalid(Item item) {
        // :: error: (dereference.of.nullable)
        item.name().length();
        // :: error: (argument.type.incompatible)
        Enum.EnumDesc.of(null, "FIRST");
        // :: error: (argument.type.incompatible)
        Enum.EnumDesc.of(ClassDesc.of("Example"), null);
    }

    long descriptor(Choice choice) {
        return choice.describeConstable().stream().count();
    }
}
