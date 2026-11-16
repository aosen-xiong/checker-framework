// Contracts documented by javax.xml.namespace.NamespaceContext.

import org.checkerframework.checker.nullness.qual.Nullable;

import java.util.Collections;
import java.util.Iterator;

import javax.xml.XMLConstants;
import javax.xml.namespace.NamespaceContext;

class NamespaceContextNullness {
    int valid(NamespaceContext context) {
        String prefix = context.getPrefix("urn:test");
        int length = prefix == null ? 0 : prefix.length();
        Iterator<String> prefixes = context.getPrefixes("urn:test");
        while (prefixes.hasNext()) {
            length += prefixes.next().length();
        }
        return length + context.getNamespaceURI("p").length();
    }

    void invalid(NamespaceContext context) {
        // :: error: (argument.type.incompatible)
        context.getNamespaceURI(null);
        // :: error: (argument.type.incompatible)
        context.getPrefix(null);
        // :: error: (argument.type.incompatible)
        context.getPrefixes(null);
        // :: error: (dereference.of.nullable)
        context.getPrefix("urn:missing").length();
    }

    static class ValidContext implements NamespaceContext {
        public String getNamespaceURI(String prefix) {
            if (prefix.equals("xml")) {
                return XMLConstants.XML_NS_URI;
            }
            if (prefix.equals("xmlns")) {
                return XMLConstants.XMLNS_ATTRIBUTE_NS_URI;
            }
            return "";
        }

        public @Nullable String getPrefix(String uri) {
            if (uri.equals(XMLConstants.XML_NS_URI)) {
                return "xml";
            }
            if (uri.equals(XMLConstants.XMLNS_ATTRIBUTE_NS_URI)) {
                return "xmlns";
            }
            return uri.isEmpty() ? "" : null;
        }

        public Iterator<String> getPrefixes(String uri) {
            String prefix = getPrefix(uri);
            return prefix == null
                    ? Collections.emptyIterator()
                    : Collections.singletonList(prefix).iterator();
        }
    }

    static class InvalidContext implements NamespaceContext {
        public String getNamespaceURI(String prefix) {
            // :: error: (return.type.incompatible)
            return null;
        }

        public @Nullable String getPrefix(String uri) {
            return null;
        }

        public Iterator<String> getPrefixes(String uri) {
            // :: error: (return.type.incompatible)
            return null;
        }
    }

    abstract static class NullElementContext implements NamespaceContext {
        public Iterator<String> getPrefixes(String uri) {
            // :: error: (return.type.incompatible)
            return Collections.<@Nullable String>singletonList(null).iterator();
        }
    }
}
