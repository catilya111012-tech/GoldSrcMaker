import com.goldsrc.maker.editor.EntityCatalog;
import com.goldsrc.maker.editor.EntityData;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class EntityCatalogTest {

    private static int failed = 0;

    private static void check(boolean condition, String name) {
        if (condition) {
            System.out.println("[PASS] " + name);
        } else {
            failed++;
            System.out.println("[FAIL] " + name);
        }
    }

    public static void main(String[] args) {
        String[] names = EntityCatalog.classNames();

        check(names.length >= 10, "каталог содержит не меньше 10 классов (их " + names.length + ")");
        check(names[0].equals("info_player_start"), "первым идёт info_player_start");

        boolean allHaveHelp = true;
        boolean allHaveFields = true;
        boolean allHaveTargetname = true;
        boolean noOriginDefaults = true;
        boolean healsHaveFields = true;
        for (String name : names) {
            String help = EntityCatalog.help(name);
            if (help == null || help.isEmpty()) {
                allHaveHelp = false;
            }
            List<EntityCatalog.FieldSpec> fields = EntityCatalog.fields(name);
            if (fields.isEmpty()) {
                allHaveFields = false;
            }
            boolean hasTargetname = false;
            boolean anyEmptyKey = false;
            for (EntityCatalog.FieldSpec spec : fields) {
                if ("targetname".equals(spec.key)) {
                    hasTargetname = true;
                }
                if (spec.key == null || spec.key.trim().isEmpty() || spec.label == null || spec.label.isEmpty()) {
                    anyEmptyKey = true;
                }
            }
            if (!hasTargetname && !name.equals("info_player_start")) {
                allHaveTargetname = false;
            }
            LinkedHashMap<String, String> defaults = EntityCatalog.defaults(name);
            if (defaults.containsKey("origin")) {
                noOriginDefaults = false;
            }
            if (anyEmptyKey) {
                healsHaveFields = false;
            }
        }
        check(allHaveHelp, "у каждого класса есть справка");
        check(allHaveFields, "у каждого класса есть редактируемые поля");
        check(healsHaveFields, "нет пустых ключей/подписей в полях");
        check(noOriginDefaults, "каталог не задаёт origin в defaults (позицию ставит редактор)");
        check(allHaveTargetname, "у gameplay-классов есть поле targetname");

        check("300".equals(EntityCatalog.defaults("light").get("light")),
                "у light яркость по умолчанию 300");
        check("0".equals(EntityCatalog.defaults("info_player_start").get("angles")),
                "у info_player_start angles по умолчанию 0");
        check(!EntityCatalog.defaults("light").containsKey("targetname"),
                "пустой targetname не попадает в defaults");
        check(EntityCatalog.defaults("unknown_custom").isEmpty(),
                "неизвестный класс не имеет defaults");

        List<EntityCatalog.FieldSpec> unknownFields = EntityCatalog.fields("weapon_custom");
        check(unknownFields.size() == 2
                        && "targetname".equals(unknownFields.get(0).key)
                        && "target".equals(unknownFields.get(1).key),
                "неизвестный класс получает generic targetname/target");

        boolean lightNumeric = false;
        boolean targetnameText = false;
        for (EntityCatalog.FieldSpec spec : EntityCatalog.fields("light")) {
            if ("light".equals(spec.key)) {
                lightNumeric = spec.numeric;
            }
            if ("targetname".equals(spec.key)) {
                targetnameText = !spec.numeric;
            }
        }
        check(lightNumeric, "поле light помечено числовым");
        check(targetnameText, "поле targetname — строковое");

        check(EntityCatalog.title("light").toLowerCase().contains("свет"),
                "у light человекочитаемый заголовок");

        EntityData entity = new EntityData("light");
        for (Map.Entry<String, String> entry : EntityCatalog.defaults("light").entrySet()) {
            entity.setKey(entry.getKey(), entry.getValue());
        }
        entity.setOrigin(64, 128, 24);
        StringBuilder map = new StringBuilder();
        entity.appendMapText(map);
        String text = map.toString();
        check(text.contains("\"classname\" \"light\""), "light пишется в .map как classname light");
        check(text.contains("\"light\" \"300\""), "яркость 300 попадает в .map");
        check(text.contains("\"origin\" \"64 128 24\""), "origin попадает в .map");

        if (failed > 0) {
            System.out.println("FAILED ENTITY CATALOG TESTS: " + failed);
            System.exit(1);
        }
        System.out.println("ALL ENTITY CATALOG TESTS PASSED");
    }
}
