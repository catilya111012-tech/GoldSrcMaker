import com.goldsrc.maker.scripting.BlockGraph;
import com.goldsrc.maker.scripting.BlockNode;
import com.goldsrc.maker.scripting.BlockType;
import com.goldsrc.maker.scripting.CodeGenerator;

import java.util.Map;

public class HybridExtraBlocksTest {

    private static int failures = 0;

    public static void main(String[] args) throws Exception {
        testNewEventsRoundTrip();
        testNewActionsRoundTrip();
        testIdempotentRegeneration();
        testMixedChainWithOldBlocks();

        if (failures == 0) {
            System.out.println("ALL EXTRA BLOCK TESTS PASSED");
        } else {
            System.out.println(failures + " FAILURES");
            System.exit(1);
        }
    }

    private static void check(boolean condition, String label) {
        if (condition) {
            System.out.println("[PASS] " + label);
        } else {
            failures++;
            System.out.println("[FAIL] " + label);
        }
    }

    private static BlockGraph applyParsed(BlockGraph graph, String text) {
        CodeGenerator.ParsedScript parsed = CodeGenerator.parse(text);
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
                    node = new BlockNode(id, region.type, 0, 0);
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
        return graph;
    }

    private static void testNewEventsRoundTrip() {
        BlockGraph graph = new BlockGraph();

        BlockNode use = graph.addNode(BlockType.ON_PLAYER_USE, 0, 0);
        use.setParam("target", "button_1");

        BlockNode death = graph.addNode(BlockType.ON_ENTITY_DEATH, 0, 0);
        death.setParam("target", "monster_zombie_1");

        String text = CodeGenerator.generate(graph);
        check(text.contains("gs_Event(\"on_player_use\", \"button_1\");"),
                "событие use сгенерировано");
        check(text.contains("gs_Event(\"on_entity_death\", \"monster_zombie_1\");"),
                "событие смерти сгенерировано");

        CodeGenerator.ParsedScript parsed = CodeGenerator.parse(text);
        CodeGenerator.ParsedRegion useRegion = parsed.regions.get(use.id);
        CodeGenerator.ParsedRegion deathRegion = parsed.regions.get(death.id);

        check(useRegion != null && useRegion.clean && useRegion.type == BlockType.ON_PLAYER_USE,
                "событие use распознано чистым");
        check("button_1".equals(useRegion.params.get("target")),
                "параметр target события use разобран");
        check(deathRegion != null && deathRegion.clean && deathRegion.type == BlockType.ON_ENTITY_DEATH,
                "событие смерти распознано чистым");
        check("monster_zombie_1".equals(deathRegion.params.get("target")),
                "параметр target события смерти разобран");
    }

    private static void testNewActionsRoundTrip() {
        BlockGraph graph = new BlockGraph();
        BlockNode start = graph.addNode(BlockType.ON_MAP_START, 0, 0);

        BlockNode variable = graph.addNode(BlockType.ACTION_SET_VARIABLE, 0, 0);
        variable.setParam("name", "coins");
        variable.setParam("value", "3");

        BlockNode delay = graph.addNode(BlockType.ACTION_DELAY, 0, 0);
        delay.setParam("seconds", "2.5");

        BlockNode health = graph.addNode(BlockType.ACTION_SET_PLAYER_HEALTH, 0, 0);
        health.setParam("amount", "150");

        BlockNode level = graph.addNode(BlockType.ACTION_CHANGE_LEVEL, 0, 0);
        level.setParam("map", "map2");

        start.nextId = variable.id;
        variable.nextId = delay.id;
        delay.nextId = health.id;
        health.nextId = level.id;

        String text = CodeGenerator.generate(graph);
        check(text.contains("gs_SetVariable(\"coins\", \"3\");"), "переменная сгенерирована");
        check(text.contains("gs_Delay(2.50);"), "пауза сгенерирована с форматом");
        check(text.contains("gs_SetPlayerHealth(150);"), "здоровье сгенерировано");
        check(text.contains("gs_ChangeLevel(\"map2\");"), "смена карты сгенерирована");

        CodeGenerator.ParsedScript parsed = CodeGenerator.parse(text);
        CodeGenerator.ParsedRegion variableRegion = parsed.regions.get(variable.id);
        CodeGenerator.ParsedRegion delayRegion = parsed.regions.get(delay.id);
        CodeGenerator.ParsedRegion healthRegion = parsed.regions.get(health.id);
        CodeGenerator.ParsedRegion levelRegion = parsed.regions.get(level.id);

        check(variableRegion.clean && "coins".equals(variableRegion.params.get("name"))
                && "3".equals(variableRegion.params.get("value")),
                "переменная разобрана обратно");
        check(delayRegion.clean && "2.50".equals(delayRegion.params.get("seconds")),
                "пауза разобрана обратно");
        check(healthRegion.clean && "150".equals(healthRegion.params.get("amount")),
                "здоровье разобрано обратно");
        check(levelRegion.clean && "map2".equals(levelRegion.params.get("map")),
                "смена карты разобрана обратно");
        check(variableRegion.nextId == delay.id && levelRegion.nextId == -1,
                "цепочка новых блоков восстановлена");
    }

    private static void testIdempotentRegeneration() {
        BlockGraph graph = new BlockGraph();
        BlockNode start = graph.addNode(BlockType.ON_MAP_START, 0, 0);
        BlockNode variable = graph.addNode(BlockType.ACTION_SET_VARIABLE, 0, 0);
        variable.setParam("name", "score");
        variable.setParam("value", "10");
        BlockNode delay = graph.addNode(BlockType.ACTION_DELAY, 0, 0);
        delay.setParam("seconds", "0.75");
        start.nextId = variable.id;
        variable.nextId = delay.id;

        String text1 = CodeGenerator.generate(graph);
        String text2 = CodeGenerator.generate(applyParsed(graph, text1));
        check(text1.equals(text2), "идемпотентная регенерация с новыми блоками");
    }

    private static void testMixedChainWithOldBlocks() {
        BlockGraph graph = BlockGraph.createDefault();
        BlockNode message = graph.findById(2);
        BlockNode variable = graph.addNode(BlockType.ACTION_SET_VARIABLE, 0, 0);
        variable.setParam("name", "visited");
        variable.setParam("value", "1");
        BlockNode sound = graph.addNode(BlockType.ACTION_PLAY_SOUND, 0, 0);

        message.nextId = variable.id;
        variable.nextId = sound.id;

        String text = CodeGenerator.generate(graph);
        CodeGenerator.ParsedScript parsed = CodeGenerator.parse(text);

        check(parsed.regions.get(2).clean, "старый блок сообщения остался чистым");
        check(parsed.regions.get(variable.id).clean, "новый блок переменной чист в смешанной цепочке");
        check(parsed.regions.get(sound.id).clean, "старый блок звука остался чистым");
        check(parsed.regions.get(2).nextId == variable.id
                        && parsed.regions.get(variable.id).nextId == sound.id,
                "смешанная цепочка восстановлена");
    }
}
