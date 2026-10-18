package com.goldsrc.maker;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.goldsrc.maker.compiler.CompilerToolchain;
import com.goldsrc.maker.compiler.MapCompiler;
import com.goldsrc.maker.core.GameFileValidator;
import com.goldsrc.maker.core.Project;
import com.goldsrc.maker.core.ProjectManager;
import com.goldsrc.maker.core.StorageManager;
import com.goldsrc.maker.editor.EditorActivity;
import com.goldsrc.maker.editor.MapData;
import com.goldsrc.maker.editor.ModelEditorActivity;
import com.goldsrc.maker.editor.TexturesActivity;
import com.goldsrc.maker.launcher.GameLauncher;
import com.goldsrc.maker.scripting.HybridScriptEngine;

import java.io.File;
import java.util.List;

public class MainActivity extends Activity {

    private static final int REQ_MANAGE_STORAGE = 1001;
    private static final int REQ_LEGACY_STORAGE = 1002;
    private static final int REQ_SAF_TREE = 1003;
    private static final int REQ_SAF_TOOLS = 1004;

    private final Handler ui = new Handler(Looper.getMainLooper());

    private StorageManager storage;
    private ProjectManager projects;

    private TextView statusView;
    private TextView pathView;
    private Button btnNewProject;
    private Button btnOpenEditor;
    private Button btnCompile;
    private Button btnRun;
    private Button btnGrant;
    private Button btnImport;

    private GameFileValidator.Report report;
    private boolean busy = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        storage = new StorageManager(this);
        projects = new ProjectManager(storage);
        HybridScriptEngine.initRuntime(this);
        buildInterface();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshEnvironmentAsync();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQ_SAF_TREE && resultCode == RESULT_OK && data != null && data.getData() != null) {
            storage.saveSafTreeUri(data.getData());
            importResourcesAsync();
        } else if (requestCode == REQ_SAF_TOOLS && resultCode == RESULT_OK && data != null && data.getData() != null) {
            storage.saveSafToolsTreeUri(data.getData());
            importToolsAsync();
        } else if (requestCode == REQ_MANAGE_STORAGE) {
            refreshEnvironmentAsync();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_LEGACY_STORAGE) {
            refreshEnvironmentAsync();
        }
    }

    private void buildInterface() {
        ScrollView scroll = new ScrollView(this);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF101418);
        root.setPadding(dp(20), dp(24), dp(20), dp(24));
        scroll.addView(root);

        TextView title = new TextView(this);
        title.setText(R.string.app_name);
        title.setTextSize(24f);
        title.setTextColor(0xFFFFC107);
        title.setGravity(Gravity.START);
        root.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText("Редактор уровней и логики GoldSrc прямо на телефоне.\n"
                + "Legally clean: приложение не содержит файлов Valve — только ваш контент.");
        subtitle.setTextColor(0xFF90A4AE);
        subtitle.setTextSize(13f);
        subtitle.setPadding(0, dp(8), 0, dp(16));
        root.addView(subtitle);

        statusView = new TextView(this);
        statusView.setTextColor(Color.WHITE);
        statusView.setTextSize(13f);
        statusView.setText(R.string.status_checking);
        root.addView(statusView);

        pathView = new TextView(this);
        pathView.setTextColor(0xFF78909C);
        pathView.setTextSize(11f);
        pathView.setPadding(0, dp(8), 0, dp(16));
        root.addView(pathView);

        btnNewProject = addButton(root, R.string.action_new_project, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onNewProject();
            }
        });

        btnOpenEditor = addButton(root, R.string.action_open_editor, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                pickProject(getString(R.string.action_open_editor), new ProjectCallback() {
                    @Override
                    public void onProject(Project project) {
                        openEditor(project);
                    }
                });
            }
        });

        btnCompile = addButton(root, R.string.action_compile_map, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                pickProject(getString(R.string.action_compile_map), new ProjectCallback() {
                    @Override
                    public void onProject(Project project) {
                        compileProject(project);
                    }
                });
            }
        });

        btnRun = addButton(root, R.string.action_run_game, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                pickProject(getString(R.string.action_run_game), new ProjectCallback() {
                    @Override
                    public void onProject(Project project) {
                        runProject(project);
                    }
                });
            }
        });

        btnGrant = addButton(root, R.string.action_grant_storage, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                requestStorageAccess();
            }
        });

        btnImport = addButton(root, R.string.action_import_resources, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                pickSafTree();
            }
        });

        addButton(root, R.string.action_import_tools, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                pickSafToolsTree();
            }
        });

        addButton(root, R.string.action_texture_editor, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                pickProject(getString(R.string.action_texture_editor), new ProjectCallback() {
                    @Override
                    public void onProject(Project project) {
                        openTextureEditor(project);
                    }
                });
            }
        });

        addButton(root, R.string.action_model_editor, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                pickProject(getString(R.string.action_model_editor), new ProjectCallback() {
                    @Override
                    public void onProject(Project project) {
                        openModelEditor(project);
                    }
                });
            }
        });

        setContentView(scroll);
    }

    private Button addButton(LinearLayout parent, int textRes, View.OnClickListener listener) {
        Button button = new Button(this);
        button.setText(textRes);
        button.setTextSize(15f);
        button.setBackgroundColor(0xFF1B5E20);
        button.setTextColor(Color.WHITE);
        button.setOnClickListener(listener);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, dp(6), 0, dp(6));
        parent.addView(button, params);
        return button;
    }

    private void refreshEnvironmentAsync() {
        statusView.setText(R.string.status_checking);
        new Thread(() -> {
            try {
                if (storage.hasAllFilesAccess()) {
                    storage.ensureBaseFolders();
                }
                final File valveDir = storage.findValveDir();
                final GameFileValidator.Report localReport = GameFileValidator.validate(valveDir);
                ui.post(() -> applyReport(localReport));
            } catch (final Exception e) {
                ui.post(() -> statusView.setText("Ошибка доступа к хранилищу: " + e.getMessage()));
            }
        }).start();
    }

    private void applyReport(GameFileValidator.Report localReport) {
        report = localReport;

        statusView.setText(GameFileValidator.buildUserMessage(
                localReport, getString(R.string.msg_missing_textures)));

        pathView.setText("Рабочая папка: " + storage.getActiveRoot().getAbsolutePath()
                + "\n" + (storage.hasAllFilesAccess()
                        ? getString(R.string.status_storage_mode_public)
                        : getString(R.string.status_storage_mode_private))
                + "\nГибридный конструктор: " + HybridScriptEngine.getRuntimeInfo());

        boolean ready = localReport.isTexturesReady();
        btnOpenEditor.setEnabled(ready);
        btnCompile.setEnabled(ready);
        btnRun.setEnabled(ready);
        btnGrant.setVisibility(storage.hasAllFilesAccess() ? View.GONE : View.VISIBLE);
    }

    private boolean passFileGate() {
        if (report == null) {
            toast("Подождите, идёт проверка ресурсов…");
            return false;
        }
        if (!report.isTexturesReady()) {
            showMissingFilesDialog();
            return false;
        }
        return true;
    }

    private void showMissingFilesDialog() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.dialog_missing_title)
                .setMessage(GameFileValidator.buildUserMessage(report, getString(R.string.msg_missing_textures)))
                .setPositiveButton(R.string.action_open_storage_settings, (dialog, which) -> requestStorageAccess())
                .setNegativeButton(R.string.action_import_resources, (dialog, which) -> pickSafTree())
                .setNeutralButton(R.string.action_cancel, null)
                .show();
    }

    private interface ProjectCallback {
        void onProject(Project project);
    }

    private void pickProject(String title, final ProjectCallback callback) {
        if (!passFileGate()) {
            return;
        }
        final List<Project> list = projects.listProjects();
        if (list.isEmpty()) {
            toast(getString(R.string.msg_no_projects));
            onNewProject();
            return;
        }
        String[] names = new String[list.size()];
        for (int i = 0; i < list.size(); i++) {
            names[i] = list.get(i).getName();
        }
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setItems(names, (dialog, which) -> callback.onProject(list.get(which)))
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    private void onNewProject() {
        if (!passFileGate()) {
            return;
        }
        final EditText input = new EditText(this);
        input.setHint("имя_проекта");
        input.setInputType(InputType.TYPE_CLASS_TEXT);

        new AlertDialog.Builder(this)
                .setTitle(R.string.action_new_project)
                .setMessage("Имя папки проекта. Разрешены A-Z, a-z, 0-9, _ и -")
                .setView(input)
                .setPositiveButton(R.string.action_create, (dialog, which) -> {
                    final String name = input.getText().toString().trim();
                    new Thread(() -> {
                        try {
                            final Project project = projects.createProject(name);
                            ui.post(() -> openEditor(project));
                        } catch (final Exception e) {
                            ui.post(() -> toast("Не удалось создать проект: " + e.getMessage()));
                        }
                    }).start();
                })
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    private void openEditor(Project project) {
        Intent intent = new Intent(this, EditorActivity.class);
        intent.putExtra(EditorActivity.EXTRA_PROJECT_NAME, project.getName());
        startActivity(intent);
    }

    private void openTextureEditor(Project project) {
        Intent intent = new Intent(this, TexturesActivity.class);
        intent.putExtra(TexturesActivity.EXTRA_PROJECT_NAME, project.getName());
        startActivity(intent);
    }

    private void openModelEditor(Project project) {
        Intent intent = new Intent(this, ModelEditorActivity.class);
        intent.putExtra(ModelEditorActivity.EXTRA_PROJECT_NAME, project.getName());
        startActivity(intent);
    }

    private void compileProject(final Project project) {
        if (busy) {
            return;
        }
        busy = true;

        final AlertDialog progress = new AlertDialog.Builder(this)
                .setTitle("Компиляция: " + project.getName())
                .setMessage("HLCSG → HLBSP → HLVIS → HLRAD…")
                .setCancelable(false)
                .create();
        progress.show();

        new Thread(() -> {
            MapCompiler.Result result;
            try {
                MapData map = projects.loadMap(project);
                result = MapCompiler.compile(this, project, map, storage.findValveDir(),
                        new MapCompiler.CompileListener() {
                            @Override
                            public void onStageStart(int stage, String stageName) {
                                ui.post(() -> progress.setMessage(
                                        "Этап " + (stage + 1) + "/4: " + stageName + "…"));
                            }

                            @Override
                            public void onStageLog(int stage, String line) {
                                final String trimmed = line.trim();
                                if (trimmed.isEmpty()) {
                                    return;
                                }
                                ui.post(() -> progress.setMessage(
                                        "Этап " + MapCompiler.STAGE_NAMES[stage] + "…\n" + trimmed));
                            }

                            @Override
                            public void onStageFinish(int stage, String stageName, int exitCode) {
                            }
                        });
            } catch (final Exception e) {
                result = MapCompiler.Result.failure("Исключение: " + e.getMessage());
            }
            final MapCompiler.Result finalResult = result;
            ui.post(() -> {
                busy = false;
                progress.dismiss();
                showCompileResult(project, finalResult);
            });
        }).start();
    }

    private void showCompileResult(final Project project, MapCompiler.Result result) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle(result.success ? "Готово: .bsp собран" : "Компиляция не завершена")
                .setMessage(result.log)
                .setPositiveButton("ОК", null);

        if (result.success && GameLauncher.isEngineInstalled(this)) {
            builder.setNeutralButton(R.string.action_run_game, (dialog, which) ->
                    runProject(project));
        }
        builder.show();
    }

    private void runProject(final Project project) {
        if (!GameLauncher.isEngineInstalled(this)) {
            showEngineInstallDialog();
            return;
        }
        final File valveDir = storage.findValveDir();
        final GameLauncher.LaunchPlan plan = GameLauncher.buildPlan(
                this, valveDir.getParentFile(), project.getName());
        GameLauncher.launchWithDialogs(this, plan);
    }

    private void showEngineInstallDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Нужен движок Xash3D FWGS")
                .setMessage("Для запуска карт установите свободный движок Xash3D FWGS "
                        + "(open-source, поддерживает GoldSrc). Файлы игр не входят в состав движка.")
                .setPositiveButton("Открыть F-Droid", (dialog, which) -> {
                    try {
                        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(GameLauncher.getInstallUrl())));
                    } catch (ActivityNotFoundException e) {
                        toast(GameLauncher.getInstallUrl());
                    }
                })
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    private void requestStorageAccess() {
        if (storage.isLegacyRuntimePermissionRequired()) {
            storage.requestLegacyRuntimePermission(this, REQ_LEGACY_STORAGE);
            return;
        }
        Intent intent = storage.buildAllFilesAccessIntent();
        if (intent != null) {
            try {
                startActivityForResult(intent, REQ_MANAGE_STORAGE);
                return;
            } catch (ActivityNotFoundException ignored) {
            }
            try {
                startActivityForResult(storage.buildAllFilesAccessFallbackIntent(), REQ_MANAGE_STORAGE);
                return;
            } catch (ActivityNotFoundException ignored) {
            }
        }
        toast("Не удалось открыть системные настройки доступа к файлам");
    }

    private void pickSafTree() {
        try {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                    | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
            startActivityForResult(intent, REQ_SAF_TREE);
        } catch (ActivityNotFoundException e) {
            toast("Система не предоставила выбор папки (SAF)");
        }
    }

    private void pickSafToolsTree() {
        try {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                    | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
            startActivityForResult(intent, REQ_SAF_TOOLS);
        } catch (ActivityNotFoundException e) {
            toast("Система не предоставила выбор папки (SAF)");
        }
    }

    private void importToolsAsync() {
        final AlertDialog progress = new AlertDialog.Builder(this)
                .setTitle("Импорт компиляторов")
                .setMessage("Поиск libhlcsg.so / libhlbsp.so / libhlvis.so / libhlrad.so…")
                .setCancelable(false)
                .create();
        progress.show();

        new Thread(() -> {
            try {
                final int copied = storage.importSafToolsToPrivate();
                final List<CompilerToolchain.ToolStatus> statuses = CompilerToolchain.probeAll(this);
                ui.post(() -> {
                    progress.dismiss();
                    showToolchainReport(copied, statuses);
                });
            } catch (final Exception e) {
                ui.post(() -> {
                    progress.dismiss();
                    toast("Ошибка импорта: " + e.getMessage());
                });
            }
        }).start();
    }

    private void showToolchainReport(int copied, List<CompilerToolchain.ToolStatus> statuses) {
        new AlertDialog.Builder(this)
                .setTitle(copied > 0 ? "Импортировано файлов: " + copied : "Бинарники не найдены")
                .setMessage(CompilerToolchain.describe(statuses))
                .setPositiveButton(R.string.action_ok, null)
                .show();
    }

    private void importResourcesAsync() {
        final AlertDialog progress = new AlertDialog.Builder(this)
                .setTitle("Импорт ресурсов")
                .setMessage("Копирование в приватное хранилище приложения…")
                .setCancelable(false)
                .create();
        progress.show();

        new Thread(() -> {
            try {
                final int copied = storage.importSafTreeToPrivate();
                ui.post(() -> {
                    progress.dismiss();
                    toast("Импортировано файлов: " + copied);
                    refreshEnvironmentAsync();
                });
            } catch (final Exception e) {
                ui.post(() -> {
                    progress.dismiss();
                    toast("Ошибка импорта: " + e.getMessage());
                });
            }
        }).start();
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    private int dp(int value) {
        return Math.round(getResources().getDisplayMetrics().density * value);
    }
}
