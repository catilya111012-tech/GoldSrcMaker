import com.goldsrc.maker.core.ModelData;
import com.goldsrc.maker.core.ModelStore;

import java.io.File;
import java.nio.file.Files;

public class ModelStoreTest {

    private static int failures = 0;

    public static void main(String[] args) throws Exception {
        File tempDir = Files.createTempDirectory("goldsrc-model-test").toFile();
        ModelStore store = new ModelStore(tempDir);

        ModelData starter = store.loadOrCreateStarter("demo_project");
        check(starter != null, "стартовая модель создана");
        check(starter.parts.size() == 2, "у стартовой модели две части: " + starter.parts.size());
        check(new File(tempDir, "models/DEMO_PROJECT.model.json").isFile(),
                "файл модели существует на диске");
        check(store.exists("DEMO_PROJECT"), "exists() видит модель");
        check(store.list().contains("DEMO_PROJECT"), "list() видит модель: " + store.list());

        ModelData loaded = store.load("DEMO_PROJECT");
        check(loaded != null && loaded.parts.size() == 2, "загрузка возвращает 2 части");
        check(Math.abs(loaded.parts.get(0).sz - 32f) < 0.001f, "размер part sz сохранён");
        check(loaded.parts.get(0).texture.equals("C1A0_WX"), "текстура part сохранена");

        loaded.addPart(new ModelData.Part("arm", "c1a0_wx", 20, 0, 24, 6, 6, 20, 2.0f));
        store.save(loaded);
        ModelData reloaded = store.load("DEMO_PROJECT");
        check(reloaded.parts.size() == 3, "третья часть сохранена и прочитана");
        check(reloaded.parts.get(2).uvScale == 2.0f, "uvScale сохранён");
        check(reloaded.parts.get(2).texture.equals("C1A0_WX"),
                "имя текстуры нормализовано в верхний регистр: " + reloaded.parts.get(2).texture);

        float[] bounds = reloaded.bounds();
        check(bounds[0] < 0 && bounds[3] > 0, "bounds вычисляются: minX=" + bounds[0] + " maxX=" + bounds[3]);
        check(bounds[2] <= 0 && bounds[5] >= 49, "bounds по Z: " + bounds[2] + ".." + bounds[5]);

        ModelData clamped = ModelData.fromJson(
                new ModelDataFromJsonHelper().json());
        check(clamped.parts.get(0).sx >= ModelData.MIN_SIZE, "слишком маленький размер поднят до MIN_SIZE");
        check(clamped.parts.get(0).sz <= ModelData.MAX_SIZE, "слишком большой размер срезан до MAX_SIZE");
        check(clamped.parts.get(0).uvScale >= 0.1f && clamped.parts.get(0).uvScale <= 8f,
                "uvScale ограничен");

        ModelStore second = new ModelStore(tempDir);
        check(second.exists("demo_project"), "имя модели регистронезависимо");

        check(second.delete("DEMO_PROJECT"), "удаление модели");
        check(!second.exists("DEMO_PROJECT"), "после удаления модели нет");
        check(second.list().isEmpty(), "список пуст после удаления");

        ModelData empty = new ModelData("test");
        try {
            store.save(empty);
            check(store.load("test") != null, "пустая модель сохраняется и читается");
        } finally {
            deleteRecursively(tempDir);
        }

        if (failures == 0) {
            System.out.println("ALL MODEL STORE TESTS PASSED");
        } else {
            System.out.println(failures + " FAILURES");
            System.exit(1);
        }
    }

    private static class ModelDataFromJsonHelper {
        org.json.JSONObject json() throws Exception {
            org.json.JSONObject root = new org.json.JSONObject();
            root.put("name", "clamps");
            org.json.JSONArray parts = new org.json.JSONArray();
            org.json.JSONObject part = new org.json.JSONObject();
            part.put("name", "tiny");
            part.put("texture", "bad name!@#");
            part.put("sx", 0.01);
            part.put("sz", 99999);
            part.put("uv", 100);
            parts.put(part);
            root.put("parts", parts);
            return root;
        }
    }

    private static void deleteRecursively(File file) {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursively(child);
                }
            }
        }
        file.delete();
    }

    private static void check(boolean condition, String label) {
        if (condition) {
            System.out.println("[PASS] " + label);
        } else {
            failures++;
            System.out.println("[FAIL] " + label);
        }
    }
}
