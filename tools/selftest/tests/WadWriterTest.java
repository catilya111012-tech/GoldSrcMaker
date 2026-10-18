import com.goldsrc.maker.core.TextureImage;
import com.goldsrc.maker.core.WadReader;
import com.goldsrc.maker.core.WadWriter;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class WadWriterTest {

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
        File dir = new File(System.getProperty("java.io.tmpdir"), "goldsrc_wadwriter_test");
        dir.mkdirs();
        File wad = new File(dir, "test.wad");
        File patched = new File(dir, "patched.wad");
        if (wad.exists()) {
            wad.delete();
        }
        if (patched.exists()) {
            patched.delete();
        }

        int[] palette = TextureImage.defaultPalette();

        TextureImage texA = new TextureImage("TEST_A", 16, 16, new byte[16 * 16], palette);
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                texA.setPixelIndex(x, y, (x + y * 16) & 0xFF);
            }
        }
        TextureImage texB = new TextureImage("TEST_B", 8, 8, new byte[64], palette);
        texB.fill(5);

        List<TextureImage> textures = new ArrayList<>();
        textures.add(texA);
        textures.add(texB);
        WadWriter.write(wad, textures);

        check(wad.isFile() && wad.length() > 0, "WAD-файл создан (" + wad.length() + " байт)");

        List<String> names = WadReader.listTextures(wad);
        check(names.size() == 2 && names.contains("TEST_A") && names.contains("TEST_B"),
                "обе текстуры видны в списке: " + names);

        TextureImage readA = WadReader.readTexture(wad, "test_a");
        check(readA != null, "текстура TEST_A читается (нечувствительно к регистру)");
        check(readA.width == 16 && readA.height == 16, "размер TEST_A 16x16");
        boolean pixelsEqual = true;
        for (int i = 0; i < texA.pixels.length; i++) {
            if (texA.pixels[i] != readA.pixels[i]) {
                pixelsEqual = false;
                break;
            }
        }
        check(pixelsEqual, "пиксели TEST_A совпадают после roundtrip");

        boolean paletteEqual = true;
        for (int i = 0; i < TextureImage.PALETTE_SIZE; i++) {
            if ((palette[i] & 0xFFFFFF) != (readA.palette[i] & 0xFFFFFF)) {
                paletteEqual = false;
                break;
            }
        }
        check(paletteEqual, "палитра TEST_A совпадает после roundtrip");

        int[] readPalette = WadReader.readPalette(wad);
        check(readPalette != null, "палитра вычитывается из WAD");
        boolean firstColor = readPalette[0] == (palette[0] | 0xFF000000)
                || (readPalette[0] & 0xFFFFFF) == (palette[0] & 0xFFFFFF);
        check(firstColor, "первый цвет палитры верен: " + Integer.toHexString(readPalette[0]));

        TextureImage readB = WadReader.readTexture(wad, "TEST_B");
        check(readB != null && readB.pixelIndex(0, 0) == 5 && readB.pixelIndex(7, 7) == 5,
                "TEST_B залит индексом 5");

        List<WadReader.WadLump> lumps = WadReader.readAllLumps(wad);
        boolean allMiptex = lumps.size() == 2;
        for (WadReader.WadLump lump : lumps) {
            if (lump.type != 0x43) {
                allMiptex = false;
            }
        }
        check(allMiptex, "оба лампа имеют тип 0x43 (miptex)");

        byte[] rawA = null;
        for (WadReader.WadLump lump : lumps) {
            if (lump.name.equals("TEST_A")) {
                rawA = lump.data;
            }
        }
        check(rawA != null, "сырой ламп TEST_A доступен");
        check(rawA.length == 40 + (16 * 16 + 8 * 8 + 4 * 4 + 2 * 2) + 770,
                "размер лампа с 4 мип-уровнями и палитрой: " + rawA.length);
        int offset0 = readInt(rawA, 24);
        int offset1 = readInt(rawA, 28);
        int offset2 = readInt(rawA, 32);
        int offset3 = readInt(rawA, 36);
        check(offset0 == 40 && offset1 == 40 + 256 && offset2 == offset1 + 64 && offset3 == offset2 + 16,
                "смещения мип-уровней корректны: " + offset0 + "/" + offset1 + "/" + offset2 + "/" + offset3);
        int paletteCount = (rawA[rawA.length - 770] & 0xFF) | ((rawA[rawA.length - 769] & 0xFF) << 8);
        check(paletteCount == 256, "в лампе записано 256 цветов палитры");

        List<TextureImage> replacements = new ArrayList<>();
        TextureImage texB2 = texB.copy();
        texB2.fill(200);
        texB2.name = "TEST_B";
        replacements.add(texB2);
        replacements.add(new TextureImage("NEW_C", 32, 32, new byte[32 * 32], palette));
        int patchedCount = WadWriter.patch(wad, replacements);
        check(patchedCount == 2, "патч обработал 2 замены");

        List<String> patchedNames = WadReader.listTextures(wad);
        check(patchedNames.size() == 3 && patchedNames.contains("NEW_C"),
                "после патча 3 текстуры: " + patchedNames);
        TextureImage patchedB = WadReader.readTexture(wad, "TEST_B");
        check(patchedB.pixelIndex(3, 3) == 200, "TEST_B заменена (индекс 200)");
        TextureImage patchedA = WadReader.readTexture(wad, "TEST_A");
        check(patchedA.pixelIndex(1, 1) == texA.pixelIndex(1, 1),
                "TEST_A не тронута патчем");
        TextureImage newC = WadReader.readTexture(wad, "NEW_C");
        check(newC != null && newC.width == 32, "NEW_C добавлена размером 32x32");

        WadWriter.write(patched, textures);
        File backup = new File(dir, "test.wad.bak");
        if (backup.exists()) {
            backup.delete();
        }
        WadWriter.write(backup, new ArrayList<>());
        check(backup.isFile() && WadReader.listTextures(backup).isEmpty(),
                "пустой WAD пишется и читается (заготовка бэкапа)");

        if (failed > 0) {
            System.out.println("FAILED WAD WRITER TESTS: " + failed);
            System.exit(1);
        }
        System.out.println("ALL WAD WRITER TESTS PASSED");
    }

    private static int readInt(byte[] data, int offset) {
        return (data[offset] & 0xFF)
                | ((data[offset + 1] & 0xFF) << 8)
                | ((data[offset + 2] & 0xFF) << 16)
                | ((data[offset + 3] & 0xFF) << 24);
    }
}
