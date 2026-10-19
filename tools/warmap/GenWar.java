import com.goldsrc.maker.core.ModelData;
import com.goldsrc.maker.core.ModelStore;
import com.goldsrc.maker.core.TextureImage;
import com.goldsrc.maker.core.TextureStore;
import com.goldsrc.maker.core.WadWriter;
import com.goldsrc.maker.editor.Brush;
import com.goldsrc.maker.editor.BrushEntity;
import com.goldsrc.maker.editor.EntityData;
import com.goldsrc.maker.editor.MapData;
import com.goldsrc.maker.scripting.BlockGraph;
import com.goldsrc.maker.scripting.BlockNode;
import com.goldsrc.maker.scripting.BlockType;
import com.goldsrc.maker.scripting.CodeGenerator;

import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.TreeSet;

public class GenWar {

    private static final String SHARE_ROOT = "/storage/emulated/0/Documents/GoldSrcMaker";

    private static final MapData map = new MapData();
    private static int failed = 0;

    public static void main(String[] args) throws Exception {
        File projectDir = new File(args.length > 0
                ? args[0]
                : SHARE_ROOT + "/projects/warzone");

        buildMap();
        writeProject(projectDir);
        verify(projectDir);

        System.out.println("брашей: " + map.brushes.size()
                + ", brush-энтити: " + map.brushEntities.size()
                + ", энтити: " + map.entities.size());
        if (failed > 0) {
            System.out.println("ПРОВАЛЕНО ПРОВЕРОК: " + failed);
            System.exit(1);
        }
        System.out.println("ПРОЕКТ СОБРАН: " + projectDir.getAbsolutePath());
    }

    // ---------------------------------------------------------------- карта

    private static void box(int x1, int y1, int z1, int x2, int y2, int z2, String tex) {
        map.brushes.add(new Brush(x1, y1, z1, x2, y2, z2, tex));
    }

    private static void ent(String cls, int x, int y, int z, String... kv) {
        EntityData e = new EntityData(cls);
        e.setOrigin(x, y, z);
        for (int i = 0; i + 1 < kv.length; i += 2) {
            e.setKey(kv[i], kv[i + 1]);
        }
        map.entities.add(e);
    }

    private static void crate(int cx, int cy, int zBottom) {
        box(cx - 16, cy - 16, zBottom, cx + 16, cy + 16, zBottom + 32, "WARCRATE");
    }

    private static void buildMap() {
        // земля и дорога (до самой северной зоны с окопами)
        box(-512, -512, -16, -94, 1408, 0, "WARGROUND");
        box(-96, -512, -16, 96, 1408, 2, "WARGROAD");
        box(94, -512, -16, 512, 1408, 0, "WARGROUND");

        // периметр (северная стена отодвинута под окопы)
        box(-512, -512, -16, 512, -464, 480, "WARCONC");
        box(-512, 1360, -16, 512, 1408, 480, "WARCONC");
        box(-512, -512, -16, -464, 1408, 480, "WARCONC");
        box(464, -512, -16, 512, 1408, 480, "WARCONC");
        box(-560, -560, 448, 560, 1408, 480, "SKY");

        // казарма (запад), дверь в восточной стене y 8..56
        box(-448, -96, -16, -424, 160, 192, "WARCAMO");
        box(-200, -96, -16, -176, 8, 192, "WARCAMO");
        box(-200, 56, -16, -176, 160, 192, "WARCAMO");
        box(-424, -96, -16, -200, -72, 192, "WARCAMO");
        box(-424, 136, -16, -200, 160, 192, "WARCAMO");
        box(-448, -96, 160, -176, 160, 192, "WARCONC");

        // дом (восток), дверь в западной стене y -56..-8
        box(424, -160, -16, 448, 96, 192, "WARBRICK");
        box(176, -160, -16, 200, -56, 192, "WARBRICK");
        box(176, -8, -16, 200, 96, 192, "WARBRICK");
        box(200, -160, -16, 424, -136, 192, "WARBRICK");
        box(200, 72, -16, 424, 96, 192, "WARBRICK");
        box(176, -160, 160, 448, 96, 192, "WARCONC");

        // бункер (север), южная дверь x -24..24, северный проход x -48..48 → окопы
        box(-176, 416, -16, -48, 448, 224, "WARCONC");
        box(48, 416, -16, 176, 448, 224, "WARCONC");
        box(-176, 256, -16, -24, 288, 224, "WARCONC");
        box(24, 256, -16, 176, 288, 224, "WARCONC");
        box(-176, 288, -16, -144, 416, 224, "WARCONC");
        box(144, 288, -16, 176, 416, 224, "WARCONC");
        box(-176, 256, 192, 176, 448, 224, "WARMETAL");

        // парапет на крыше бункера
        box(-176, 432, 224, 176, 448, 256, "WARSANDB");
        box(-176, 256, 224, 176, 272, 256, "WARSANDB");
        box(-176, 256, 224, -160, 448, 256, "WARSANDB");
        box(160, 256, 224, 176, 448, 256, "WARSANDB");

        // мешки с песком
        box(-152, -224, -8, -24, -200, 40, "WARSANDB");
        box(24, -224, -8, 152, -200, 40, "WARSANDB");
        box(-208, 176, -8, -48, 200, 40, "WARSANDB");
        box(48, 176, -8, 208, 200, 40, "WARSANDB");

        // низкие бетонные укрытия
        box(-448, 288, -8, -224, 304, 48, "WARCONC");
        box(224, 176, -8, 448, 192, 48, "WARCONC");

        // === окопы: две линии земляных валов севернее бункера ===
        // первая линия: южный вал y 704..736 (проход-зигзаг x -336..-272) и
        // северный вал y 800..832 (проход x 272..336); дорога проходит насквозь
        box(-448, 704, -16, -336, 736, 88, "WARSANDB");
        box(-272, 704, -16, -96, 736, 88, "WARSANDB");
        box(96, 704, -16, 448, 736, 88, "WARSANDB");
        box(-448, 800, -16, -96, 832, 88, "WARSANDB");
        box(96, 800, -16, 272, 832, 88, "WARSANDB");
        box(336, 800, -16, 448, 832, 88, "WARSANDB");

        // укрытия и припасы в самом окопе
        box(-192, 744, -8, -128, 776, 48, "WARCONC");
        box(128, 744, -8, 192, 776, 48, "WARCONC");
        crate(-64, 768, -8);
        crate(64, 768, -8);

        // вторая линия: вал y 1152..1184 (проходы: дорога и фланг x 160..224)
        box(-448, 1152, -16, -96, 1184, 88, "WARSANDB");
        box(96, 1152, -16, 160, 1184, 88, "WARSANDB");
        box(224, 1152, -16, 448, 1184, 88, "WARSANDB");

        // бетонные блоки и ящики в поле
        box(-384, 944, -8, -256, 960, 48, "WARCONC");
        box(256, 1008, -8, 384, 1024, 48, "WARCONC");
        crate(-136, 880, -8);
        crate(136, 880, -8);

        // штаб врага: мешки перед северной стеной
        box(-192, 1280, -8, 192, 1312, 48, "WARSANDB");
        crate(-96, 1248, -8);
        crate(96, 1248, -8);
        crate(0, 1332, -8);

        // ящики
        crate(-136, -152, -8);
        crate(-104, -152, -8);
        crate(-120, -152, 23);
        crate(136, -152, -8);
        crate(168, -152, -8);
        crate(152, -152, 23);
        crate(-384, 112, -8);
        crate(400, -96, -8);

        // стартовая оружейная: пустая комната с оружием (врагов нет),
        // выход на север — в деревню с HECU
        box(-224, -464, -16, -192, -256, 192, "WARCONC");  // западная стена
        box(192, -464, -16, 224, -256, 192, "WARCONC");    // восточная стена
        box(-224, -288, -16, -48, -256, 192, "WARCONC");   // северная стена, слева от двери
        box(48, -288, -16, 224, -256, 192, "WARCONC");     // северная стена, справа от двери
        box(-224, -464, 192, 224, -256, 208, "WARMETAL");  // потолок
        crate(-160, -336, 0);
        crate(160, -336, 0);

        // бронедверь на север: уезжает на восток в стену-пенал
        // (angle 0 = +X; ход = (136-2)-8 = 126, открытая прячется в стене x 48..224)
        BrushEntity blastDoor = new BrushEntity("func_door");
        blastDoor.setKey("angle", "0");
        blastDoor.setKey("speed", "100");
        blastDoor.setKey("wait", "-1");
        blastDoor.setKey("lip", "8");
        blastDoor.brushes.add(new Brush(-48, -289, 0, 88, -255, 194, "WARMETAL"));
        map.brushEntities.add(blastDoor);

        // свет неба
        ent("light_environment", 0, 320, 430, "pitch", "-90",
                "_light", "170 180 200 350");

        // старт игрока: оружейная (без врагов)
        ent("info_player_start", 0, -432, 24, "angle", "90", "angles", "0 90 0");
        ent("item_suit", 48, -432, 8);
        ent("weapon_crowbar", -144, -448, 8);
        ent("weapon_9mmhandgun", -144, -416, 8);
        ent("ammo_9mmclip", -144, -384, 8);
        ent("weapon_handgrenade", -144, -352, 8);
        ent("weapon_9mmAR", 144, -448, 8);
        ent("ammo_9mmAR", 144, -416, 8);
        ent("ammo_9mmAR", 144, -384, 8);
        ent("weapon_shotgun", 144, -352, 8);
        ent("item_healthkit", -48, -368, 8);
        ent("ammo_9mmclip", -160, -336, 40);
        ent("ammo_buckshot", 160, -336, 40);
        ent("light", 0, -360, 150, "_light", "300");
        ent("light", -128, -440, 140, "_light", "220");
        ent("light", 128, -440, 140, "_light", "220");

        // улица: припасы за мешками
        ent("weapon_9mmAR", -88, -240, 8);
        ent("ammo_9mmAR", -120, -240, 8);
        ent("ammo_9mmclip", -72, -240, 8);
        ent("weapon_handgrenade", 96, -240, 8);
        ent("ammo_ARgrenades", 120, -240, 8);
        ent("item_battery", 0, -352, 8);
        ent("light", 0, -320, 160, "_light", "280");
        ent("item_healthkit", 0, -96, 8);

        // первая линия HECU (спиной к спавну, чтобы не расстреливали сразу)
        ent("monster_human_grunt", -88, -32, 8, "angle", "180", "weapons", "1");
        ent("monster_human_grunt", 88, -32, 8, "angle", "0", "weapons", "1");

        // горящие ящики
        ent("env_fire", -120, -152, 48, "firesize", "64", "firehealth", "1000000",
                "ignitionpoint", "32", "damagescale", "0");
        ent("ambient_generic", -120, -152, 56, "message", "ambient/fire/fire_med_loop1.wav",
                "health", "8");
        ent("env_fire", 152, -152, 48, "firesize", "64", "firehealth", "1000000",
                "ignitionpoint", "32", "damagescale", "0");
        ent("ambient_generic", 152, -152, 56, "message", "ambient/fire/fire_med_loop1.wav",
                "health", "8");

        // западный фланг
        ent("monster_human_grunt", -320, -264, 8, "angle", "0", "weapons", "2");

        // казарма: союзник и дробовик
        ent("monster_barney", -320, -16, 8, "angle", "270", "targetname", "ally");
        ent("weapon_shotgun", -352, -48, 8);
        ent("ammo_buckshot", -384, -48, 8);
        ent("ammo_buckshot", -352, -80, 8);
        ent("weapon_handgrenade", -288, -48, 8);
        ent("item_healthkit", -352, 96, 8);
        ent("item_battery", -288, 96, 8);
        ent("light", -312, 32, 140, "_light", "300");

        // дом: засада
        ent("monster_human_grunt", 288, -96, 8, "angle", "270");
        ent("monster_human_grunt", 320, -32, 8, "angle", "180");
        ent("ammo_9mmAR", 288, -64, 8);
        ent("weapon_9mmhandgun", 352, 32, 8);
        ent("light", 312, -32, 140, "_light", "300");

        // вторая линия у бункера
        ent("monster_human_grunt", -96, 224, 8, "angle", "270", "weapons", "3");
        ent("monster_human_grunt", 96, 224, 8, "angle", "270", "weapons", "3");
        ent("light", 0, 144, 160, "_light", "280");

        // бункер: РПГ и припасы
        ent("weapon_rpg", 0, 352, 8);
        ent("ammo_rpgclip", -64, 352, 8);
        ent("ammo_rpgclip", 64, 352, 8);
        ent("item_healthkit", 96, 352, 8);
        ent("item_battery", -96, 352, 8);
        ent("light", 0, 352, 170, "_light", "350");

        // снайперы и командир на крыше
        ent("monster_human_grunt", -96, 352, 236, "angle", "270", "weapons", "1");
        ent("monster_human_grunt", 96, 352, 236, "angle", "270", "weapons", "1");
        ent("monster_human_assassin", 0, 352, 236, "angle", "270", "targetname", "boss");

        // === окопы: наши — отряд охранников держит первую линию ===
        ent("monster_barney", -320, 768, 8, "angle", "270");
        ent("monster_barney", -224, 768, 8, "angle", "270");
        ent("monster_barney", -48, 768, 8, "angle", "270");
        ent("monster_barney", 48, 768, 8, "angle", "270");
        ent("monster_barney", 224, 768, 8, "angle", "270");
        ent("monster_barney", 320, 768, 8, "angle", "270");
        ent("item_healthkit", -176, 768, 8);
        ent("ammo_9mmAR", 176, 768, 8);
        ent("ammo_buckshot", 0, 768, 8);

        // противник в нейтральной полосе
        ent("monster_human_grunt", -288, 880, 8, "angle", "180", "weapons", "2");
        ent("monster_human_grunt", 0, 880, 8, "angle", "180", "weapons", "2");
        ent("monster_human_grunt", 288, 880, 8, "angle", "180", "weapons", "2");

        // вторая линия обороны врага
        ent("monster_human_grunt", -320, 1120, 8, "angle", "180", "weapons", "1");
        ent("monster_human_grunt", -96, 1120, 8, "angle", "180", "weapons", "0");
        ent("monster_human_grunt", 96, 1120, 8, "angle", "180", "weapons", "0");
        ent("monster_human_grunt", 320, 1120, 8, "angle", "180", "weapons", "3");
        ent("ammo_ARgrenades", 0, 1104, 8);
        ent("item_battery", -160, 1200, 8);
        ent("item_healthkit", 0, 1200, 8);
        ent("ammo_rpgclip", 160, 1200, 8);

        // штаб: охрана и командир (warlord) за мешками
        ent("monster_human_grunt", -128, 1224, 8, "angle", "180", "weapons", "1");
        ent("monster_human_grunt", 128, 1224, 8, "angle", "180", "weapons", "2");
        ent("monster_human_assassin", -48, 1340, 8, "angle", "180", "targetname", "warlord");
        ent("monster_human_assassin", 48, 1340, 8, "angle", "180");
        ent("weapon_shotgun", -160, 1224, 8);
        ent("ammo_buckshot", -192, 1224, 8);
    }

    // ------------------------------------------------------------- текстуры

    private interface Painter {
        int index(int x, int y);
    }

    private static int hash(int x, int y, int seed) {
        int h = x * 374761393 + y * 668265263 + seed * 1013904223;
        h = (h ^ (h >>> 13)) * 1274126177;
        h ^= h >>> 16;
        return h & 0x7FFFFFFF;
    }

    private static int noise(int x, int y, int cell, int seed) {
        return hash(Math.floorDiv(x, cell), Math.floorDiv(y, cell), seed);
    }

    private static int[] pal(int... colors) {
        int[] p = new int[TextureImage.PALETTE_SIZE];
        for (int i = 0; i < p.length; i++) {
            p[i] = i < colors.length ? 0xFF000000 | colors[i] : 0xFF6E6E6E;
        }
        return p;
    }

    private static TextureImage tex(String name, int[] palette, Painter painter) {
        int w = 128;
        int h = 128;
        byte[] pixels = new byte[w * h];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                pixels[y * w + x] = (byte) (painter.index(x, y) & 0xFF);
            }
        }
        return new TextureImage(name, w, h, pixels, palette);
    }

    private static List<TextureImage> buildTextures() {
        List<TextureImage> textures = new ArrayList<>();

        int[] groundPal = pal(0x3B2F1F, 0x4A3B28, 0x5C4A32, 0x6E5940, 0x7F6A4E, 0x8A8578, 0x6B665C);
        textures.add(tex("WARGROUND", groundPal, (x, y) -> {
            int base = 1 + noise(x, y, 8, 2) % 3;
            if (noise(x, y, 16, 1) % 7 == 0) base = 4;
            if (noise(x, y, 32, 5) % 9 == 0) base = 0;
            if (noise(x, y, 8, 4) % 23 == 0) base = 5;
            if (hash(x, y, 3) % 37 == 0) base = 6;
            return base;
        }));

        int[] roadPal = pal(0x1F1F22, 0x2B2B2E, 0x333336, 0x3B3B3F, 0x45454A, 0x585860);
        textures.add(tex("WARGROAD", roadPal, (x, y) -> {
            int base = 1 + noise(x, y, 8, 5) % 3;
            if (noise(x, y, 24, 6) % 11 == 0) base = 4;
            if (noise(x, y, 16, 7) % 13 == 0) base = 5;
            if (hash(x, y, 8) % 67 == 0) base = 0;
            return base;
        }));

        int[] concPal = pal(0x565656, 0x6E6E6E, 0x7A7A7A, 0x868686, 0x929292, 0x4A4A4A, 0x606060);
        textures.add(tex("WARCONC", concPal, (x, y) -> {
            int base = 1 + noise(x, y, 8, 9) % 3;
            if (noise(x, y, 32, 10) % 5 == 0) base = 6;
            if (y % 32 < 2) base = 5;
            if (noise(x, y, 4, 11) % 29 == 0) base = 0;
            return base;
        }));

        int[] brickPal = pal(0x3A3A3A, 0x7A4A3A, 0x8A5644, 0x6B3F32, 0x935F4B, 0x5C392E);
        textures.add(tex("WARBRICK", brickPal, (x, y) -> {
            int row = Math.floorDiv(y, 16);
            int bx = x + (row % 2) * 16;
            if (y % 16 < 2 || Math.floorMod(bx, 32) < 2) return 0;
            int result = 1 + hash(Math.floorDiv(bx, 32), row, 11) % 4;
            if (hash(x, y, 12) % 41 == 0) result = 5;
            return result;
        }));

        int[] metalPal = pal(0x3F4247, 0x5A5E64, 0x6A6E74, 0x767A80, 0x9AA0A8, 0x7A5A3A, 0x4A4E54);
        textures.add(tex("WARMETAL", metalPal, (x, y) -> {
            int base = 2;
            if (hash(x, y, 13) % 23 == 0) base = 1;
            if (x % 32 < 2 || y % 32 < 2) base = 0;
            int rx = Math.floorMod(x, 32);
            int ry = Math.floorMod(y, 32);
            if (rx >= 4 && rx <= 6 && ry >= 4 && ry <= 6) base = 4;
            if (noise(x, y, 20, 14) % 6 == 0) base = 5;
            return base;
        }));

        int[] sandPal = pal(0x5A5238, 0x8A7C56, 0x97885F, 0x7C6F4C, 0xA6976B);
        textures.add(tex("WARSANDB", sandPal, (x, y) -> {
            int row = Math.floorDiv(y, 16);
            int bx = Math.floorMod(x + (row % 2) * 16, 32);
            int ly = Math.floorMod(y, 16);
            double dx = (bx - 15.5) / 15.0;
            double dy = (ly - 8.0) / 8.0;
            if (dx * dx + dy * dy > 1.0) return 0;
            int result = 1 + hash(Math.floorDiv(x + (row % 2) * 16, 32), row, 15) % 3;
            if (dy < -0.35) result = 4;
            if (hash(x, y, 16) % 53 == 0) result = 0;
            return result;
        }));

        int[] cratePal = pal(0x503C22, 0x7A5C36, 0x8A6A3F, 0x96754A, 0x6B5028, 0x3A2C18, 0xA9875A);
        textures.add(tex("WARCRATE", cratePal, (x, y) -> {
            int base = 2;
            if (y % 16 < 1) base = 0;
            if (noise(x, y, 4, 17) % 6 == 0) base = 1;
            if (noise(x, y, 24, 18) % 5 == 0) base = 3;
            if (x < 6 || x > 121 || y < 6 || y > 121) {
                base = 4;
                if (x < 2 || x > 125 || y < 2 || y > 125) base = 0;
            }
            boolean nail = (x >= 8 && x <= 10 && y >= 8 && y <= 10)
                    || (x >= 117 && x <= 119 && y >= 8 && y <= 10)
                    || (x >= 8 && x <= 10 && y >= 117 && y <= 119)
                    || (x >= 117 && x <= 119 && y >= 117 && y <= 119);
            if (nail) base = (x == 9 && y == 9) ? 6 : 5;
            return base;
        }));

        int[] camoPal = pal(0x2E3B22, 0x4A5A32, 0x5C6E3E, 0x6E4A2E, 0x23281C, 0x7A8A4E);
        textures.add(tex("WARCAMO", camoPal, (x, y) -> {
            int base = 1;
            if (noise(x, y, 32, 19) % 3 == 0) base = 2;
            if (noise(x, y, 24, 20) % 3 == 1) base = 3;
            if (noise(x, y, 20, 21) % 5 == 0) base = 0;
            if (noise(x, y, 16, 22) % 7 == 0) base = 4;
            if (hash(x, y, 23) % 199 == 0) base = 5;
            return base;
        }));

        return textures;
    }

    // -------------------------------------------------------------- модели

    private static ModelData buildCrateModel() {
        ModelData model = new ModelData("war_crate");
        model.addPart(new ModelData.Part("body", "WARCRATE", 0, 0, 12, 24, 24, 24, 0.5f));
        return model;
    }

    private static ModelData buildBarrelModel() {
        ModelData model = new ModelData("war_barrel");
        model.addPart(new ModelData.Part("body", "WARMETAL", 0, 0, 15, 20, 20, 30, 0.5f));
        model.addPart(new ModelData.Part("lid", "WARMETAL", 0, 0, 31, 22, 22, 4, 0.5f));
        return model;
    }

    // -------------------------------------------------------------- скрипт

    private static BlockGraph buildGraph() {
        BlockGraph graph = new BlockGraph();

        BlockNode start = graph.addNode(BlockType.ON_MAP_START, 40, 40);
        BlockNode welcome = graph.addNode(BlockType.ACTION_SHOW_MESSAGE, 40, 180);
        welcome.setParam("text", "Оружейная: возьми снаряжение. Бронедверь открывается от касания — за ней деревня с HECU.");
        BlockNode pause = graph.addNode(BlockType.ACTION_DELAY, 40, 320);
        pause.setParam("seconds", "4");
        BlockNode hint = graph.addNode(BlockType.ACTION_SHOW_MESSAGE, 40, 460);
        hint.setParam("text", "Бери дробовик, MP5 и гранаты. За бункером на севере — окопы: там держат оборону наши!");

        BlockNode bossDead = graph.addNode(BlockType.ON_ENTITY_DEATH, 400, 40);
        bossDead.setParam("target", "boss");
        BlockNode villageDone = graph.addNode(BlockType.ACTION_SHOW_MESSAGE, 400, 180);
        villageDone.setParam("text", "Деревня зачищена! Прорывайся через бункер на север — в окопах идёт бойня.");

        BlockNode warlordDead = graph.addNode(BlockType.ON_ENTITY_DEATH, 760, 40);
        warlordDead.setParam("target", "warlord");
        BlockNode victory = graph.addNode(BlockType.ACTION_SHOW_MESSAGE, 760, 180);
        victory.setParam("text", "Командир окопов уничтожен — бойня выиграна! Победа!");
        BlockNode done = graph.addNode(BlockType.ACTION_END, 760, 320);

        start.nextId = welcome.id;
        welcome.nextId = pause.id;
        pause.nextId = hint.id;
        bossDead.nextId = villageDone.id;
        warlordDead.nextId = victory.id;
        victory.nextId = done.id;
        return graph;
    }

    // --------------------------------------------------------------- файлы

    private static void writeProject(File dir) throws Exception {
        String name = dir.getName();
        if (!name.matches("[A-Za-z0-9_\\-]{1,48}")) {
            throw new IllegalArgumentException("Недопустимое имя проекта: " + name);
        }
        if (!dir.isDirectory() && !dir.mkdirs()) {
            throw new IOException("Не удалось создать каталог: " + dir);
        }

        JSONObject meta = new JSONObject();
        meta.put("format", 1);
        meta.put("name", name);
        meta.put("created", System.currentTimeMillis());
        writeText(new File(dir, "project.json"), meta.toString(2));

        writeText(new File(dir, "map.json"), map.toJson().toString(2));

        File halflifeWad = new File(SHARE_ROOT, "valve/halflife.wad");
        File projectWad = new File(dir, "textures.wad");
        writeText(new File(dir, "main.map"),
                map.toMapText(halflifeWad.getAbsolutePath(), projectWad.getAbsolutePath()));

        WadWriter.write(projectWad, buildTextures());

        ModelStore models = new ModelStore(dir);
        models.save(buildCrateModel());
        models.save(buildBarrelModel());

        BlockGraph graph = buildGraph();
        writeText(new File(dir, "graph.json"), graph.toJson().toString(2));
        writeText(new File(dir, "main.angelscript"), CodeGenerator.generate(graph));
    }

    private static void verify(File dir) throws Exception {
        MapData reload = MapData.fromJson(new JSONObject(readText(new File(dir, "map.json"))));
        check(reload.brushes.size() == map.brushes.size(),
                "map.json: брашей " + reload.brushes.size() + " из " + map.brushes.size());
        check(reload.brushEntities.size() == map.brushEntities.size(),
                "map.json: brush-энтити " + reload.brushEntities.size()
                        + " из " + map.brushEntities.size());
        check(reload.entities.size() == map.entities.size(),
                "map.json: энтити " + reload.entities.size() + " из " + map.entities.size());
        boolean allValid = true;
        for (Brush brush : reload.brushes) {
            if (brush.width() <= 0 || brush.depth() <= 0 || brush.height() <= 0) {
                allValid = false;
            }
        }
        check(allValid, "все браши имеют ненулевой объём");

        boolean hasSpawn = false;
        for (EntityData entity : reload.entities) {
            if ("info_player_start".equals(entity.classname)) {
                hasSpawn = true;
            }
        }
        check(hasSpawn, "есть info_player_start");

        TextureStore store = new TextureStore(dir);
        List<TextureImage> textures = store.loadAll();
        TreeSet<String> names = new TreeSet<>();
        for (TextureImage texture : textures) {
            names.add(texture.name);
        }
        check(textures.size() == 8, "textures.wad: " + textures.size() + " текстур");
        check(names.containsAll(Arrays.asList("WARGROUND", "WARGROAD", "WARCONC", "WARBRICK",
                        "WARMETAL", "WARSANDB", "WARCRATE", "WARCAMO")),
                "имена текстур на месте: " + names);

        ModelStore models = new ModelStore(dir);
        check(models.list().size() == 2, "моделей: " + models.list());
        check(models.load("war_crate") != null && models.load("war_barrel") != null,
                "модели war_crate и war_barrel читаются");

        BlockGraph graph = BlockGraph.fromJson(new JSONObject(readText(new File(dir, "graph.json"))));
        check(graph.nodes.size() == 9, "graph.json: узлов " + graph.nodes.size());
        String script = readText(new File(dir, "main.angelscript"));
        check(script.contains("gs_block_"), "main.angelscript содержит блоки");

        String mapText = readText(new File(dir, "main.map"));
        check(mapText.contains("\"classname\" \"worldspawn\""), "main.map: worldspawn есть");
        check(mapText.contains("halflife.wad;") && mapText.contains("textures.wad"),
                "main.map: оба WAD в worldspawn");
        int faceLines = countOccurrences(mapText, " 0 1 1\n");
        int brushEntityFaces = 0;
        for (BrushEntity brushEntity : reload.brushEntities) {
            brushEntityFaces += brushEntity.brushes.size() * 6;
        }
        int expectedFaces = map.brushes.size() * 6 + brushEntityFaces;
        check(faceLines == expectedFaces,
                "main.map: граней " + faceLines + " (ожидалось " + expectedFaces + ")");
        check(mapText.contains("\"classname\" \"monster_human_grunt\""),
                "main.map: солдаты HECU на месте");
        check(mapText.contains("\"targetname\" \"boss\""), "main.map: командир на месте");
        check(mapText.contains("\"targetname\" \"warlord\""), "main.map: командир окопов на месте");
        check(countOccurrences(mapText, "\"classname\" \"monster_barney\"") >= 7,
                "main.map: отряд наших в окопах (Барни)");
        check(mapText.contains("\"classname\" \"func_door\""), "main.map: func_door на месте");
        check(mapText.contains("\"wait\" \"-1\""), "main.map: дверь не закрывается сама");
        check(!mapText.contains("\"light\" \""),
                "main.map: устаревшего ключа light нет (все лампы на _light)");
        check(mapText.contains("\"_light\" \"300\"") && mapText.contains("\"_light\" \"220\""),
                "main.map: яркости ламп на месте");
    }

    private static int countOccurrences(String text, String needle) {
        int count = 0;
        int index = 0;
        while ((index = text.indexOf(needle, index)) >= 0) {
            count++;
            index += needle.length();
        }
        return count;
    }

    private static void check(boolean condition, String label) {
        if (condition) {
            System.out.println("[PASS] " + label);
        } else {
            failed++;
            System.out.println("[FAIL] " + label);
        }
    }

    private static String readText(File file) throws IOException {
        StringBuilder builder = new StringBuilder();
        try (InputStreamReader reader = new InputStreamReader(
                new FileInputStream(file), StandardCharsets.UTF_8)) {
            char[] buffer = new char[4096];
            int read;
            while ((read = reader.read(buffer)) > 0) {
                builder.append(buffer, 0, read);
            }
        }
        return builder.toString();
    }

    private static void writeText(File file, String text) throws IOException {
        File parent = file.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("Не удалось создать каталог: " + parent);
        }
        try (OutputStreamWriter writer = new OutputStreamWriter(
                new FileOutputStream(file), StandardCharsets.UTF_8)) {
            writer.write(text);
        }
    }
}
