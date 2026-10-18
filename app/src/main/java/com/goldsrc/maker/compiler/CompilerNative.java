package com.goldsrc.maker.compiler;

public final class CompilerNative {

    public static final int STAGE_CSG = 0;
    public static final int STAGE_BSP = 1;
    public static final int STAGE_VIS = 2;
    public static final int STAGE_RAD = 3;

    public static final int RESULT_MISSING_BINARY = 2;
    public static final int RESULT_SPAWN_FAILED = 3;

    private static final boolean LIBRARY_LOADED;

    static {
        boolean loaded;
        try {
            System.loadLibrary("goldsrc_native");
            loaded = true;
        } catch (UnsatisfiedLinkError e) {
            loaded = false;
        }
        LIBRARY_LOADED = loaded;
    }

    private CompilerNative() {
    }

    public static boolean isAvailable() {
        return LIBRARY_LOADED;
    }

    public static native int runStage(int stage, String toolDir, String workDir, String mapName);
}
