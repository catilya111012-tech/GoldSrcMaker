import com.goldsrc.maker.core.TextureImage;
import com.goldsrc.maker.core.TextureStore;

import java.io.File;
import java.util.List;

public class TextureStoreTest {

    private static int failed = 0;

    private static void check(boolean condition, String name) {
        if (condition) {
            System.out.println("[PASS] " + name);
        } else {
            failed++;
            System.out.println("[FAIL] " + name);
        }
    }

    public static void main(String[] args) throws Exception {
        File projectDir = new File(System.getProperty("java.io.tmpdir"), "goldsrc_texstore_test");
        deleteRecursively(projectDir);
        projectDir.mkdirs();

        TextureStore store = new TextureStore(projectDir);
        check(!store.exists(), "хранилище пустое до первой записи");
        check(store.loadAll().isEmpty(), "loadAll на пустом хранилище — пусто");

        int[] palette = TextureImage.defaultPalette();
        TextureImage brick = new TextureImage("BRICK_01", 32, 32, new byte[32 * 32], palette);
        brick.fill(7);
        store.save(brick);
        check(store.exists(), "после save хранилище существует");
        check(store.has("brick_01"), "текстура находится по имени в любом регистре");

        TextureImage read = store.load("BRICK_01");
        check(read != null && read.width == 32 && read.width == 32, "размер сохранился 32x32");
        check(read.pixelIndex(0, 0) == 7 && read.pixelIndex(31, 31) == 7, "пиксели сохранились");

        TextureImage metal = new TextureImage("METAL_02", 64, 64, new byte[64 * 64], palette);
        metal.fill(100);
        store.save(metal);
        List<TextureImage> all = store.loadAll();
        check(all.size() == 2, "две текстуры в хранилище (их " + all.size() + ")");

        brick.fill(9);
        store.save(brick);
        read = store.load("BRICK_01");
        check(store.count() == 2, "повторный save не создаёт дубликат");
        check(read.pixelIndex(5, 5) == 9, "повторный save обновляет пиксели");

        boolean deleted = store.delete("metal_02");
        check(deleted, "удаление существующей текстуры прошло");
        check(store.count() == 1, "осталась одна текстура");
        check(!store.delete("НЕТ_ТАКОЙ"), "удаление несуществующей — false");
        check(store.load("METAL_02") == null, "удалённая текстура не читается");

        File wad = store.getWadFile();
        check(wad.getName().equals("textures.wad") && wad.getParentFile().equals(projectDir),
                "файл хранилища — projects/<имя>/textures.wad");

        if (failed > 0) {
            System.out.println("FAILED TEXTURE STORE TESTS: " + failed);
            System.exit(1);
        }
        System.out.println("ALL TEXTURE STORE TESTS PASSED");
    }

    private static void deleteRecursively(File file) {
        if (file == null || !file.exists()) {
            return;
        }
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) {
                deleteRecursively(child);
            }
        }
        file.delete();
    }
}
