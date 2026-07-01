package checker_reconcile.trace;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** One JSONL event emitted by the Checker Framework trace exporter. */
public final class TraceEvent {
    public final int lineNumber;
    public final String event;
    public final String id;
    public final Map<String, Object> fields;

    public TraceEvent(int lineNumber, Map<String, Object> fields) {
        this.lineNumber = lineNumber;
        this.fields = Collections.unmodifiableMap(new LinkedHashMap<>(fields));
        this.event = stringField("event");
        this.id = stringField("id");
    }

    public String stringField(String name) {
        Object value = fields.get(name);
        return value == null ? "" : value.toString();
    }

    @SuppressWarnings("unchecked")
    public List<Object> listField(String name) {
        Object value = fields.get(name);
        return value instanceof List<?> ? (List<Object>) value : Collections.emptyList();
    }
}
