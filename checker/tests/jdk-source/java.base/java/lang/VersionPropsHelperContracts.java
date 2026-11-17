// Check against VersionProps.java from the JDK's configured build output.
package java.lang;

import org.checkerframework.framework.qual.AnnotatedFor;

import java.util.HashMap;
import java.util.List;

@AnnotatedFor("nullness")
class VersionPropsHelperContracts {
    int versionNumbers() {
        List<Integer> parsed = VersionProps.parseVersionNumbers("17.0.1");
        List<Integer> current = VersionProps.versionNumbers();
        return parsed.get(0).intValue() + current.get(0).intValue();
    }

    int optionalMetadata() {
        String pre = VersionProps.pre().orElse(null);
        Integer build = VersionProps.build().orElse(null);
        String optional = VersionProps.optional().orElse(null);
        return (pre == null ? 0 : pre.length())
                + (build == null ? 0 : build.intValue())
                + (optional == null ? 0 : optional.length());
    }

    void initialize() {
        VersionProps.init(new HashMap<String, String>());
    }
}
