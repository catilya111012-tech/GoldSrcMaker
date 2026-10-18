package com.goldsrc.maker.render;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.TextView;

import com.goldsrc.maker.core.ModelData;
import com.goldsrc.maker.core.ModelStore;
import com.goldsrc.maker.core.Project;
import com.goldsrc.maker.core.ProjectManager;
import com.goldsrc.maker.core.StorageManager;
import com.goldsrc.maker.editor.MapData;

public class VulkanPreviewActivity extends Activity {

    public static final String EXTRA_PROJECT_NAME = "project_name";
    public static final String EXTRA_MODE = "mode";
    public static final String EXTRA_MODEL_NAME = "model_name";
    public static final String MODE_MAP = "map";
    public static final String MODE_MODEL = "model";

    private final Handler ui = new Handler(Looper.getMainLooper());
    private TextView statusView;
    private String projectName;
    private String modelName;
    private String mode = MODE_MAP;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        projectName = getIntent().getStringExtra(EXTRA_PROJECT_NAME);
        modelName = getIntent().getStringExtra(EXTRA_MODEL_NAME);
        String requestedMode = getIntent().getStringExtra(EXTRA_MODE);
        mode = MODE_MODEL.equals(requestedMode) ? MODE_MODEL : MODE_MAP;

        FrameLayout root = new FrameLayout(this);

        VulkanSurfaceView surfaceView = new VulkanSurfaceView(this);
        root.addView(surfaceView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));

        statusView = new TextView(this);
        statusView.setTextColor(Color.WHITE);
        statusView.setBackgroundColor(0xB3000000);
        statusView.setPadding(24, 16, 24, 16);
        statusView.setText("3D-превью (Vulkan)\nПроект: " + projectName + "\nЗагрузка геометрии "
                + (MODE_MODEL.equals(mode) ? "модели…" : "карты…"));
        FrameLayout.LayoutParams statusParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM);
        root.addView(statusView, statusParams);

        surfaceView.setStatusListener(message -> setStatus(message));

        setContentView(root);

        loadGeometryAsync(surfaceView);
    }

    private void loadGeometryAsync(final VulkanSurfaceView surfaceView) {
        new Thread(() -> {
            try {
                StorageManager storage = new StorageManager(this);
                ProjectManager projects = new ProjectManager(storage);
                Project project = projects.load(projectName);
                if (project == null) {
                    ui.post(() -> setStatus("Проект не найден: " + projectName));
                    return;
                }
                if (MODE_MODEL.equals(mode)) {
                    ModelStore store = new ModelStore(project.getDir());
                    ModelData model = modelName != null ? store.load(modelName) : null;
                    if (model == null) {
                        model = store.loadOrCreateStarter(project.getName());
                    }
                    final float[] modelVertices = GeometryBuilder.buildModel(model);
                    final int parts = model.parts.size();
                    ui.post(() -> {
                        surfaceView.setGeometry(modelVertices);
                        setStatus("Части модели: " + parts
                                + ", вершин: " + GeometryBuilder.vertexCount(modelVertices)
                                + "\nВид: изометрия сверху, 45°.");
                    });
                    return;
                }
                MapData map = projects.loadMap(project);
                final float[] vertices = GeometryBuilder.build(map);
                final int brushes = map.brushes.size();
                final int entities = map.entities.size();
                ui.post(() -> {
                    surfaceView.setGeometry(vertices);
                    setStatus("Браши: " + brushes + ", энтити: " + entities
                            + ", вершин: " + GeometryBuilder.vertexCount(vertices)
                            + (vertices.length / GeometryBuilder.FLOATS_PER_VERTEX >= GeometryBuilder.MAX_VERTICES
                            ? " (геометрия усечена)" : "")
                            + "\nВид: изометрия сверху, 45°.");
                });
            } catch (final Exception e) {
                ui.post(() -> setStatus("Ошибка загрузки геометрии: " + e.getMessage()));
            }
        }).start();
    }

    private void setStatus(String message) {
        statusView.setText("3D-превью (Vulkan)\nПроект: " + projectName + "\n" + message);
    }
}
