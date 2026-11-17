// Checked by typecheck-jdk.sh with this CF-owned source root patched into java.base.
package java.lang;

import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.framework.qual.AnnotatedFor;

import java.util.Spliterator;

@AnnotatedFor("nullness")
class TextHelperContracts {
    String concatenation(@Nullable Object value) {
        String result = StringConcatHelper.simpleConcat(value, null);
        result += StringConcatHelper.newStringOf(null);
        result += StringConcatHelper.stringOf(null);
        return result;
    }

    void optionalPrefixes(byte[] buffer, long indexCoder) {
        StringConcatHelper.prepend(indexCoder, buffer, true, null);
        StringConcatHelper.prepend(indexCoder, buffer, (byte) 1, null);
        StringConcatHelper.prepend(indexCoder, buffer, 'x', null);
        StringConcatHelper.prepend(indexCoder, buffer, (short) 1, null);
        StringConcatHelper.prepend(indexCoder, buffer, 1, null);
        StringConcatHelper.prepend(indexCoder, buffer, 1L, null);
        StringConcatHelper.prepend(indexCoder, buffer, "x", null);
    }

    int compression(char[] chars, byte[] utf16, int[] codePoints) {
        byte[] first = StringUTF16.compress(chars, 0, chars.length);
        byte[] second = StringUTF16.compress(utf16, 0, utf16.length / 2);
        byte[] third = StringLatin1.toBytes(codePoints, 0, codePoints.length);
        return (first == null ? 0 : first.length)
                + (second == null ? 0 : second.length)
                + (third == null ? 0 : third.length);
    }

    int splitting(byte[] latin1, byte[] utf16) {
        Spliterator.OfInt first = new StringLatin1.CharsSpliterator(latin1, 0).trySplit();
        Spliterator.OfInt second = new StringUTF16.CharsSpliterator(utf16, 0).trySplit();
        Spliterator.OfInt third = new StringUTF16.CodePointsSpliterator(utf16, 0).trySplit();
        return (first == null ? 0 : (int) first.estimateSize())
                + (second == null ? 0 : (int) second.estimateSize())
                + (third == null ? 0 : (int) third.estimateSize());
    }
}
