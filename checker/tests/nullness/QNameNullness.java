// Contracts documented by javax.xml.namespace.QName.

import javax.xml.namespace.QName;

class QNameNullness {
    int valid() {
        QName two = new QName(null, "local");
        QName three = new QName(null, "local", "p");
        two.equals(null);
        return two.getNamespaceURI().length()
                + three.getPrefix().length()
                + QName.valueOf("{urn:test}local").getLocalPart().length();
    }

    void invalid() {
        // :: error: (argument.type.incompatible)
        new QName(null);
        // :: error: (argument.type.incompatible)
        new QName("urn:test", null);
        // :: error: (argument.type.incompatible)
        new QName("urn:test", null, "p");
        // :: error: (argument.type.incompatible)
        new QName("urn:test", "local", null);
        // :: error: (argument.type.incompatible)
        QName.valueOf(null);
    }

    static class Name extends QName {
        Name() {
            super(null, "local");
        }

        @Override
        public String getLocalPart() {
            return super.getLocalPart();
        }
    }
}
