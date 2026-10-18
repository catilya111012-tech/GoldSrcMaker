package com.goldsrc.maker.compiler;

import android.content.Context;

import com.goldsrc.maker.core.Project;
import com.goldsrc.maker.core.ProjectManager;
import com.goldsrc.maker.editor.MapData;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public final class MapCompiler {

    public static final class Result {
        public final boolean success;
        public final String log;
        public final File bspFile;

        public Result(boolean success, String log, File bspFile) {
            this.success = success;
            this.log = log;
            this.bspFile = bspFile;
        }

        public static Result failure(String log) {
            return new Result(false, log, null);
        }
    }

    private static final String[] STAGE_NAMES = {"HLCSG", "HLBSP", "HLVIS", "HLRAD"};
    private static final String[] STAGE_BINARIES = {"hlcsg", "hlbsp", "hlvis", "hlrad"};
    private static final int STAGE_LOG_LIMIT = 6000;

    private MapCompiler() {
    }

    public static Result compile(Context context, Project project, MapData map, File valveDir) throws IOException {
        StringBuilder log = new StringBuilder();

        if (valveDir == null || !new File(valveDir, "halflife.wad").isFile()) {
            return Result.failure("Не найден halflife.wad в каталоге valve.\n"
                    + "Пожалуйста, скопируйте оригинальные файлы текстур со своего ПК отдельно.");
        }

        File workDir = new File(context.getExternalFilesDir(null), "compile/" + project.getName());
        if (!workDir.exists() && !workDir.mkdirs()) {
            return Result.failure("Не удалось создать рабочий каталог компиляции: " + workDir.getAbsolutePath());
        }

        File wadFile = new File(valveDir, "halflife.wad");
        File mapFile = new File(workDir, "main.map");
        ProjectManager.writeText(mapFile, map.toMapText(wadFile.getAbsolutePath()));

        String toolDir = context.getApplicationInfo().nativeLibraryDir;

        for (int stage = 0; stage < STAGE_NAMES.length; stage++) {
            int code = runStage(context, stage, toolDir, workDir);
            log.append(STAGE_NAMES[stage]).append(": exit=").append(code).append("\n");

            String stageLog = readStageLog(workDir, stage);
            if (!stageLog.isEmpty()) {
                log.append(stageLog).append("\n");
            }

            if (code == CompilerNative.RESULT_MISSING_BINARY) {
                log.append("\nНе найден мобильный порт компилятора lib")
                        .append(STAGE_BINARIES[stage]).append(".so (").append(STAGE_NAMES[stage]).append(").\n")
                        .append("Поместите собранные бинарники (VHLT / ericw-tools, лицензия GPL) в:\n")
                        .append("app/src/main/jniLibs/arm64-v8a/lib")
                        .append(STAGE_BINARIES[stage]).append(".so\n")
                        .append("Приложение не распространяет их: это сторонние открытые утилиты.");
                return Result.failure(log.toString());
            }

            if (code != 0) {
                log.append("\nЭтап ").append(STAGE_NAMES[stage]).append(" завершился с ошибкой.\n");
                return Result.failure(log.toString());
            }
        }

        File bsp = new File(workDir, "main.bsp");
        if (!bsp.isFile()) {
            log.append("\nmain.bsp не был создан компилятором.");
            return Result.failure(log.toString());
        }

        File compiledDir = new File(project.getDir(), "compiled");
        if (!compiledDir.exists() && !compiledDir.mkdirs()) {
            return Result.failure("Не удалось создать каталог: " + compiledDir.getAbsolutePath());
        }
        File output = new File(compiledDir, project.getName() + ".bsp");
        copyFile(bsp, output);

        File mapsDir = new File(valveDir, "maps");
        if (!mapsDir.exists() && !mapsDir.mkdirs()) {
            return Result.failure("Не удалось создать каталог вашей игры: " + mapsDir.getAbsolutePath());
        }
        File installed = new File(mapsDir, project.getName() + ".bsp");
        copyFile(bsp, installed);

        log.append("\nГотово:\n  ").append(output.getAbsolutePath())
                .append("\n  ").append(installed.getAbsolutePath())
                .append("\n\nЗапустите игру кнопкой «Запустить игру» - карта уже в вашем valve/maps.");
        return new Result(true, log.toString(), output);
    }

    private static int runStage(Context context, int stage, String toolDir, File workDir) {
        if (CompilerNative.isAvailable()) {
            try {
                int code = CompilerNative.runStage(stage, toolDir, workDir.getAbsolutePath(), "main");
                if (code != CompilerNative.RESULT_MISSING_BINARY) {
                    return code;
                }
            } catch (UnsatisfiedLinkError ignored) {
            }
        }
        return runStageWithProcess(toolDir, stage, workDir);
    }

    private static int runStageWithProcess(String toolDir, int stage, File workDir) {
        File binary = new File(toolDir, "lib" + STAGE_BINARIES[stage] + ".so");
        if (!binary.isFile()) {
            return CompilerNative.RESULT_MISSING_BINARY;
        }
        try {
            ProcessBuilder builder = new ProcessBuilder(binary.getAbsolutePath(), "main");
            builder.directory(workDir);
            builder.redirectErrorStream(true);
            builder.redirectOutput(new File(workDir, "compile_" + stage + ".log"));
            Process process = builder.start();
            return process.waitFor();
        } catch (IOException e) {
            return CompilerNative.RESULT_SPAWN_FAILED;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return CompilerNative.RESULT_SPAWN_FAILED;
        }
    }

    private static String readStageLog(File workDir, int stage) {
        File logFile = new File(workDir, "compile_" + stage + ".log");
        if (!logFile.isFile()) {
            return "";
        }
        try {
            String content = ProjectManager.readText(logFile);
            if (content.length() > STAGE_LOG_LIMIT) {
                content = content.substring(content.length() - STAGE_LOG_LIMIT);
            }
            return content.trim();
        } catch (IOException e) {
            return "";
        }
    }

    private static void copyFile(File source, File destination) throws IOException {
        try (InputStream input = new FileInputStream(source);
             OutputStream output = new FileOutputStream(destination)) {
            byte[] buffer = new byte[65536];
            int read;
            while ((read = input.read(buffer)) > 0) {
                output.write(buffer, 0, read);
            }
        }
    }
}
