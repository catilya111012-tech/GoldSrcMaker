package com.goldsrc.maker.editor;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class MapData {

    public final List<Brush> brushes = new ArrayList<>();
    public final List<BrushEntity> brushEntities = new ArrayList<>();
    public final List<EntityData> entities = new ArrayList<>();

    public String toMapText(String wadAbsolutePath) {
        return toMapText(wadAbsolutePath, null);
    }

    public String toMapText(String wadAbsolutePath, String extraWadAbsolutePath) {
        StringBuilder builder = new StringBuilder();
        builder.append("{\n");
        builder.append("\"classname\" \"worldspawn\"\n");
        builder.append("\"mapversion\" \"220\"\n");
        String wadValue = sanitize(wadAbsolutePath);
        if (extraWadAbsolutePath != null && !extraWadAbsolutePath.isEmpty()) {
            wadValue = wadValue + ";" + sanitize(extraWadAbsolutePath);
        }
        builder.append("\"wad\" \"").append(wadValue).append("\"\n");
        for (Brush brush : brushes) {
            brush.appendMapText(builder);
        }
        builder.append("}\n");
        for (BrushEntity brushEntity : brushEntities) {
            brushEntity.appendMapText(builder);
        }
        for (EntityData entity : entities) {
            entity.appendMapText(builder);
        }
        return builder.toString();
    }

    private static String sanitize(String value) {
        if (value == null) {
            return "";
        }
        return value.replace('"', '\'');
    }

    public JSONObject toJson() throws JSONException {
        JSONObject root = new JSONObject();
        JSONArray brushArray = new JSONArray();
        for (Brush brush : brushes) {
            brushArray.put(brush.toJson());
        }
        root.put("brushes", brushArray);
        JSONArray brushEntityArray = new JSONArray();
        for (BrushEntity brushEntity : brushEntities) {
            brushEntityArray.put(brushEntity.toJson());
        }
        root.put("brushEntities", brushEntityArray);
        JSONArray entityArray = new JSONArray();
        for (EntityData entity : entities) {
            entityArray.put(entity.toJson());
        }
        root.put("entities", entityArray);
        return root;
    }

    public static MapData fromJson(JSONObject root) throws JSONException {
        MapData map = new MapData();
        JSONArray brushArray = root.optJSONArray("brushes");
        if (brushArray != null) {
            for (int i = 0; i < brushArray.length(); i++) {
                map.brushes.add(Brush.fromJson(brushArray.getJSONObject(i)));
            }
        }
        JSONArray brushEntityArray = root.optJSONArray("brushEntities");
        if (brushEntityArray != null) {
            for (int i = 0; i < brushEntityArray.length(); i++) {
                map.brushEntities.add(BrushEntity.fromJson(brushEntityArray.getJSONObject(i)));
            }
        }
        JSONArray entityArray = root.optJSONArray("entities");
        if (entityArray != null) {
            for (int i = 0; i < entityArray.length(); i++) {
                map.entities.add(EntityData.fromJson(entityArray.getJSONObject(i)));
            }
        }
        return map;
    }

    public MapData copy() {
        MapData copy = new MapData();
        for (Brush brush : brushes) {
            copy.brushes.add(brush.copy());
        }
        for (BrushEntity brushEntity : brushEntities) {
            copy.brushEntities.add(brushEntity.copy());
        }
        for (EntityData entity : entities) {
            copy.entities.add(entity.copy());
        }
        return copy;
    }
}
