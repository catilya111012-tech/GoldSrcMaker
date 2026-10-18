package com.goldsrc.maker.core;

public class TextureImage {

    public static final int PALETTE_SIZE = 256;

    public String name;
    public final int width;
    public final int height;
    public final byte[] pixels;
    public final int[] palette;

    public TextureImage(String name, int width, int height) {
        this(name, width, height, new byte[width * height], defaultPalette());
    }

    public TextureImage(String name, int width, int height, byte[] pixels, int[] palette) {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Размер текстуры должен быть > 0: " + width + "x" + height);
        }
        if (pixels.length != width * height) {
            throw new IllegalArgumentException("Пикселей " + pixels.length + ", ожидалось " + (width * height));
        }
        this.name = sanitizeName(name);
        this.width = width;
        this.height = height;
        this.pixels = pixels;
        this.palette = normalizePalette(palette);
    }

    public int pixelIndex(int x, int y) {
        return pixels[y * width + x] & 0xFF;
    }

    public void setPixelIndex(int x, int y, int paletteIndex) {
        pixels[y * width + x] = (byte) (paletteIndex & 0xFF);
    }

    public int colorAt(int x, int y) {
        return palette[pixelIndex(x, y)];
    }

    public int argbColor(int paletteIndex) {
        return palette[paletteIndex & 0xFF];
    }

    public void fill(int paletteIndex) {
        for (int i = 0; i < pixels.length; i++) {
            pixels[i] = (byte) (paletteIndex & 0xFF);
        }
    }

    public TextureImage copy() {
        return new TextureImage(name, width, height, pixels.clone(), palette.clone());
    }

    public static String sanitizeName(String name) {
        String clean = name == null ? "" : name.trim().toUpperCase(java.util.Locale.ROOT);
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < clean.length() && builder.length() < 15; i++) {
            char c = clean.charAt(i);
            if (Character.isLetterOrDigit(c) || c == '_' || c == '-' || c == '.' || c == '{' || c == '}' || c == '~') {
                builder.append(c);
            }
        }
        return builder.toString();
    }

    public static int[] defaultPalette() {
        int[] palette = new int[PALETTE_SIZE];
        palette[0] = 0xFF000000;
        for (int i = 1; i < PALETTE_SIZE; i++) {
            int r = (i >> 5) & 0x07;
            int g = (i >> 2) & 0x07;
            int b = i & 0x03;
            palette[i] = 0xFF000000
                    | (r * 255 / 7 << 16)
                    | (g * 255 / 7 << 8)
                    | (b * 255 / 3);
        }
        return palette;
    }

    private static int[] normalizePalette(int[] palette) {
        int[] result = new int[PALETTE_SIZE];
        if (palette != null) {
            System.arraycopy(palette, 0, result, 0, Math.min(palette.length, PALETTE_SIZE));
        }
        for (int i = 0; i < PALETTE_SIZE; i++) {
            if ((result[i] >>> 24) == 0 && result[i] != 0) {
                result[i] = 0xFF000000 | (result[i] & 0xFFFFFF);
            }
        }
        return result;
    }
}
