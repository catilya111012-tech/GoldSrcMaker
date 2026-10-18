import com.goldsrc.maker.scripting.BlockGraph;
import com.goldsrc.maker.scripting.BlockNode;
import com.goldsrc.maker.scripting.BlockType;
import com.goldsrc.maker.scripting.ScriptSimulator;

import java.util.List;

public class ScriptSimulationTest {

    private static int failures = 0;

    public static void main(String[] args) throws Exception {
        testHappyPathWithVariableAndDelay();
        testConditionFalseStopsChain();
        testMultipleEventsSimulated();
        testEmptyGraphMessage();

        if (failures == 0) {
            System.out.println("ALL SIMULATION TESTS PASSED");
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

    private static boolean contains(List<String> log, String fragment) {
        for (String line : log) {
            if (line.contains(fragment)) {
                return true;
            }
        }
        return false;
    }

    private static BlockGraph buildConditionGraph(String variableValue, String conditionValue) {
        BlockGraph graph = new BlockGraph();
        BlockNode start = graph.addNode(BlockType.ON_MAP_START, 0, 0);
        BlockNode variable = graph.addNode(BlockType.ACTION_SET_VARIABLE, 0, 0);
        variable.setParam("name", "coins");
        variable.setParam("value", variableValue);
        BlockNode delay = graph.addNode(BlockType.ACTION_DELAY, 0, 0);
        delay.setParam("seconds", "2.5");
        BlockNode condition = graph.addNode(BlockType.CONDITION_IF, 0, 0);
        condition.setParam("variable", "coins");
        condition.setParam("operator", ">");
        condition.setParam("value", conditionValue);
        BlockNode message = graph.addNode(BlockType.ACTION_SHOW_MESSAGE, 0, 0);
        message.setParam("text", "У вас есть монеты");

        start.nextId = variable.id;
        variable.nextId = delay.id;
        delay.nextId = condition.id;
        condition.nextId = message.id;
        return graph;
    }

    private static void testHappyPathWithVariableAndDelay() {
        BlockGraph graph = buildConditionGraph("3", "2");
        List<String> log = ScriptSimulator.simulate(graph);

        check(contains(log, "=== Событие: старт карты (#1) ==="), "заголовок события в логе");
        check(contains(log, "переменная coins = 3"), "переменная применена");
        check(contains(log, "пауза 2.5 сек (t = 2.5)"), "пауза учтена в часах");
        check(contains(log, "условие coins > 2 -> истина"), "условие вычислено по переменной");
        check(contains(log, "сообщение: У вас есть монеты"), "цепочка дошла до сообщения");
        check(contains(log, "виртуальное время: 2.5 сек"), "итоговое виртуальное время");
    }

    private static void testConditionFalseStopsChain() {
        BlockGraph graph = buildConditionGraph("1", "2");
        List<String> log = ScriptSimulator.simulate(graph);

        check(contains(log, "условие coins > 2 -> ложь"), "условие вычислено как ложь");
        check(contains(log, "ветка не выполнена, цепочка остановлена"), "остановка отмечена в логе");
        check(!contains(log, "сообщение: У вас есть монеты"), "сообщение не выполнено");
    }

    private static void testMultipleEventsSimulated() {
        BlockGraph graph = new BlockGraph();
        BlockNode start = graph.addNode(BlockType.ON_MAP_START, 0, 0);
        BlockNode use = graph.addNode(BlockType.ON_PLAYER_USE, 0, 0);
        use.setParam("target", "button_1");
        BlockNode health = graph.addNode(BlockType.ACTION_SET_PLAYER_HEALTH, 0, 0);
        health.setParam("amount", "50");
        use.nextId = health.id;

        List<String> log = ScriptSimulator.simulate(graph);

        check(contains(log, "=== Событие: старт карты (#" + start.id + ") ==="),
                "первое событие симулировано");
        check(contains(log, "=== Событие: игрок нажал объект (use) (#" + use.id + ") ==="),
                "второе событие симулировано");
        check(contains(log, "игрок нажал: button_1"), "событие use в логе");
        check(contains(log, "здоровье игрока := 50"), "действие второго события выполнено");
    }

    private static void testEmptyGraphMessage() {
        List<String> log = ScriptSimulator.simulate(new BlockGraph());
        check(log.size() == 1 && log.get(0).contains("Нет событий"),
                "пустой граф даёт понятное сообщение");
    }
}
