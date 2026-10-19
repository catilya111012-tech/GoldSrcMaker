package com.goldsrc.maker.editor;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Brush-энтити (func_door, func_wall и т.п.): блок keyvalue-полей плюс
 * собственные браши. В формате .map пишется как отдельный энтити-блок,
 * сразу за которым идут определения его брашей.
 */
public class BrushEntity {

    public String classname;
    public final LinkedHashMap<String, String> keys = new LinkedHashMap<>();
    public final List<Brush> brushes = new ArrayList<>();

    public BrushEntity(String classname) {
        this.classname = (classname == null || classname.isEmpty()) ? "func_wall" : classname;
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

    public void appendMapText(StringBuilder builder) {
        builder.append("{\n");
        builder.append("\"classname\" \"").append(classname).append("\"\n");
        for (Map.Entry<String, String> entry : keys.entrySet()) {
            builder.append('"').append(entry.getKey()).append("\" \"")
                    .append(sanitize(entry.getValue())).append("\"\n");
        }
        for (Brush brush : brushes) {
            brush.appendMapText(builder);
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
        JSONArray brushArray = new JSONArray();
        for (Brush brush : brushes) {
            brushArray.put(brush.toJson());
        }
        json.put("brushes", brushArray);
        return json;
    }

    public static BrushEntity fromJson(JSONObject json) {
        BrushEntity entity = new BrushEntity(json.optString("classname", "func_wall"));
        JSONObject keyJson = json.optJSONObject("keys");
        if (keyJson != null) {
            Iterator<String> iterator = keyJson.keys();
            while (iterator.hasNext()) {
                String key = iterator.next();
                entity.setKey(key, keyJson.optString(key));
            }
        }
        JSONArray brushArray = json.optJSONArray("brushes");
        if (brushArray != null) {
            for (int i = 0; i < brushArray.length(); i++) {
                entity.brushes.add(Brush.fromJson(brushArray.optJSONObject(i)));
            }
        }
        return entity;
    }

    public BrushEntity copy() {
        BrushEntity copy = new BrushEntity(classname);
        copy.keys.putAll(keys);
        for (Brush brush : brushes) {
            copy.brushes.add(brush.copy());
        }
        return copy;
    }
}
