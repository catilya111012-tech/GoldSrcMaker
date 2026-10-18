import com.goldsrc.maker.editor.Brush;
import com.goldsrc.maker.editor.EntityData;
import com.goldsrc.maker.editor.MapData;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MapExportTest {

    private static int failures = 0;

    public static void main(String[] args) throws Exception {
        MapData map = new MapData();
        map.brushes.add(new Brush(-64, -64, -16, 64, 64, 0, "C1A0_WX"));
        EntityData light = new EntityData("light");
        light.setOrigin(0, 0, 64);
        light.setKey("light", "300");
        map.entities.add(light);

        String text = map.toMapText("/storage/emulated/0/Documents/GoldSrcMaker/valve/halflife.wad");
        System.out.println("----- exported .map -----");
        System.out.println(text);
        System.out.println("-------------------------");

        check(text.contains("\"classname\" \"worldspawn\""), "worldspawn присутствует");
        check(text.contains("\"wad\" \"/storage/emulated/0/Documents/GoldSrcMaker/valve/halflife.wad\""),
                "путь к WAD пользователя в worldspawn");

        String combined = map.toMapText("/valve/halflife.wad", "/projects/demo/textures.wad");
        check(combined.contains(
                        "\"wad\" \"/valve/halflife.wad;/projects/demo/textures.wad\""),
                "worldspawn поддерживает список WAD через ';'");
        check(!map.toMapText("/valve/halflife.wad", null).contains(";"),
                "без проектного WAD список не добавляется");
        check(text.contains("\"classname\" \"light\""), "point entity light присутствует");
        check(text.contains("\"light\" \"300\""), "keyvalue энтити присутствует");
        check(text.contains("C1A0_WX"), "текстура браша присутствует");

        int faceLines = countOccurrences(text, " ) ( ");
        check(faceLines == 12, "в браше 6 фейсов по 3 точки (маркеров ' ) ( ': " + faceLines + ")");

        Pattern face = Pattern.compile(
                "\\( (-?\\d+) (-?\\d+) (-?\\d+) \\) \\( (-?\\d+) (-?\\d+) (-?\\d+) \\) \\( (-?\\d+) (-?\\d+) (-?\\d+) \\) (\\S+)");
        Matcher matcher = face.matcher(text);
        int faces = 0;
        Set<String> normals = new HashSet<>();
        while (matcher.find()) {
            faces++;
            int ax = Integer.parseInt(matcher.group(1));
            int ay = Integer.parseInt(matcher.group(2));
            int az = Integer.parseInt(matcher.group(3));
            int bx = Integer.parseInt(matcher.group(4));
            int by = Integer.parseInt(matcher.group(5));
            int bz = Integer.parseInt(matcher.group(6));
            int cx = Integer.parseInt(matcher.group(7));
            int cy = Integer.parseInt(matcher.group(8));
            int cz = Integer.parseInt(matcher.group(9));

            int ux = bx - ax, uy = by - ay, uz = bz - az;
            int vx = cx - ax, vy = cy - ay, vz = cz - az;
            int nx = uy * vz - uz * vy;
            int ny = uz * vx - ux * vz;
            int nz = ux * vy - uy * vx;

            int g = gcd(gcd(Math.abs(nx), Math.abs(ny)), Math.abs(nz));
            if (g != 0) {
                nx /= g;
                ny /= g;
                nz /= g;
            }
            normals.add(nx + "," + ny + "," + nz);

            checkAxisAlignedFace(ax, ay, az, bx, by, bz, cx, cy, cz, nx, ny, nz, faces);
        }

        check(faces == 6, "распознано 6 фейсов браша");
        Set<String> expected = new HashSet<>();
        expected.add("1,0,0");
        expected.add("-1,0,0");
        expected.add("0,1,0");
        expected.add("0,-1,0");
        expected.add("0,0,1");
        expected.add("0,0,-1");
        check(normals.equals(expected), "нормали всех 6 фейсов смотрят наружу: " + normals);

        if (failures == 0) {
            System.out.println("ALL MAP TESTS PASSED");
        } else {
            System.out.println(failures + " FAILURES");
            System.exit(1);
        }
    }

    private static void checkAxisAlignedFace(int ax, int ay, int az, int bx, int by, int bz,
                                             int cx, int cy, int cz,
                                             int nx, int ny, int nz, int faceIndex) {
        int coordinate;
        if (nx != 0) {
            coordinate = ax;
            check(ax == bx && bx == cx, "фейс " + faceIndex + ": плоскость X выровнена");
            check(coordinate == (nx > 0 ? 64 : -64), "фейс " + faceIndex + ": сторона X верная");
        } else if (ny != 0) {
            coordinate = ay;
            check(ay == by && by == cy, "фейс " + faceIndex + ": плоскость Y выровнена");
            check(coordinate == (ny > 0 ? 64 : -64), "фейс " + faceIndex + ": сторона Y верная");
        } else if (nz != 0) {
            coordinate = az;
            check(az == bz && bz == cz, "фейс " + faceIndex + ": плоскость Z выровнена");
            check(coordinate == (nz > 0 ? 0 : -16), "фейс " + faceIndex + ": сторона Z верная");
        } else {
            check(false, "фейс " + faceIndex + ": нулевая нормаль");
        }
    }

    private static int gcd(int a, int b) {
        return b == 0 ? a : gcd(b, a % b);
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
            failures++;
            System.out.println("[FAIL] " + label);
        }
    }
}
