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
            if (numLumps <= 0 || numLumps > 200000) {
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

    private static int readLittleEndianInt(RandomAccessFile file) throws IOException {
        int b0 = file.read();
        int b1 = file.read();
        int b2 = file.read();
        int b3 = file.read();
        if (b0 < 0 || b1 < 0 || b2 < 0 || b3 < 0) {
            throw new IOException("Неожиданный конец WAD-файла");
        }
        return b0 | (b1 << 8) | (b2 << 16) | (b3 << 24);
    }

    private static String readName(byte[] lump) {
        int length = 0;
        while (length < NAME_LENGTH && lump[NAME_LENGTH + length] != 0) {
            length++;
        }
        return new String(lump, NAME_LENGTH, length, StandardCharsets.US_ASCII);
    }
}
