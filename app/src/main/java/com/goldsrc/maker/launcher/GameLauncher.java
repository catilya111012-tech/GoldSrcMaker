package com.goldsrc.maker.launcher;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;

import java.io.File;

public final class GameLauncher {

    private static final String[] ENGINE_PACKAGES = {
            "su.xash.engine",
            "in.celest.xash3d.fwgs"
    };

    private static final String INSTALL_URL = "https://f-droid.org/packages/su.xash.engine/";

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

    public static boolean isEngineInstalled(Context context) {
        return findEnginePackage(context) != null;
    }

    public static Intent buildLaunchIntent(Context context, File workingRoot, String mapName) {
        String packageName = findEnginePackage(context);
        if (packageName == null) {
            return null;
        }
        Intent launchIntent = context.getPackageManager().getLaunchIntentForPackage(packageName);
        if (launchIntent == null) {
            return null;
        }
        launchIntent.putExtra("-basedir", workingRoot.getAbsolutePath());
        launchIntent.putExtra("-game", "valve");
        launchIntent.putExtra("+map", mapName);
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return launchIntent;
    }

    public static boolean launch(Activity activity, File workingRoot, String mapName) {
        Intent intent = buildLaunchIntent(activity, workingRoot, mapName);
        if (intent == null) {
            return false;
        }
        try {
            activity.startActivity(intent);
            return true;
        } catch (ActivityNotFoundException e) {
            return false;
        }
    }
}
