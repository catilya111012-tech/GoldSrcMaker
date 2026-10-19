package com.goldsrc.maker.launcher;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.AppOpsManager;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;

import com.goldsrc.maker.engine.XashActivity;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public final class GameLauncher {

    public static final class LaunchPlan {
        public final Intent intent;
        public final String fatalError;
        public final List<String> warnings = new ArrayList<>();
        public String enginePackage;
        public boolean embedded;

        private LaunchPlan(Intent intent, String fatalError) {
            this.intent = intent;
            this.fatalError = fatalError;
        }

        public boolean isRunnable() {
            return fatalError == null && intent != null;
        }
    }

    private static final String[] ENGINE_PACKAGES = {
            "su.xash.engine",
            "su.xash.engine.test",
            "in.celest.xash3d.fwgs"
    };

    private static final String ENGINE_ACTIVITY_SUFFIX = "XashActivity";
    private static final String INSTALL_URL = "https://f-droid.org/packages/su.xash.engine/";
    private static final String GAME_DIR_NAME = "valve";

    private GameLauncher() {
    }

    public static String getInstallUrl() {
        return INSTALL_URL;
    }

    public static String findEnginePackage(Context context) {
        PackageManager manager = context.getPackageManager();
        for (String packageName : ENGINE_PACKAGES) {
            try {
                manager.getPackageInfo(packageName, 0);
                return packageName;
            } catch (PackageManager.NameNotFoundException ignored) {
            }
        }
        return null;
    }

    /** Встроенный движок: нативные библиотеки лежат внутри нашего APK. */
    public static boolean hasEmbeddedEngine(Context context) {
        try {
            String libDir = context.getApplicationInfo().nativeLibraryDir;
            if (libDir == null) {
                return false;
            }
            return new File(libDir, "libxash.so").isFile()
                    && new File(libDir, "libSDL2.so").isFile();
        } catch (Exception ignored) {
            return false;
        }
    }

    public static boolean isEngineInstalled(Context context) {
        return hasEmbeddedEngine(context) || findEnginePackage(context) != null;
    }

    public static String findEngineActivity(Context context, String packageName) {
        PackageManager manager = context.getPackageManager();
        try {
            PackageInfo info = manager.getPackageInfo(packageName, PackageManager.GET_ACTIVITIES);
            if (info.activities != null) {
                for (ActivityInfo activity : info.activities) {
                    if (activity.name != null && activity.name.endsWith(ENGINE_ACTIVITY_SUFFIX)) {
                        return activity.name;
                    }
                }
            }
            Intent launch = manager.getLaunchIntentForPackage(packageName);
            if (launch != null && launch.getComponent() != null) {
                return launch.getComponent().getClassName();
            }
        } catch (PackageManager.NameNotFoundException ignored) {
        }
        return null;
    }

    public static LaunchPlan buildPlan(Context context, File workingRoot, String mapName) {
        if (workingRoot == null) {
            return new LaunchPlan(null, "Не определена рабочая папка GoldSrcMaker.");
        }
        if (mapName == null || mapName.trim().isEmpty()) {
            return new LaunchPlan(null, "Не задано имя карты для запуска.");
        }

        boolean embedded = hasEmbeddedEngine(context);
        String packageName = embedded ? context.getPackageName() : findEnginePackage(context);
        if (packageName == null) {
            return new LaunchPlan(null, "Движок Xash3D FWGS не установлен.\n"
                    + "Установите свободный движок (open-source) и повторите запуск.");
        }

        Intent intent;
        if (embedded) {
            intent = buildEmbeddedIntent(context, workingRoot, mapName);
        } else {
            String activityName = findEngineActivity(context, packageName);
            if (activityName == null) {
                return new LaunchPlan(null, "У движка " + packageName + " не найдена точка запуска XashActivity.");
            }
            intent = buildLaunchIntent(packageName, activityName, workingRoot, mapName);
        }

        File valveDir = new File(workingRoot, GAME_DIR_NAME);
        File mapsDir = new File(valveDir, "maps");
        File bsp = new File(mapsDir, mapName + ".bsp");
        if (!bsp.isFile()) {
            return new LaunchPlan(null, "Карта не скомпилирована:\n" + bsp.getAbsolutePath()
                    + "\n\nСначала выполните «Скомпилировать карту».");
        }
        if (!new File(valveDir, "halflife.wad").isFile()) {
            return new LaunchPlan(null, "В каталоге игры нет halflife.wad:\n" + valveDir.getAbsolutePath()
                    + "\n\nСкопируйте оригинальные файлы текстур со своего ПК отдельно.");
        }

        LaunchPlan plan = new LaunchPlan(intent, null);
        plan.enginePackage = packageName;
        plan.embedded = embedded;

        if (!new File(valveDir, "pak0.pak").isFile()) {
            plan.warnings.add("Не найден pak0.pak: меню, спрайты, звуки и модели вашей игры "
                    + "могут не загрузиться. Скопируйте его в " + valveDir.getAbsolutePath());
        }
        if (!engineHasAllFilesAccess(context, packageName)) {
            if (embedded) {
                plan.warnings.add("У приложения нет разрешения «Доступ ко всем файлам» — "
                        + "без него встроенный движок не увидит файлы игры. Выдайте его: Настройки → "
                        + "Приложения → GoldSrcMaker → Разрешения → «Файлы и медиа» → "
                        + "«Разрешить управление всеми файлами» (на Samsung: Настройки → "
                        + "Приложения → ⋮ «Специальный доступ» → «Управление всеми файлами»).");
            } else {
                plan.warnings.add("У движка Xash3D нет разрешения «Доступ ко всем файлам» — "
                        + "без него движок не увидит файлы игры. Выдайте его: Настройки → "
                        + "Приложения → Xash3D → Разрешения → «Файлы и медиа» → "
                        + "«Разрешить управление всеми файлами» (на Samsung: Настройки → "
                        + "Приложения → ⋮ «Специальный доступ» → «Управление всеми файлами»).");
            }
        }
        if (isPrivateStorage(context, workingRoot)) {
            plan.warnings.add("Рабочая папка находится в приватном хранилище приложения. "
                    + "Движок может не увидеть файлы игры. Разрешите доступ «Все файлы» "
                    + "и используйте общий каталог Documents/GoldSrcMaker.");
        }
        return plan;
    }

    /** Запуск встроенного движка внутри нашего приложения. */
    public static Intent buildEmbeddedIntent(Context context, File workingRoot, String mapName) {
        Intent intent = new Intent(context, XashActivity.class);
        intent.putExtra("basedir", workingRoot.getAbsolutePath());
        intent.putExtra("gamedir", GAME_DIR_NAME);
        intent.putExtra("argv", buildGameArgv(workingRoot, mapName));
        return intent;
    }

    public static Intent buildLaunchIntent(String packageName, String activityName,
                                           File workingRoot, String mapName) {
        Intent intent = new Intent();
        intent.setComponent(new ComponentName(packageName, activityName));
        intent.putExtra("basedir", workingRoot.getAbsolutePath());
        intent.putExtra("gamedir", GAME_DIR_NAME);
        intent.putExtra("argv", buildGameArgv(workingRoot, mapName));
        return intent;
    }

    /** Аргументы запуска: лог, сенсорное управление и видимый индикатор джойстика. */
    private static String buildGameArgv(File workingRoot, String mapName) {
        return "-log " + workingRoot.getAbsolutePath() + "/game.log"
                + " +touch_enable 1 +touch_move_indicator 1"
                + " +map " + mapName;
    }

    public static boolean engineHasAllFilesAccess(Context context, String packageName) {
        try {
            AppOpsManager ops = (AppOpsManager) context.getSystemService(Context.APP_OPS_SERVICE);
            if (ops == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
                return true;
            }
            int uid = context.getPackageManager().getApplicationInfo(packageName, 0).uid;
            String op = "android:manage_external_storage";
            int mode;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                mode = ops.unsafeCheckOpNoThrow(op, uid, packageName);
            } else {
                mode = ops.checkOpNoThrow(op, uid, packageName);
            }
            return mode == AppOpsManager.MODE_ALLOWED;
        } catch (Exception ignored) {
            return true;
        }
    }

    public static boolean isPrivateStorage(Context context, File workingRoot) {
        File privateRoot = context.getExternalFilesDir(null);
        if (privateRoot == null || workingRoot == null) {
            return false;
        }
        try {
            String prefix = privateRoot.getCanonicalPath() + File.separator;
            String target = workingRoot.getCanonicalPath() + File.separator;
            return target.startsWith(prefix);
        } catch (IOException ignored) {
            return false;
        }
    }

    public static String launch(Activity activity, LaunchPlan plan) {
        if (!plan.isRunnable()) {
            return plan.fatalError == null ? "Не удалось подготовить запуск движка." : plan.fatalError;
        }
        try {
            activity.startActivity(plan.intent);
            return null;
        } catch (ActivityNotFoundException e) {
            ComponentName component = plan.intent.getComponent();
            return "Система не разрешила запуск движка"
                    + (component == null ? "" : " (" + component.flattenToShortString() + ")")
                    + ".";
        } catch (SecurityException e) {
            return "Система запретила запуск движка: " + e.getMessage();
        }
    }

    public static void launchWithDialogs(final Activity activity, final LaunchPlan plan) {
        if (!plan.isRunnable()) {
            new AlertDialog.Builder(activity)
                    .setTitle("Запуск невозможен")
                    .setMessage(plan.fatalError == null ? "Не удалось подготовить запуск." : plan.fatalError)
                    .setPositiveButton("ОК", null)
                    .show();
            return;
        }
        if (!plan.warnings.isEmpty()) {
            StringBuilder message = new StringBuilder();
            for (String warning : plan.warnings) {
                message.append("• ").append(warning).append("\n\n");
            }
            AlertDialog.Builder builder = new AlertDialog.Builder(activity)
                    .setTitle("Запуск с предупреждениями")
                    .setMessage(message.toString().trim())
                    .setPositiveButton("Всё равно запустить", (dialog, which) -> doLaunch(activity, plan))
                    .setNegativeButton("Отмена", null);
            if (plan.enginePackage != null) {
                String settingsLabel = plan.embedded ? "Настройки приложения" : "Настройки Xash3D";
                builder.setNeutralButton(settingsLabel, (dialog, which) -> openEngineSettings(activity, plan.enginePackage));
            }
            builder.show();
            return;
        }
        doLaunch(activity, plan);
    }

    private static void openEngineSettings(Activity activity, String packageName) {
        try {
            Intent intent = new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            intent.setData(android.net.Uri.parse("package:" + packageName));
            activity.startActivity(intent);
        } catch (ActivityNotFoundException e) {
            new AlertDialog.Builder(activity)
                    .setTitle("Настройки Xash3D")
                    .setMessage("Откройте: Настройки → Приложения → Xash3D → Разрешения → "
                            + "«Файлы и медиа» → «Разрешить управление всеми файлами».")
                    .setPositiveButton("ОК", null)
                    .show();
        }
    }

    private static void doLaunch(Activity activity, LaunchPlan plan) {
        String error = launch(activity, plan);
        if (error != null) {
            new AlertDialog.Builder(activity)
                    .setTitle("Ошибка запуска")
                    .setMessage(error)
                    .setPositiveButton("ОК", null)
                    .show();
        }
    }
}
