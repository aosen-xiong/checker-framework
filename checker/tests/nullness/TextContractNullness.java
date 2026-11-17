// Public text contracts distinguish nullable contents from nonnull containers.

import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.framework.qual.AnnotatedFor;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

@AnnotatedFor("nullness")
class TextContractNullness {
    int strings(String receiver, @Nullable String text, @Nullable Object value) {
        receiver.equals(null);
        if (receiver.equalsIgnoreCase(text)) {
            text.length();
        }
        List<@Nullable CharSequence> elements = Arrays.asList("first", null, "last");
        String joined = String.join("-", elements);
        String varargs = String.join("-", (CharSequence) null, "last");
        String formatted = String.format((Locale) null, "%s", value);
        int total =
                joined.length()
                        + varargs.length()
                        + formatted.length()
                        + String.valueOf(value).length();
        for (String part : receiver.split(",")) {
            total += part.length();
        }
        return total;
    }

    int builders(
            @Nullable String text,
            @Nullable CharSequence sequence,
            @Nullable Object object,
            @Nullable StringBuffer buffer) {
        StringBuilder builder = new StringBuilder();
        builder.append(text).append(sequence).append(object).append(buffer);
        builder.append(sequence, 0, 1).insert(0, text).insert(0, sequence).insert(0, object);
        StringBuffer synchronizedBuilder = new StringBuffer();
        synchronizedBuilder.append(text).append(sequence).append(object).append(buffer);
        synchronizedBuilder
                .append(sequence, 0, 1)
                .insert(0, text)
                .insert(0, sequence)
                .insert(0, object);
        return builder.toString().length() + synchronizedBuilder.toString().length();
    }

    void invalid(String string, StringBuilder builder, StringBuffer buffer) {
        // :: error: (argument.type.incompatible)
        new StringBuilder((String) null);
        // :: error: (argument.type.incompatible)
        new StringBuffer((CharSequence) null);
        // :: error: (argument.type.incompatible)
        builder.append((char[]) null);
        // :: error: (argument.type.incompatible)
        buffer.append((char[]) null);
        // :: error: (argument.type.incompatible)
        builder.replace(0, 0, null);
        // :: error: (argument.type.incompatible)
        buffer.replace(0, 0, null);
        // :: error: (argument.type.incompatible)
        string.compareTo(null);
        // :: error: (argument.type.incompatible)
        String.join(null, "text");
        // :: error: (argument.type.incompatible)
        String.join("-", (CharSequence[]) null);
        // :: error: (argument.type.incompatible)
        String.join("-", (Iterable<CharSequence>) null);
        // :: error: (argument.type.incompatible)
        String.valueOf((char[]) null);
    }
}
