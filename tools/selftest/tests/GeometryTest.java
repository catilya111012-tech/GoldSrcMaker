import com.goldsrc.maker.core.ModelData;
import com.goldsrc.maker.editor.Brush;
import com.goldsrc.maker.editor.EntityData;
import com.goldsrc.maker.editor.MapData;
import com.goldsrc.maker.render.GeometryBuilder;

public class GeometryTest {

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
        MapData empty = new MapData();
        check(GeometryBuilder.build(empty).length == 0, "пустая карта — пустая геометрия");
        check(GeometryBuilder.build(null).length == 0, "null-карта — пустая геометрия");

        MapData map = new MapData();
        map.brushes.add(new Brush(0, 0, 0, 64, 64, 128, "FLOOR_TILE01"));
        float[] box = GeometryBuilder.build(map);
        check(box.length == GeometryBuilder.VERTICES_PER_BOX * GeometryBuilder.FLOATS_PER_VERTEX,
                "один браш = 36 вершин = 216 float (получено " + box.length + ")");
        check(GeometryBuilder.vertexCount(box) == GeometryBuilder.VERTICES_PER_BOX,
                "vertexCount = 36");

        float minX = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE;
        float minZ = Float.MAX_VALUE;
        float maxZ = -Float.MAX_VALUE;
        boolean colorsInRange = true;
        for (int i = 0; i < box.length; i += GeometryBuilder.FLOATS_PER_VERTEX) {
            minX = Math.min(minX, box[i]);
            maxX = Math.max(maxX, box[i]);
            minZ = Math.min(minZ, box[i + 2]);
            maxZ = Math.max(maxZ, box[i + 2]);
            for (int c = 3; c < 6; c++) {
                if (box[i + c] < 0f || box[i + c] > 1f) {
                    colorsInRange = false;
                }
            }
        }
        check(minX == 0f && maxX == 64f, "X браша в границах [0;64] (min " + minX + ", max " + maxX + ")");
        check(minZ == 0f && maxZ == 128f, "Z браша в границах [0;128] (min " + minZ + ", max " + maxZ + ")");
        check(colorsInRange, "цвета всех вершин в диапазоне 0..1");

        float topBrightness = -1f;
        float bottomBrightness = -1f;
        for (int i = 0; i < box.length; i += GeometryBuilder.FLOATS_PER_VERTEX) {
            float brightness = box[i + 3] + box[i + 4] + box[i + 5];
            if (box[i + 2] == 128f) {
                topBrightness = Math.max(topBrightness, brightness);
            }
            if (box[i + 2] == 0f) {
                bottomBrightness = Math.max(bottomBrightness, brightness);
            }
        }
        check(topBrightness > 0f && topBrightness > bottomBrightness,
                "верхняя грань светлее нижней (" + topBrightness + " > " + bottomBrightness + ")");

        MapData withEntity = new MapData();
        EntityData entity = new EntityData("info_player_start");
        entity.setOrigin(100, 100, 24);
        withEntity.entities.add(entity);
        float[] entityCube = GeometryBuilder.build(withEntity);
        check(entityCube.length == 216, "энтити = кубик из 36 вершин");
        float cubeCenterZ = 24 + GeometryBuilder.ENTITY_CUBE_Z_OFFSET;
        float half = GeometryBuilder.ENTITY_CUBE_SIZE;
        boolean entityInBounds = true;
        for (int i = 0; i < entityCube.length; i += GeometryBuilder.FLOATS_PER_VERTEX) {
            if (entityCube[i] < 100 - half || entityCube[i] > 100 + half
                    || entityCube[i + 1] < 100 - half || entityCube[i + 1] > 100 + half
                    || entityCube[i + 2] < cubeCenterZ - half || entityCube[i + 2] > cubeCenterZ + half) {
                entityInBounds = false;
            }
        }
        check(entityInBounds, "кубик энтити в пределах ±" + half + " от origin");

        MapData big = new MapData();
        for (int i = 0; i < 4000; i++) {
            big.brushes.add(new Brush(i * 128, 0, 0, i * 128 + 64, 64, 64, "T" + i));
        }
        float[] capped = GeometryBuilder.build(big);
        check(GeometryBuilder.vertexCount(capped) == GeometryBuilder.MAX_VERTICES,
                "слишком большая карта усечена до " + GeometryBuilder.MAX_VERTICES + " вершин (получено "
                        + GeometryBuilder.vertexCount(capped) + ")");

        ModelData model = new ModelData("m");
        model.parts.add(new ModelData.Part("body", "A", 0, 0, 16, 24, 24, 32, 1.0f));
        model.parts.add(new ModelData.Part("head", "B", 0, 0, 42, 14, 14, 14, 1.0f));
        float[] modelGeometry = GeometryBuilder.buildModel(model);
        check(GeometryBuilder.vertexCount(modelGeometry)
                        == 2 * GeometryBuilder.VERTICES_PER_BOX,
                "модель из двух частей = 72 вершины (получено "
                        + GeometryBuilder.vertexCount(modelGeometry) + ")");
        float modelMinZ = Float.MAX_VALUE;
        float modelMaxZ = -Float.MAX_VALUE;
        for (int i = 0; i < modelGeometry.length; i += GeometryBuilder.FLOATS_PER_VERTEX) {
            modelMinZ = Math.min(modelMinZ, modelGeometry[i + 2]);
            modelMaxZ = Math.max(modelMaxZ, modelGeometry[i + 2]);
        }
        check(modelMinZ == 0f && modelMaxZ == 49f,
                "Z модели в границах [0;49] (min " + modelMinZ + ", max " + modelMaxZ + ")");
        check(GeometryBuilder.buildModel(new ModelData("empty")).length == 0,
                "модель без частей — пустая геометрия");

        if (failed > 0) {
            System.out.println("FAILED GEOMETRY TESTS: " + failed);
            System.exit(1);
        }
        System.out.println("ALL GEOMETRY TESTS PASSED");
    }
}
