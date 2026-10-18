package com.goldsrc.maker.core;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class WadReader {

    private static final int LUMP_ENTRY_SIZE = 32;
    private static final int NAME_LENGTH = 16;
    private static final int MIPTEX_HEADER_SIZE = 40;
    private static final int PALETTE_BYTES = 2 + TextureImage.PALETTE_SIZE * 3;

    public static final class WadLump {
        public final String name;
        public final int type;
        public final byte[] data;

        public WadLump(String name, int type, byte[] data) {
            this.name = name;
            this.type = type;
            this.data = data;
        }
    }

    private WadReader() {
    }

    public static List<String> listTextures(File wadFile) throws IOException {
        if (wadFile == null || !wadFile.isFile()) {
            throw new IOException("WAD-файл не найден");
        }

        List<String> textures = new ArrayList<>();

        try (RandomAccessFile file = new RandomAccessFile(wadFile, "r")) {
            byte[] magicBytes = new byte[4];
            file.readFully(magicBytes);
            String magic = new String(magicBytes, StandardCharsets.US_ASCII);
            if (!"WAD3".equals(magic) && !"WAD2".equals(magic)) {
                throw new IOException("Файл не является WAD2/WAD3: " + magic);
            }

            int numLumps = readLittleEndianInt(file);
            int infoOffset = readLittleEndianInt(file);
            if (numLumps < 0 || numLumps > 200000) {
                throw new IOException("Некорректное число записей: " + numLumps);
            }

            file.seek(infoOffset);
            byte[] lump = new byte[LUMP_ENTRY_SIZE];
            for (int i = 0; i < numLumps; i++) {
                file.readFully(lump);
                int type = lump[12] & 0xFF;
                if (type != 0x43 && type != 0x44) {
                    continue;
                }
                String name = readName(lump);
                if (!name.isEmpty()) {
                    textures.add(name);
                }
            }
        }

        Set<String> unique = new LinkedHashSet<>(textures);
        List<String> result = new ArrayList<>(unique);
        Collections.sort(result, String.CASE_INSENSITIVE_ORDER);
        return result;
    }

    public static List<WadLump> readAllLumps(File wadFile) throws IOException {
        if (wadFile == null || !wadFile.isFile()) {
            throw new IOException("WAD-файл не найден");
        }
        List<WadLump> lumps = new ArrayList<>();
        try (RandomAccessFile file = new RandomAccessFile(wadFile, "r")) {
            readAndValidateHeader(file);
            int numLumps = readLittleEndianInt(file);
            int infoOffset = readLittleEndianInt(file);
            if (numLumps < 0 || numLumps > 200000) {
                throw new IOException("Некорректное число записей: " + numLumps);
            }
            file.seek(infoOffset);
            byte[] entries = new byte[numLumps * LUMP_ENTRY_SIZE];
            file.readFully(entries);

            for (int i = 0; i < numLumps; i++) {
                int base = i * LUMP_ENTRY_SIZE;
                long position = unsignedInt(entries, base);
                int size = (int) unsignedInt(entries, base + 8);
                int type = entries[base + 12] & 0xFF;
                String name = readName(entries, base);
                if (size < 0) {
                    size = 0;
                }
                long limit = file.length();
                if (position < 0 || position > limit) {
                    position = limit;
                }
                if (position + size > limit) {
                    size = (int) Math.max(0, limit - position);
                }
                byte[] data = new byte[size];
                if (size > 0) {
                    file.seek(position);
                    file.readFully(data);
                }
                lumps.add(new WadLump(name, type, data));
            }
        }
        return lumps;
    }

    public static TextureImage readTexture(File wadFile, String textureName) throws IOException {
        String wanted = TextureImage.sanitizeName(textureName);
        for (WadLump lump : readAllLumps(wadFile)) {
            if (lump.type != 0x43 && lump.type != 0x44) {
                continue;
            }
            if (!lump.name.equalsIgnoreCase(wanted) && !lump.name.equalsIgnoreCase(textureName)) {
                continue;
            }
            return parseMiptex(lump.name, lump.data);
        }
        return null;
    }

    public static TextureImage parseTextureLump(WadLump lump) throws IOException {
        if (lump == null || (lump.type != 0x43 && lump.type != 0x44)) {
            return null;
        }
        return parseMiptex(lump.name, lump.data);
    }

    public static int[] readPalette(File wadFile) throws IOException {
        for (WadLump lump : readAllLumps(wadFile)) {
            if (lump.type != 0x43 || lump.data.length < MIPTEX_HEADER_SIZE + PALETTE_BYTES) {
                continue;
            }
            int[] palette = extractPalette(lump.data);
            if (palette != null) {
                return palette;
            }
        }
        return null;
    }

    private static TextureImage parseMiptex(String name, byte[] data) throws IOException {
        if (data.length < MIPTEX_HEADER_SIZE) {
            throw new IOException("Ламп " + name + " слишком мал для miptex: " + data.length);
        }
        int width = (int) unsignedInt(data, 16);
        int height = (int) unsignedInt(data, 20);
        long offset0 = unsignedInt(data, 24);
        if (width <= 0 || height <= 0 || width > 4096 || height > 4096) {
            throw new IOException("Некорректный размер текстуры " + name + ": " + width + "x" + height);
        }
        long pixelBytes = (long) width * height;
        if (offset0 < 0 || offset0 + pixelBytes > data.length) {
            throw new IOException("Пиксельные данные " + name + " выходят за пределы лампа");
        }
        byte[] pixels = new byte[(int) pixelBytes];
        System.arraycopy(data, (int) offset0, pixels, 0, pixels.length);

        int[] palette = extractPalette(data);
        if (palette == null) {
            palette = TextureImage.defaultPalette();
        }
        return new TextureImage(name, width, height, pixels, palette);
    }

    private static int[] extractPalette(byte[] data) {
        int paletteOffset = data.length - PALETTE_BYTES;
        if (paletteOffset <= 0) {
            return null;
        }
        int colors = readLittleEndianShort(data, paletteOffset);
        if (colors < 1 || colors > TextureImage.PALETTE_SIZE) {
            return null;
        }
        int[] palette = new int[TextureImage.PALETTE_SIZE];
        for (int i = 0; i < TextureImage.PALETTE_SIZE; i++) {
            int base = paletteOffset + 2 + i * 3;
            int r = data[base] & 0xFF;
            int g = data[base + 1] & 0xFF;
            int b = data[base + 2] & 0xFF;
            palette[i] = 0xFF000000 | (r << 16) | (g << 8) | b;
        }
        return palette;
    }

    private static void readAndValidateHeader(RandomAccessFile file) throws IOException {
        byte[] magicBytes = new byte[4];
        file.readFully(magicBytes);
        String magic = new String(magicBytes, StandardCharsets.US_ASCII);
        if (!"WAD3".equals(magic) && !"WAD2".equals(magic)) {
            throw new IOException("Файл не является WAD2/WAD3: " + magic);
        }
    }

    private static long unsignedInt(byte[] data, int offset) {
        return (data[offset] & 0xFFL)
                | ((data[offset + 1] & 0xFFL) << 8)
                | ((data[offset + 2] & 0xFFL) << 16)
                | ((data[offset + 3] & 0xFFL) << 24);
    }

    private static int readLittleEndianShort(byte[] data, int offset) {
        return (data[offset] & 0xFF) | ((data[offset + 1] & 0xFF) << 8);
    }

    private static int readLittleEndianInt(RandomAccessFile file) throws IOException {        int b0 = file.read();
        int b1 = file.read();
        int b2 = file.read();
        int b3 = file.read();
        if (b0 < 0 || b1 < 0 || b2 < 0 || b3 < 0) {
            throw new IOException("Неожиданный конец WAD-файла");
        }
        return b0 | (b1 << 8) | (b2 << 16) | (b3 << 24);
    }

    private static String readName(byte[] lump) {
        return readName(lump, 0);
    }

    private static String readName(byte[] data, int base) {
        int offset = base + NAME_LENGTH;
        int length = 0;
        while (length < NAME_LENGTH && data[offset + length] != 0) {
            length++;
        }
        return new String(data, offset, length, StandardCharsets.US_ASCII);
    }
}
