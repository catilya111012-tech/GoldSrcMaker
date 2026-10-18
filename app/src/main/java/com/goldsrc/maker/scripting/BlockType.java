package com.goldsrc.maker.scripting;

public enum BlockType {

    ON_MAP_START(
            "Событие: старт карты",
            Kind.EVENT,
            "on_map_start",
            new String[]{},
            new String[]{}),

    ON_PLAYER_TOUCH(
            "Событие: игрок коснулся объекта",
            Kind.EVENT,
            "on_player_touch",
            new String[]{"target"},
            new String[]{"door_1"}),

    ON_TRIGGER_ENTER(
            "Событие: игрок вошёл в триггер",
            Kind.EVENT,
            "on_trigger_enter",
            new String[]{"trigger"},
            new String[]{"trigger_1"}),

    ACTION_SPAWN_ENTITY(
            "Действие: создать энтити",
            Kind.ACTION,
            null,
            new String[]{"classname", "origin", "targetname"},
            new String[]{"light", "64 64 64", "spawned_1"}),

    ACTION_SET_KEYVALUE(
            "Действие: задать параметр",
            Kind.ACTION,
            null,
            new String[]{"target", "key", "value"},
            new String[]{"target_1", "health", "100"}),

    ACTION_GIVE_ITEM(
            "Действие: выдать предмет",
            Kind.ACTION,
            null,
            new String[]{"item", "amount"},
            new String[]{"item_healthkit", "25"}),

    ACTION_PLAY_SOUND(
            "Действие: воспроизвести звук",
            Kind.ACTION,
            null,
            new String[]{"sound", "volume"},
            new String[]{"ambience/water1.wav", "1.0"}),

    ACTION_OPEN_DOOR(
            "Действие: активировать target",
            Kind.ACTION,
            null,
            new String[]{"target"},
            new String[]{"door_1"}),

    ACTION_SHOW_MESSAGE(
            "Действие: показать сообщение",
            Kind.ACTION,
            null,
            new String[]{"text"},
            new String[]{"Добро пожаловать в GoldSrc Maker!"}),

    ACTION_TELEPORT(
            "Действие: телепортировать игрока",
            Kind.ACTION,
            null,
            new String[]{"destination"},
            new String[]{"spawn_2"}),

    ACTION_END(
            "Действие: завершить цепочку",
            Kind.ACTION,
            null,
            new String[]{},
            new String[]{}),

    CONDITION_IF(
            "Условие: если",
            Kind.CONDITION,
            null,
            new String[]{"variable", "operator", "value"},
            new String[]{"door_1_opened", "==", "1"});

    public enum Kind {
        EVENT,
        ACTION,
        CONDITION
    }

    public final String title;
    public final Kind kind;
    public final String eventName;
    public final String[] paramNames;
    public final String[] paramDefaults;

    BlockType(String title, Kind kind, String eventName, String[] paramNames, String[] paramDefaults) {
        this.title = title;
        this.kind = kind;
        this.eventName = eventName;
        this.paramNames = paramNames;
        this.paramDefaults = paramDefaults;
    }

    public int color() {
        switch (kind) {
            case EVENT:
                return 0xFF2E7D32;
            case CONDITION:
                return 0xFFB26A00;
            default:
                return 0xFF37474F;
        }
    }
}
