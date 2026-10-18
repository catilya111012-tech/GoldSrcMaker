package com.goldsrc.maker.editor;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;

import com.goldsrc.maker.core.TextureImage;

public class PaletteView extends View {

    public interface Listener {
        void onColorSelected(int paletteIndex);
    }

    private static final int COLUMNS = 16;

    private final Paint swatchPaint = new Paint();
    private final Paint selectedPaint = new Paint();
    private final Paint borderPaint = new Paint();
    private final RectF rect = new RectF();

    private int[] palette = TextureImage.defaultPalette();
    private int selectedIndex = 1;
    private Listener listener;

    public PaletteView(Context context) {
        super(context);
        selectedPaint.setStyle(Paint.Style.STROKE);
        selectedPaint.setStrokeWidth(5f);
        selectedPaint.setColor(0xFFFFEB3B);
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(2f);
        borderPaint.setColor(0xFF37474F);
        setBackgroundColor(0xFF0E1114);
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public void setPalette(int[] palette) {
        if (palette != null && palette.length == TextureImage.PALETTE_SIZE) {
            this.palette = palette;
        }
        invalidate();
    }

    public int getSelectedIndex() {
        return selectedIndex;
    }

    public void setSelectedIndex(int index) {
        selectedIndex = index & 0xFF;
        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int swatch = Math.max(1, width / COLUMNS);
        int height = swatch * (TextureImage.PALETTE_SIZE / COLUMNS);
        setMeasuredDimension(width, height);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int swatchWidth = getWidth() / COLUMNS;
        int swatchHeight = Math.max(1, getHeight() / (TextureImage.PALETTE_SIZE / COLUMNS));
        for (int i = 0; i < TextureImage.PALETTE_SIZE; i++) {
            int column = i % COLUMNS;
            int row = i / COLUMNS;
            float left = column * swatchWidth;
            float top = row * swatchHeight;
            rect.set(left, top, left + swatchWidth, top + swatchHeight);
            swatchPaint.setColor(0xFF000000 | (palette[i] & 0xFFFFFF));
            canvas.drawRect(rect, swatchPaint);
            if (i == selectedIndex) {
                rect.inset(3f, 3f);
                canvas.drawRect(rect, selectedPaint);
                rect.inset(-3f, -3f);
            }
        }
        canvas.drawRect(0, 0, getWidth(), getHeight(), borderPaint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getActionMasked() != MotionEvent.ACTION_DOWN
                && event.getActionMasked() != MotionEvent.ACTION_MOVE) {
            return true;
        }
        int swatchWidth = Math.max(1, getWidth() / COLUMNS);
        int swatchHeight = Math.max(1, getHeight() / (TextureImage.PALETTE_SIZE / COLUMNS));
        int column = (int) (event.getX() / swatchWidth);
        int row = (int) (event.getY() / swatchHeight);
        column = Math.max(0, Math.min(COLUMNS - 1, column));
        row = Math.max(0, Math.min(TextureImage.PALETTE_SIZE / COLUMNS - 1, row));
        int index = row * COLUMNS + column;
        if (index != selectedIndex || event.getActionMasked() == MotionEvent.ACTION_DOWN) {
            selectedIndex = index;
            invalidate();
            if (listener != null) {
                listener.onColorSelected(index);
            }
        }
        return true;
    }
}
