package com.goldsrc.maker.scripting;

import android.content.Context;

import com.goldsrc.maker.core.Project;
import com.goldsrc.maker.core.ProjectManager;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
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
        List<String> log = new ArrayList<>();
        Map<String, String> variables = new HashMap<>();
        BlockNode start = graph.firstRoot();
        if (start == null) {
            log.add("Нет стартового события ON_MAP_START.");
            return log;
        }
        executeChain(start.id, variables, log);
        return log;
    }

    private void executeChain(int startId, Map<String, String> variables, List<String> log) {
        int current = startId;
        int guard = 0;
        while (current >= 0 && guard++ < 512) {
            BlockNode node = graph.findById(current);
            if (node == null) {
                return;
            }

            switch (node.type) {
                case ON_MAP_START:
                    log.add("[событие] старт карты");
                    break;
                case ON_PLAYER_TOUCH:
                    log.add("[событие] игрок коснулся: " + node.getParam("target"));
                    break;
                case ON_TRIGGER_ENTER:
                    log.add("[событие] вход в триггер: " + node.getParam("trigger"));
                    break;
                case ACTION_SPAWN_ENTITY:
                    log.add("создать " + node.getParam("classname") + " в точке " + node.getParam("origin"));
                    break;
                case ACTION_SET_KEYVALUE:
                    variables.put(node.getParam("target") + "." + node.getParam("key"), node.getParam("value"));
                    log.add("задать " + node.getParam("target") + "." + node.getParam("key")
                            + " = " + node.getParam("value"));
                    break;
                case ACTION_GIVE_ITEM:
                    log.add("выдать " + node.getParam("item") + " x" + node.getParam("amount"));
                    break;
                case ACTION_PLAY_SOUND:
                    log.add("звук " + node.getParam("sound") + " громкость " + node.getParam("volume"));
                    break;
                case ACTION_OPEN_DOOR:
                    log.add("активировать " + node.getParam("target"));
                    break;
                case ACTION_SHOW_MESSAGE:
                    log.add("сообщение: " + node.getParam("text"));
                    break;
                case ACTION_TELEPORT:
                    log.add("телепорт к " + node.getParam("destination"));
                    break;
                case ACTION_END:
                    log.add("конец цепочки");
                    return;
                case CONDITION_IF:
                    String left = variables.containsKey(node.getParam("variable"))
                            ? variables.get(node.getParam("variable"))
                            : node.getParam("variable");
                    boolean pass = evaluate(left, node.getParam("operator"), node.getParam("value"));
                    log.add("условие " + node.getParam("variable") + " " + node.getParam("operator")
                            + " " + node.getParam("value") + " -> " + (pass ? "истина" : "ложь"));
                    if (!pass) {
                        return;
                    }
                    break;
                default:
                    break;
            }
            current = node.nextId;
        }
    }

    private static boolean evaluate(String left, String operator, String right) {
        try {
            double a = Double.parseDouble(left.trim());
            double b = Double.parseDouble(right.trim());
            switch (operator) {
                case "==":
                    return a == b;
                case "!=":
                    return a != b;
                case ">":
                    return a > b;
                case "<":
                    return a < b;
                case ">=":
                    return a >= b;
                case "<=":
                    return a <= b;
                default:
                    return false;
            }
        } catch (NumberFormatException ignored) {
        }
        switch (operator) {
            case "==":
                return left.equals(right);
            case "!=":
                return !left.equals(right);
            default:
                return false;
        }
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
