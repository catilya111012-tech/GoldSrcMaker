package org.json;

final class JsonParser {

    private final String src;
    private int pos;

    private JsonParser(String src) {
        this.src = src;
    }

    static Object parse(String text) throws JSONException {
        JsonParser parser = new JsonParser(text);
        parser.skipWhitespace();
        Object value = parser.parseValue();
        parser.skipWhitespace();
        if (parser.pos < parser.src.length()) {
            throw parser.error("лишние данные после JSON");
        }
        return value;
    }

    private Object parseValue() throws JSONException {
        if (pos >= src.length()) {
            throw error("неожиданный конец текста");
        }
        char c = src.charAt(pos);
        switch (c) {
            case '{':
                return parseObject();
            case '[':
                return parseArray();
            case '"':
                return parseString();
            case 't':
                expect("true");
                return Boolean.TRUE;
            case 'f':
                expect("false");
                return Boolean.FALSE;
            case 'n':
                expect("null");
                return null;
            default:
                return parseNumber();
        }
    }

    private JSONObject parseObject() throws JSONException {
        JSONObject object = new JSONObject();
        pos++;
        skipWhitespace();
        if (peek() == '}') {
            pos++;
            return object;
        }
        while (true) {
            skipWhitespace();
            if (peek() != '"') {
                throw error("ожидалось имя поля");
            }
            String key = parseString();
            skipWhitespace();
            if (peek() != ':') {
                throw error("ожидалось ':'");
            }
            pos++;
            skipWhitespace();
            object.put(key, parseValue());
            skipWhitespace();
            char c = peek();
            if (c == ',') {
                pos++;
                continue;
            }
            if (c == '}') {
                pos++;
                return object;
            }
            throw error("ожидалось ',' или '}'");
        }
    }

    private JSONArray parseArray() throws JSONException {
        JSONArray array = new JSONArray();
        pos++;
        skipWhitespace();
        if (peek() == ']') {
            pos++;
            return array;
        }
        while (true) {
            skipWhitespace();
            array.put(parseValue());
            skipWhitespace();
            char c = peek();
            if (c == ',') {
                pos++;
                continue;
            }
            if (c == ']') {
                pos++;
                return array;
            }
            throw error("ожидалось ',' или ']'");
        }
    }

    private String parseString() throws JSONException {
        pos++;
        StringBuilder builder = new StringBuilder();
        while (true) {
            if (pos >= src.length()) {
                throw error("незакрытая строка");
            }
            char c = src.charAt(pos++);
            if (c == '"') {
                return builder.toString();
            }
            if (c == '\\') {
                if (pos >= src.length()) {
                    throw error("незавершённая escape-последовательность");
                }
                char e = src.charAt(pos++);
                switch (e) {
                    case '"':
                        builder.append('"');
                        break;
                    case '\\':
                        builder.append('\\');
                        break;
                    case '/':
                        builder.append('/');
                        break;
                    case 'b':
                        builder.append('\b');
                        break;
                    case 'f':
                        builder.append('\f');
                        break;
                    case 'n':
                        builder.append('\n');
                        break;
                    case 'r':
                        builder.append('\r');
                        break;
                    case 't':
                        builder.append('\t');
                        break;
                    case 'u':
                        if (pos + 4 > src.length()) {
                            throw error("обрыв \\u");
                        }
                        builder.append((char) Integer.parseInt(src.substring(pos, pos + 4), 16));
                        pos += 4;
                        break;
                    default:
                        throw error("неизвестная escape-последовательность \\" + e);
                }
            } else {
                builder.append(c);
            }
        }
    }

    private Object parseNumber() throws JSONException {
        int start = pos;
        while (pos < src.length()) {
            char c = src.charAt(pos);
            if ((c >= '0' && c <= '9') || c == '-' || c == '+' || c == '.'
                    || c == 'e' || c == 'E') {
                pos++;
            } else {
                break;
            }
        }
        String token = src.substring(start, pos);
        if (token.isEmpty()) {
            throw error("ожидалось число");
        }
        try {
            if (token.indexOf('.') < 0 && token.indexOf('e') < 0 && token.indexOf('E') < 0) {
                return Long.parseLong(token);
            }
            return Double.parseDouble(token);
        } catch (NumberFormatException e) {
            throw error("неверное число: " + token);
        }
    }

    private void expect(String literal) throws JSONException {
        if (!src.startsWith(literal, pos)) {
            throw error("ожидалось '" + literal + "'");
        }
        pos += literal.length();
    }

    private char peek() throws JSONException {
        if (pos >= src.length()) {
            throw error("неожиданный конец текста");
        }
        return src.charAt(pos);
    }

    private void skipWhitespace() {
        while (pos < src.length() && Character.isWhitespace(src.charAt(pos))) {
            pos++;
        }
    }

    private JSONException error(String message) {
        return new JSONException(message + " (позиция " + pos + ")");
    }
}
