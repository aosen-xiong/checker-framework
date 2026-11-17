// Check through typecheck-jdk.sh with this module root in JDK_EXTRA_SOURCE_ROOTS.
package java.lang;

import org.checkerframework.framework.qual.AnnotatedFor;

import java.util.Locale;

@AnnotatedFor("nullness")
class UnicodeHelperContracts {
    int guarded(int codePoint, String text, int index, Locale locale) {
        String name = CharacterName.getInstance().getName(codePoint);
        char[] mapping = CharacterData.of(codePoint).toUpperCaseCharArray(codePoint);
        char[] lower = ConditionalSpecialCasing.toLowerCaseCharArray(text, index, locale);
        return (name == null ? 0 : name.length())
                + (mapping == null ? 0 : mapping.length)
                + (lower == null ? 0 : lower.length);
    }

    int languageIndependent() {
        ConditionalSpecialCasing.Entry entry =
                new ConditionalSpecialCasing.Entry(1, new char[] {'a'}, new char[] {'A'}, null, 0);
        String language = entry.getLanguage();
        return entry.getLowerCase().length
                + entry.getUpperCase().length
                + (language == null ? 0 : language.length());
    }

    int bmpMapping(char codePoint) {
        return Character.toUpperCaseCharArray(codePoint).length;
    }
}
