package com.goldsrc.maker.editor;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Locale;

public class Brush {

    public static final String AXIS_U_X = "[ 0 1 0 0 ]";
    public static final String AXIS_V_X = "[ 0 0 -1 0 ]";
    public static final String AXIS_U_Y = "[ 1 0 0 0 ]";
    public static final String AXIS_V_Y = "[ 0 0 -1 0 ]";
    public static final String AXIS_U_Z = "[ 1 0 0 0 ]";
    public static final String AXIS_V_Z = "[ 0 -1 0 0 ]";

    public int x1;
    public int y1;
    public int z1;
    public int x2;
    public int y2;
    public int z2;
    public String texture;

    public Brush(int x1, int y1, int z1, int x2, int y2, int z2, String texture) {
        this.x1 = Math.min(x1, x2);
        this.y1 = Math.min(y1, y2);
        this.z1 = Math.min(z1, z2);
        this.x2 = Math.max(x1, x2);
        this.y2 = Math.max(y1, y2);
        this.z2 = Math.max(z1, z2);
        this.texture = (texture == null || texture.isEmpty()) ? "NULL" : texture;
    }

    public int width() {
        return x2 - x1;
    }

    public int depth() {
        return y2 - y1;
    }

    public int height() {
        return z2 - z1;
    }

    public boolean containsPointTopView(int worldX, int worldY) {
        return worldX >= x1 && worldX <= x2 && worldY >= y1 && worldY <= y2;
    }

    public void appendMapText(StringBuilder builder) {
        builder.append("{\n");
        appendFace(builder, x2, y1, z1, x2, y2, z1, x2, y1, z2, AXIS_U_X, AXIS_V_X);
        appendFace(builder, x1, y1, z1, x1, y1, z2, x1, y2, z1, AXIS_U_X, AXIS_V_X);
        appendFace(builder, x1, y2, z1, x1, y2, z2, x2, y2, z1, AXIS_U_Y, AXIS_V_Y);
        appendFace(builder, x1, y1, z1, x2, y1, z1, x1, y1, z2, AXIS_U_Y, AXIS_V_Y);
        appendFace(builder, x1, y1, z2, x2, y1, z2, x1, y2, z2, AXIS_U_Z, AXIS_V_Z);
        appendFace(builder, x1, y1, z1, x1, y2, z1, x2, y1, z1, AXIS_U_Z, AXIS_V_Z);
        builder.append("}\n");
    }

    private void appendFace(StringBuilder builder,
                            int ax, int ay, int az,
                            int bx, int by, int bz,
                            int cx, int cy, int cz,
                            String uAxis, String vAxis) {
        builder.append(String.format(Locale.US,
                "( %d %d %d ) ( %d %d %d ) ( %d %d %d ) %s %s %s 0 1 1\n",
                ax, ay, az, cx, cy, cz, bx, by, bz, texture, uAxis, vAxis));
    }

    public JSONObject toJson() throws JSONException {
        JSONObject json = new JSONObject();
        json.put("x1", x1);
        json.put("y1", y1);
        json.put("z1", z1);
        json.put("x2", x2);
        json.put("y2", y2);
        json.put("z2", z2);
        json.put("texture", texture);
        return json;
    }

    public static Brush fromJson(JSONObject json) {
        return new Brush(
                json.optInt("x1", 0),
                json.optInt("y1", 0),
                json.optInt("z1", 0),
                json.optInt("x2", 0),
                json.optInt("y2", 0),
                json.optInt("z2", 0),
                json.optString("texture", "NULL"));
    }

    public Brush copy() {
        return new Brush(x1, y1, z1, x2, y2, z2, texture);
    }
}
