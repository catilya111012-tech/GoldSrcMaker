package com.goldsrc.maker.compiler;

import android.content.Context;

import com.goldsrc.maker.core.StorageManager;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public final class CompilerToolchain {

    public enum Location {
        APK_LIBS,
        IMPORTED,
        MISSING
    }

    public static final class ToolStatus {
        public final String stageName;
        public final File file;
        public final Location location;
        public final boolean executable;
        public final String detail;

        ToolStatus(String stageName, File file, Location location, boolean executable, String detail) {
            this.stageName = stageName;
            this.file = file;
            this.location = location;
            this.executable = executable;
            this.detail = detail;
        }
    }

    private static final long PROBE_TIMEOUT_SECONDS = 8;

    private CompilerToolchain() {
    }

    public static File resolveNativeLibDir(Context context) {
        return new File(context.getApplicationInfo().nativeLibraryDir);
    }

    public static File resolveTool(Context context, int stage) {
        File inApk = new File(resolveNativeLibDir(context),
                "lib" + MapCompiler.STAGE_BINARIES[stage] + ".so");
        if (inApk.isFile()) {
            return inApk;
        }
        File imported = new File(new StorageManager(context).getToolsDir(),
                "lib" + MapCompiler.STAGE_BINARIES[stage] + ".so");
        if (imported.isFile()) {
            return imported;
        }
        return null;
    }

    public static Location locationOf(Context context, File tool) {
        if (tool == null) {
            return Location.MISSING;
        }
        try {
            String apkPrefix = resolveNativeLibDir(context).getCanonicalPath() + File.separator;
            if (tool.getCanonicalPath().startsWith(apkPrefix)) {
                return Location.APK_LIBS;
            }
        } catch (IOException ignored) {
        }
        return Location.IMPORTED;
    }

    public static List<ToolStatus> probeAll(Context context) {
        List<ToolStatus> statuses = new ArrayList<>();
        for (int stage = 0; stage < MapCompiler.STAGE_NAMES.length; stage++) {
            statuses.add(probeTool(context, stage));
        }
        return statuses;
    }

    public static ToolStatus probeTool(Context context, int stage) {
        String stageName = MapCompiler.STAGE_NAMES[stage];
        File tool = resolveTool(context, stage);
        if (tool == null) {
            return new ToolStatus(stageName, null, Location.MISSING, false,
                    "не найден (lib" + MapCompiler.STAGE_BINARIES[stage] + ".so)");
        }

        Location location = locationOf(context, tool);
        try {
            ProcessBuilder builder = new ProcessBuilder(tool.getAbsolutePath());
            builder.redirectErrorStream(true);
            Process process = builder.start();
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String firstLine = reader.readLine();
            boolean finished = process.waitFor(PROBE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!finished) {
                process.destroy();
            }
            String detail = firstLine == null || firstLine.trim().isEmpty()
                    ? "запускается (вывод пуст)"
                    : "запускается: " + firstLine.trim();
            return new ToolStatus(stageName, tool, location, true, detail);
        } catch (IOException e) {
            return new ToolStatus(stageName, tool, location, false,
                    "не запускается: " + e.getMessage()
                            + " (Android может запрещать запуск из приватного хранилища, ограничение W^X)");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new ToolStatus(stageName, tool, location, false, "проверка прервана");
        }
    }

    public static boolean allExecutable(List<ToolStatus> statuses) {
        for (ToolStatus status : statuses) {
            if (!status.executable) {
                return false;
            }
        }
        return true;
    }

    public static String describe(List<ToolStatus> statuses) {
        StringBuilder builder = new StringBuilder();
        for (ToolStatus status : statuses) {
            builder.append(status.stageName).append(": ");
            switch (status.location) {
                case APK_LIBS:
                    builder.append("[в APK] ");
                    break;
                case IMPORTED:
                    builder.append("[импортирован] ");
                    break;
                default:
                    builder.append("[нет] ");
                    break;
            }
            builder.append(status.detail).append("\n");
        }

        builder.append("\n");
        if (allExecutable(statuses)) {
            builder.append("Все четыре стадии готовы к компиляции.");
        } else {
            builder.append("Часть инструментов недоступна.\n")
                    .append("• Кнопкой «Импорт компиляторов (SAF)» выберите папку с бинарниками ")
                    .append("libhlcsg.so / libhlbsp.so / libhlvis.so / libhlrad.so (сборки VHLT или ericw-tools под arm64-v8a).\n")
                    .append("• Либо разработчику: положить их в app/src/main/jniLibs/<abi>/ и пересобрать APK — ")
                    .append("это единственный путь, который гарантированно обходит ограничение W^X на современных Android.");
        }
        return builder.toString();
    }
}
