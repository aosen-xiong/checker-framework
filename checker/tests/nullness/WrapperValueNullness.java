// Primitive-wrapper contracts: null-tolerant properties/equality and numeric preconditions.

import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.framework.qual.AnnotatedFor;

@AnnotatedFor("nullness")
class WrapperValueNullness {
    int booleans(@Nullable String text) {
        Boolean value = Boolean.valueOf(text);
        Boolean.valueOf((String) null);
        Boolean.parseBoolean(null);
        Boolean.getBoolean(null);
        if (Boolean.parseBoolean(text)) {
            return text.length();
        }
        if (Boolean.getBoolean(text)) {
            return text.length();
        }
        return value.toString().length();
    }

    long properties(
            @Nullable String name, @Nullable Integer intDefault, @Nullable Long longDefault) {
        Integer intPrimitive = Integer.getInteger(name, 1);
        Long longPrimitive = Long.getLong(name, 1L);
        Integer intBoxed = Integer.getInteger(name, Integer.valueOf(1));
        Long longBoxed = Long.getLong(name, Long.valueOf(1L));
        Integer maybeInt = Integer.getInteger(name, intDefault);
        Long maybeLong = Long.getLong(name, longDefault);
        Integer.getInteger(null);
        Long.getLong(null);
        long sum = intPrimitive + longPrimitive + intBoxed + longBoxed;
        if (maybeInt != null) {
            sum += maybeInt.intValue();
        }
        if (maybeLong != null) {
            sum += maybeLong.longValue();
        }
        return sum;
    }

    void equality(Boolean b, Byte y, Short s, Integer i, Long l, Float f, Double d) {
        b.equals(null);
        y.equals(null);
        s.equals(null);
        i.equals(null);
        l.equals(null);
        f.equals(null);
        d.equals(null);
    }

    int results() {
        Boolean b = Boolean.valueOf(true);
        Byte y = Byte.valueOf((byte) 1);
        Short s = Short.valueOf((short) 1);
        Integer i = Integer.valueOf("1");
        Long l = Long.valueOf("1");
        Float f = Float.valueOf("1");
        Double d = Double.valueOf("1");
        return b.toString().length()
                + y.toString().length()
                + s.toString().length()
                + i.toString().length()
                + l.toString().length()
                + f.toString().length()
                + d.toString().length();
    }

    void invalidProperties(@Nullable String name) {
        // :: error: (dereference.of.nullable)
        Integer.getInteger(name).intValue();
        // :: error: (dereference.of.nullable)
        Long.getLong(name).longValue();
        // :: error: (dereference.of.nullable)
        Integer.getInteger(name, (Integer) null).intValue();
        // :: error: (dereference.of.nullable)
        Long.getLong(name, (Long) null).longValue();
    }

    void invalidArguments(Boolean b, Byte y, Short s, Integer i, Long l, Float f, Double d) {
        // :: error: (argument.type.incompatible)
        b.compareTo(null);
        // :: error: (argument.type.incompatible)
        y.compareTo(null);
        // :: error: (argument.type.incompatible)
        s.compareTo(null);
        // :: error: (argument.type.incompatible)
        i.compareTo(null);
        // :: error: (argument.type.incompatible)
        l.compareTo(null);
        // :: error: (argument.type.incompatible)
        f.compareTo(null);
        // :: error: (argument.type.incompatible)
        d.compareTo(null);
        // :: error: (argument.type.incompatible)
        Byte.parseByte(null);
        // :: error: (argument.type.incompatible)
        Short.parseShort(null);
        // :: error: (argument.type.incompatible)
        Integer.parseInt(null);
        // :: error: (argument.type.incompatible)
        Long.parseLong(null);
        // :: error: (argument.type.incompatible)
        Float.parseFloat(null);
        // :: error: (argument.type.incompatible)
        Double.parseDouble(null);
    }
}
