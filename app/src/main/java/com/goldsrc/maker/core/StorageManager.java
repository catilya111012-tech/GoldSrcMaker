package com.goldsrc.maker.core;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.Settings;

import androidx.documentfile.provider.DocumentFile;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Locale;

public class StorageManager {

    public static final String ROOT_DIR_NAME = "GoldSrcMaker";
    public static final String VALVE_DIR_NAME = "valve";
    public static final String PROJECTS_DIR_NAME = "projects";
    public static final String COMPILED_DIR_NAME = "compiled";
    public static final String TOOLS_DIR_NAME = "tools";

    private static final String PREFS_NAME = "goldsrc_maker_prefs";
    private static final String KEY_SAF_TREE = "saf_tree_uri";
    private static final String KEY_SAF_TREE_TOOLS = "saf_tree_uri_tools";

    private final Context context;
    private final SharedPreferences prefs;

    public StorageManager(Context context) {
        this.context = context.getApplicationContext();
        this.prefs = this.context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public boolean hasAllFilesAccess() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return Environment.isExternalStorageManager();
        }
        return context.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                == PackageManager.PERMISSION_GRANTED;
    }

    public boolean isLegacyRuntimePermissionRequired() {
        return Build.VERSION.SDK_INT <= Build.VERSION_CODES.P
                && context.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED;
    }

    public Intent buildAllFilesAccessIntent() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
            intent.setData(Uri.parse("package:" + context.getPackageName()));
            return intent;
        }
        return null;
    }

    public Intent buildAllFilesAccessFallbackIntent() {
        return new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
    }

    public void requestLegacyRuntimePermission(Activity activity, int requestCode) {
        activity.requestPermissions(new String[]{
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.READ_EXTERNAL_STORAGE
        }, requestCode);
    }

    public File getPublicRoot() {
        return new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), ROOT_DIR_NAME);
    }

    public File getPrivateRoot() {
        return new File(context.getExternalFilesDir(null), ROOT_DIR_NAME);
    }

    public File getActiveRoot() {
        if (hasAllFilesAccess()) {
            return getPublicRoot();
        }
        return getPrivateRoot();
    }

    public File findValveDir() {
        File publicValve = new File(getPublicRoot(), VALVE_DIR_NAME);
        if (new File(publicValve, "halflife.wad").isFile()) {
            return publicValve;
        }
        File privateValve = new File(getPrivateRoot(), VALVE_DIR_NAME);
        if (new File(privateValve, "halflife.wad").isFile()) {
            return privateValve;
        }
        if (hasAllFilesAccess()) {
            return publicValve;
        }
        return privateValve;
    }

    public File getProjectsDir() {
        File dir = new File(getActiveRoot(), PROJECTS_DIR_NAME);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return dir;
    }

    public File getCompiledDir() {
        File dir = new File(getActiveRoot(), COMPILED_DIR_NAME);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return dir;
    }

    public File getToolsDir() {
        File dir = new File(getPrivateRoot(), TOOLS_DIR_NAME);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return dir;
    }

    public boolean ensureBaseFolders() {
        File root = getActiveRoot();
        File[] dirs = {
                root,
                new File(root, VALVE_DIR_NAME),
                new File(root, PROJECTS_DIR_NAME),
                new File(root, COMPILED_DIR_NAME),
                new File(root, "import")
        };
        for (File dir : dirs) {
            if (!dir.exists() && !dir.mkdirs()) {
                return false;
            }
        }
        return true;
    }

    public void saveSafTreeUri(Uri treeUri) {
        saveSafTreeUri(treeUri, KEY_SAF_TREE);
    }

    public void saveSafToolsTreeUri(Uri treeUri) {
        saveSafTreeUri(treeUri, KEY_SAF_TREE_TOOLS);
    }

    private void saveSafTreeUri(Uri treeUri, String key) {
        final int flags = Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION;
        try {
            context.getContentResolver().takePersistableUriPermission(treeUri, flags);
        } catch (SecurityException ignored) {
            try {
                context.getContentResolver().takePersistableUriPermission(treeUri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (SecurityException ignoredAgain) {
            }
        }
        prefs.edit().putString(key, treeUri.toString()).apply();
    }

    public Uri getSafTreeUri() {
        String value = prefs.getString(KEY_SAF_TREE, null);
        if (value == null) {
            return null;
        }
        return Uri.parse(value);
    }

    public Uri getSafToolsTreeUri() {
        String value = prefs.getString(KEY_SAF_TREE_TOOLS, null);
        if (value == null) {
            return null;
        }
        return Uri.parse(value);
    }

    public boolean hasSafTree() {
        return getSafTreeUri() != null;
    }

    public int importSafTreeToPrivate() throws IOException {
        Uri treeUri = getSafTreeUri();
        if (treeUri == null) {
            return 0;
        }
        DocumentFile source = DocumentFile.fromTreeUri(context, treeUri);
        if (source == null || !source.isDirectory()) {
            return 0;
        }
        File destination = getPrivateRoot();
        return copyDocumentTree(source, destination);
    }

    public static final String TOOL_HLCSG = "libhlcsg.so";
    public static final String TOOL_HLBSP = "libhlbsp.so";
    public static final String TOOL_HLVIS = "libhlvis.so";
    public static final String TOOL_HLRAD = "libhlrad.so";

    private static final String[] TOOL_STAGE_NAMES = {"hlcsg", "hlbsp", "hlvis", "hlrad"};
    private static final String[] TOOL_TARGET_NAMES = {TOOL_HLCSG, TOOL_HLBSP, TOOL_HLVIS, TOOL_HLRAD};

    public int importSafToolsToPrivate() throws IOException {
        Uri treeUri = getSafToolsTreeUri();
        if (treeUri == null) {
            return 0;
        }
        DocumentFile source = DocumentFile.fromTreeUri(context, treeUri);
        if (source == null || !source.isDirectory()) {
            return 0;
        }
        return copyToolTree(source, getToolsDir());
    }

    private int copyToolTree(DocumentFile source, File destination) throws IOException {
        if (source.isDirectory()) {
            int copied = 0;
            for (DocumentFile child : source.listFiles()) {
                copied += copyToolTree(child, destination);
            }
            return copied;
        }

        String name = source.getName();
        if (name == null) {
            return 0;
        }
        int stage = stageForToolName(name);
        if (stage < 0) {
            return 0;
        }

        if (!destination.exists() && !destination.mkdirs()) {
            throw new IOException("Не удалось создать каталог: " + destination.getAbsolutePath());
        }
        File target = new File(destination, TOOL_TARGET_NAMES[stage]);

        try (InputStream rawInput = context.getContentResolver().openInputStream(source.getUri())) {
            if (rawInput == null) {
                throw new IOException("Не удалось открыть: " + source.getUri());
            }
            BufferedInputStream input = new BufferedInputStream(rawInput);
            if (!isElfBinary(input)) {
                return 0;
            }
            try (OutputStream output = new FileOutputStream(target)) {
                byte[] buffer = new byte[65536];
                int read;
                while ((read = input.read(buffer)) > 0) {
                    output.write(buffer, 0, read);
                }
            }
        }
        return 1;
    }

    private static int stageForToolName(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        for (int i = 0; i < TOOL_STAGE_NAMES.length; i++) {
            if (lower.contains(TOOL_STAGE_NAMES[i])) {
                return i;
            }
        }
        return -1;
    }

    private static boolean isElfBinary(BufferedInputStream input) throws IOException {
        byte[] magic = new byte[4];
        int read = 0;
        while (read < magic.length) {
            int step = input.read(magic, read, magic.length - read);
            if (step < 0) {
                return false;
            }
            read += step;
        }
        return magic[0] == 0x7F && magic[1] == 'E' && magic[2] == 'L' && magic[3] == 'F';
    }

    private int copyDocumentTree(DocumentFile source, File destination) throws IOException {
        if (source.isDirectory()) {
            if (!destination.exists() && !destination.mkdirs()) {
                throw new IOException("Не удалось создать каталог: " + destination.getAbsolutePath());
            }
            int copied = 0;
            DocumentFile[] children = source.listFiles();
            for (DocumentFile child : children) {
                String name = child.getName();
                if (name == null) {
                    continue;
                }
                copied += copyDocumentTree(child, new File(destination, name));
            }
            return copied;
        }

        try (InputStream input = context.getContentResolver().openInputStream(source.getUri())) {
            if (input == null) {
                throw new IOException("Не удалось открыть: " + source.getUri());
            }
            try (OutputStream output = new FileOutputStream(destination)) {
                byte[] buffer = new byte[65536];
                int read;
                while ((read = input.read(buffer)) > 0) {
                    output.write(buffer, 0, read);
                }
            }
        }
        return 1;
    }

    public String describeMode() {
        return hasAllFilesAccess() ? "public" : "private";
    }
}
