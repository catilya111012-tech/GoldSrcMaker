package com.goldsrc.maker.core;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class WadWriter {

    private static final int MIPTEX_TYPE = 0x43;
    private static final int MIPTEX_HEADER_SIZE = 40;
    private static final int NAME_LENGTH = 16;
    private static final int LUMP_ENTRY_SIZE = 32;
    private static final int MIP_LEVELS = 4;

    private WadWriter() {
    }

    public static void write(File outFile, List<TextureImage> textures) throws IOException {
        List<WadReader.WadLump> lumps = new ArrayList<>();
        for (TextureImage texture : textures) {
            lumps.add(new WadReader.WadLump(texture.name, MIPTEX_TYPE, buildMiptex(texture)));
        }
        writeFile(outFile, lumps);
    }

    public static int patch(File wadFile, List<TextureImage> replacements) throws IOException {
        LinkedHashMap<String, WadReader.WadLump> lumps = new LinkedHashMap<>();
        if (wadFile != null && wadFile.isFile()) {
            for (WadReader.WadLump lump : WadReader.readAllLumps(wadFile)) {
                lumps.put(lump.name.toUpperCase(Locale.ROOT), lump);
            }
        }
        int patched = 0;
        for (TextureImage texture : replacements) {
            lumps.put(texture.name.toUpperCase(Locale.ROOT),
                    new WadReader.WadLump(texture.name, MIPTEX_TYPE, buildMiptex(texture)));
            patched++;
        }
        writeFile(wadFile, new ArrayList<>(lumps.values()));
        return patched;
    }

    public static byte[] buildMiptex(TextureImage texture) {
        int[] levelSizes = new int[MIP_LEVELS];
        int[] levelWidths = new int[MIP_LEVELS];
        int[] levelHeights = new int[MIP_LEVELS];
        for (int level = 0; level < MIP_LEVELS; level++) {
            levelWidths[level] = Math.max(1, texture.width >> level);
            levelHeights[level] = Math.max(1, texture.height >> level);
            levelSizes[level] = levelWidths[level] * levelHeights[level];
        }

        int mipBytes = 0;
        for (int size : levelSizes) {
            mipBytes += size;
        }
        int paletteBytes = 2 + TextureImage.PALETTE_SIZE * 3;
        byte[] data = new byte[MIPTEX_HEADER_SIZE + mipBytes + paletteBytes];

        byte[] nameBytes = texture.name.getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(nameBytes, 0, data, 0, Math.min(nameBytes.length, NAME_LENGTH - 1));
        writeInt(data, 16, texture.width);
        writeInt(data, 20, texture.height);

        int cursor = MIPTEX_HEADER_SIZE;
        byte[] levelPixels = texture.pixels;
        int levelWidth = texture.width;
        int levelHeight = texture.height;
        Map<Integer, Integer> nearestCache = new HashMap<>();
        for (int level = 0; level < MIP_LEVELS; level++) {
            writeInt(data, 24 + level * 4, cursor);
            System.arraycopy(levelPixels, 0, data, cursor, levelSizes[level]);
            cursor += levelSizes[level];
            if (level + 1 < MIP_LEVELS) {
                byte[] next = downsample(levelPixels, levelWidth, levelHeight, texture.palette, nearestCache);
                levelPixels = next;
                levelWidth = levelWidths[level + 1];
                levelHeight = levelHeights[level + 1];
            }
        }

        writeShort(data, cursor, TextureImage.PALETTE_SIZE);
        cursor += 2;
        for (int i = 0; i < TextureImage.PALETTE_SIZE; i++) {
            int color = texture.palette[i];
            data[cursor++] = (byte) ((color >> 16) & 0xFF);
            data[cursor++] = (byte) ((color >> 8) & 0xFF);
            data[cursor++] = (byte) (color & 0xFF);
        }
        return data;
    }

    private static byte[] downsample(byte[] pixels, int width, int height,
                                     int[] palette, Map<Integer, Integer> nearestCache) {
        int newWidth = Math.max(1, width / 2);
        int newHeight = Math.max(1, height / 2);
        byte[] result = new byte[newWidth * newHeight];
        for (int y = 0; y < newHeight; y++) {
            for (int x = 0; x < newWidth; x++) {
                int redSum = 0;
                int greenSum = 0;
                int blueSum = 0;
                int samples = 0;
                for (int dy = 0; dy < 2; dy++) {
                    for (int dx = 0; dx < 2; dx++) {
                        int sx = Math.min(width - 1, x * 2 + dx);
                        int sy = Math.min(height - 1, y * 2 + dy);
                        int color = palette[pixels[sy * width + sx] & 0xFF];
                        redSum += (color >> 16) & 0xFF;
                        greenSum += (color >> 8) & 0xFF;
                        blueSum += color & 0xFF;
                        samples++;
                    }
                }
                int r = redSum / samples;
                int g = greenSum / samples;
                int b = blueSum / samples;
                result[y * newWidth + x] = (byte) nearestPaletteIndex(palette, r, g, b, nearestCache);
            }
        }
        return result;
    }

    private static int nearestPaletteIndex(int[] palette, int r, int g, int b, Map<Integer, Integer> cache) {
        int key = (r << 16) | (g << 8) | b;
        Integer cached = cache.get(key);
        if (cached != null) {
            return cached;
        }
        int bestIndex = 0;
        int bestDistance = Integer.MAX_VALUE;
        for (int i = 0; i < TextureImage.PALETTE_SIZE; i++) {
            int color = palette[i];
            int dr = ((color >> 16) & 0xFF) - r;
            int dg = ((color >> 8) & 0xFF) - g;
            int db = (color & 0xFF) - b;
            int distance = dr * dr + dg * dg + db * db;
            if (distance < bestDistance) {
                bestDistance = distance;
                bestIndex = i;
            }
        }
        cache.put(key, bestIndex);
        return bestIndex;
    }

    private static void writeFile(File outFile, List<WadReader.WadLump> lumps) throws IOException {
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        List<int[]> entries = new ArrayList<>();
        for (WadReader.WadLump lump : lumps) {
            byte[] data = lump.data;
            entries.add(new int[]{body.size(), data.length});
            body.write(data);
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write("WAD3".getBytes(StandardCharsets.US_ASCII));
        writeIntTo(out, lumps.size());
        writeIntTo(out, 12 + body.size());
        body.writeTo(out);
        for (int i = 0; i < lumps.size(); i++) {
            WadReader.WadLump lump = lumps.get(i);
            int[] entry = entries.get(i);
            writeIntTo(out, 12 + entry[0]);
            writeIntTo(out, entry[1]);
            writeIntTo(out, entry[1]);
            out.write(MIPTEX_TYPE);
            out.write(0);
            out.write(0);
            out.write(0);
            byte[] nameBytes = lump.name.getBytes(StandardCharsets.US_ASCII);
            byte[] padded = new byte[NAME_LENGTH];
            System.arraycopy(nameBytes, 0, padded, 0, Math.min(nameBytes.length, NAME_LENGTH));
            out.write(padded);
        }

        try (FileOutputStream fileStream = new FileOutputStream(outFile)) {
            out.writeTo(fileStream);
        }
    }

    private static void writeInt(byte[] data, int offset, int value) {
        data[offset] = (byte) (value & 0xFF);
        data[offset + 1] = (byte) ((value >> 8) & 0xFF);
        data[offset + 2] = (byte) ((value >> 16) & 0xFF);
        data[offset + 3] = (byte) ((value >> 24) & 0xFF);
    }

    private static void writeShort(byte[] data, int offset, int value) {
        data[offset] = (byte) (value & 0xFF);
        data[offset + 1] = (byte) ((value >> 8) & 0xFF);
    }

    private static void writeIntTo(ByteArrayOutputStream out, int value) {
        out.write(value & 0xFF);
        out.write((value >> 8) & 0xFF);
        out.write((value >> 16) & 0xFF);
        out.write((value >> 24) & 0xFF);
    }
}
