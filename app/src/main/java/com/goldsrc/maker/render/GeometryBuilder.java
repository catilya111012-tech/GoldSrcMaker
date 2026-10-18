package com.goldsrc.maker.render;

import com.goldsrc.maker.core.ModelData;
import com.goldsrc.maker.editor.Brush;
import com.goldsrc.maker.editor.EntityData;
import com.goldsrc.maker.editor.MapData;

public final class GeometryBuilder {

    public static final int FLOATS_PER_VERTEX = 6;
    public static final int VERTICES_PER_BOX = 36;
    public static final int MAX_VERTICES = 120000;
    public static final float ENTITY_CUBE_SIZE = 8.0f;
    public static final float ENTITY_CUBE_Z_OFFSET = 24.0f;

    private static final int[] TEXTURE_PALETTE = {
            0xFF546E7A, 0xFF8D6E63, 0xFF789262, 0xFF7E57C2,
            0xFF5C6BC0, 0xFF66BB6A, 0xFFEF6C00, 0xFF8E24AA
    };

    private static final int[] ENTITY_PALETTE = {
            0xFFFFD54F, 0xFFFF7043, 0xFF26C6DA, 0xFFEC407A, 0xFF9CCC65
    };

    private GeometryBuilder() {
    }

    public static float[] build(MapData map) {
        if (map == null) {
            return new float[0];
        }

        final int wanted = (map.brushes.size() + map.entities.size()) * VERTICES_PER_BOX;
        final int vertexCount = Math.min(wanted, MAX_VERTICES);
        final float[] data = new float[vertexCount * FLOATS_PER_VERTEX];

        int cursor = 0;
        for (Brush brush : map.brushes) {
            if (cursor >= data.length) {
                break;
            }
            int baseColor = colorFromPalette(TEXTURE_PALETTE, brush.texture);
            cursor = emitShadedBox(data, cursor,
                    brush.x1, brush.y1, brush.z1, brush.x2, brush.y2, brush.z2, baseColor);
        }
        for (EntityData entity : map.entities) {
            if (cursor >= data.length) {
                break;
            }
            int baseColor = colorFromPalette(ENTITY_PALETTE, entity.classname);
            float x = entity.originX();
            float y = entity.originY();
            float z = entity.originZ() + ENTITY_CUBE_Z_OFFSET;
            cursor = emitShadedBox(data, cursor,
                    x - ENTITY_CUBE_SIZE, y - ENTITY_CUBE_SIZE, z - ENTITY_CUBE_SIZE,
                    x + ENTITY_CUBE_SIZE, y + ENTITY_CUBE_SIZE, z + ENTITY_CUBE_SIZE,
                    baseColor);
        }

        if (cursor == data.length) {
            return data;
        }
        return trimTo(data, cursor);
    }

    public static int vertexCount(float[] data) {
        return data.length / FLOATS_PER_VERTEX;
    }

    public static float[] buildModel(ModelData model) {
        if (model == null || model.parts.isEmpty()) {
            return new float[0];
        }
        final int wanted = model.parts.size() * VERTICES_PER_BOX;
        final int vertexCount = Math.min(wanted, MAX_VERTICES);
        final float[] data = new float[vertexCount * FLOATS_PER_VERTEX];

        int cursor = 0;
        for (ModelData.Part part : model.parts) {
            if (cursor >= data.length) {
                break;
            }
            int baseColor = colorFromPalette(TEXTURE_PALETTE, part.texture);
            cursor = emitShadedBox(data, cursor,
                    part.minX(), part.minY(), part.minZ(),
                    part.maxX(), part.maxY(), part.maxZ(),
                    baseColor);
        }

        if (cursor == data.length) {
            return data;
        }
        return trimTo(data, cursor);
    }

    private static int emitShadedBox(float[] data, int cursor,
                                     float x1, float y1, float z1,
                                     float x2, float y2, float z2,
                                     int baseColor) {
        float lx = Math.min(x1, x2);
        float ly = Math.min(y1, y2);
        float lz = Math.min(z1, z2);
        float ux = Math.max(x1, x2);
        float uy = Math.max(y1, y2);
        float uz = Math.max(z1, z2);

        cursor = emitQuad(data, cursor,
                lx, ly, uz, lx, uy, uz, ux, uy, uz, ux, ly, uz, scale(baseColor, 1.0f));
        cursor = emitQuad(data, cursor,
                lx, ly, lz, ux, ly, lz, ux, uy, lz, lx, uy, lz, scale(baseColor, 0.45f));
        cursor = emitQuad(data, cursor,
                lx, uy, lz, lx, uy, uz, ux, uy, uz, ux, uy, lz, scale(baseColor, 0.8f));
        cursor = emitQuad(data, cursor,
                lx, ly, lz, lx, ly, uz, ux, ly, uz, ux, ly, lz, scale(baseColor, 0.65f));
        cursor = emitQuad(data, cursor,
                ux, ly, lz, ux, ly, uz, ux, uy, uz, ux, uy, lz, scale(baseColor, 0.7f));
        cursor = emitQuad(data, cursor,
                lx, ly, lz, lx, uy, lz, lx, uy, uz, lx, ly, uz, scale(baseColor, 0.75f));
        return cursor;
    }

    private static int emitQuad(float[] data, int cursor,
                                float ax, float ay, float az,
                                float bx, float by, float bz,
                                float cx, float cy, float cz,
                                float dx, float dy, float dz,
                                int color) {
        cursor = putVertex(data, cursor, ax, ay, az, color);
        cursor = putVertex(data, cursor, bx, by, bz, color);
        cursor = putVertex(data, cursor, cx, cy, cz, color);
        cursor = putVertex(data, cursor, ax, ay, az, color);
        cursor = putVertex(data, cursor, cx, cy, cz, color);
        cursor = putVertex(data, cursor, dx, dy, dz, color);
        return cursor;
    }

    private static int putVertex(float[] data, int cursor, float x, float y, float z, int color) {
        if (cursor + FLOATS_PER_VERTEX > data.length) {
            return cursor;
        }
        data[cursor++] = x;
        data[cursor++] = y;
        data[cursor++] = z;
        data[cursor++] = ((color >> 16) & 0xFF) / 255.0f;
        data[cursor++] = ((color >> 8) & 0xFF) / 255.0f;
        data[cursor++] = (color & 0xFF) / 255.0f;
        return cursor;
    }

    private static float[] trimTo(float[] data, int length) {
        float[] trimmed = new float[length];
        System.arraycopy(data, 0, trimmed, 0, length);
        return trimmed;
    }

    private static int scale(int color, float factor) {
        int r = Math.round(((color >> 16) & 0xFF) * factor);
        int g = Math.round(((color >> 8) & 0xFF) * factor);
        int b = Math.round((color & 0xFF) * factor);
        r = Math.min(255, Math.max(0, r));
        g = Math.min(255, Math.max(0, g));
        b = Math.min(255, Math.max(0, b));
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    private static int colorFromPalette(int[] palette, String seed) {
        int hash = seed == null ? 0 : seed.hashCode();
        return palette[Math.abs(hash) % palette.length];
    }
}
