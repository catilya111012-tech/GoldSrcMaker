package com.goldsrc.maker.editor;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

public class EntityData {

    public String classname;
    public final LinkedHashMap<String, String> keys = new LinkedHashMap<>();

    public EntityData(String classname) {
        this.classname = (classname == null || classname.isEmpty()) ? "info_target" : classname;
    }

    public void setKey(String key, String value) {
        if (value == null) {
            keys.remove(key);
        } else {
            keys.put(key, value);
        }
    }

    public String getKey(String key) {
        return keys.get(key);
    }

    public int originX() {
        return originComponent(0);
    }

    public int originY() {
        return originComponent(1);
    }

    public int originZ() {
        return originComponent(2);
    }

    public void setOrigin(int x, int y, int z) {
        setKey("origin", x + " " + y + " " + z);
    }

    private int originComponent(int index) {
        String origin = keys.get("origin");
        if (origin == null) {
            return 0;
        }
        String[] parts = origin.trim().split("\\s+");
        if (parts.length <= index) {
            return 0;
        }
        try {
            return Math.round(Float.parseFloat(parts[index]));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public void appendMapText(StringBuilder builder) {
        builder.append("{\n");
        builder.append("\"classname\" \"").append(classname).append("\"\n");
        for (Map.Entry<String, String> entry : keys.entrySet()) {
            builder.append('"').append(entry.getKey()).append("\" \"")
                    .append(sanitize(entry.getValue())).append("\"\n");
        }
        builder.append("}\n");
    }

    private static String sanitize(String value) {
        return value.replace('"', '\'');
    }

    public JSONObject toJson() throws JSONException {
        JSONObject json = new JSONObject();
        json.put("classname", classname);
        JSONObject keyJson = new JSONObject();
        for (Map.Entry<String, String> entry : keys.entrySet()) {
            keyJson.put(entry.getKey(), entry.getValue());
        }
        json.put("keys", keyJson);
        return json;
    }

    public static EntityData fromJson(JSONObject json) {
        EntityData entity = new EntityData(json.optString("classname", "info_target"));
        JSONObject keyJson = json.optJSONObject("keys");
        if (keyJson != null) {
            Iterator<String> iterator = keyJson.keys();
            while (iterator.hasNext()) {
                String key = iterator.next();
                entity.setKey(key, keyJson.optString(key));
            }
        }
        return entity;
    }

    public EntityData copy() {
        EntityData copy = new EntityData(classname);
        copy.keys.putAll(keys);
        return copy;
    }
}
