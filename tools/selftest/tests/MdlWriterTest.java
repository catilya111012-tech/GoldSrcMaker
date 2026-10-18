import com.goldsrc.maker.core.MdlWriter;
import com.goldsrc.maker.core.ModelData;
import com.goldsrc.maker.core.TextureImage;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class MdlWriterTest {

    private static int failures = 0;

    public static void main(String[] args) throws Exception {
        File tempDir = Files.createTempDirectory("goldsrc-mdl-test").toFile();
        File out = new File(tempDir, "demo.mdl");

        ModelData model = new ModelData("demo");
        model.parts.add(new ModelData.Part("body", "WALL_A", 0, 0, 16, 24, 24, 32, 1.0f));
        model.parts.add(new ModelData.Part("head", "WALL_B", 0, 0, 42, 14, 14, 14, 1.0f));

        TextureImage wallA = checker("WALL_A", 64, 64, 15, 63);
        TextureImage wallB = checker("WALL_B", 32, 32, 200, 201);
        List<TextureImage> textures = new ArrayList<>();
        textures.add(wallA);
        textures.add(wallB);

        MdlWriter.write(out, model, textures, null);

        byte[] data = Files.readAllBytes(out.toPath());
        check(data.length > 1000, "файл записан, размер " + data.length);

        String magic = new String(data, 0, 4, StandardCharsets.US_ASCII);
        check("IDST".equals(magic), "сигнатура IDST: " + magic);
        check(intAt(data, 4) == 10, "версия 10");
        check(intAt(data, 72) == data.length, "length в заголовке == размер файла");

        String name = cString(data, 8, 64);
        check(name.equals("demo"), "имя модели: " + name);

        int numbones = intAt(data, 140);
        int boneindex = intAt(data, 144);
        check(numbones == 1, "одна кость");
        check(boneindex == MdlWriter.HEADER_SIZE, "boneindex == 244");
        check(intAt(data, boneindex + 32) == -1, "у кости нет родителя");

        int numseq = intAt(data, 164);
        int seqindex = intAt(data, 168);
        check(numseq == 1, "одна последовательность");
        check(intAt(data, seqindex + 56) == 1, "numframes == 1");
        int animindex = intAt(data, seqindex + 124);
        check(animindex > 0 && animindex < data.length, "animindex в пределах файла: " + animindex);

        int numseqgroups = intAt(data, 172);
        int seqgroupindex = intAt(data, 176);
        check(numseqgroups == 1, "одна группа последовательностей");
        String groupLabel = cString(data, seqgroupindex, 32);
        check("default".equals(groupLabel), "группа 0 называется default: " + groupLabel);

        int numtextures = intAt(data, 180);
        int textureindex = intAt(data, 184);
        int texturedataindex = intAt(data, 188);
        check(numtextures == 2, "две текстуры (по одной на часть)");
        check(texturedataindex == textureindex + numtextures * MdlWriter.TEXTURE_SIZE,
                "texturedataindex сразу за структурами текстур");

        boolean foundA = false;
        boolean foundB = false;
        for (int i = 0; i < numtextures; i++) {
            int base = textureindex + i * MdlWriter.TEXTURE_SIZE;
            String texName = cString(data, base, 64);
            int width = intAt(data, base + 68);
            int height = intAt(data, base + 72);
            int index = intAt(data, base + 76);
            check(width == 64 || width == 32, "ширина " + texName + " = " + width);
            check(height == width, "высота совпадает для " + texName);
            check(index >= texturedataindex && index < data.length,
                    "данные " + texName + " в файле: " + index);
            int expectedMipSize = width * height + width * height / 4 + width * height / 16
                    + Math.max(1, width * height / 64);
            check(index + expectedMipSize <= data.length,
                    "мип-цепочка " + texName + " помещается (" + expectedMipSize + " байт)");
            if ("wall_a".equals(texName)) {
                foundA = true;
            }
            if ("wall_b".equals(texName)) {
                foundB = true;
            }
        }
        check(foundA && foundB, "имена текстур в нижнем регистре: wall_a, wall_b");

        int numskinref = intAt(data, 192);
        int numskinfamilies = intAt(data, 196);
        int skinindex = intAt(data, 200);
        check(numskinref == 2 && numskinfamilies == 1, "1 семейство скинов, 2 ссылки");
        check(shortAt(data, skinindex) == 0 && shortAt(data, skinindex + 2) == 1,
                "скины 0 и 1");

        int numbodyparts = intAt(data, 204);
        int bodypartindex = intAt(data, 208);
        check(numbodyparts == 1, "одна bodypart");
        int modelindex = intAt(data, bodypartindex + 72);
        int nummesh = intAt(data, modelindex + 72);
        int meshindex = intAt(data, modelindex + 76);
        int numverts = intAt(data, modelindex + 80);
        int vertinfoindex = intAt(data, modelindex + 84);
        int vertindex = intAt(data, modelindex + 88);
        check(nummesh == 2, "две mesh (по части)");
        check(numverts == 16, "16 вершин (2 коробки по 8)");
        check(vertinfoindex > 0 && vertindex > vertinfoindex, "vertinfo до vertindex");

        boolean allBoneZero = true;
        for (int i = 0; i < numverts; i++) {
            if (data[vertinfoindex + i] != 0) {
                allBoneZero = false;
            }
        }
        check(allBoneZero, "все вершины привязаны к кости 0");

        float firstX = floatAt(data, vertindex);
        float firstY = floatAt(data, vertindex + 4);
        float firstZ = floatAt(data, vertindex + 8);
        check(Math.abs(firstX - (-12f)) < 0.001f, "x первой вершины = -12: " + firstX);
        check(Math.abs(firstY - (-12f)) < 0.001f, "y первой вершины = -12: " + firstY);
        check(Math.abs(firstZ - 0f) < 0.001f, "z первой вершины = 0: " + firstZ);
        float topZ = floatAt(data, vertindex + 7 * 12 + 8);
        check(Math.abs(topZ - 32f) < 0.001f, "z последней вершины коробки = 32: " + topZ);

        int totalTris = 0;
        int maxVertIndex = -1;
        for (int m = 0; m < nummesh; m++) {
            int base = meshindex + m * MdlWriter.MESH_SIZE;
            int numtris = intAt(data, base);
            int triindex = intAt(data, base + 4);
            int skinref = intAt(data, base + 8);
            check(numtris == 12, "mesh " + m + ": 12 треугольников");
            check(skinref == m, "mesh " + m + " использует свою текстуру");
            for (int t = 0; t < numtris * 3; t++) {
                int v = shortAt(data, triindex + t * 2);
                maxVertIndex = Math.max(maxVertIndex, v);
            }
            totalTris += numtris;
        }
        check(totalTris == 24, "всего 24 треугольника");
        check(maxVertIndex < numverts, "индексы вершин валидны: max=" + maxVertIndex);

        check(shortAt(data, animindex) == 12, "offset[0] анимации = 12");
        check(shortAt(data, animindex + 10) == 22, "offset[5] анимации = 22");
        boolean animZeros = true;
        for (int i = 0; i < 6; i++) {
            if (shortAt(data, animindex + 12 + i * 2) != 0) {
                animZeros = false;
            }
        }
        check(animZeros, "кадровые значения анимации нулевые");
        check(animindex + 24 == data.length, "блок анимации — конец файла");

        File noTexOut = new File(tempDir, "notex.mdl");
        MdlWriter.write(noTexOut, ModelData.createDefault("notex"), new ArrayList<>(), null);
        byte[] noTexData = Files.readAllBytes(noTexOut.toPath());
        check(intAt(noTexData, 180) == 2, "без текстур создаются дефолтные (2 части)");
        String defaultName = cString(noTexData, intAt(noTexData, 184), 64);
        check(defaultName.startsWith("dev_"), "имя дефолтной текстуры: " + defaultName);

        System.out.println("состояние: " + Arrays.toString(new String[]{
                "размер=" + data.length,
                "текстуры=" + numtextures,
                "вершин=" + numverts}));

        deleteRecursively(tempDir);

        if (failures == 0) {
            System.out.println("ALL MDL WRITER TESTS PASSED");
        } else {
            System.out.println(failures + " FAILURES");
            System.exit(1);
        }
    }

    private static TextureImage checker(String name, int size, int size2, int a, int b) {
        byte[] pixels = new byte[size * size2];
        for (int y = 0; y < size2; y++) {
            for (int x = 0; x < size; x++) {
                pixels[y * size + x] = (byte) (((x / 8) + (y / 8)) % 2 == 0 ? a : b);
            }
        }
        return new TextureImage(name, size, size2, pixels, TextureImage.defaultPalette());
    }

    private static int intAt(byte[] data, int offset) {
        return (data[offset] & 0xFF) | ((data[offset + 1] & 0xFF) << 8)
                | ((data[offset + 2] & 0xFF) << 16) | ((data[offset + 3] & 0xFF) << 24);
    }

    private static int shortAt(byte[] data, int offset) {
        return (data[offset] & 0xFF) | ((data[offset + 1] & 0xFF) << 8);
    }

    private static float floatAt(byte[] data, int offset) {
        return Float.intBitsToFloat(intAt(data, offset));
    }

    private static String cString(byte[] data, int offset, int max) {
        int end = offset;
        while (end < offset + max && data[end] != 0) {
            end++;
        }
        return new String(data, offset, end - offset, StandardCharsets.UTF_8);
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
