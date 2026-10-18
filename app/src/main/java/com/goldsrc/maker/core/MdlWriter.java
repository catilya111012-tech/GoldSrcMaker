package com.goldsrc.maker.core;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class MdlWriter {

    public static final String MAGIC = "IDST";
    public static final int VERSION = 10;

    public static final int HEADER_SIZE = 244;
    public static final int BONE_SIZE = 112;
    public static final int SEQUENCE_SIZE = 176;
    public static final int SEQUENCE_GROUP_SIZE = 104;
    public static final int TEXTURE_SIZE = 80;
    public static final int BODYPART_SIZE = 76;
    public static final int MODEL_SIZE = 112;
    public static final int MESH_SIZE = 20;
    public static final int VERTEX_SIZE = 12;

    public static final int VERTICES_PER_BOX = 8;
    public static final int TRIANGLES_PER_BOX = 12;
    public static final int MIP_LEVELS = 4;

    private static final int[][] BOX_FACES = {
            {4, 5, 6, 7},
            {0, 3, 2, 1},
            {0, 1, 5, 4},
            {3, 7, 6, 2},
            {1, 2, 6, 5},
            {0, 4, 7, 3},
    };

    private static final int DEFAULT_TEXTURE_SIZE = 64;

    private final ModelData model;
    private final Map<String, TextureImage> textures;
    private final int[] stockPalette;
    private final float[] bounds;

    private MdlWriter(ModelData model, Map<String, TextureImage> textures, int[] stockPalette) {
        this.model = model;
        this.textures = textures;
        this.stockPalette = stockPalette;
        this.bounds = model.bounds();
    }

    public static void write(File outFile, ModelData model,
                             List<TextureImage> availableTextures,
                             int[] stockPalette) throws IOException {
        Map<String, TextureImage> map = new LinkedHashMap<>();
        if (availableTextures != null) {
            for (TextureImage texture : availableTextures) {
                map.put(texture.name.toUpperCase(Locale.ROOT), texture);
            }
        }
        MdlWriter writer = new MdlWriter(model, map, stockPalette);
        try (OutputStream output = new FileOutputStream(outFile)) {
            output.write(writer.build());
        }
    }

    private byte[] build() throws IOException {
        int numParts = Math.max(1, model.parts.size());
        int numVerts = numParts * VERTICES_PER_BOX;
        int numTris = numParts * TRIANGLES_PER_BOX;

        int boneOffset = HEADER_SIZE;
        int sequenceOffset = boneOffset + BONE_SIZE;
        int seqGroupOffset = sequenceOffset + SEQUENCE_SIZE;
        int textureOffset = seqGroupOffset + SEQUENCE_GROUP_SIZE;
        int textureDataOffset = textureOffset + numParts * TEXTURE_SIZE;
        int textureDataSize = 0;

        TextureImage[] partTextures = new TextureImage[numParts];
        byte[][] partMips = new byte[numParts][];
        for (int i = 0; i < numParts; i++) {
            partTextures[i] = effectiveTexture(i);
            partMips[i] = buildMipChain(partTextures[i]);
            textureDataSize += partMips[i].length;
        }

        int skinOffset = textureDataOffset + textureDataSize;
        int bodyPartOffset = skinOffset + numParts * 2;
        int modelOffset = bodyPartOffset + BODYPART_SIZE;
        int meshOffset = modelOffset + MODEL_SIZE;
        int vertInfoOffset = meshOffset + numParts * MESH_SIZE;
        int vertexOffset = vertInfoOffset + numVerts;
        int triIndexOffset = vertexOffset + numVerts * VERTEX_SIZE;
        int animOffset = triIndexOffset + numTris * 3 * 2;
        int totalSize = animOffset + 12 + 12;

        try (LeOutputStream out = new LeOutputStream(totalSize)) {
            out.writeAscii(MAGIC);
            out.writeInt(VERSION);

            byte[] nameField = new byte[64];
            byte[] nameBytes = model.name.toLowerCase(Locale.ROOT)
                    .getBytes(StandardCharsets.UTF_8);
            System.arraycopy(nameBytes, 0, nameField, 0, Math.min(63, nameBytes.length));
            out.write(nameField);

            out.writeInt(totalSize);
            out.writeFloats(0, 0, 0);
            out.writeFloats(bounds[0], bounds[1], bounds[2]);
            out.writeFloats(bounds[3], bounds[4], bounds[5]);
            out.writeFloats(bounds[0], bounds[1], bounds[2]);
            out.writeFloats(bounds[3], bounds[4], bounds[5]);
            out.writeInt(0);

            out.writeInt(1);
            out.writeInt(boneOffset);
            out.writeInt(0);
            out.writeInt(0);
            out.writeInt(0);
            out.writeInt(0);
            out.writeInt(1);
            out.writeInt(sequenceOffset);
            out.writeInt(1);
            out.writeInt(seqGroupOffset);
            out.writeInt(numParts);
            out.writeInt(textureOffset);
            out.writeInt(textureDataOffset);
            out.writeInt(numParts);
            out.writeInt(1);
            out.writeInt(skinOffset);
            out.writeInt(1);
            out.writeInt(bodyPartOffset);
            out.writeInt(0);
            out.writeInt(0);
            out.writeInt(0);
            out.writeInt(0);
            out.writeInt(0);
            out.writeInt(0);
            out.writeInt(0);
            out.writeInt(0);

            writeBone(out);
            writeSequence(out, animOffset);
            writeSequenceGroup(out);
            writeTextures(out, partTextures, partMips);
            writeSkin(out, numParts);
            writeBodyPart(out, numParts, modelOffset, meshOffset, vertInfoOffset,
                    vertexOffset, triIndexOffset);
            writeVertInfo(out, numVerts);
            writeVertices(out);
            writeTriangles(out, numParts);
            writeAnimBlock(out);
            return out.toByteArray();
        }
    }

    private TextureImage effectiveTexture(int partIndex) {
        if (!model.parts.isEmpty()) {
            ModelData.Part part = model.parts.get(Math.min(partIndex, model.parts.size() - 1));
            TextureImage found = textures.get(part.texture.toUpperCase(Locale.ROOT));
            if (found != null) {
                return found;
            }
        }
        return defaultTexture();
    }

    private TextureImage defaultTexture() {
        int size = DEFAULT_TEXTURE_SIZE;
        byte[] pixels = new byte[size * size];
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                int index = ((x / 8) + (y / 8)) % 2 == 0 ? 15 : 63;
                pixels[y * size + x] = (byte) index;
            }
        }
        int[] effectivePalette = stockPalette != null ? stockPalette : TextureImage.defaultPalette();
        return new TextureImage("DEV_TEXTURE", size, size, pixels, effectivePalette);
    }

    private void writeBone(LeOutputStream out) throws IOException {
        byte[] name = "Bone".getBytes(StandardCharsets.UTF_8);
        byte[] nameField = new byte[32];
        System.arraycopy(name, 0, nameField, 0, name.length);
        out.write(nameField);
        out.writeInt(-1);
        out.writeInt(0);
        for (int i = 0; i < 6; i++) {
            out.writeInt(-1);
        }
        for (int i = 0; i < 6; i++) {
            out.writeFloat(0);
        }
        for (int i = 0; i < 6; i++) {
            out.writeFloat(1);
        }
    }

    private void writeSequence(LeOutputStream out, int animOffset) throws IOException {
        byte[] label = "idle".getBytes(StandardCharsets.UTF_8);
        byte[] labelField = new byte[32];
        System.arraycopy(label, 0, labelField, 0, label.length);
        out.write(labelField);
        out.writeFloat(30.0f);
        out.writeInt(0);
        out.writeInt(0);
        out.writeInt(0);
        out.writeInt(0);
        out.writeInt(0);
        out.writeInt(1);
        out.writeInt(0);
        out.writeInt(0);
        out.writeInt(0);
        out.writeInt(0);
        out.writeFloats(0, 0, 0);
        out.writeInt(0);
        out.writeInt(0);
        out.writeFloats(bounds[0], bounds[1], bounds[2]);
        out.writeFloats(bounds[3], bounds[4], bounds[5]);
        out.writeInt(1);
        out.writeInt(animOffset);
        out.writeInt(0);
        out.writeInt(0);
        out.writeFloat(0);
        out.writeFloat(0);
        out.writeFloat(0);
        out.writeFloat(0);
        out.writeInt(0);
        out.writeInt(0);
        out.writeInt(0);
        out.writeInt(0);
        out.writeInt(0);
        out.writeInt(0);
    }

    private void writeSequenceGroup(LeOutputStream out) throws IOException {
        byte[] label = "default".getBytes(StandardCharsets.UTF_8);
        byte[] labelField = new byte[32];
        System.arraycopy(label, 0, labelField, 0, label.length);
        out.write(labelField);
        byte[] nameField = new byte[64];
        System.arraycopy(label, 0, nameField, 0, label.length);
        out.write(nameField);
        out.writeInt(0);
        out.writeInt(0);
    }

    private void writeTextures(LeOutputStream out, TextureImage[] partTextures,
                               byte[][] mips) throws IOException {
        int cursor = textureIndexEnd(partTextures.length);
        for (int i = 0; i < partTextures.length; i++) {
            TextureImage texture = partTextures[i];
            byte[] name = texture.name.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8);
            byte[] nameField = new byte[64];
            System.arraycopy(name, 0, nameField, 0, Math.min(63, name.length));
            out.write(nameField);
            out.writeInt(0);
            out.writeInt(texture.width);
            out.writeInt(texture.height);
            out.writeInt(cursor);
            cursor += mips[i].length;
        }
        for (byte[] mip : mips) {
            out.write(mip);
        }
    }

    private int textureIndexEnd(int numParts) {
        return HEADER_SIZE + BONE_SIZE + SEQUENCE_SIZE + SEQUENCE_GROUP_SIZE
                + numParts * TEXTURE_SIZE;
    }

    private void writeSkin(LeOutputStream out, int numParts) throws IOException {
        for (int i = 0; i < numParts; i++) {
            out.writeShort(i);
        }
    }

    private void writeBodyPart(LeOutputStream out, int numParts, int modelOffset,
                               int meshOffset, int vertInfoOffset, int vertexOffset,
                               int triIndexOffset) throws IOException {
        byte[] partName = "body".getBytes(StandardCharsets.UTF_8);
        byte[] partNameField = new byte[64];
        System.arraycopy(partName, 0, partNameField, 0, partName.length);
        out.write(partNameField);
        out.writeInt(1);
        out.writeInt(0);
        out.writeInt(modelOffset);

        byte[] modelName = model.name.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8);
        byte[] modelNameField = new byte[64];
        System.arraycopy(modelName, 0, modelNameField, 0, Math.min(63, modelName.length));
        out.write(modelNameField);
        out.writeInt(0);
        float maxExtent = 0;
        for (int i = 0; i < 3; i++) {
            maxExtent = Math.max(maxExtent, bounds[i + 3] - bounds[i]);
        }
        out.writeFloat(maxExtent / 2.0f);
        out.writeInt(numParts);
        out.writeInt(meshOffset);
        out.writeInt(numParts * VERTICES_PER_BOX);
        out.writeInt(vertInfoOffset);
        out.writeInt(vertexOffset);
        out.writeInt(0);
        out.writeInt(0);
        out.writeInt(0);
        out.writeInt(0);
        out.writeInt(0);

        for (int i = 0; i < numParts; i++) {
            out.writeInt(TRIANGLES_PER_BOX);
            out.writeInt(triIndexOffset + i * TRIANGLES_PER_BOX * 3 * 2);
            out.writeInt(i);
            out.writeInt(0);
            out.writeInt(0);
        }
    }

    private void writeVertInfo(LeOutputStream out, int numVerts) throws IOException {
        out.write(new byte[numVerts]);
    }

    private void writeVertices(LeOutputStream out) throws IOException {
        if (model.parts.isEmpty()) {
            return;
        }
        for (ModelData.Part part : model.parts) {
            float minX = part.minX();
            float minY = part.minY();
            float minZ = part.minZ();
            float maxX = part.maxX();
            float maxY = part.maxY();
            float maxZ = part.maxZ();
            float[][] corners = {
                    {minX, minY, minZ},
                    {maxX, minY, minZ},
                    {maxX, maxY, minZ},
                    {minX, maxY, minZ},
                    {minX, minY, maxZ},
                    {maxX, minY, maxZ},
                    {maxX, maxY, maxZ},
                    {minX, maxY, maxZ},
            };
            for (float[] corner : corners) {
                out.writeFloats(corner[0], corner[1], corner[2]);
            }
        }
    }

    private void writeTriangles(LeOutputStream out, int numParts) throws IOException {
        for (int i = 0; i < numParts; i++) {
            for (int[] face : BOX_FACES) {
                out.writeShort(face[0]);
                out.writeShort(face[1]);
                out.writeShort(face[2]);
                out.writeShort(face[0]);
                out.writeShort(face[2]);
                out.writeShort(face[3]);
            }
        }
    }

    private void writeAnimBlock(LeOutputStream out) throws IOException {
        out.writeShort(12);
        out.writeShort(14);
        out.writeShort(16);
        out.writeShort(18);
        out.writeShort(20);
        out.writeShort(22);
        for (int i = 0; i < 6; i++) {
            out.writeShort(0);
        }
    }

    private byte[] buildMipChain(TextureImage source) {
        int width = source.width;
        int height = source.height;
        byte[][] levels = new byte[MIP_LEVELS][];
        int total = 0;
        for (int level = 0; level < MIP_LEVELS; level++) {
            int levelWidth = Math.max(1, width >> level);
            int levelHeight = Math.max(1, height >> level);
            levels[level] = remapAndResize(source, levelWidth, levelHeight);
            total += levels[level].length;
        }
        byte[] result = new byte[total];
        int cursor = 0;
        for (byte[] level : levels) {
            System.arraycopy(level, 0, result, cursor, level.length);
            cursor += level.length;
        }
        return result;
    }

    private byte[] remapAndResize(TextureImage source, int width, int height) {
        byte[] result = new byte[width * height];
        int[] cache = new int[256];
        for (int i = 0; i < cache.length; i++) {
            cache[i] = -2;
        }
        float sx = source.width / (float) width;
        float sy = source.height / (float) height;
        for (int y = 0; y < height; y++) {
            int srcY = Math.min(source.height - 1, (int) ((y + 0.5f) * sy));
            for (int x = 0; x < width; x++) {
                int srcX = Math.min(source.width - 1, (int) ((x + 0.5f) * sx));
                int index = source.pixelIndex(srcX, srcY);
                if (stockPalette != null) {
                    if (cache[index] == -2) {
                        cache[index] = nearestStockIndex(source.palette[index]);
                    }
                    index = cache[index];
                }
                result[y * width + x] = (byte) index;
            }
        }
        return result;
    }

    private int nearestStockIndex(int argb) {
        int best = 0;
        long bestDistance = Long.MAX_VALUE;
        for (int i = 0; i < stockPalette.length; i++) {
            long distance = colorDistance(argb, stockPalette[i]);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = i;
            }
        }
        return best;
    }

    private static long colorDistance(int a, int b) {
        int dr = ((a >> 16) & 0xFF) - ((b >> 16) & 0xFF);
        int dg = ((a >> 8) & 0xFF) - ((b >> 8) & 0xFF);
        int db = (a & 0xFF) - (b & 0xFF);
        return (long) dr * dr + (long) dg * dg + (long) db * db;
    }

    private static final class LeOutputStream extends OutputStream {

        private final byte[] buffer;
        private int position;

        LeOutputStream(int size) {
            this.buffer = new byte[size];
        }

        byte[] toByteArray() {
            return buffer;
        }

        @Override
        public void write(int value) {
            buffer[position++] = (byte) value;
        }

        @Override
        public void write(byte[] data) {
            System.arraycopy(data, 0, buffer, position, data.length);
            position += data.length;
        }

        void writeAscii(String text) {
            for (int i = 0; i < text.length(); i++) {
                buffer[position++] = (byte) text.charAt(i);
            }
        }

        void writeInt(int value) {
            buffer[position++] = (byte) value;
            buffer[position++] = (byte) (value >> 8);
            buffer[position++] = (byte) (value >> 16);
            buffer[position++] = (byte) (value >> 24);
        }

        void writeShort(int value) {
            buffer[position++] = (byte) value;
            buffer[position++] = (byte) (value >> 8);
        }

        void writeFloat(float value) {
            writeInt(Float.floatToIntBits(value));
        }

        void writeFloats(float a, float b, float c) {
            writeFloat(a);
            writeFloat(b);
            writeFloat(c);
        }
    }
}
