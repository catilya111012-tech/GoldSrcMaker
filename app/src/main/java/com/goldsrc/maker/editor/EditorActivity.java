package com.goldsrc.maker.editor;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.goldsrc.maker.compiler.MapCompiler;
import com.goldsrc.maker.core.Project;
import com.goldsrc.maker.core.ProjectManager;
import com.goldsrc.maker.core.StorageManager;
import com.goldsrc.maker.core.WadReader;
import com.goldsrc.maker.launcher.GameLauncher;
import com.goldsrc.maker.render.VulkanPreviewActivity;
import com.goldsrc.maker.scripting.BlockGraphView;
import com.goldsrc.maker.scripting.BlockNode;
import com.goldsrc.maker.scripting.BlockType;
import com.goldsrc.maker.scripting.CodeEditorView;
import com.goldsrc.maker.scripting.HybridScriptEngine;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class EditorActivity extends Activity {

    public static final String EXTRA_PROJECT_NAME = "project_name";

    private static final String[] ENTITY_CLASSES = {
            "info_player_start",
            "info_player_deathmatch",
            "light",
            "light_environment",
            "info_target",
            "ambient_generic",
            "env_sprite",
            "item_healthkit",
            "item_battery",
            "weapon_crowbar",
            "monster_scientist",
            "monster_zombie"
    };

    private final Handler ui = new Handler(Looper.getMainLooper());

    private StorageManager storage;
    private ProjectManager projects;
    private Project project;
    private MapData mapData;
    private HybridScriptEngine engine;

    private BrushCanvasView canvasView;
    private BlockGraphView graphView;
    private CodeEditorView codeView;
    private TextView statusView;
    private TextView caretView;
    private LinearLayout canvasTools;
    private LinearLayout blockTools;
    private LinearLayout codeTools;

    private Button wallButton;
    private Button roomButton;
    private Button entityButton;
    private Button eraseButton;
    private Button textureButton;
    private Button entityClassButton;

    private boolean codeDirty = false;
    private boolean busy = false;
    private BrushCanvasView.Mode lastTool = BrushCanvasView.Mode.WALL;

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

        try {
            mapData = projects.loadMap(project);
            engine = new HybridScriptEngine(this, project);
        } catch (Exception e) {
            toast("Ошибка загрузки проекта: " + e.getMessage());
            finish();
            return;
        }

        buildInterface();
        refreshAllViews();
        updateStatus();
    }

    @Override
    protected void onPause() {
        super.onPause();
        saveEverything();
    }

    private void buildInterface() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.BLACK);

        HorizontalScrollView modeBar = new HorizontalScrollView(this);
        modeBar.setBackgroundColor(0xFF0D3312);
        LinearLayout modeRow = new LinearLayout(this);
        modeBar.addView(modeRow);

        canvasView = new BrushCanvasView(this);
        graphView = new BlockGraphView(this);
        codeView = new CodeEditorView(this);

        FrameLayout content = new FrameLayout(this);
        content.addView(canvasView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        content.addView(graphView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        content.addView(codeView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        LinearLayout.LayoutParams contentParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);

        canvasTools = buildCanvasTools();
        blockTools = buildBlockTools();
        codeTools = buildCodeTools();

        LinearLayout mainBar = new LinearLayout(this);
        mainBar.setBackgroundColor(0xFF101820);
        mainBar.addView(button("Сохранить", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                saveEverything();
                toast("Проект сохранён");
            }
        }));
        mainBar.addView(button("Компилировать .bsp", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                compileProject();
            }
        }));
        mainBar.addView(button("Тест логики", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                testLogic();
            }
        }));
        mainBar.addView(button("Экспорт .map", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                exportMap();
            }
        }));

        statusView = new TextView(this);
        statusView.setTextSize(11f);
        statusView.setTextColor(0xFF90A4AE);
        statusView.setPadding(dp(8), dp(4), dp(8), dp(6));

        root.addView(modeBar);
        root.addView(content, contentParams);
        root.addView(canvasTools);
        root.addView(blockTools);
        root.addView(codeTools);
        root.addView(mainBar);
        root.addView(statusView);

        setContentView(root);

        modeRow.addView(button("2D-конструктор", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                switchMode(0);
            }
        }));
        modeRow.addView(button("Блоки", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                switchMode(1);
            }
        }));
        modeRow.addView(button("Код", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                switchMode(2);
            }
        }));
        modeRow.addView(button("3D-превью (Vulkan)", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                open3dPreview();
            }
        }));

        wireListeners();
        switchMode(0);
    }

    private LinearLayout buildCanvasTools() {
        LinearLayout row = toolbar();

        wallButton = button("Стена", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                lastTool = BrushCanvasView.Mode.WALL;
                canvasView.setMode(lastTool);
                highlightTool(lastTool);
            }
        });
        roomButton = button("Комната", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                lastTool = BrushCanvasView.Mode.ROOM;
                canvasView.setMode(lastTool);
                highlightTool(lastTool);
            }
        });
        entityButton = button("Энтити", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                lastTool = BrushCanvasView.Mode.ENTITY;
                canvasView.setMode(lastTool);
                highlightTool(lastTool);
            }
        });
        eraseButton = button("Ластик", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                lastTool = BrushCanvasView.Mode.ERASE;
                canvasView.setMode(lastTool);
                highlightTool(lastTool);
            }
        });

        textureButton = button("Текстура: NULL", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                chooseTexture();
            }
        });
        entityClassButton = button("Энтити: " + ENTITY_CLASSES[0], new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                chooseEntityClass();
            }
        });

        EditText floorZField = new EditText(this);
        floorZField.setHint("Z пола");
        floorZField.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_SIGNED);
        floorZField.setText("0");
        floorZField.setTextColor(Color.WHITE);
        floorZField.setWidth(dp(110));
        floorZField.addTextChangedListener(new TextWatcherAdapter() {
            @Override
            public void afterTextChanged(Editable s) {
                try {
                    canvasView.setFloorZ(Integer.parseInt(s.toString()));
                } catch (NumberFormatException ignored) {
                }
            }
        });

        EditText heightField = new EditText(this);
        heightField.setHint("Высота");
        heightField.setInputType(InputType.TYPE_CLASS_NUMBER);
        heightField.setText("128");
        heightField.setTextColor(Color.WHITE);
        heightField.setWidth(dp(110));
        heightField.addTextChangedListener(new TextWatcherAdapter() {
            @Override
            public void afterTextChanged(Editable s) {
                try {
                    canvasView.setWallHeight(Integer.parseInt(s.toString()));
                } catch (NumberFormatException ignored) {
                }
            }
        });

        Button undoButton = button("Undo", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                canvasView.undo();
            }
        });

        row.addView(wallButton);
        row.addView(roomButton);
        row.addView(entityButton);
        row.addView(eraseButton);
        row.addView(textureButton);
        row.addView(entityClassButton);
        row.addView(floorZField);
        row.addView(heightField);
        row.addView(undoButton);
        return row;
    }

    private LinearLayout buildBlockTools() {
        LinearLayout row = toolbar();
        row.addView(button("Добавить блок", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showAddBlockDialog();
            }
        }));
        row.addView(button("Блоки → код", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                codeView.setTextQuiet(engine.syncGraphToText());
                codeDirty = false;
                toast("Код сгенерирован из блоков");
            }
        }));
        return row;
    }

    private LinearLayout buildCodeTools() {
        LinearLayout row = toolbar();
        row.addView(button("Код → блоки", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                engine.syncTextToGraph(codeView.getContent());
                graphView.setGraph(engine.getGraph());
                codeView.setTextQuiet(engine.getScriptText());
                codeDirty = false;
                toast("Блоки обновлены из текста");
            }
        }));
        row.addView(button("Блоки → код", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                codeView.setTextQuiet(engine.syncGraphToText());
                codeDirty = false;
            }
        }));

        caretView = new TextView(this);
        caretView.setTextColor(0xFF90A4AE);
        caretView.setPadding(dp(12), dp(10), dp(12), dp(10));
        caretView.setText("Стр 1:1");
        row.addView(caretView);
        return row;
    }

    private void wireListeners() {
        canvasView.setChangeListener(new BrushCanvasView.MapChangeListener() {
            @Override
            public void onMapChanged() {
                updateStatus();
            }
        });

        codeView.setEditListener(new CodeEditorView.EditListener() {
            @Override
            public void onUserEdit(String text) {
                codeDirty = true;
                updateStatus();
            }
        });

        codeView.setCaretListener(new CodeEditorView.CaretListener() {
            @Override
            public void onCaret(int line, int column) {
                caretView.setText("Стр " + line + ":" + column);
            }
        });

        graphView.setListener(new BlockGraphView.Listener() {
            @Override
            public void onNodeTapped(BlockNode node) {
                showParamDialog(node);
            }

            @Override
            public void onNodeLongPressed(BlockNode node) {
                confirmDelete(node);
            }

            @Override
            public void onNodeMoved(BlockNode node) {
                engine.onNodeMoved();
            }
        });
    }

    private void switchMode(int mode) {
        canvasView.setVisibility(mode == 0 ? View.VISIBLE : View.GONE);
        graphView.setVisibility(mode == 1 ? View.VISIBLE : View.GONE);
        codeView.setVisibility(mode == 2 ? View.VISIBLE : View.GONE);
        canvasTools.setVisibility(mode == 0 ? View.VISIBLE : View.GONE);
        blockTools.setVisibility(mode == 1 ? View.VISIBLE : View.GONE);
        codeTools.setVisibility(mode == 2 ? View.VISIBLE : View.GONE);
        if (mode == 0) {
            highlightTool(lastTool);
        }
    }

    private void highlightTool(BrushCanvasView.Mode mode) {
        int active = 0xFF2E7D32;
        int idle = 0xFF263238;
        wallButton.setBackgroundColor(mode == BrushCanvasView.Mode.WALL ? active : idle);
        roomButton.setBackgroundColor(mode == BrushCanvasView.Mode.ROOM ? active : idle);
        entityButton.setBackgroundColor(mode == BrushCanvasView.Mode.ENTITY ? active : idle);
        eraseButton.setBackgroundColor(mode == BrushCanvasView.Mode.ERASE ? active : idle);
    }

    private void refreshAllViews() {
        canvasView.setMapData(mapData);
        graphView.setGraph(engine.getGraph());
        codeView.setTextQuiet(engine.getScriptText());
        textureButton.setText("Текстура: " + canvasView.getTexture());
    }

    private void updateStatus() {
        String dirty = codeDirty ? "  • код изменён (примените к блокам)" : "";
        statusView.setText(String.format(
                "Проект: %s | брашей: %d | энтити: %d | хранилище: %s | runtime: %s%s",
                project.getName(),
                mapData.brushes.size(),
                mapData.entities.size(),
                storage.describeMode(),
                HybridScriptEngine.getRuntimeInfo(),
                dirty));
    }

    private void showAddBlockDialog() {
        final BlockType[] types = BlockType.values();
        String[] titles = new String[types.length];
        for (int i = 0; i < types.length; i++) {
            titles[i] = types[i].title;
        }
        new AlertDialog.Builder(this)
                .setTitle("Добавить блок логики")
                .setItems(titles, (dialog, which) -> {
                    engine.addBlock(types[which]);
                    graphView.setGraph(engine.getGraph());
                    codeView.setTextQuiet(engine.getScriptText());
                    codeDirty = false;
                    toast("Блок добавлен в цепочку");
                })
                .setNegativeButton("Отмена", null)
                .show();
    }

    private void showParamDialog(final BlockNode node) {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(16), dp(8), dp(16), dp(8));

        final List<String> paramKeys = new ArrayList<>(node.params.keySet());
        final List<EditText> fields = new ArrayList<>();

        for (String key : paramKeys) {
            TextView label = new TextView(this);
            label.setText(key);
            label.setTextColor(0xFF90A4AE);
            layout.addView(label);

            EditText field = new EditText(this);
            field.setText(node.getParam(key));
            layout.addView(field);
            fields.add(field);
        }

        TextView hint = new TextView(this);
        hint.setText("Блок #" + node.id + (node.userEdited ? " (изменён вручную в коде)" : ""));
        hint.setTextColor(0xFFFFB300);
        layout.addView(hint);

        ScrollView wrapper = new ScrollView(this);
        wrapper.addView(layout);

        new AlertDialog.Builder(this)
                .setTitle(node.type.title)
                .setView(wrapper)
                .setPositiveButton("Применить", (dialog, which) -> {
                    for (int i = 0; i < paramKeys.size(); i++) {
                        engine.setParam(node.id, paramKeys.get(i), fields.get(i).getText().toString());
                    }
                    codeView.setTextQuiet(engine.getScriptText());
                    codeDirty = false;
                    graphView.invalidate();
                })
                .setNeutralButton("Удалить", (dialog, which) -> confirmDelete(node))
                .setNegativeButton("Отмена", null)
                .show();
    }

    private void confirmDelete(final BlockNode node) {
        new AlertDialog.Builder(this)
                .setTitle("Удалить блок #" + node.id + "?")
                .setMessage(node.type.title)
                .setPositiveButton("Удалить", (dialog, which) -> {
                    engine.removeBlock(node.id);
                    graphView.setGraph(engine.getGraph());
                    codeView.setTextQuiet(engine.getScriptText());
                    codeDirty = false;
                })
                .setNegativeButton("Отмена", null)
                .show();
    }

    private void chooseTexture() {
        final EditText input = new EditText(this);
        input.setText(canvasView.getTexture());

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(16), dp(8), dp(16), dp(8));
        layout.addView(input);

        final File wadFile = new File(storage.findValveDir(), "halflife.wad");
        Button browse = new Button(this);
        browse.setText("Список текстур из " + wadFile.getName());
        browse.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                new Thread(() -> {
                    try {
                        final List<String> names = WadReader.listTextures(wadFile);
                        ui.post(() -> showTextureList(names, input));
                    } catch (final Exception e) {
                        ui.post(() -> toast("WAD: " + e.getMessage()));
                    }
                }).start();
            }
        });
        layout.addView(browse);

        new AlertDialog.Builder(this)
                .setTitle("Текстура брашей")
                .setView(layout)
                .setPositiveButton("Применить", (dialog, which) -> {
                    canvasView.setTexture(input.getText().toString().trim());
                    textureButton.setText("Текстура: " + canvasView.getTexture());
                })
                .setNegativeButton("Отмена", null)
                .show();
    }

    private void showTextureList(List<String> names, EditText input) {
        if (names.isEmpty()) {
            toast("В WAD не найдено ни одной текстуры");
            return;
        }
        int cap = Math.min(names.size(), 500);
        final String[] items = names.subList(0, cap).toArray(new String[0]);
        new AlertDialog.Builder(this)
                .setTitle("Текстуры WAD (всего " + names.size() + ")")
                .setItems(items, (dialog, which) -> input.setText(items[which]))
                .setNegativeButton("Закрыть", null)
                .show();
    }

    private void chooseEntityClass() {
        new AlertDialog.Builder(this)
                .setTitle("Класс энтити")
                .setItems(ENTITY_CLASSES, (dialog, which) -> {
                    canvasView.setEntityClass(ENTITY_CLASSES[which]);
                    entityClassButton.setText("Энтити: " + ENTITY_CLASSES[which]);
                })
                .setNegativeButton("Отмена", null)
                .show();
    }

    private void testLogic() {
        if (codeDirty) {
            engine.syncTextToGraph(codeView.getContent());
            graphView.setGraph(engine.getGraph());
            codeDirty = false;
        }
        List<String> log = engine.simulate();
        StringBuilder builder = new StringBuilder();
        for (String line : log) {
            builder.append(line).append("\n");
        }
        new AlertDialog.Builder(this)
                .setTitle("Проигрывание логики (симуляция)")
                .setMessage(builder.length() == 0 ? "(нет шагов)" : builder.toString())
                .setPositiveButton("ОК", null)
                .show();
    }

    private void exportMap() {
        try {
            saveEverything();
            projects.exportMapFile(project, mapData);
            toast("Экспортировано: " + project.getMapFile().getAbsolutePath());
        } catch (Exception e) {
            toast("Ошибка экспорта: " + e.getMessage());
        }
    }

    private void compileProject() {
        if (busy) {
            return;
        }
        busy = true;
        saveEverything();

        final AlertDialog progress = new AlertDialog.Builder(this)
                .setTitle("Компиляция карты")
                .setMessage("Запуск HLCSG → HLBSP → HLVIS → HLRAD…")
                .setCancelable(false)
                .create();
        progress.show();

        new Thread(() -> {
            MapCompiler.Result result;
            try {
                result = MapCompiler.compile(this, project, mapData, storage.findValveDir());
            } catch (Exception e) {
                result = MapCompiler.Result.failure("Исключение: " + e.getMessage());
            }
            final MapCompiler.Result finalResult = result;
            ui.post(() -> {
                busy = false;
                progress.dismiss();
                showCompileResult(finalResult);
            });
        }).start();
    }

    private void showCompileResult(final MapCompiler.Result result) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle(result.success ? "Компиляция успешна" : "Компиляция не завершена")
                .setMessage(result.log)
                .setPositiveButton("ОК", null);

        if (result.success && GameLauncher.isEngineInstalled(this)) {
            builder.setNeutralButton("Запустить игру", (dialog, which) -> {
                GameLauncher.launch(EditorActivity.this, storage.getActiveRoot(), project.getName());
            });
        }
        builder.show();
    }

    private void open3dPreview() {
        Intent intent = new Intent(this, VulkanPreviewActivity.class);
        intent.putExtra(VulkanPreviewActivity.EXTRA_PROJECT_NAME, project.getName());
        startActivity(intent);
    }

    private void saveEverything() {
        try {
            if (codeDirty) {
                engine.syncTextToGraph(codeView.getContent());
                codeDirty = false;
            }
            projects.saveMap(project, mapData);
            engine.saveAll();
            updateStatus();
        } catch (Exception e) {
            toast("Ошибка сохранения: " + e.getMessage());
        }
    }

    private LinearLayout toolbar() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setBackgroundColor(0xFF101820);
        return row;
    }

    private Button button(String text, View.OnClickListener listener) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextSize(13f);
        button.setBackgroundColor(0xFF263238);
        button.setTextColor(Color.WHITE);
        button.setOnClickListener(listener);
        return button;
    }

    private int dp(int value) {
        return Math.round(getResources().getDisplayMetrics().density * value);
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private abstract static class TextWatcherAdapter implements TextWatcher {
        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
        }
    }
}
