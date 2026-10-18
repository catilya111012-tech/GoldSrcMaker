package com.goldsrc.maker.scripting;

import android.content.Context;

import com.goldsrc.maker.core.Project;
import com.goldsrc.maker.core.ProjectManager;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;

public class HybridScriptEngine {

    private static boolean runtimeInitialized = false;
    private static String runtimeInfo = "java-only";

    private final Project project;
    private BlockGraph graph;
    private String scriptText;

    public static void initRuntime(Context context) {
        if (runtimeInitialized) {
            return;
        }
        runtimeInitialized = true;
        try {
            System.loadLibrary("goldsrc_native");
            runtimeInfo = nativeRuntimeInfo();
        } catch (UnsatisfiedLinkError e) {
            runtimeInfo = "java-only";
        } catch (SecurityException e) {
            runtimeInfo = "java-only";
        }
    }

    public static String getRuntimeInfo() {
        return runtimeInfo;
    }

    public HybridScriptEngine(Context context, Project project) throws IOException, JSONException {
        this.project = project;

        File graphFile = project.getGraphFile();
        if (graphFile.isFile()) {
            graph = BlockGraph.fromJson(new JSONObject(ProjectManager.readText(graphFile)));
        } else {
            graph = BlockGraph.createDefault();
        }

        File scriptFile = project.getScriptFile();
        if (scriptFile.isFile()) {
            scriptText = ProjectManager.readText(scriptFile);
        } else {
            scriptText = CodeGenerator.generate(graph);
        }

        saveAll();
    }

    public BlockGraph getGraph() {
        return graph;
    }

    public String getScriptText() {
        return scriptText;
    }

    public Project getProject() {
        return project;
    }

    public BlockNode addBlock(BlockType type) {
        float baseY = 60 + graph.nodes.size() * 150f;
        BlockNode node = graph.addNode(type, 60, baseY);

        if (type.kind != BlockType.Kind.EVENT) {
            BlockNode root = graph.firstRoot();
            if (root == null) {
                root = graph.addNode(BlockType.ON_MAP_START, 60, 60);
                node.x = 60;
                node.y = 210;
            }
            graph.appendToChain(root, node);
        }

        syncGraphToText();
        return node;
    }

    public void removeBlock(int id) {
        graph.removeNode(id);
        syncGraphToText();
    }

    public void setParam(int nodeId, String name, String value) {
        BlockNode node = graph.findById(nodeId);
        if (node == null) {
            return;
        }
        node.setParam(name, value);
        syncGraphToText();
    }

    public void onNodeMoved() {
        saveAllQuiet();
    }

    public String syncGraphToText() {
        scriptText = CodeGenerator.generate(graph);
        saveAllQuiet();
        return scriptText;
    }

    public void syncTextToGraph(String newText) {
        scriptText = newText;
        CodeGenerator.ParsedScript parsed = CodeGenerator.parse(newText);

        graph.header = parsed.header;
        graph.preludes.clear();
        graph.preludes.putAll(parsed.preludes);
        graph.epilogue = parsed.epilogue;

        for (Map.Entry<Integer, CodeGenerator.ParsedRegion> entry : parsed.regions.entrySet()) {
            int id = entry.getKey();
            CodeGenerator.ParsedRegion region = entry.getValue();
            BlockNode node = graph.findById(id);

            if (region.clean) {
                if (region.type == null) {
                    continue;
                }
                if (node == null) {
                    node = new BlockNode(id, region.type, 60, 60 + graph.nodes.size() * 150f);
                    graph.nodes.add(node);
                }
                node.type = region.type;
                for (Map.Entry<String, String> param : region.params.entrySet()) {
                    node.setParam(param.getKey(), param.getValue());
                }
                node.nextId = region.nextId;
                node.userEdited = false;
                node.customBody = null;
            } else if (node != null) {
                node.userEdited = true;
                node.customBody = region.customBody;
            }
        }

        saveAllQuiet();
    }

    public List<String> simulate() {
        return ScriptSimulator.simulate(graph);
    }

    public void saveAll() throws IOException {
        ProjectManager.writeText(project.getScriptFile(), scriptText);
        saveGraphJson();
    }

    private void saveAllQuiet() {
        try {
            saveAll();
        } catch (IOException ignored) {
        }
    }

    private void saveGraphJson() throws IOException {
        try {
            ProjectManager.writeText(project.getGraphFile(), graph.toJson().toString(2));
        } catch (JSONException e) {
            throw new IOException(e);
        }
    }

    private static native String nativeRuntimeInfo();
}
