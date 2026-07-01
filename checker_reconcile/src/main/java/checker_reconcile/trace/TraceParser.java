package checker_reconcile.trace;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Minimal JSONL parser for trace events. Unknown event kinds are preserved. */
public final class TraceParser {
    public List<TraceEvent> parse(Path path) throws IOException {
        List<TraceEvent> result = new ArrayList<>();
        List<String> lines = Files.readAllLines(path);
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i).trim();
            if (!line.isEmpty()) {
                result.add(new TraceEvent(i + 1, new Parser(line, i + 1).parseObject()));
            }
        }
        return result;
    }

    private static final class Parser {
        private final String input;
        private final int lineNumber;
        private int index;

        Parser(String input, int lineNumber) {
            this.input = input;
            this.lineNumber = lineNumber;
        }

        Map<String, Object> parseObject() {
            expect('{');
            Map<String, Object> result = new LinkedHashMap<>();
            skipWhitespace();
            if (peek('}')) {
                index++;
                return result;
            }
            while (true) {
                String key = parseString();
                expect(':');
                result.put(key, parseValue());
                skipWhitespace();
                if (peek('}')) {
                    index++;
                    return result;
                }
                expect(',');
            }
        }

        private List<Object> parseArray() {
            expect('[');
            List<Object> result = new ArrayList<>();
            skipWhitespace();
            if (peek(']')) {
                index++;
                return result;
            }
            while (true) {
                result.add(parseValue());
                skipWhitespace();
                if (peek(']')) {
                    index++;
                    return result;
                }
                expect(',');
            }
        }

        private Object parseValue() {
            skipWhitespace();
            if (peek('"')) {
                return parseString();
            }
            if (peek('{')) {
                return parseObject();
            }
            if (peek('[')) {
                return parseArray();
            }
            if (input.startsWith("true", index)) {
                index += 4;
                return true;
            }
            if (input.startsWith("false", index)) {
                index += 5;
                return false;
            }
            if (input.startsWith("null", index)) {
                index += 4;
                return null;
            }
            int start = index;
            while (index < input.length() && "-0123456789.eE+".indexOf(input.charAt(index)) >= 0) {
                index++;
            }
            if (start == index) {
                throw error("expected JSON value");
            }
            String number = input.substring(start, index);
            if (number.indexOf('.') >= 0 || number.indexOf('e') >= 0 || number.indexOf('E') >= 0) {
                return Double.parseDouble(number);
            }
            return Long.parseLong(number);
        }

        private String parseString() {
            expect('"');
            StringBuilder result = new StringBuilder();
            while (index < input.length()) {
                char ch = input.charAt(index++);
                if (ch == '"') {
                    return result.toString();
                }
                if (ch == '\\') {
                    if (index >= input.length()) {
                        throw error("unterminated escape");
                    }
                    char escaped = input.charAt(index++);
                    switch (escaped) {
                        case '"':
                        case '\\':
                        case '/':
                            result.append(escaped);
                            break;
                        case 'b':
                            result.append('\b');
                            break;
                        case 'f':
                            result.append('\f');
                            break;
                        case 'n':
                            result.append('\n');
                            break;
                        case 'r':
                            result.append('\r');
                            break;
                        case 't':
                            result.append('\t');
                            break;
                        default:
                            throw error("unsupported escape");
                    }
                } else {
                    result.append(ch);
                }
            }
            throw error("unterminated string");
        }

        private void expect(char ch) {
            skipWhitespace();
            if (index >= input.length() || input.charAt(index) != ch) {
                throw error("expected '" + ch + "'");
            }
            index++;
        }

        private boolean peek(char ch) {
            skipWhitespace();
            return index < input.length() && input.charAt(index) == ch;
        }

        private void skipWhitespace() {
            while (index < input.length() && Character.isWhitespace(input.charAt(index))) {
                index++;
            }
        }

        private IllegalArgumentException error(String message) {
            return new IllegalArgumentException("trace line " + lineNumber + ": " + message);
        }
    }
}
