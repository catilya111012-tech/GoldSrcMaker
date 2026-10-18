package com.goldsrc.maker.scripting;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class BlockGraph {

    public final List<BlockNode> nodes = new ArrayList<>();
    public String header = CodeGenerator.HEADER;
    public final LinkedHashMap<Integer, String> preludes = new LinkedHashMap<>();
    public String epilogue = "";

    public BlockNode findById(int id) {
        for (BlockNode node : nodes) {
            if (node.id == id) {
                return node;
            }
        }
        return null;
    }

    public int nextId() {
        int max = 0;
        for (BlockNode node : nodes) {
            max = Math.max(max, node.id);
        }
        return max + 1;
    }

    public BlockNode addNode(BlockType type, float x, float y) {
        BlockNode node = new BlockNode(nextId(), type, x, y);
        nodes.add(node);
        return node;
    }

    public void removeNode(int id) {
        for (BlockNode node : nodes) {
            if (node.nextId == id) {
                BlockNode removed = findById(id);
                node.nextId = removed == null ? -1 : removed.nextId;
            }
        }
        BlockNode removed = findById(id);
        if (removed != null) {
            nodes.remove(removed);
        }
        preludes.remove(id);
    }

    public BlockNode firstRoot() {
        for (BlockNode node : nodes) {
            if (node.type == BlockType.ON_MAP_START) {
                return node;
            }
        }
        for (BlockNode node : nodes) {
            if (node.type.kind == BlockType.Kind.EVENT) {
                return node;
            }
        }
        return null;
    }

    public void appendToChain(BlockNode root, BlockNode node) {
        if (root == null || node == null) {
            return;
        }
        BlockNode current = root;
        Set<Integer> visited = new HashSet<>();
        while (current.nextId >= 0 && !visited.contains(current.nextId)) {
            visited.add(current.id);
            BlockNode next = findById(current.nextId);
            if (next == null) {
                break;
            }
            current = next;
        }
        current.nextId = node.id;
    }

    public List<BlockNode> chainFrom(int startId) {
        List<BlockNode> chain = new ArrayList<>();
        Set<Integer> visited = new HashSet<>();
        int current = startId;
        while (current >= 0 && !visited.contains(current)) {
            visited.add(current);
            BlockNode node = findById(current);
            if (node == null) {
                break;
            }
            chain.add(node);
            current = node.nextId;
        }
        return chain;
    }

    public List<BlockNode> emissionOrder() {
        List<BlockNode> ordered = new ArrayList<>();
        Set<Integer> seen = new HashSet<>();
        for (BlockNode node : nodes) {
            if (node.type.kind == BlockType.Kind.EVENT) {
                for (BlockNode chained : chainFrom(node.id)) {
                    if (seen.add(chained.id)) {
                        ordered.add(chained);
                    }
                }
            }
        }
        for (BlockNode node : nodes) {
            if (seen.add(node.id)) {
                ordered.add(node);
            }
        }
        return ordered;
    }

    public JSONObject toJson() throws JSONException {
        JSONObject root = new JSONObject();
        root.put("header", header == null ? "" : header);
        root.put("epilogue", epilogue == null ? "" : epilogue);

        JSONObject preludeJson = new JSONObject();
        for (Map.Entry<Integer, String> entry : preludes.entrySet()) {
            preludeJson.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        root.put("preludes", preludeJson);

        JSONArray nodeArray = new JSONArray();
        for (BlockNode node : nodes) {
            nodeArray.put(node.toJson());
        }
        root.put("nodes", nodeArray);
        return root;
    }

    public static BlockGraph fromJson(JSONObject root) {
        BlockGraph graph = new BlockGraph();
        graph.header = root.optString("header", CodeGenerator.HEADER);
        graph.epilogue = root.optString("epilogue", "");

        JSONObject preludeJson = root.optJSONObject("preludes");
        if (preludeJson != null) {
            java.util.Iterator<String> keys = preludeJson.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                try {
                    graph.preludes.put(Integer.parseInt(key), preludeJson.optString(key));
                } catch (NumberFormatException ignored) {
                }
            }
        }

        JSONArray nodeArray = root.optJSONArray("nodes");
        if (nodeArray != null) {
            for (int i = 0; i < nodeArray.length(); i++) {
                graph.nodes.add(BlockNode.fromJson(nodeArray.optJSONObject(i)));
            }
        }
        return graph;
    }

    public static BlockGraph createDefault() {
        BlockGraph graph = new BlockGraph();
        BlockNode start = graph.addNode(BlockType.ON_MAP_START, 60, 60);
        BlockNode message = graph.addNode(BlockType.ACTION_SHOW_MESSAGE, 60, 240);
        message.setParam("text", "Добро пожаловать в GoldSrc Maker!");
        start.nextId = message.id;
        return graph;
    }
}
