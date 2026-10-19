package com.goldsrc.maker.editor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class EntityCatalog {

    public static final class FieldSpec {
        public final String key;
        public final String label;
        public final boolean numeric;

        public FieldSpec(String key, String label, boolean numeric) {
            this.key = key;
            this.label = label;
            this.numeric = numeric;
        }
    }

    private static final class ClassDef {
        final String help;
        final LinkedHashMap<String, String> defaults = new LinkedHashMap<>();
        final List<FieldSpec> fields = new ArrayList<>();

        ClassDef(String help) {
            this.help = help;
        }

        ClassDef field(String key, String label) {
            fields.add(new FieldSpec(key, label, false));
            return this;
        }

        ClassDef number(String key, String label) {
            fields.add(new FieldSpec(key, label, true));
            return this;
        }

        ClassDef def(String key, String value) {
            defaults.put(key, value);
            return this;
        }
    }

    private static final LinkedHashMap<String, ClassDef> DEFS = new LinkedHashMap<>();
    private static final LinkedHashMap<String, String> TITLES = new LinkedHashMap<>();

    static {
        register("info_player_start", "Точка появления игрока", new ClassDef("Игрок начинает игру здесь. Должна быть одна на карту.")
                .number("angles", "Углы (0/90/180/270)")
                .field("target", "Цель (target)")
                .def("angles", "0"));

        register("info_player_deathmatch", "Точка появления (DM)", new ClassDef("Дополнительная точка появления для режима deathmatch.")
                .number("angles", "Углы (0/90/180/270)")
                .def("angles", "0"));

        register("info_target", "Маркер-цель", new ClassDef("Именованная цель для target другого энтити или скрипта.")
                .field("targetname", "Имя (targetname)"));

        register("light", "Источник света", new ClassDef("Точечный свет. Яркость 100..500, цвета можно не задавать.")
                .number("_light", "Яркость (light)")
                .field("_color", "Цвет R G B (опц.)")
                .def("_light", "300"));

        register("light_environment", "Свет неба", new ClassDef("Направленный свет от неба (обычно один на карту).")
                .number("_light", "Яркость (light)")
                .number("pitch", "Pitch (обычно -90)")
                .field("_color", "Цвет R G B (опц.)")
                .def("_light", "300")
                .def("pitch", "-90"));

        register("ambient_generic", "Звук", new ClassDef("Зацикленный или триггерный звук.")
                .field("message", "Файл звука (напр. ambient/waterfall1.wav)")
                .number("volume", "Громкость 0..10")
                .def("volume", "10"));

        register("env_sprite", "Спрайт", new ClassDef("Анимированный спрайт (огонь, дым и т.п.).")
                .field("model", "Файл спрайта (.spr)")
                .number("framerate", "Кадров/сек")
                .def("framerate", "10"));

        register("item_healthkit", "Аптечка", new ClassDef("Подбираемый предмет: аптечка.")
                .number("health", "Сколько здоровья даёт")
                .def("health", "15"));

        register("item_battery", "Батарея", new ClassDef("Подбираемый предмет: броня.")
                .number("armorvalue", "Сколько брони даёт")
                .def("armorvalue", "15"));

        register("weapon_crowbar", "Оружие (лом)", new ClassDef("Подбираемое оружие. Аналогично можно добавить weapon_* по имени.")
                .number("spawnflags", "Флаги появления")
                .def("spawnflags", "0"));

        register("monster_scientist", "NPC: учёный", new ClassDef("Дружественный NPC-учёный.")
                .field("targetname", "Имя (targetname)")
                .field("target", "Цель (target)"));

        register("monster_zombie", "NPC: зомби", new ClassDef("Враждебный NPC-зомби.")
                .field("targetname", "Имя (targetname)")
                .field("target", "Цель (target)"));

        register("monster_human_grunt", "NPC: солдат HECU", new ClassDef("Враждебный солдат HECU: стреляет из MP5 или дробовика, кидает гранаты.")
                .number("weapons", "Оружие (1=MP5, 2=дробовик, 3=оба)")
                .def("weapons", "1"));

        register("monster_human_assassin", "NPC: ассасин HECU", new ClassDef("Элитный боец HECU: быстрая, прыгает, стреляет с двух рук.")
                .number("weapons", "Оружие (оставьте -1)")
                .def("weapons", "-1"));

        register("monster_barney", "NPC: Барни (союзник)", new ClassDef("Дружественный охранник: воюет на стороне игрока.")
                .number("weapons", "Оружие (0=пистолет)")
                .def("weapons", "0"));

        register("env_fire", "Огонь", new ClassDef("Очаг огня (костёр, горящие обломки).")
                .number("firesize", "Размер очага")
                .number("firehealth", "Запас горения")
                .def("firesize", "64")
                .def("firehealth", "1000000"));

        addItemLike("item_suit", "Костюм HEV", "Подбираемый костюм HEV (нужен для брони).");
        addItemLike("weapon_9mmhandgun", "Оружие: пистолет", "Подбираемый пистолет 9мм.");
        addItemLike("weapon_9mmAR", "Оружие: MP5", "Подбираемый автомат MP5 (и гранатомёт под стволом).");
        addItemLike("weapon_shotgun", "Оружие: дробовик", "Подбираемый дробовик.");
        addItemLike("weapon_rpg", "Оружие: РПГ", "Подбираемая ракетница.");
        addItemLike("weapon_handgrenade", "Оружие: гранаты", "Подбираемые ручные гранаты.");
        addItemLike("ammo_9mmclip", "Припасы: 9мм", "Патроны для пистолета и MP5.");
        addItemLike("ammo_9mmAR", "Припасы: MP5", "Магазины для MP5.");
        addItemLike("ammo_ARgrenades", "Припасы: гранаты MP5", "Подствольные гранаты MP5.");
        addItemLike("ammo_buckshot", "Припасы: дробь", "Патроны для дробовика.");
        addItemLike("ammo_rpgclip", "Припасы: ракеты", "Ракеты для РПГ.");
    }

    private static void addItemLike(String classname, String title, String help) {
        register(classname, title, new ClassDef(help)
                .number("spawnflags", "Флаги появления")
                .def("spawnflags", "0"));
    }

    private static void register(String classname, String title, ClassDef def) {
        def.defaults.putIfAbsent("targetname", "");
        DEFS.put(classname, def);
        TITLES.put(classname, title);
    }

    private EntityCatalog() {
    }

    public static String[] classNames() {
        return DEFS.keySet().toArray(new String[0]);
    }

    public static String help(String classname) {
        ClassDef def = DEFS.get(classname);
        if (def == null) {
            return "Пользовательский класс " + classname + ": задайте keyvalues вручную.";
        }
        return def.help;
    }

    public static String title(String classname) {
        String title = TITLES.get(classname);
        return title == null ? classname : title;
    }

    public static List<FieldSpec> fields(String classname) {
        ClassDef def = DEFS.get(classname);
        if (def == null) {
            List<FieldSpec> generic = new ArrayList<>();
            generic.add(new FieldSpec("targetname", "Имя (targetname)", false));
            generic.add(new FieldSpec("target", "Цель (target)", false));
            return generic;
        }
        List<FieldSpec> merged = new ArrayList<>(def.fields);
        boolean hasTargetname = false;
        boolean hasTarget = false;
        for (FieldSpec spec : merged) {
            if ("targetname".equals(spec.key)) {
                hasTargetname = true;
            }
            if ("target".equals(spec.key)) {
                hasTarget = true;
            }
        }
        if (!hasTargetname) {
            merged.add(0, new FieldSpec("targetname", "Имя (targetname)", false));
        }
        if (!hasTarget) {
            merged.add(new FieldSpec("target", "Цель (target)", false));
        }
        return Collections.unmodifiableList(merged);
    }

    public static LinkedHashMap<String, String> defaults(String classname) {
        LinkedHashMap<String, String> result = new LinkedHashMap<>();
        ClassDef def = DEFS.get(classname);
        if (def != null) {
            for (Map.Entry<String, String> entry : def.defaults.entrySet()) {
                if (entry.getValue() != null && !entry.getValue().isEmpty()
                        && !"targetname".equals(entry.getKey())) {
                    result.put(entry.getKey(), entry.getValue());
                }
            }
        }
        return result;
    }
}
