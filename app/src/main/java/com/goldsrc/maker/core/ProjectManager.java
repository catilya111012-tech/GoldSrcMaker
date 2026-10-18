package com.goldsrc.maker.core;

import com.goldsrc.maker.editor.Brush;
import com.goldsrc.maker.editor.EntityData;
import com.goldsrc.maker.editor.MapData;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

public class ProjectManager {

    public static final String NAME_PATTERN = "[A-Za-z0-9_\\-]{1,48}";

    private final StorageManager storage;

    public ProjectManager(StorageManager storage) {
        this.storage = storage;
    }

    public Project createProject(String name) throws IOException, JSONException {
        validateName(name);
        File dir = new File(storage.getProjectsDir(), name);
        if (dir.exists()) {
            throw new IOException("Проект уже существует: " + name);
        }
        if (!dir.mkdirs()) {
            throw new IOException("Не удалось создать каталог проекта: " + dir.getAbsolutePath());
        }

        Project project = new Project(name, dir);

        JSONObject meta = new JSONObject();
        meta.put("format", 1);
        meta.put("name", name);
        meta.put("created", System.currentTimeMillis());
        writeText(project.getProjectJsonFile(), meta.toString(2));

        saveMap(project, buildStarterMap());
        return project;
    }

    public static MapData buildStarterMap() {
        MapData map = new MapData();

        map.brushes.add(new Brush(-256, -256, -16, 256, 256, 0, "NULL"));

        EntityData spawn = new EntityData("info_player_start");
        spawn.setOrigin(0, 0, 24);
        spawn.setKey("angle", "0");
        map.entities.add(spawn);

        EntityData light = new EntityData("light");
        light.setOrigin(0, 0, 160);
        light.setKey("light", "300");
        map.entities.add(light);

        return map;
    }

    public List<Project> listProjects() {
        List<Project> result = new ArrayList<>();
        File[] dirs = storage.getProjectsDir().listFiles();
        if (dirs == null) {
            return result;
        }
        Arrays.sort(dirs, new Comparator<File>() {
            @Override
            public int compare(File a, File b) {
                return a.getName().compareToIgnoreCase(b.getName());
            }
        });
        for (File dir : dirs) {
            if (dir.isDirectory()) {
                result.add(new Project(dir.getName(), dir));
            }
        }
        return result;
    }

    public Project load(String name) {
        File dir = new File(storage.getProjectsDir(), name);
        if (!dir.isDirectory()) {
            return null;
        }
        return new Project(name, dir);
    }

    public MapData loadMap(Project project) throws IOException, JSONException {
        File jsonFile = project.getMapJsonFile();
        if (!jsonFile.isFile()) {
            return new MapData();
        }
        return MapData.fromJson(new JSONObject(readText(jsonFile)));
    }

    public void saveMap(Project project, MapData map) throws IOException, JSONException {
        writeText(project.getMapJsonFile(), map.toJson().toString(2));
        exportMapFile(project, map);
    }

    public void exportMapFile(Project project, MapData map) throws IOException {
        File wadFile = new File(storage.findValveDir(), "halflife.wad");
        writeText(project.getMapFile(), map.toMapText(wadFile.getAbsolutePath(), projectWadPath(project)));
    }

    public static String projectWadPath(Project project) {
        File projectWad = new TextureStore(project.getDir()).getWadFile();
        return projectWad.isFile() ? projectWad.getAbsolutePath() : null;
    }

    private void validateName(String name) throws IOException {
        if (name == null || !name.matches(NAME_PATTERN)) {
            throw new IOException("Недопустимое имя проекта. Разрешены A-Z, a-z, 0-9, _ и -");
        }
    }

    public static String readText(File file) throws IOException {
        StringBuilder builder = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
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

    public static void writeText(File file, String text) throws IOException {
        File parent = file.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("Не удалось создать каталог: " + parent.getAbsolutePath());
        }
        try (OutputStreamWriter writer = new OutputStreamWriter(
                new FileOutputStream(file), StandardCharsets.UTF_8)) {
            writer.write(text);
        }
    }
}
