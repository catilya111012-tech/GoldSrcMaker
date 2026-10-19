package com.goldsrc.maker.engine;

/*
 * Based on Xash3D FWGS XashActivity
 * (android/app/src/main/java/su/xash/engine/XashActivity.java),
 * Copyright (C) Xash3D FWGS contributors, GPL-3.0.
 * Adapted for the built-in (embedded) engine inside GoldSrcMaker.
 */

import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.res.AssetManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings.Secure;
import android.util.Log;
import android.view.DisplayCutout;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowManager;

import org.libsdl.app.SDLActivity;

import com.goldsrc.maker.engine.util.SoftKeyboardPan;

import java.io.File;
import java.util.Arrays;

public class XashActivity extends SDLActivity {
    private static final String TAG = "GoldSrcEngine";

    private boolean mUseVolumeKeys;
    private volatile float[] mWindowInsets = new float[4];
    private String mPackageName;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            getWindow().getAttributes().layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        }

        SoftKeyboardPan.assistActivity(this);

        // mSurface is null if SDL failed to start and shows its error dialog
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && mSurface != null) {
            mSurface.setOnApplyWindowInsetsListener((view, insets) -> {
                updateWindowInsets(view, insets);
                return insets;
            });
            mSurface.addOnLayoutChangeListener((view, l, t, r, b, ol, ot, or, ob) ->
                    updateWindowInsets(view, view.getRootWindowInsets()));
            mSurface.requestApplyInsets();
        }
    }

    // JNI reads a snapshot; all View access stays on Android's UI thread.
    private float[] getWindowInsets() {
        return mWindowInsets;
    }

    private void updateWindowInsets(View surface, WindowInsets insets) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P)
            return;

        if (insets == null || surface.getWidth() <= 0 || surface.getHeight() <= 0)
            return;

        // Only the display cutout: system bars are hidden in immersive mode and reporting them
        // while they're swiped in would shift the whole layout.
        int left = 0, top = 0, right = 0, bottom = 0;
        DisplayCutout cutout = insets.getDisplayCutout();
        if (cutout != null) {
            left = cutout.getSafeInsetLeft();
            top = cutout.getSafeInsetTop();
            right = cutout.getSafeInsetRight();
            bottom = cutout.getSafeInsetBottom();
        }
        float width = surface.getWidth(), height = surface.getHeight();
        mWindowInsets = new float[] { left / width, top / height, right / width, bottom / height };
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        // Now that we don't exit from native code, we need to exit here, resetting
        // application state (actually global variables that we don't cleanup on exit)
        System.exit(0);
    }

    @Override
    protected String[] getLibraries() {
        return new String[]{"SDL2", "xash"};
    }

    @SuppressLint("HardwareIds")
    private String getAndroidID() {
        return Secure.getString(getContentResolver(), Secure.ANDROID_ID);
    }

    @SuppressLint("ApplySharedPref")
    private void saveAndroidID(String id) {
        getSharedPreferences("xash_preferences", MODE_PRIVATE).edit().putString("xash_id", id).commit();
    }

    private String loadAndroidID() {
        return getSharedPreferences("xash_preferences", MODE_PRIVATE).getString("xash_id", "");
    }

    @Override
    public String getCallingPackage() {
        if (mPackageName != null) {
            return mPackageName;
        }

        return super.getCallingPackage();
    }

    private AssetManager getAssets(boolean isEngine) {
        if (isEngine) {
            return getAssets();
        }

        // Assets of the launcher package; the engine and the launcher are the same
        // application here, so it's the same AssetManager.
        return getAssets();
    }

    private String[] getAssetsList(boolean isEngine, String path) {
        AssetManager am = getAssets(isEngine);

        try {
            return am.list(path);
        } catch (Exception e) {
            e.printStackTrace();
        }

        return new String[]{};
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (SDLActivity.mBrokenLibraries) {
            return false;
        }

        int keyCode = event.getKeyCode();
        if (!mUseVolumeKeys) {
            if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || keyCode == KeyEvent.KEYCODE_VOLUME_UP
                    || keyCode == KeyEvent.KEYCODE_CAMERA || keyCode == KeyEvent.KEYCODE_ZOOM_IN
                    || keyCode == KeyEvent.KEYCODE_ZOOM_OUT) {
                return false;
            }
        }

        return getWindow().superDispatchKeyEvent(event);
    }

    private static String intentInfo(Intent intent) {
        StringBuilder sb = new StringBuilder();
        sb.append("basedir = ").append(intent.getStringExtra("basedir")).append('\n');
        sb.append("gamedir = ").append(intent.getStringExtra("gamedir")).append('\n');
        sb.append("argv = ").append(intent.getStringExtra("argv")).append('\n');
        String[] env = intent.getStringArrayExtra("env");
        if (env != null)
            sb.append("env = ").append(Arrays.toString(env)).append('\n');
        return sb.toString();
    }

    @Override
    protected String[] getArguments() {
        File crashDir = new File(getFilesDir(), "crashes");
        crashDir.mkdirs();
        nativeSetenv("XASH3D_CRASH_DIR", crashDir.getAbsolutePath());

        Intent intent = getIntent();
        if (intent != null) {
            Log.i(TAG, "launch info:\n" + intentInfo(intent));
        }

        String gamedir = intent != null ? intent.getStringExtra("gamedir") : null;
        if (gamedir == null) gamedir = "valve";
        nativeSetenv("XASH3D_GAME", gamedir);

        String gamelibdir = intent != null ? intent.getStringExtra("gamelibdir") : null;
        if (gamelibdir == null) {
            // built-in engine libraries (libhl_android_*.so, libclient_android_*.so, …)
            gamelibdir = getApplicationInfo().nativeLibraryDir;
        }
        nativeSetenv("XASH3D_GAMELIBDIR", gamelibdir);

        String rodir = System.getenv("XASH3D_RODIR");
        if (rodir == null) {
            rodir = getFilesDir().getAbsolutePath() + "/gamelibs";
            nativeSetenv("XASH3D_RODIR", rodir);
        }

        String pakfile = intent != null ? intent.getStringExtra("pakfile") : null;
        if (pakfile != null) nativeSetenv("XASH3D_EXTRAS_PAK2", pakfile);

        String basedir = intent != null ? intent.getStringExtra("basedir") : null;
        if (basedir != null) {
            nativeSetenv("XASH3D_BASEDIR", basedir);
        } else {
            String rootPath = Environment.getExternalStorageDirectory().getAbsolutePath() + "/xash";
            nativeSetenv("XASH3D_BASEDIR", rootPath);
        }

        mUseVolumeKeys = intent != null && intent.getBooleanExtra("usevolume", false);
        mPackageName = intent != null ? intent.getStringExtra("package") : null;

        String[] env = intent != null ? intent.getStringArrayExtra("env") : null;
        if (env != null) {
            for (int i = 0; i + 1 < env.length; i += 2)
                nativeSetenv(env[i], env[i + 1]);
        }

        String argv = intent != null ? intent.getStringExtra("argv") : null;
        if (argv == null) argv = "-log";

        return argv.split(" ");
    }
}
