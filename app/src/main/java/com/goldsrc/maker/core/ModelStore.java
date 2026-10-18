package com.goldsrc.maker.core;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ModelStore {

    public static final String DIR_NAME = "models";
    public static final String SUFFIX = ".model.json";

    private final File projectDir;

    public ModelStore(File projectDir) {
        this.projectDir = projectDir;
    }

    public File getDir() {
        return new File(projectDir, DIR_NAME);
    }

    public File fileOf(String name) {
        return new File(getDir(), TextureImage.sanitizeName(name) + SUFFIX);
    }

    public boolean exists(String name) {
        return fileOf(name).isFile();
    }

    public List<String> list() {
        List<String> names = new ArrayList<>();
        File[] files = getDir().listFiles();
        if (files == null) {
            return names;
        }
        for (File file : files) {
            String fileName = file.getName();
            if (file.isFile() && fileName.endsWith(SUFFIX)) {
                names.add(fileName.substring(0, fileName.length() - SUFFIX.length()));
            }
        }
        Collections.sort(names);
        return names;
    }

    public ModelData load(String name) throws IOException {
        File file = fileOf(name);
        if (!file.isFile()) {
            return null;
        }
        try {
            return ModelData.fromJson(new JSONObject(readText(file)));
        } catch (JSONException e) {
            throw new IOException("Повреждённый JSON модели " + name + ": " + e.getMessage());
        }
    }

    public void save(ModelData model) throws IOException {
        if (!getDir().exists() && !getDir().mkdirs()) {
            throw new IOException("Не удалось создать каталог моделей: " + getDir().getAbsolutePath());
        }
        try {
            writeText(fileOf(model.name), model.toJson().toString(2));
        } catch (JSONException e) {
            throw new IOException("Ошибка сериализации модели: " + e.getMessage());
        }
    }

    private static String readText(File file) throws IOException {
        StringBuilder builder = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                new FileInputStream(file), StandardCharsets.UTF_8))) {
            String line;
            boolean first = true;
            while ((line = reader.readLine()) != null) {
                if (!first) {
                    builder.append('\n');
                }
                builder.append(line);
                first = false;
            }
        }
        return builder.toString();
    }

    private static void writeText(File file, String text) throws IOException {
        try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(
                new FileOutputStream(file), StandardCharsets.UTF_8))) {
            writer.write(text);
        }
    }

    public boolean delete(String name) {
        File file = fileOf(name);
        return !file.exists() || file.delete();
    }

    public ModelData loadOrCreateStarter(String starterName) throws IOException {
        String safeName = TextureImage.sanitizeName(starterName);
        ModelData existing = load(safeName);
        if (existing != null) {
            return existing;
        }
        ModelData model = ModelData.createDefault(safeName);
        save(model);
        return model;
    }
}
