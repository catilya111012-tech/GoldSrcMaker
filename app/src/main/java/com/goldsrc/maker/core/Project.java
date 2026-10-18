package com.goldsrc.maker.core;

import java.io.File;

public class Project {

    private final String name;
    private final File dir;

    public Project(String name, File dir) {
        this.name = name;
        this.dir = dir;
    }

    public String getName() {
        return name;
    }

    public File getDir() {
        return dir;
    }

    public File getProjectJsonFile() {
        return new File(dir, "project.json");
    }

    public File getMapJsonFile() {
        return new File(dir, "map.json");
    }

    public File getMapFile() {
        return new File(dir, "main.map");
    }

    public File getScriptFile() {
        return new File(dir, "main.angelscript");
    }

    public File getGraphFile() {
        return new File(dir, "graph.json");
    }

    public boolean exists() {
        return dir != null && dir.isDirectory();
    }
}
