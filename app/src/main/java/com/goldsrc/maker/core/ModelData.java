package com.goldsrc.maker.core;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class ModelData {

    public static final float MIN_SIZE = 1.0f;
    public static final float MAX_SIZE = 512.0f;
    public static final int MAX_PARTS = 64;
    private static final float DEFAULT_PART_SIZE = 16.0f;

    public static class Part {
        public String name;
        public String texture;
        public float x;
        public float y;
        public float z;
        public float sx;
        public float sy;
        public float sz;
        public float uvScale;

        public Part(String name, String texture,
                    float x, float y, float z,
                    float sx, float sy, float sz,
                    float uvScale) {
            this.name = name;
            this.texture = texture;
            this.x = x;
            this.y = y;
            this.z = z;
            this.sx = sx;
            this.sy = sy;
            this.sz = sz;
            this.uvScale = uvScale;
        }

        public float minX() {
            return x - sx / 2f;
        }

        public float minY() {
            return y - sy / 2f;
        }

        public float minZ() {
            return z - sz / 2f;
        }

        public float maxX() {
            return x + sx / 2f;
        }

        public float maxY() {
            return y + sy / 2f;
        }

        public float maxZ() {
            return z + sz / 2f;
        }

        public Part copy() {
            return new Part(name, texture, x, y, z, sx, sy, sz, uvScale);
        }

        public JSONObject toJson() throws JSONException {
            JSONObject json = new JSONObject();
            json.put("name", name);
            json.put("texture", texture);
            json.put("x", x);
            json.put("y", y);
            json.put("z", z);
            json.put("sx", sx);
            json.put("sy", sy);
            json.put("sz", sz);
            json.put("uv", uvScale);
            return json;
        }

        public static Part fromJson(JSONObject json) {
            String partName = json.optString("name", "part");
            String partTexture = TextureImage.sanitizeName(json.optString("texture", ""));
            if (partTexture.isEmpty()) {
                partTexture = "DEV_MEASUREGENERIC01";
            }
            return new Part(
                    partName,
                    partTexture,
                    (float) json.optDouble("x", 0),
                    (float) json.optDouble("y", 0),
                    (float) json.optDouble("z", 0),
                    clampSize((float) json.optDouble("sx", DEFAULT_PART_SIZE)),
                    clampSize((float) json.optDouble("sy", DEFAULT_PART_SIZE)),
                    clampSize((float) json.optDouble("sz", DEFAULT_PART_SIZE)),
                    clampUv((float) json.optDouble("uv", 1.0)));
        }
    }

    public String name;
    public final List<Part> parts = new ArrayList<>();

    public ModelData(String name) {
        this.name = name;
    }

    public static float clampSize(float value) {
        return Math.max(MIN_SIZE, Math.min(MAX_SIZE, value));
    }

    public static float clampUv(float value) {
        return Math.max(0.1f, Math.min(8.0f, value));
    }

    public static ModelData createDefault(String name) {
        ModelData model = new ModelData(name);
        model.parts.add(new Part("body", "C1A0_WX",
                0, 0, 16, 24, 24, 32, 1.0f));
        model.parts.add(new Part("head", "C1A0_WX",
                0, 0, 42, 14, 14, 14, 1.0f));
        return model;
    }

    public void addPart(Part part) {
        if (parts.size() < MAX_PARTS) {
            parts.add(part);
        }
    }

    public void removePart(int index) {
        if (index >= 0 && index < parts.size()) {
            parts.remove(index);
        }
    }

    public void replacePart(int index, Part part) {
        if (index >= 0 && index < parts.size()) {
            parts.set(index, part);
        }
    }

    public boolean isEmpty() {
        return parts.isEmpty();
    }

    public ModelData copy() {
        ModelData copy = new ModelData(name);
        for (Part part : parts) {
            copy.parts.add(part.copy());
        }
        return copy;
    }

    public float[] bounds() {
        if (parts.isEmpty()) {
            return new float[]{-8, -8, 0, 8, 8, 16};
        }
        float[] bounds = {
                Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE,
                -Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE
        };
        for (Part part : parts) {
            bounds[0] = Math.min(bounds[0], part.minX());
            bounds[1] = Math.min(bounds[1], part.minY());
            bounds[2] = Math.min(bounds[2], part.minZ());
            bounds[3] = Math.max(bounds[3], part.maxX());
            bounds[4] = Math.max(bounds[4], part.maxY());
            bounds[5] = Math.max(bounds[5], part.maxZ());
        }
        return bounds;
    }

    public JSONObject toJson() throws JSONException {
        JSONObject root = new JSONObject();
        root.put("name", name);
        JSONArray array = new JSONArray();
        for (Part part : parts) {
            array.put(part.toJson());
        }
        root.put("parts", array);
        return root;
    }

    public static ModelData fromJson(JSONObject root) throws JSONException {
        ModelData model = new ModelData(root.optString("name", "model"));
        JSONArray array = root.optJSONArray("parts");
        if (array != null) {
            for (int i = 0; i < array.length() && i < MAX_PARTS; i++) {
                model.parts.add(Part.fromJson(array.getJSONObject(i)));
            }
        }
        return model;
    }
}
