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

import com.goldsrc.maker.core.MdlWriter;
import com.goldsrc.maker.core.ModelData;
import com.goldsrc.maker.core.ModelStore;
import com.goldsrc.maker.core.Project;
import com.goldsrc.maker.core.ProjectManager;
import com.goldsrc.maker.core.StorageManager;
import com.goldsrc.maker.core.TextureImage;
import com.goldsrc.maker.core.TextureStore;
import com.goldsrc.maker.core.WadReader;
import com.goldsrc.maker.render.VulkanPreviewActivity;

import java.io.File;
import java.io.IOException;
import java.util.List;

public class ModelEditorActivity extends Activity {

    public static final String EXTRA_PROJECT_NAME = "project_name";

    private final Handler ui = new Handler(Looper.getMainLooper());

    private StorageManager storage;
    private Project project;
    private ModelStore store;
    private ModelData model;

    private Button modelButton;
    private LinearLayout partList;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        String projectName = getIntent().getStringExtra(EXTRA_PROJECT_NAME);
        storage = new StorageManager(this);
        ProjectManager projects = new ProjectManager(storage);
        project = projects.load(projectName);
        if (project == null) {
            toast("Проект не найден: " + projectName);
            finish();
            return;
        }
        store = new ModelStore(project.getDir());

        buildInterface();
        loadModelAsync();
    }

    private void buildInterface() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF15191D);

        LinearLayout toolbar = new LinearLayout(this);
        toolbar.setOrientation(LinearLayout.HORIZONTAL);
        toolbar.setPadding(dp(8), dp(8), dp(8), 0);

        modelButton = toolButton("Модель: —", v -> showModelPicker());
        toolbar.addView(modelButton);
        toolbar.addView(toolButton("Новая…", v -> showNewModelDialog()));
        toolbar.addView(toolButton("Добавить часть", v -> editPart(-1)));
        toolbar.addView(toolButton("3D-превью", v -> openPreview()));
        toolbar.addView(toolButton("Экспорт .mdl", v -> exportMdl()));

        HorizontalScrollView toolbarScroll = new HorizontalScrollView(this);
        toolbarScroll.addView(toolbar);
        root.addView(toolbarScroll);

        TextView hint = new TextView(this);
        hint.setText("Модель состоит из частей-коробок. Тапни часть, чтобы изменить "
                + "позицию, размер и текстуру. Экспорт положит .mdl в valve/models.");
        hint.setTextColor(0xFF90A4AE);
        hint.setTextSize(12f);
        hint.setPadding(dp(12), dp(8), dp(12), dp(4));
        root.addView(hint);

        ScrollView scroll = new ScrollView(this);
        partList = new LinearLayout(this);
        partList.setOrientation(LinearLayout.VERTICAL);
        partList.setPadding(dp(8), dp(4), dp(8), dp(16));
        scroll.addView(partList);
        root.addView(scroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        setContentView(root);
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

    private void loadModelAsync() {
        new Thread(() -> {
            try {
                final ModelData loaded = store.loadOrCreateStarter(project.getName());
                ui.post(() -> {
                    model = loaded;
                    modelButton.setText("Модель: " + loaded.name);
                    rebuildPartList();
                });
            } catch (final IOException e) {
                ui.post(() -> toast("Ошибка загрузки модели: " + e.getMessage()));
            }
        }).start();
    }

    private void rebuildPartList() {
        partList.removeAllViews();
        if (model == null) {
            return;
        }
        for (int i = 0; i < model.parts.size(); i++) {
            final int index = i;
            ModelData.Part part = model.parts.get(i);
            TextView row = new TextView(this);
            row.setText((i + 1) + ". " + part.name + "  [" + part.texture + "]\n"
                    + "позиция (" + fmt(part.x) + ", " + fmt(part.y) + ", " + fmt(part.z) + ")"
                    + "   размер (" + fmt(part.sx) + ", " + fmt(part.sy) + ", " + fmt(part.sz) + ")"
                    + "   UV x" + fmt(part.uvScale));
            row.setTextColor(0xFFECEFF1);
            row.setTextSize(13f);
            row.setPadding(dp(12), dp(10), dp(12), dp(10));
            row.setBackgroundColor(i % 2 == 0 ? 0xFF1E2429 : 0xFF232A30);
            row.setOnClickListener(v -> editPart(index));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            params.setMargins(0, 0, 0, dp(4));
            partList.addView(row, params);
        }
        if (model.parts.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("Частей нет. Нажми «Добавить часть».");
            empty.setTextColor(0xFF90A4AE);
            empty.setPadding(dp(12), dp(12), dp(12), dp(12));
            partList.addView(empty);
        }
    }

    private static String fmt(float value) {
        if (value == Math.round(value)) {
            return String.valueOf(Math.round(value));
        }
        return String.format(java.util.Locale.US, "%.1f", value);
    }

    private void showModelPicker() {
        final List<String> names = store.list();
        if (names.isEmpty()) {
            toast("Моделей пока нет — создайте «Новая…»");
            return;
        }
        String[] items = names.toArray(new String[0]);
        new AlertDialog.Builder(this)
                .setTitle("Модели проекта")
                .setItems(items, (dialog, which) -> loadNamedModel(items[which]))
                .setNeutralButton("Удалить…", (dialog, which) -> confirmDeleteModel())
                .setNegativeButton("Отмена", null)
                .show();
    }

    private void loadNamedModel(final String name) {
        new Thread(() -> {
            try {
                final ModelData loaded = store.load(name);
                if (loaded == null) {
                    ui.post(() -> toast("Модель не найдена: " + name));
                    return;
                }
                ui.post(() -> {
                    model = loaded;
                    modelButton.setText("Модель: " + loaded.name);
                    rebuildPartList();
                });
            } catch (final IOException e) {
                ui.post(() -> toast("Ошибка: " + e.getMessage()));
            }
        }).start();
    }

    private void confirmDeleteModel() {
        final List<String> names = store.list();
        if (names.isEmpty()) {
            return;
        }
        String[] items = names.toArray(new String[0]);
        new AlertDialog.Builder(this)
                .setTitle("Удалить модель")
                .setItems(items, (dialog, which) -> {
                    store.delete(items[which]);
                    if (model != null && model.name.equalsIgnoreCase(items[which])) {
                        model = null;
                        modelButton.setText("Модель: —");
                        partList.removeAllViews();
                    }
                    toast("Удалена " + items[which]);
                })
                .setNegativeButton("Отмена", null)
                .show();
    }

    private void showNewModelDialog() {
        EditText input = new EditText(this);
        input.setHint("имя модели, напр. MY_PROP");
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(18), dp(10), dp(18), dp(10));
        layout.addView(input);
        new AlertDialog.Builder(this)
                .setTitle("Новая модель")
                .setView(layout)
                .setPositiveButton("Создать", (dialog, which) -> {
                    String name = TextureImage.sanitizeName(input.getText().toString());
                    if (name.isEmpty()) {
                        toast("Введите имя модели");
                        return;
                    }
                    if (store.exists(name)) {
                        toast(name + " уже существует");
                        return;
                    }
                    ModelData created = ModelData.createDefault(name);
                    saveAsync(created, () -> {
                        model = created;
                        modelButton.setText("Модель: " + created.name);
                        rebuildPartList();
                    });
                })
                .setNegativeButton("Отмена", null)
                .show();
    }

    private void editPart(final int index) {
        if (model == null) {
            toast("Сначала создайте модель");
            return;
        }
        final boolean isNew = index < 0;
        final ModelData.Part existing = isNew
                ? new ModelData.Part("part" + (model.parts.size() + 1), "C1A0_WX",
                0, 0, 16, 16, 16, 16, 1.0f)
                : model.parts.get(index);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(18), dp(10), dp(18), dp(10));

        EditText nameInput = new EditText(this);
        nameInput.setHint("имя части");
        nameInput.setText(existing.name);
        layout.addView(nameInput);

        EditText textureInput = new EditText(this);
        textureInput.setHint("текстура (имя из WAD)");
        textureInput.setText(existing.texture);
        layout.addView(textureInput);

        Button projectTextures = new Button(this);
        projectTextures.setText("Текстуры проекта");
        projectTextures.setOnClickListener(v -> new Thread(() -> {
            try {
                final List<TextureImage> loaded =
                        new TextureStore(project.getDir()).loadAll();
                final String[] items = new String[loaded.size()];
                for (int i = 0; i < loaded.size(); i++) {
                    items[i] = loaded.get(i).name;
                }
                ui.post(() -> {
                    if (items.length == 0) {
                        toast("В проекте нет текстур");
                        return;
                    }
                    new AlertDialog.Builder(this)
                            .setTitle("Текстуры проекта")
                            .setItems(items, (d, w) -> textureInput.setText(items[w]))
                            .setNegativeButton("Отмена", null)
                            .show();
                });
            } catch (final IOException e) {
                ui.post(() -> toast("Ошибка: " + e.getMessage()));
            }
        }).start());
        layout.addView(projectTextures);

        EditText xyzInput = new EditText(this);
        xyzInput.setHint("позиция X Y Z");
        xyzInput.setText(fmt(existing.x) + " " + fmt(existing.y) + " " + fmt(existing.z));
        layout.addView(xyzInput);

        EditText sizeInput = new EditText(this);
        sizeInput.setHint("размер SX SY SZ (1..512)");
        sizeInput.setText(fmt(existing.sx) + " " + fmt(existing.sy) + " " + fmt(existing.sz));
        layout.addView(sizeInput);

        EditText uvInput = new EditText(this);
        uvInput.setHint("масштаб текстуры (0.1..8)");
        uvInput.setText(fmt(existing.uvScale));
        layout.addView(uvInput);

        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle(isNew ? "Новая часть" : "Часть " + existing.name)
                .setView(layout)
                .setPositiveButton("Применить", (dialog, which) -> {
                    float[] xyz = parseTriple(xyzInput.getText().toString(), 0, 0, 16);
                    float[] size = parseTriple(sizeInput.getText().toString(), 16, 16, 16);
                    float uv = parseFloat(uvInput.getText().toString(), 1.0f);
                    String partName = nameInput.getText().toString().trim();
                    if (partName.isEmpty()) {
                        partName = "part";
                    }
                    String texture = TextureImage.sanitizeName(textureInput.getText().toString());
                    if (texture.isEmpty()) {
                        texture = "DEV_MEASUREGENERIC01";
                    }
                    ModelData.Part part = new ModelData.Part(
                            partName, texture,
                            xyz[0], xyz[1], xyz[2],
                            ModelData.clampSize(size[0]),
                            ModelData.clampSize(size[1]),
                            ModelData.clampSize(size[2]),
                            ModelData.clampUv(uv));
                    if (isNew) {
                        model.addPart(part);
                    } else {
                        model.replacePart(index, part);
                    }
                    saveAsync(model, this::rebuildPartList);
                })
                .setNegativeButton("Отмена", null);
        if (!isNew) {
            builder.setNeutralButton("Удалить", (dialog, which) -> {
                model.removePart(index);
                saveAsync(model, this::rebuildPartList);
            });
        }
        builder.show();
    }

    private static float[] parseTriple(String text, float fallbackX, float fallbackY,
                                       float fallbackZ) {
        float[] result = {fallbackX, fallbackY, fallbackZ};
        String[] parts = text.trim().split("[\\s,;]+");
        for (int i = 0; i < 3 && i < parts.length; i++) {
            result[i] = parseFloat(parts[i], result[i]);
        }
        return result;
    }

    private static float parseFloat(String text, float fallback) {
        try {
            return Float.parseFloat(text.trim().replace(',', '.'));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private void saveAsync(final ModelData snapshot, final Runnable after) {
        new Thread(() -> {
            try {
                store.save(snapshot);
                if (after != null) {
                    ui.post(after);
                }
            } catch (final IOException e) {
                ui.post(() -> toast("Ошибка сохранения: " + e.getMessage()));
            }
        }).start();
    }

    private void openPreview() {
        if (model == null) {
            toast("Сначала создайте модель");
            return;
        }
        saveAsync(model, () -> {
            Intent intent = new Intent(this, VulkanPreviewActivity.class);
            intent.putExtra(VulkanPreviewActivity.EXTRA_PROJECT_NAME, project.getName());
            intent.putExtra(VulkanPreviewActivity.EXTRA_MODE, VulkanPreviewActivity.MODE_MODEL);
            intent.putExtra(VulkanPreviewActivity.EXTRA_MODEL_NAME, model.name);
            startActivity(intent);
        });
    }

    private void exportMdl() {
        if (model == null || model.parts.isEmpty()) {
            toast("Сначала создайте модель и добавьте части");
            return;
        }
        final ModelData snapshot = model;
        toast("Экспорт " + snapshot.name + ".mdl…");
        new Thread(() -> {
            try {
                store.save(snapshot);
                File modelsDir = new File(storage.findValveDir(), "models");
                if (!modelsDir.exists() && !modelsDir.mkdirs()) {
                    ui.post(() -> toast("Не удалось создать valve/models"));
                    return;
                }
                final File out = new File(modelsDir,
                        snapshot.name.toLowerCase(java.util.Locale.ROOT) + ".mdl");

                List<TextureImage> textures = new TextureStore(project.getDir()).loadAll();
                int[] stockPalette = null;
                File halflifeWad = new File(storage.findValveDir(), "halflife.wad");
                if (halflifeWad.isFile()) {
                    try {
                        stockPalette = WadReader.readPalette(halflifeWad);
                    } catch (IOException ignored) {
                    }
                }
                MdlWriter.write(out, snapshot, textures, stockPalette);

                final long size = out.length();
                ui.post(() -> new AlertDialog.Builder(this)
                        .setTitle("Экспортировано")
                        .setMessage("Модель записана:\n" + out.getAbsolutePath()
                                + "\n(" + size + " байт)\n\n"
                                + "Как использовать: создайте в игре монстра/проп или "
                                + "поставьте ent-энтити с model=\"*<номер>\" либо "
                                + "cycler с model=\"models/" + out.getName() + "\".")
                        .setPositiveButton("ОК", null)
                        .show());
            } catch (final IOException e) {
                ui.post(() -> toast("Ошибка экспорта: " + e.getMessage()));
            }
        }).start();
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }
}
