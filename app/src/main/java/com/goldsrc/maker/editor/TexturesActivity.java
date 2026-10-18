package com.goldsrc.maker.editor;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.goldsrc.maker.core.Project;
import com.goldsrc.maker.core.ProjectManager;
import com.goldsrc.maker.core.StorageManager;
import com.goldsrc.maker.core.TextureImage;
import com.goldsrc.maker.core.TextureStore;
import com.goldsrc.maker.core.WadReader;
import com.goldsrc.maker.core.WadWriter;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

public class TexturesActivity extends Activity {

    public static final String EXTRA_PROJECT_NAME = "project_name";

    private static final int[] SIZES = {32, 64, 128, 256};
    private static final int TOOL_ACTIVE = 0xFFF57C00;
    private static final int TOOL_IDLE = 0xFF263238;

    private final Handler ui = new Handler(Looper.getMainLooper());

    private StorageManager storage;
    private ProjectManager projects;
    private Project project;
    private TextureStore store;

    private PixelCanvasView pixelCanvas;
    private PaletteView paletteView;
    private Button textureButton;
    private Button penButton;
    private Button eraseButton;
    private Button fillButton;
    private Button pickButton;

    private List<TextureImage> textures = new ArrayList<>();
    private TextureImage current;
    private boolean dirty;
    private int[] projectPalette;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        String projectName = getIntent().getStringExtra(EXTRA_PROJECT_NAME);
        storage = new StorageManager(this);
        projects = new ProjectManager(storage);
        project = projects.load(projectName);
        if (project == null) {
            toast("Проект не найден: " + projectName);
            finish();
            return;
        }
        store = new TextureStore(project.getDir());

        buildInterface();
        loadPaletteAsync();
        loadTexturesAsync(null);
    }

    private void buildInterface() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF15191D);

        LinearLayout toolbar = new LinearLayout(this);
        toolbar.setOrientation(LinearLayout.HORIZONTAL);
        toolbar.setPadding(dp(8), dp(8), dp(8), 0);

        textureButton = toolButton("Текстура: —", v -> showTexturePicker());
        toolbar.addView(textureButton);

        toolbar.addView(toolButton("Новая", v -> showNewTextureDialog()));
        toolbar.addView(toolButton("Импорт…", v -> showImportDialog()));
        toolbar.addView(toolButton("Удалить", v -> confirmDelete()));
        toolbar.addView(toolButton("Отменить", v -> pixelCanvas.undo()));

        HorizontalScrollView toolbarScroll = new HorizontalScrollView(this);
        toolbarScroll.addView(toolbar);
        root.addView(toolbarScroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        pixelCanvas = new PixelCanvasView(this);
        pixelCanvas.setListener(new PixelCanvasView.Listener() {
            @Override
            public void onTextureEdited() {
                dirty = true;
            }

            @Override
            public void onColorPicked(int paletteIndex) {
                paletteView.setSelectedIndex(paletteIndex);
                updateToolButtons();
            }
        });
        root.addView(pixelCanvas, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout tools = new LinearLayout(this);
        tools.setOrientation(LinearLayout.HORIZONTAL);
        tools.setPadding(dp(8), dp(8), dp(8), 0);
        penButton = toolButton("Карандаш", v -> setTool(PixelCanvasView.Tool.PEN));
        eraseButton = toolButton("Ластик", v -> setTool(PixelCanvasView.Tool.ERASE));
        fillButton = toolButton("Заливка", v -> setTool(PixelCanvasView.Tool.FILL));
        pickButton = toolButton("Пипетка", v -> setTool(PixelCanvasView.Tool.PICK));
        tools.addView(penButton);
        tools.addView(eraseButton);
        tools.addView(fillButton);
        tools.addView(pickButton);
        root.addView(tools);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setPadding(dp(8), dp(8), dp(8), 0);
        Button save = toolButton("Сохранить", v -> saveCurrent(true));
        Button export = toolButton("Экспорт в игру", v -> showExportDialog());
        save.setBackgroundColor(0xFF1B5E20);
        export.setBackgroundColor(0xFF1B5E20);
        actions.addView(save);
        actions.addView(export);
        root.addView(actions);

        paletteView = new PaletteView(this);
        paletteView.setListener(paletteIndex -> pixelCanvas.setColorIndex(paletteIndex));
        ScrollView paletteScroll = new ScrollView(this);
        paletteScroll.addView(paletteView);
        LinearLayout.LayoutParams paletteParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(150));
        paletteParams.setMargins(0, dp(8), 0, 0);
        root.addView(paletteScroll, paletteParams);

        setContentView(root);
        setTool(PixelCanvasView.Tool.PEN);
    }

    private Button toolButton(String text, View.OnClickListener listener) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextSize(12f);
        button.setAllCaps(false);
        button.setTextColor(Color.WHITE);
        button.setBackgroundColor(0xFF263238);
        button.setOnClickListener(listener);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 0, dp(6), 0);
        button.setLayoutParams(params);
        return button;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void setTool(PixelCanvasView.Tool tool) {
        pixelCanvas.setTool(tool);
        updateToolButtons();
    }

    private void updateToolButtons() {
        PixelCanvasView.Tool activeTool = pixelCanvas.getTool();
        penButton.setBackgroundColor(activeTool == PixelCanvasView.Tool.PEN ? TOOL_ACTIVE : TOOL_IDLE);
        eraseButton.setBackgroundColor(activeTool == PixelCanvasView.Tool.ERASE ? TOOL_ACTIVE : TOOL_IDLE);
        fillButton.setBackgroundColor(activeTool == PixelCanvasView.Tool.FILL ? TOOL_ACTIVE : TOOL_IDLE);
        pickButton.setBackgroundColor(activeTool == PixelCanvasView.Tool.PICK ? TOOL_ACTIVE : TOOL_IDLE);
    }

    private void loadPaletteAsync() {
        new Thread(() -> {
            int[] palette = null;
            try {
                File wad = new File(storage.findValveDir(), "halflife.wad");
                if (wad.isFile()) {
                    palette = WadReader.readPalette(wad);
                }
            } catch (IOException ignored) {
            }
            final int[] loaded = palette;
            ui.post(() -> {
                projectPalette = loaded;
                paletteView.setPalette(loaded);
            });
        }).start();
    }

    private void loadTexturesAsync(final Runnable after) {
        new Thread(() -> {
            try {
                final List<TextureImage> loaded = store.loadAll();
                ui.post(() -> {
                    textures = loaded;
                    if (current == null && !loaded.isEmpty()) {
                        selectTexture(loaded.get(0));
                    }
                    if (loaded.isEmpty()) {
                        textureButton.setText("Текстура: —");
                        pixelCanvas.setTexture(null);
                    }
                    if (after != null) {
                        after.run();
                    }
                });
            } catch (final IOException e) {
                ui.post(() -> toast("Ошибка чтения текстур проекта: " + e.getMessage()));
            }
        }).start();
    }

    private void selectTexture(TextureImage texture) {
        textureButton.setText("Текстура: " + texture.name);
        pixelCanvas.setTexture(texture);
        paletteView.setPalette(texture.palette);
        current = texture;
        dirty = false;
    }

    private void showTexturePicker() {
        if (textures.isEmpty()) {
            toast("В проекте пока нет текстур — создайте «Новая» или «Импорт…»");
            return;
        }
        final String[] names = new String[textures.size()];
        for (int i = 0; i < textures.size(); i++) {
            TextureImage texture = textures.get(i);
            names[i] = texture.name + "  " + texture.width + "x" + texture.height;
        }
        new AlertDialog.Builder(this)
                .setTitle("Текстура проекта")
                .setItems(names, (dialog, which) -> {
                    saveCurrent(false);
                    selectTexture(textures.get(which));
                })
                .setNegativeButton("Отмена", null)
                .show();
    }

    private void showNewTextureDialog() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(18), dp(10), dp(18), dp(10));

        EditText nameInput = new EditText(this);
        nameInput.setHint("имя, напр. NEW_WALL");
        layout.addView(nameInput);

        final int[] chosenSize = {64};
        String[] sizeLabels = new String[SIZES.length];
        for (int i = 0; i < SIZES.length; i++) {
            sizeLabels[i] = SIZES[i] + "x" + SIZES[i];
        }
        new AlertDialog.Builder(this)
                .setTitle("Новая текстура")
                .setView(layout)
                .setSingleChoiceItems(sizeLabels, 1, (dialog, which) -> chosenSize[0] = SIZES[which])
                .setPositiveButton("Создать", (dialog, which) -> {
                    String name = TextureImage.sanitizeName(nameInput.getText().toString());
                    if (name.isEmpty()) {
                        toast("Введите имя текстуры");
                        return;
                    }
                    int finalSize = chosenSize[0];
                    if (textureByName(name) != null) {
                        new AlertDialog.Builder(this)
                                .setTitle("Уже есть")
                                .setMessage("Текстура " + name + " уже существует. Перезаписать?")
                                .setPositiveButton("Перезаписать", (d, w) ->
                                        createTexture(name, finalSize))
                                .setNegativeButton("Отмена", null)
                                .show();
                        return;
                    }
                    createTexture(name, finalSize);
                })
                .setNegativeButton("Отмена", null)
                .show();
    }

    private void createTexture(String name, int size) {
        int[] palette = current != null ? current.palette
                : (projectPalette != null ? projectPalette : TextureImage.defaultPalette());
        final TextureImage texture = new TextureImage(name, size, size, new byte[size * size], palette);
        saveToStoreAsync(texture, "Создана " + name + " " + size + "x" + size, () -> selectTexture(texture));
    }

    private void showImportDialog() {
        final File wad = new File(storage.findValveDir(), "halflife.wad");
        if (!wad.isFile()) {
            toast("Не найден halflife.wad — импорт невозможен");
            return;
        }
        new Thread(() -> {
            try {
                final List<String> names = WadReader.listTextures(wad);
                ui.post(() -> {
                    if (names.isEmpty()) {
                        toast("В WAD нет текстур");
                        return;
                    }
                    int cap = Math.min(names.size(), 500);
                    final String[] items = names.subList(0, cap).toArray(new String[0]);
                    new AlertDialog.Builder(this)
                            .setTitle("Импорт из halflife.wad (всего " + names.size() + ")")
                            .setItems(items, (dialog, which) -> importTextureAsync(wad, items[which]))
                            .setNegativeButton("Отмена", null)
                            .show();
                });
            } catch (final IOException e) {
                ui.post(() -> toast("WAD: " + e.getMessage()));
            }
        }).start();
    }

    private void importTextureAsync(final File wad, final String name) {
        new Thread(() -> {
            try {
                final TextureImage texture = WadReader.readTexture(wad, name);
                if (texture == null) {
                    ui.post(() -> toast("Не удалось прочитать " + name));
                    return;
                }
                store.save(texture);
                ui.post(() -> {
                    toast("Импортирована " + texture.name);
                    loadTexturesAsync(() -> selectTexture(texture));
                });
            } catch (final IOException e) {
                ui.post(() -> toast("Ошибка импорта: " + e.getMessage()));
            }
        }).start();
    }

    private void confirmDelete() {
        if (current == null) {
            toast("Сначала выберите текстуру");
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("Удалить текстуру?")
                .setMessage(current.name + " будет удалена из проекта "
                        + "(в halflife.wad она не тронута).")
                .setPositiveButton("Удалить", (dialog, which) -> deleteAsync(current.name))
                .setNegativeButton("Отмена", null)
                .show();
    }

    private void deleteAsync(final String name) {
        new Thread(() -> {
            try {
                store.delete(name);
                ui.post(() -> {
                    current = null;
                    toast("Удалена " + name);
                    loadTexturesAsync(() -> {
                        if (textures.isEmpty()) {
                            textureButton.setText("Текстура: —");
                            pixelCanvas.setTexture(null);
                        }
                    });
                });
            } catch (final IOException e) {
                ui.post(() -> toast("Ошибка удаления: " + e.getMessage()));
            }
        }).start();
    }

    private void saveCurrent(boolean notifyUser) {
        if (current == null) {
            if (notifyUser) {
                toast("Нет выбранной текстуры");
            }
            return;
        }
        if (!dirty && notifyUser) {
            toast(current.name + " уже сохранена в проекте");
            return;
        }
        final TextureImage texture = current;
        saveToStoreAsync(texture, notifyUser ? "Сохранена " + texture.name : null, null);
    }

    private void saveToStoreAsync(final TextureImage texture, final String message, final Runnable after) {
        new Thread(() -> {
            try {
                store.save(texture);
                ui.post(() -> {
                    if (message != null) {
                        toast(message);
                    }
                    dirty = false;
                    boolean found = false;
                    for (int i = 0; i < textures.size(); i++) {
                        if (textures.get(i).name.equalsIgnoreCase(texture.name)) {
                            textures.set(i, texture);
                            found = true;
                            break;
                        }
                    }
                    if (!found) {
                        textures.add(texture);
                    }
                    if (after != null) {
                        after.run();
                    }
                });
            } catch (final IOException e) {
                ui.post(() -> toast("Ошибка сохранения: " + e.getMessage()));
            }
        }).start();
    }

    private TextureImage textureByName(String name) {
        for (TextureImage texture : textures) {
            if (texture.name.equalsIgnoreCase(name)) {
                return texture;
            }
        }
        return null;
    }

    private void showExportDialog() {
        if (textures.isEmpty()) {
            toast("Нет текстур для экспорта");
            return;
        }
        File valveDir = storage.findValveDir();
        String[] options = {
                "Отдельный WAD: valve/" + project.getName() + "_tex.wad (безопасно)",
                "Дописать в halflife.wad (сделает halflife.wad.bak)"
        };
        new AlertDialog.Builder(this)
                .setTitle("Экспорт текстур в игру (" + textures.size() + ")")
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        exportSeparateWad(valveDir);
                    } else {
                        exportIntoHalflife(valveDir);
                    }
                })
                .setNegativeButton("Отмена", null)
                .show();
    }

    private void exportSeparateWad(final File valveDir) {
        new Thread(() -> {
            try {
                File target = new File(valveDir, project.getName() + "_tex.wad");
                store.save(current != null ? current : textures.get(0));
                copyFile(store.getWadFile(), target);
                ui.post(() -> new AlertDialog.Builder(this)
                        .setTitle("Экспортировано")
                        .setMessage("Текстуры записаны в:\n" + target.getAbsolutePath()
                                + "\n\nПри компиляции карты этот WAD автоматически добавится в worldspawn, "
                                + "и Xash3D сможет показать текстуры.")
                        .setPositiveButton("ОК", null)
                        .show());
            } catch (final IOException e) {
                ui.post(() -> toast("Ошибка экспорта: " + e.getMessage()));
            }
        }).start();
    }

    private void exportIntoHalflife(final File valveDir) {
        new Thread(() -> {
            try {
                File halflife = new File(valveDir, "halflife.wad");
                File backup = new File(valveDir, "halflife.wad.bak");
                if (!halflife.isFile()) {
                    ui.post(() -> toast("halflife.wad не найден"));
                    return;
                }
                if (!backup.isFile()) {
                    copyFile(halflife, backup);
                }
                if (current != null) {
                    store.save(current);
                }
                final int patched = WadWriter.patch(halflife, store.loadAll());
                ui.post(() -> new AlertDialog.Builder(this)
                        .setTitle("Готово: обновлено " + patched + " шт.")
                        .setMessage("Текстуры проекта записаны в halflife.wad.\n"
                                + "Резервная копия: halflife.wad.bak\n\n"
                                + "Откройте игру заново — текстуры видны в halflife.wad.")
                        .setPositiveButton("ОК", null)
                        .show());
            } catch (final IOException e) {
                ui.post(() -> toast("Ошибка записи в halflife.wad: " + e.getMessage()));
            }
        }).start();
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

    @Override
    protected void onPause() {
        super.onPause();
        if (dirty && current != null) {
            final TextureImage texture = current;
            new Thread(() -> {
                try {
                    store.save(texture);
                } catch (IOException ignored) {
                }
            }).start();
        }
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }
}
