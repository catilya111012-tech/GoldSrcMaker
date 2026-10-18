package com.goldsrc.maker.scripting;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

public class BlockNode {

    public int id;
    public BlockType type;
    public float x;
    public float y;
    public int nextId = -1;
    public boolean userEdited = false;
    public String customBody = null;
    public final LinkedHashMap<String, String> params = new LinkedHashMap<>();

    public BlockNode(int id, BlockType type, float x, float y) {
        this.id = id;
        this.type = type;
        this.x = x;
        this.y = y;
        for (int i = 0; i < type.paramNames.length; i++) {
            params.put(type.paramNames[i], type.paramDefaults[i]);
        }
    }

    public String getParam(String name) {
        String value = params.get(name);
        return value == null ? "" : value;
    }

    public void setParam(String name, String value) {
        params.put(name, value == null ? "" : value);
    }

    public String displayTitle() {
        return type.title + "  #" + id;
    }

    public JSONObject toJson() throws JSONException {
        JSONObject json = new JSONObject();
        json.put("id", id);
        json.put("type", type.name());
        json.put("x", x);
        json.put("y", y);
        json.put("nextId", nextId);
        json.put("userEdited", userEdited);
        if (customBody != null) {
            json.put("customBody", customBody);
        }
        JSONObject paramJson = new JSONObject();
        for (Map.Entry<String, String> entry : params.entrySet()) {
            paramJson.put(entry.getKey(), entry.getValue());
        }
        json.put("params", paramJson);
        return json;
    }

    public static BlockNode fromJson(JSONObject json) {
        int id = json.optInt("id", 1);
        BlockType type;
        try {
            type = BlockType.valueOf(json.optString("type", BlockType.ACTION_SHOW_MESSAGE.name()));
        } catch (IllegalArgumentException e) {
            type = BlockType.ACTION_SHOW_MESSAGE;
        }
        BlockNode node = new BlockNode(id, type,
                (float) json.optDouble("x", 40),
                (float) json.optDouble("y", 40));
        node.nextId = json.optInt("nextId", -1);
        node.userEdited = json.optBoolean("userEdited", false);
        if (json.has("customBody")) {
            node.customBody = json.optString("customBody", null);
        }
        JSONObject paramJson = json.optJSONObject("params");
        if (paramJson != null) {
            Iterator<String> iterator = paramJson.keys();
            while (iterator.hasNext()) {
                String key = iterator.next();
                node.params.put(key, paramJson.optString(key));
            }
        }
        return node;
    }
}
