// @below-java17-jdk-skip-test
// Character's documented nullable results, nonnull factories and null preconditions.

import org.checkerframework.framework.qual.AnnotatedFor;

@AnnotatedFor("nullness")
class UnicodeNullness {
    int guarded(int codePoint) {
        String name = Character.getName(codePoint);
        Character.UnicodeBlock block = Character.UnicodeBlock.of(codePoint);
        return (name == null ? 0 : name.length()) + (block == null ? 0 : block.toString().length());
    }

    int concrete(int codePoint, String name, CharSequence text, char[] chars) {
        Character value = Character.valueOf('a');
        value.equals(null);
        Character.UnicodeBlock.BASIC_LATIN.equals(null);
        return value.charValue()
                + Character.toString(codePoint).length()
                + Character.toChars(codePoint).length
                + Character.UnicodeBlock.forName(name).toString().length()
                + Character.UnicodeScript.forName(name).name().length()
                + Character.UnicodeScript.of(codePoint).name().length()
                + Character.codePointAt(text, 0)
                + Character.codePointAt(chars, 0);
    }

    void invalid(int codePoint, Character value) {
        // :: error: (dereference.of.nullable)
        Character.getName(codePoint).length();
        // :: error: (dereference.of.nullable)
        Character.UnicodeBlock.of(codePoint).toString();
        // :: error: (dereference.of.nullable)
        Character.UnicodeBlock.of('a').toString();
        // :: error: (argument.type.incompatible)
        Character.UnicodeBlock.forName(null);
        // :: error: (argument.type.incompatible)
        Character.UnicodeScript.forName(null);
        // :: error: (argument.type.incompatible)
        Character.codePointOf(null);
        // :: error: (argument.type.incompatible)
        Character.codePointAt((CharSequence) null, 0);
        // :: error: (argument.type.incompatible)
        Character.codePointAt((char[]) null, 0);
        // :: error: (argument.type.incompatible)
        Character.toChars(codePoint, null, 0);
        // :: error: (argument.type.incompatible)
        value.compareTo(null);
    }
}
