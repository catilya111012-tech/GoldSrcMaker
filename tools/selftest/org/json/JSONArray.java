package org.json;

import java.util.ArrayList;
import java.util.List;

public class JSONArray {

    private final List<Object> list = new ArrayList<>();

    public JSONArray put(Object value) {
        list.add(value);
        return this;
    }

    public int length() {
        return list.size();
    }

    public JSONObject getJSONObject(int index) {
        return (JSONObject) list.get(index);
    }

    public JSONObject optJSONObject(int index) {
        Object value = list.get(index);
        return value instanceof JSONObject ? (JSONObject) value : null;
    }

    @Override
    public String toString() {
        return list.toString();
    }
}
