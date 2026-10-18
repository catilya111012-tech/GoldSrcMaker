package com.goldsrc.maker.scripting;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class CodeGenerator {

    public static final String HEADER =
            "// GoldSrc Mobile Game Maker - hybrid script\n" +
            "// API: gs_Event, gs_RunNext, gs_EndChain, gs_CheckCondition,\n" +
            "//      gs_SpawnEntity, gs_SetKeyValue, gs_GiveItem, gs_PlaySound,\n" +
            "//      gs_UseTarget, gs_ShowMessage, gs_Teleport\n";

    private static final Pattern REGION = Pattern.compile(
            "// >>> block:(\\d+)\\n(.*?)// <<< block:\\1\\n", Pattern.DOTALL);

    private static final Pattern FUNCTION = Pattern.compile(
            "void gs_block_(\\d+)\\(\\) \\{\\n(.*)\\n\\}\\n", Pattern.DOTALL);

    private static final Pattern P_EVENT = Pattern.compile(
            "^gs_Event\\(\"([a-z_]+)\"(?:, \"([^\"]*)\")?\\);$");
    private static final Pattern P_RUN = Pattern.compile("^gs_RunNext\\((\\d+)\\);$");
    private static final Pattern P_END = Pattern.compile("^gs_EndChain\\(\\);$");
    private static final Pattern P_COND = Pattern.compile(
            "^if \\(gs_CheckCondition\\(\"([^\"]*)\", \"([^\"]*)\", \"([^\"]*)\"\\)\\) \\{$");
    private static final Pattern P_SPAWN = Pattern.compile(
            "^gs_SpawnEntity\\(\"([^\"]*)\", \"([^\"]*)\", \"([^\"]*)\"\\);$");
    private static final Pattern P_SETKV = Pattern.compile(
            "^gs_SetKeyValue\\(\"([^\"]*)\", \"([^\"]*)\", \"([^\"]*)\"\\);$");
    private static final Pattern P_GIVE = Pattern.compile(
            "^gs_GiveItem\\(\"([^\"]*)\", (\\d+)\\);$");
    private static final Pattern P_SOUND = Pattern.compile(
            "^gs_PlaySound\\(\"([^\"]*)\", ([0-9.]+)\\);$");
    private static final Pattern P_USE = Pattern.compile(
            "^gs_UseTarget\\(\"([^\"]*)\"\\);$");
    private static final Pattern P_MSG = Pattern.compile(
            "^gs_ShowMessage\\(\"([^\"]*)\"\\);$");
    private static final Pattern P_TP = Pattern.compile(
            "^gs_Teleport\\(\"([^\"]*)\"\\);$");

    private static final int NO_NEXT = Integer.MIN_VALUE;

    private CodeGenerator() {
    }

    public static final class ParsedRegion {
        public int id;
        public BlockType type;
        public int nextId = -1;
        public boolean clean;
        public String customBody;
        public final LinkedHashMap<String, String> params = new LinkedHashMap<>();
    }

    public static final class ParsedScript {
        public String header = HEADER;
        public final LinkedHashMap<Integer, ParsedRegion> regions = new LinkedHashMap<>();
        public final LinkedHashMap<Integer, String> preludes = new LinkedHashMap<>();
        public String epilogue = "";
    }

    public static String generate(BlockGraph graph) {
        StringBuilder builder = new StringBuilder();
        String header = graph.header == null || graph.header.isEmpty() ? HEADER : graph.header;
        builder.append(ensureNewline(header));

        for (BlockNode node : graph.emissionOrder()) {
            String prelude = graph.preludes.get(node.id);
            if (prelude != null && !prelude.isEmpty()) {
                builder.append(ensureNewline(prelude));
            }
            builder.append("// >>> block:").append(node.id).append("\n");
            builder.append(renderNode(node));
            builder.append("// <<< block:").append(node.id).append("\n");
        }

        if (graph.epilogue != null && !graph.epilogue.isEmpty()) {
            builder.append(ensureNewline(graph.epilogue));
        }
        return builder.toString();
    }

    private static String renderNode(BlockNode node) {
        StringBuilder builder = new StringBuilder();
        builder.append("void gs_block_").append(node.id).append("() {\n");
        if (node.userEdited && node.customBody != null) {
            builder.append(node.customBody);
            if (!node.customBody.endsWith("\n")) {
                builder.append("\n");
            }
        } else {
            for (String line : buildBody(node)) {
                builder.append("    ").append(line).append("\n");
            }
        }
        builder.append("}\n");
        return builder.toString();
    }

    private static List<String> buildBody(BlockNode node) {
        List<String> lines = new ArrayList<>();
        boolean condition = false;

        switch (node.type) {
            case ON_MAP_START:
                lines.add("gs_Event(\"on_map_start\");");
                break;
            case ON_PLAYER_TOUCH:
                lines.add("gs_Event(\"on_player_touch\", " + quote(node.getParam("target")) + ");");
                break;
            case ON_TRIGGER_ENTER:
                lines.add("gs_Event(\"on_trigger_enter\", " + quote(node.getParam("trigger")) + ");");
                break;
            case ACTION_SPAWN_ENTITY:
                lines.add("gs_SpawnEntity(" + quote(node.getParam("classname")) + ", "
                        + quote(node.getParam("origin")) + ", "
                        + quote(node.getParam("targetname")) + ");");
                break;
            case ACTION_SET_KEYVALUE:
                lines.add("gs_SetKeyValue(" + quote(node.getParam("target")) + ", "
                        + quote(node.getParam("key")) + ", "
                        + quote(node.getParam("value")) + ");");
                break;
            case ACTION_GIVE_ITEM:
                lines.add("gs_GiveItem(" + quote(node.getParam("item")) + ", "
                        + intOrDefault(node.getParam("amount"), 1) + ");");
                break;
            case ACTION_PLAY_SOUND:
                lines.add("gs_PlaySound(" + quote(node.getParam("sound")) + ", "
                        + String.format(Locale.US, "%.2f", floatOrDefault(node.getParam("volume"), 1.0f)) + ");");
                break;
            case ACTION_OPEN_DOOR:
                lines.add("gs_UseTarget(" + quote(node.getParam("target")) + ");");
                break;
            case ACTION_SHOW_MESSAGE:
                lines.add("gs_ShowMessage(" + quote(node.getParam("text")) + ");");
                break;
            case ACTION_TELEPORT:
                lines.add("gs_Teleport(" + quote(node.getParam("destination")) + ");");
                break;
            case ACTION_END:
                lines.add("gs_EndChain();");
                break;
            case CONDITION_IF:
                condition = true;
                lines.add("if (gs_CheckCondition(" + quote(node.getParam("variable")) + ", "
                        + quote(node.getParam("operator")) + ", "
                        + quote(node.getParam("value")) + ")) {");
                lines.add("    " + chainTail(node.nextId));
                lines.add("}");
                break;
            default:
                break;
        }

        if (!condition && node.type != BlockType.ACTION_END) {
            lines.add(chainTail(node.nextId));
        }
        return lines;
    }

    private static String chainTail(int nextId) {
        if (nextId >= 0) {
            return "gs_RunNext(" + nextId + ");";
        }
        return "gs_EndChain();";
    }

    private static String quote(String value) {
        if (value == null) {
            value = "";
        }
        String sanitized = value
                .replace('\\', '/')
                .replace('"', '\'')
                .replace('\n', ' ')
                .replace('\r', ' ');
        return "\"" + sanitized + "\"";
    }

    private static int intOrDefault(String value, int fallback) {
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static float floatOrDefault(String value, float fallback) {
        try {
            return Float.parseFloat(value.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static String ensureNewline(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        if (text.endsWith("\n")) {
            return text;
        }
        return text + "\n";
    }

    public static ParsedScript parse(String text) {
        ParsedScript parsed = new ParsedScript();
        if (text == null) {
            return parsed;
        }

        Matcher matcher = REGION.matcher(text);
        int lastEnd = 0;
        boolean firstRegion = true;

        while (matcher.find()) {
            String gap = text.substring(lastEnd, matcher.start());
            int id = Integer.parseInt(matcher.group(1));
            if (firstRegion) {
                parsed.header = gap;
                firstRegion = false;
            } else {
                parsed.preludes.put(id, gap);
            }
            parsed.regions.put(id, parseRegion(id, matcher.group(2)));
            lastEnd = matcher.end();
        }

        if (firstRegion) {
            parsed.header = text;
            return parsed;
        }
        parsed.epilogue = text.substring(lastEnd);
        return parsed;
    }

    private static ParsedRegion parseRegion(int id, String regionText) {
        ParsedRegion region = new ParsedRegion();
        region.id = id;

        Matcher functionMatcher = FUNCTION.matcher(regionText);
        if (!functionMatcher.matches()) {
            region.clean = false;
            region.customBody = regionText;
            return region;
        }

        String inner = functionMatcher.group(2);
        List<String> lines = new ArrayList<>();
        for (String rawLine : inner.split("\n", -1)) {
            String trimmed = rawLine.trim();
            if (!trimmed.isEmpty()) {
                lines.add(trimmed);
            }
        }

        if (lines.isEmpty()) {
            region.clean = false;
            region.customBody = inner;
            return region;
        }

        String first = lines.get(0);

        Matcher eventMatcher = P_EVENT.matcher(first);
        if (eventMatcher.matches()) {
            BlockType type = eventTypeFor(eventMatcher.group(1));
            if (type == null || lines.size() != 2) {
                return asCustom(region, inner);
            }
            int next = parseRunOrEnd(lines.get(1));
            if (next == NO_NEXT) {
                return asCustom(region, inner);
            }
            region.type = type;
            if (eventMatcher.group(2) != null) {
                if (type == BlockType.ON_PLAYER_TOUCH) {
                    region.params.put("target", eventMatcher.group(2));
                } else if (type == BlockType.ON_TRIGGER_ENTER) {
                    region.params.put("trigger", eventMatcher.group(2));
                }
            }
            region.nextId = next;
            region.clean = true;
            return region;
        }

        Matcher condMatcher = P_COND.matcher(first);
        if (condMatcher.matches()) {
            if (lines.size() != 3 || !lines.get(2).equals("}")) {
                return asCustom(region, inner);
            }
            int next = parseRunOrEnd(lines.get(1));
            if (next == NO_NEXT) {
                return asCustom(region, inner);
            }
            region.type = BlockType.CONDITION_IF;
            region.params.put("variable", condMatcher.group(1));
            region.params.put("operator", condMatcher.group(2));
            region.params.put("value", condMatcher.group(3));
            region.nextId = next;
            region.clean = true;
            return region;
        }

        if (lines.size() == 1) {
            if (matchAction(first, region) && region.type == BlockType.ACTION_END) {
                region.nextId = -1;
                region.clean = true;
                return region;
            }
            return asCustom(region, inner);
        }

        if (lines.size() != 2) {
            return asCustom(region, inner);
        }
        int next = parseRunOrEnd(lines.get(1));
        if (next == NO_NEXT) {
            return asCustom(region, inner);
        }
        if (!matchAction(first, region)) {
            return asCustom(region, inner);
        }
        region.nextId = next;
        region.clean = true;
        return region;
    }

    private static ParsedRegion asCustom(ParsedRegion region, String inner) {
        region.clean = false;
        region.customBody = inner;
        return region;
    }

    private static boolean matchAction(String line, ParsedRegion region) {
        Matcher matcher;

        matcher = P_SPAWN.matcher(line);
        if (matcher.matches()) {
            region.type = BlockType.ACTION_SPAWN_ENTITY;
            region.params.put("classname", matcher.group(1));
            region.params.put("origin", matcher.group(2));
            region.params.put("targetname", matcher.group(3));
            return true;
        }

        matcher = P_SETKV.matcher(line);
        if (matcher.matches()) {
            region.type = BlockType.ACTION_SET_KEYVALUE;
            region.params.put("target", matcher.group(1));
            region.params.put("key", matcher.group(2));
            region.params.put("value", matcher.group(3));
            return true;
        }

        matcher = P_GIVE.matcher(line);
        if (matcher.matches()) {
            region.type = BlockType.ACTION_GIVE_ITEM;
            region.params.put("item", matcher.group(1));
            region.params.put("amount", matcher.group(2));
            return true;
        }

        matcher = P_SOUND.matcher(line);
        if (matcher.matches()) {
            region.type = BlockType.ACTION_PLAY_SOUND;
            region.params.put("sound", matcher.group(1));
            region.params.put("volume", matcher.group(2));
            return true;
        }

        matcher = P_USE.matcher(line);
        if (matcher.matches()) {
            region.type = BlockType.ACTION_OPEN_DOOR;
            region.params.put("target", matcher.group(1));
            return true;
        }

        matcher = P_MSG.matcher(line);
        if (matcher.matches()) {
            region.type = BlockType.ACTION_SHOW_MESSAGE;
            region.params.put("text", matcher.group(1));
            return true;
        }

        matcher = P_TP.matcher(line);
        if (matcher.matches()) {
            region.type = BlockType.ACTION_TELEPORT;
            region.params.put("destination", matcher.group(1));
            return true;
        }

        if (P_END.matcher(line).matches()) {
            region.type = BlockType.ACTION_END;
            return true;
        }

        return false;
    }

    private static int parseRunOrEnd(String line) {
        Matcher runMatcher = P_RUN.matcher(line);
        if (runMatcher.matches()) {
            return Integer.parseInt(runMatcher.group(1));
        }
        if (P_END.matcher(line).matches()) {
            return -1;
        }
        return NO_NEXT;
    }

    private static BlockType eventTypeFor(String eventName) {
        for (BlockType type : BlockType.values()) {
            if (type.kind == BlockType.Kind.EVENT && eventName.equals(type.eventName)) {
                return type;
            }
        }
        return null;
    }
}
