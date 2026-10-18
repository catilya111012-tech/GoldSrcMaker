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
import java.io.RandomAccessFile;

public final class MapCompiler {

    public interface CompileListener {
        void onStageStart(int stage, String stageName);

        void onStageLog(int stage, String line);

        void onStageFinish(int stage, String stageName, int exitCode);
    }

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

    public static final String[] STAGE_NAMES = {"HLCSG", "HLBSP", "HLVIS", "HLRAD"};
    public static final String[] STAGE_BINARIES = {"hlcsg", "hlbsp", "hlvis", "hlrad"};

    private static final int STAGE_LOG_LIMIT = 6000;
    private static final long POLL_INTERVAL_MS = 200;

    private MapCompiler() {
    }

    public static Result compile(Context context, Project project, MapData map, File valveDir)
            throws IOException {
        return compile(context, project, map, valveDir, null);
    }

    public static Result compile(Context context, Project project, MapData map, File valveDir,
                                 CompileListener listener) throws IOException {
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
        ProjectManager.writeText(mapFile, map.toMapText(wadFile.getAbsolutePath(),
                ProjectManager.projectWadPath(project)));

        for (int stage = 0; stage < STAGE_NAMES.length; stage++) {
            File tool = CompilerToolchain.resolveTool(context, stage);
            if (tool == null) {
                log.append(buildMissingToolMessage(stage));
                return Result.failure(log.toString());
            }

            if (listener != null) {
                listener.onStageStart(stage, STAGE_NAMES[stage]);
            }

            File stageLogFile = new File(workDir, "compile_" + stage + ".log");
            if (stageLogFile.exists() && !stageLogFile.delete()) {
                log.append("Предупреждение: не удалось очистить журнал ").append(stageLogFile.getName()).append("\n");
            }

            LogPoller poller = new LogPoller(stageLogFile, stage, listener);
            poller.start();

            int code = runStage(tool, stage, workDir);
            poller.stop();

            if (listener != null) {
                listener.onStageFinish(stage, STAGE_NAMES[stage], code);
            }

            log.append(STAGE_NAMES[stage]).append(": exit=").append(code).append("\n");

            String stageLog = readStageLog(workDir, stage);
            if (!stageLog.isEmpty()) {
                log.append(stageLog).append("\n");
            }

            if (code != 0) {
                log.append("\nЭтап ").append(STAGE_NAMES[stage]).append(" завершился с ошибкой.\n");
                if (code == 127) {
                    log.append("\nКод 127 обычно означает, что Android запретил запуск бинарника ")
                            .append("из приватного хранилища приложения (ограничение W^X) или бинарник ")
                            .append("собран под другую архитектуру. Соберите APK с компиляторами в jniLibs ")
                            .append("под arm64-v8a.\n");
                }
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

        String projectWad = ProjectManager.projectWadPath(project);
        if (projectWad != null) {
            File wadTarget = new File(valveDir, "textures.wad");
            copyFile(new File(projectWad), wadTarget);
            log.append("\nТекстуры проекта установлены: ").append(wadTarget.getAbsolutePath());
        }

        log.append("\nГотово:\n  ").append(output.getAbsolutePath())
                .append("\n  ").append(installed.getAbsolutePath())
                .append("\n\nЗапустите игру кнопкой «Запустить игру» - карта уже в вашем valve/maps.");
        return new Result(true, log.toString(), output);
    }

    private static String buildMissingToolMessage(int stage) {
        return "Не найден компилятор lib" + STAGE_BINARIES[stage] + ".so (" + STAGE_NAMES[stage] + ").\n\n"
                + "Вариант 1: кнопка «Импорт компиляторов (SAF)» — выберите папку с бинарниками "
                + "libhlcsg.so / libhlbsp.so / libhlvis.so / libhlrad.so.\n"
                + "Вариант 2 (надёжный): разработчику положить их в app/src/main/jniLibs/arm64-v8a/ "
                + "и пересобрать APK.\n\n"
                + "Приложение не распространяет компиляторы: VHLT / ericw-tools — сторонние GPL-утилиты.";
    }

    private static int runStage(File tool, int stage, File workDir) {
        if (CompilerNative.isAvailable()) {
            try {
                int code = CompilerNative.runStage(stage,
                        tool.getParentFile().getAbsolutePath(),
                        workDir.getAbsolutePath(),
                        "main");
                return code;
            } catch (UnsatisfiedLinkError ignored) {
            }
        }
        return runStageWithProcess(tool, stage, workDir);
    }

    private static int runStageWithProcess(File tool, int stage, File workDir) {
        try {
            ProcessBuilder builder = new ProcessBuilder(tool.getAbsolutePath(), "main");
            builder.directory(workDir);
            builder.environment().put("LD_LIBRARY_PATH", tool.getParentFile().getAbsolutePath());
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

    private static final class LogPoller {
        private final File logFile;
        private final int stage;
        private final CompileListener listener;
        private final Thread thread;
        private volatile boolean running = true;
        private long position = 0;

        LogPoller(File logFile, int stage, CompileListener listener) {
            this.logFile = logFile;
            this.stage = stage;
            this.listener = listener;
            this.thread = new Thread(this::pollLoop);
            this.thread.setDaemon(true);
        }

        void start() {
            if (listener != null) {
                thread.start();
            }
        }

        void stop() {
            running = false;
            if (listener != null) {
                try {
                    thread.join(2000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }

        private void pollLoop() {
            while (running) {
                drain();
                try {
                    Thread.sleep(POLL_INTERVAL_MS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
            drain();
        }

        private void drain() {
            if (!logFile.isFile()) {
                return;
            }
            try (RandomAccessFile access = new RandomAccessFile(logFile, "r")) {
                if (access.length() <= position) {
                    return;
                }
                access.seek(position);
                String line;
                while ((line = access.readLine()) != null) {
                    emit(line);
                }
                position = access.getFilePointer();
            } catch (IOException ignored) {
            }
        }

        private void emit(String line) {
            if (listener == null) {
                return;
            }
            try {
                listener.onStageLog(stage, line);
            } catch (RuntimeException ignored) {
            }
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
