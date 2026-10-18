package com.goldsrc.maker.render;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

public class VulkanSurfaceView extends SurfaceView implements SurfaceHolder.Callback {

    public interface StatusListener {
        void onStatus(String message);
    }

    private static final boolean LIBRARY_LOADED;
    private static final long FRAME_INTERVAL_MS = 16L;

    static {
        boolean loaded;
        try {
            System.loadLibrary("goldsrc_native");
            loaded = true;
        } catch (UnsatisfiedLinkError e) {
            loaded = false;
        }
        LIBRARY_LOADED = loaded;
    }

    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean rendererActive = false;
    private StatusListener statusListener;

    private final Runnable frameLoop = new Runnable() {
        @Override
        public void run() {
            if (!rendererActive) {
                return;
            }
            try {
                nativeDrawFrame();
            } catch (UnsatisfiedLinkError ignored) {
            }
            handler.postDelayed(this, FRAME_INTERVAL_MS);
        }
    };

    public VulkanSurfaceView(Context context) {
        super(context);
        getHolder().addCallback(this);
    }

    public static boolean isAvailable() {
        return LIBRARY_LOADED;
    }

    public void setStatusListener(StatusListener listener) {
        this.statusListener = listener;
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        if (!LIBRARY_LOADED) {
            notifyStatus("Нативный Vulkan-модуль не собран. Соберите проект с NDK (CMake).");
            return;
        }
        try {
            nativeSurfaceCreated(holder.getSurface());
            rendererActive = true;
            notifyStatus("Vulkan: " + nativeRendererInfo());
            handler.post(frameLoop);
        } catch (Throwable t) {
            notifyStatus("Vulkan init error: " + t.getMessage());
        }
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        if (!rendererActive) {
            return;
        }
        try {
            nativeSurfaceChanged(width, height);
        } catch (Throwable ignored) {
        }
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        rendererActive = false;
        handler.removeCallbacks(frameLoop);
        if (!LIBRARY_LOADED) {
            return;
        }
        try {
            nativeSurfaceDestroyed();
        } catch (Throwable ignored) {
        }
    }

    private void notifyStatus(String message) {
        if (statusListener != null) {
            statusListener.onStatus(message);
        }
    }

    private native void nativeSurfaceCreated(Surface surface);

    private native void nativeSurfaceChanged(int width, int height);

    private native void nativeDrawFrame();

    private native void nativeSurfaceDestroyed();

    private native String nativeRendererInfo();
}
