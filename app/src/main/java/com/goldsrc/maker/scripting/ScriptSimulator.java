package com.goldsrc.maker.scripting;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class ScriptSimulator {

    private static final int STEP_GUARD = 512;

    private ScriptSimulator() {
    }

    public static List<String> simulate(BlockGraph graph) {
        List<String> log = new ArrayList<>();
        List<BlockNode> events = new ArrayList<>();
        for (BlockNode node : graph.nodes) {
            if (node.type.kind == BlockType.Kind.EVENT) {
                events.add(node);
            }
        }

        if (events.isEmpty()) {
            log.add("Нет событий в графе. Добавьте «Событие: старт карты».");
            return log;
        }

        double clock = 0;
        int totalSteps = 0;
        for (BlockNode event : events) {
            log.add("=== " + event.type.title + " (#" + event.id + ") ===");
            Map<String, String> variables = new HashMap<>();
            double[] clockRef = new double[]{clock};
            int[] stepsRef = new int[]{0};
            executeChain(graph, event.id, variables, log, clockRef, stepsRef);
            clock = clockRef[0];
            totalSteps += stepsRef[0];
        }

        if (clock > 0) {
            log.add(String.format(Locale.US,
                    "виртуальное время: %.1f сек, шагов: %d", clock, totalSteps));
        }
        return log;
    }

    private static void executeChain(BlockGraph graph, int startId, Map<String, String> variables,
                                     List<String> log, double[] clock, int[] steps) {
        int current = startId;
        int guard = 0;
        while (current >= 0 && guard++ < STEP_GUARD) {
            BlockNode node = graph.findById(current);
            if (node == null) {
                return;
            }
            steps[0]++;

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
                case ON_PLAYER_USE:
                    log.add("[событие] игрок нажал: " + node.getParam("target"));
                    break;
                case ON_ENTITY_DEATH:
                    log.add("[событие] уничтожен объект: " + node.getParam("target"));
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
                case ACTION_SET_VARIABLE:
                    variables.put(node.getParam("name"), node.getParam("value"));
                    log.add("переменная " + node.getParam("name") + " = " + node.getParam("value"));
                    break;
                case ACTION_DELAY:
                    double seconds = parseDoubleOr(node.getParam("seconds"), 1.0);
                    clock[0] += seconds;
                    log.add(String.format(Locale.US, "пауза %.1f сек (t = %.1f)", seconds, clock[0]));
                    break;
                case ACTION_SET_PLAYER_HEALTH:
                    log.add("здоровье игрока := " + node.getParam("amount"));
                    break;
                case ACTION_CHANGE_LEVEL:
                    log.add("переход на карту " + node.getParam("map"));
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
                        log.add("ветка не выполнена, цепочка остановлена");
                        return;
                    }
                    break;
                default:
                    break;
            }
            current = node.nextId;
        }
    }

    private static double parseDoubleOr(String value, double fallback) {
        try {
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException e) {
            return fallback;
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
}
