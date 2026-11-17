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

    // Concrete BMP implementations strengthen CharacterData's nullable result.
    int generatedMappings() {
        return CharacterDataLatin1.instance.toUpperCaseCharArray(0x00df).length
                + CharacterData00.instance.toUpperCaseCharArray(0x0130).length;
    }

    // Other planes retain CharacterData's nullable default implementation.
    int otherPlanes() {
        char[] plane1 = CharacterData01.instance.toUpperCaseCharArray(0x10000);
        char[] plane2 = CharacterData02.instance.toUpperCaseCharArray(0x20000);
        char[] plane3 = CharacterData03.instance.toUpperCaseCharArray(0x30000);
        char[] plane14 = CharacterData0E.instance.toUpperCaseCharArray(0xe0000);
        return (plane1 == null ? 0 : plane1.length)
                + (plane2 == null ? 0 : plane2.length)
                + (plane3 == null ? 0 : plane3.length)
                + (plane14 == null ? 0 : plane14.length);
    }

    int bmpMapping(char codePoint) {
        return Character.toUpperCaseCharArray(codePoint).length;
    }
}
