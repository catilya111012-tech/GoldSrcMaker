import com.goldsrc.maker.scripting.BlockGraph;
import com.goldsrc.maker.scripting.BlockNode;
import com.goldsrc.maker.scripting.BlockType;
import com.goldsrc.maker.scripting.CodeGenerator;

import java.util.Map;

public class HybridRoundTripTest {

    private static int failures = 0;

    public static void main(String[] args) throws Exception {
        testDefaultRoundTrip();
        testManualEditInsideRegion();
        testCustomBodyPreserved();
        testFreeSegmentPreserved();
        testJsonPersistence();
        testChainEdit();

        if (failures == 0) {
            System.out.println("ALL TESTS PASSED");
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

    private static void testDefaultRoundTrip() {
        BlockGraph graph = BlockGraph.createDefault();
        String text1 = CodeGenerator.generate(graph);

        CodeGenerator.ParsedScript parsed = CodeGenerator.parse(text1);
        check(parsed.regions.size() == 2, "регионов после генерации: " + parsed.regions.size());

        CodeGenerator.ParsedRegion start = parsed.regions.get(1);
        check(start != null && start.clean && start.type == BlockType.ON_MAP_START,
                "событие старта распознано как чистое");

        CodeGenerator.ParsedRegion message = parsed.regions.get(2);
        check(message != null && message.clean && message.type == BlockType.ACTION_SHOW_MESSAGE,
                "блок сообщения распознан как чистый");
        check("Добро пожаловать в GoldSrc Maker!".equals(message.params.get("text")),
                "параметр text разобран");
        check(start.nextId == 2, "цепочка nextId 1→2 разобрана");

        String text2 = CodeGenerator.generate(applyParsed(graph, text1));
        check(text1.equals(text2), "идемпотентная регенерация без изменений");
    }

    private static void testManualEditInsideRegion() {
        BlockGraph graph = BlockGraph.createDefault();
        String text = CodeGenerator.generate(graph);
        String edited = text.replace("Добро пожаловать в GoldSrc Maker!", "Привет из текста");

        applyParsed(graph, edited);

        check("Привет из текста".equals(graph.findById(2).getParam("text")),
                "правка внутри региона применилась к блоку");
        check(!graph.findById(2).userEdited, "регион остался чистым (не userEdited)");

        String regenerated = CodeGenerator.generate(graph);
        check(regenerated.contains("Привет из текста"), "регенерация сохранила правку");
    }

    private static void testCustomBodyPreserved() {
        String text = CodeGenerator.generate(BlockGraph.createDefault());
        String custom = text.replace(
                "    gs_ShowMessage(\"Добро пожаловать в GoldSrc Maker!\");\n    gs_EndChain();\n",
                "    int i = 42;\n    gs_ShowMessage(\"custom \" + i);\n");
        check(!custom.equals(text), "ручная замена тела блока выполнена");

        BlockGraph graph = applyParsed(BlockGraph.createDefault(), custom);
        BlockNode message = graph.findById(2);
        check(message.userEdited, "блок помечен userEdited");
        check(message.customBody != null && message.customBody.contains("int i = 42;"),
                "customBody захвачен");

        String regenerated = CodeGenerator.generate(graph);
        check(regenerated.contains("int i = 42;"), "ручной код сохранён при регенерации");
        check(regenerated.contains("gs_Event(\"on_map_start\");"),
                "соседний чистый регион не пострадал");
    }

    private static void testFreeSegmentPreserved() {
        String text = CodeGenerator.generate(BlockGraph.createDefault());
        String withFree = text.replace("// >>> block:2",
                "// мой ручной код между регионами\n// >>> block:2");
        check(!withFree.equals(text), "свободный сегмент вставлен");

        BlockGraph graph = applyParsed(BlockGraph.createDefault(), withFree);
        String regenerated = CodeGenerator.generate(graph);

        check(regenerated.contains("// мой ручной код между регионами"),
                "свободный текст между регионами сохранён");
        check(regenerated.indexOf("мой ручной код") < regenerated.indexOf("// >>> block:2"),
                "позиция свободного текста сохранена");
    }

    private static void testJsonPersistence() throws Exception {
        BlockGraph graph = BlockGraph.createDefault();
        graph.preludes.put(2, "// free segment\n");

        BlockGraph restored = BlockGraph.fromJson(graph.toJson());

        check(restored.nodes.size() == 2, "узлы восстановлены из JSON");
        check(restored.findById(1).nextId == 2, "цепочка восстановлена");
        check("// free segment\n".equals(restored.preludes.get(2)), "preludes восстановлены");
        check(restored.findById(2).getParam("text").startsWith("Добро пожаловать"),
                "параметры блока восстановлены");
        check(restored.findById(2).type == BlockType.ACTION_SHOW_MESSAGE, "тип блока восстановлен");
    }

    private static void testChainEdit() {
        BlockGraph graph = BlockGraph.createDefault();
        BlockNode sound = graph.addNode(BlockType.ACTION_PLAY_SOUND, 0, 0);
        graph.appendToChain(graph.firstRoot(), sound);

        String text = CodeGenerator.generate(graph);
        applyParsed(graph, text);

        check(graph.findById(2).nextId == sound.id, "цепочка удлинилась: сообщение → звук");
        check(graph.findById(sound.id).nextId == -1, "звук — конец цепочки");
        check(CodeGenerator.parse(CodeGenerator.generate(graph)).regions.get(sound.id).clean,
                "новый блок остаётся чистым после round-trip");
        check(CodeGenerator.generate(graph).contains("gs_PlaySound(\"ambience/water1.wav\", 1.00);"),
                "звук сгенерирован с нормализованной громкостью");
    }
}
