package com.goldsrc.maker.render;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.TextView;

public class VulkanPreviewActivity extends Activity {

    public static final String EXTRA_PROJECT_NAME = "project_name";

    private TextView statusView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        String projectName = getIntent().getStringExtra(EXTRA_PROJECT_NAME);

        FrameLayout root = new FrameLayout(this);

        VulkanSurfaceView surfaceView = new VulkanSurfaceView(this);
        root.addView(surfaceView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));

        statusView = new TextView(this);
        statusView.setTextColor(Color.WHITE);
        statusView.setBackgroundColor(0xB3000000);
        statusView.setPadding(24, 16, 24, 16);
        statusView.setText("3D-превью (Vulkan)\nПроект: " + projectName
                + "\nЗагрузка геометрии BSP подключается к конвейеру рендера.");
        FrameLayout.LayoutParams statusParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM);
        root.addView(statusView, statusParams);

        surfaceView.setStatusListener(message -> statusView.setText(
                "3D-превью (Vulkan)\nПроект: " + projectName + "\n" + message));

        setContentView(root);
    }
}
