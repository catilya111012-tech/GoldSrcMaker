package com.goldsrc.maker.core;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;

public final class TextureStore {

    public static final String WAD_NAME = "textures.wad";

    private final File wadFile;

    public TextureStore(File projectDir) {
        this.wadFile = new File(projectDir, WAD_NAME);
    }

    public File getWadFile() {
        return wadFile;
    }

    public boolean exists() {
        return wadFile.isFile();
    }

    public List<TextureImage> loadAll() throws IOException {
        if (!wadFile.isFile()) {
            return new ArrayList<>();
        }
        List<TextureImage> textures = new ArrayList<>();
        for (WadReader.WadLump lump : WadReader.readAllLumps(wadFile)) {
            TextureImage texture = WadReader.parseTextureLump(lump);
            if (texture != null) {
                textures.add(texture);
            }
        }
        return textures;
    }

    public TextureImage load(String name) throws IOException {
        if (!wadFile.isFile()) {
            return null;
        }
        return WadReader.readTexture(wadFile, name);
    }

    public boolean has(String name) throws IOException {
        return load(name) != null;
    }

    public void save(TextureImage texture) throws IOException {
        LinkedHashMap<String, TextureImage> map = new LinkedHashMap<>();
        for (TextureImage existing : loadAll()) {
            map.put(existing.name.toUpperCase(Locale.ROOT), existing);
        }
        map.put(texture.name.toUpperCase(Locale.ROOT), texture);
        WadWriter.write(wadFile, new ArrayList<>(map.values()));
    }

    public boolean delete(String name) throws IOException {
        LinkedHashMap<String, TextureImage> map = new LinkedHashMap<>();
        boolean removed = false;
        for (TextureImage existing : loadAll()) {
            if (existing.name.equalsIgnoreCase(name)) {
                removed = true;
                continue;
            }
            map.put(existing.name.toUpperCase(Locale.ROOT), existing);
        }
        if (removed) {
            WadWriter.write(wadFile, new ArrayList<>(map.values()));
        }
        return removed;
    }

    public int count() throws IOException {
        return loadAll().size();
    }
}
