import com.goldsrc.maker.core.WadReader;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;

public class WadReaderTest {

    private static int failures = 0;

    public static void main(String[] args) throws Exception {
        File wad = File.createTempFile("goldsrc_test", ".wad");
        wad.deleteOnExit();

        try (FileOutputStream out = new FileOutputStream(wad)) {
            ByteBuffer buffer = ByteBuffer.allocate(12 + 32 * 3).order(ByteOrder.LITTLE_ENDIAN);
            buffer.put("WAD3".getBytes("US-ASCII"));
            buffer.putInt(3);
            buffer.putInt(12);

            putEntry(buffer, 0, 0x43, "C1A0_WX");
            putEntry(buffer, 128, 0x40, "SKY_NIGHT");
            putEntry(buffer, 256, 0x43, "FLOOR_TILE01");

            out.write(buffer.array());
        }

        List<String> textures = WadReader.listTextures(wad);
        System.out.println("textures: " + textures);

        check(textures.size() == 2, "только текстурные лампы (0x43), без mip (0x40)");
        check(textures.contains("C1A0_WX"), "C1A0_WX найден");
        check(textures.contains("FLOOR_TILE01"), "FLOOR_TILE01 найден");
        check(!textures.contains("SKY_NIGHT"), "SKY_NIGHT не текстура и не попал в список");

        boolean threw = false;
        File notWad = File.createTempFile("goldsrc_bad", ".wad");
        notWad.deleteOnExit();
        try (FileOutputStream out = new FileOutputStream(notWad)) {
            out.write("NOPE".getBytes("US-ASCII"));
            out.write(new byte[64]);
        }
        try {
            WadReader.listTextures(notWad);
        } catch (Exception e) {
            threw = true;
        }
        check(threw, "не-WAD файл отклонён");

        if (failures == 0) {
            System.out.println("ALL WAD TESTS PASSED");
        } else {
            System.out.println(failures + " FAILURES");
            System.exit(1);
        }
    }

    private static void putEntry(ByteBuffer buffer, int filePos, int type, String name) throws Exception {
        buffer.putInt(filePos);
        buffer.putInt(0);
        buffer.putInt(0);
        buffer.put((byte) type);
        buffer.put((byte) 0);
        buffer.putShort((short) 0);
        byte[] nameBytes = new byte[16];
        byte[] raw = name.getBytes("US-ASCII");
        System.arraycopy(raw, 0, nameBytes, 0, Math.min(raw.length, 15));
        buffer.put(nameBytes);
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
