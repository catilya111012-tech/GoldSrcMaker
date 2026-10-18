package com.goldsrc.maker.core;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public final class GameFileValidator {

    public static final class Report {
        public final File valveDir;
        public final List<String> present = new ArrayList<>();
        public final List<String> missingRequired = new ArrayList<>();
        public final List<String> missingRecommended = new ArrayList<>();

        public Report(File valveDir) {
            this.valveDir = valveDir;
        }

        public boolean isTexturesReady() {
            return missingRequired.isEmpty();
        }
    }

    private static final String[] REQUIRED_FILES = {
            "halflife.wad"
    };

    private static final String[] RECOMMENDED_FILES = {
            "pak0.pak",
            "liquids.wad",
            "xeno.wad",
            "decals.wad"
    };

    private static final String[] RECOMMENDED_DIRS = {
            "gfx",
            "models",
            "sound",
            "sprites",
            "maps"
    };

    private GameFileValidator() {
    }

    public static Report validate(File valveDir) {
        Report report = new Report(valveDir);
        if (valveDir == null || !valveDir.isDirectory()) {
            for (String name : REQUIRED_FILES) {
                report.missingRequired.add(name);
            }
            for (String name : RECOMMENDED_FILES) {
                report.missingRecommended.add(name);
            }
            for (String name : RECOMMENDED_DIRS) {
                report.missingRecommended.add(name + "/");
            }
            return report;
        }

        for (String name : REQUIRED_FILES) {
            if (new File(valveDir, name).isFile()) {
                report.present.add(name);
            } else {
                report.missingRequired.add(name);
            }
        }

        for (String name : RECOMMENDED_FILES) {
            if (new File(valveDir, name).isFile()) {
                report.present.add(name);
            } else {
                report.missingRecommended.add(name);
            }
        }

        for (String name : RECOMMENDED_DIRS) {
            if (new File(valveDir, name).isDirectory()) {
                report.present.add(name + "/");
            } else {
                report.missingRecommended.add(name + "/");
            }
        }

        return report;
    }

    public static String buildUserMessage(Report report, String legalNotice) {
        StringBuilder builder = new StringBuilder();

        if (!report.isTexturesReady()) {
            builder.append(legalNotice).append("\n\n");
            builder.append("Не найдены обязательные файлы в каталоге:\n");
            builder.append(report.valveDir == null ? "(каталог не определён)" : report.valveDir.getAbsolutePath());
            builder.append("\n\n");
            for (String name : report.missingRequired) {
                builder.append("  • ").append(name).append("\n");
            }
            builder.append("\nОжидаемая структура: GoldSrcMaker/valve/halflife.wad");
        } else {
            builder.append("Игровые ресурсы найдены:\n");
            builder.append(report.valveDir.getAbsolutePath()).append("\n\n");
            builder.append("Редактор и компиляция .bsp доступны.");
        }

        if (!report.missingRecommended.isEmpty()) {
            builder.append("\n\nОтсутствуют (не критично):\n");
            for (String name : report.missingRecommended) {
                builder.append("  – ").append(name).append("\n");
            }
        }

        return builder.toString();
    }
}
