package com.goldsrc.maker.editor;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.view.MotionEvent;
import android.view.View;

import com.goldsrc.maker.core.TextureImage;

import java.util.ArrayDeque;
import java.util.Deque;

public class PixelCanvasView extends View {

    public enum Tool {
        PEN,
        ERASE,
        FILL,
        PICK
    }

    public interface Listener {
        void onTextureEdited();

        void onColorPicked(int paletteIndex);
    }

    private static final int UNDO_LIMIT = 30;
    private static final int GRID_MIN_CELL = 10;

    private final Paint bitmapPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Paint gridPaint = new Paint();
    private final Paint borderPaint = new Paint();
    private final Rect destRect = new Rect();
    private final Deque<byte[]> undoStack = new ArrayDeque<>();

    private TextureImage texture;
    private Bitmap bitmap;
    private Listener listener;
    private Tool tool = Tool.PEN;
    private int colorIndex = 1;
    private int cellSize = 1;
    private int originX;
    private int originY;
    private boolean strokePushedUndo;
    private int lastPixelX = -1;
    private int lastPixelY = -1;

    public PixelCanvasView(Context context) {
        super(context);
        gridPaint.setColor(0x33FFFFFF);
        gridPaint.setStrokeWidth(1f);
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(4f);
        borderPaint.setColor(0xFF8BC34A);
        setBackgroundColor(0xFF15191D);
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public void setTexture(TextureImage texture) {
        this.texture = texture;
        this.undoStack.clear();
        if (texture != null) {
            bitmap = Bitmap.createBitmap(texture.width, texture.height, Bitmap.Config.ARGB_8888);
            refreshBitmap();
        } else {
            bitmap = null;
        }
        invalidate();
    }

    public TextureImage getTexture() {
        return texture;
    }

    public void setTool(Tool tool) {
        this.tool = tool;
    }

    public Tool getTool() {
        return tool;
    }

    public void setColorIndex(int colorIndex) {
        this.colorIndex = colorIndex & 0xFF;
    }

    public int getColorIndex() {
        return colorIndex;
    }

    public void undo() {
        if (undoStack.isEmpty() || texture == null) {
            return;
        }
        byte[] saved = undoStack.pop();
        System.arraycopy(saved, 0, texture.pixels, 0, saved.length);
        refreshBitmap();
        invalidate();
        fireEdited();
    }

    private void pushUndo() {
        if (texture == null) {
            return;
        }
        undoStack.push(texture.pixels.clone());
        while (undoStack.size() > UNDO_LIMIT) {
            undoStack.removeLast();
        }
    }

    private void refreshBitmap() {
        if (bitmap == null || texture == null) {
            return;
        }
        int[] argb = new int[texture.width * texture.height];
        for (int i = 0; i < argb.length; i++) {
            argb[i] = texture.palette[texture.pixels[i] & 0xFF];
        }
        bitmap.setPixels(argb, 0, texture.width, 0, 0, texture.width, texture.height);
    }

    private void fireEdited() {
        if (listener != null) {
            listener.onTextureEdited();
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (texture == null || bitmap == null) {
            return;
        }

        int viewWidth = getWidth();
        int viewHeight = getHeight();
        cellSize = Math.max(1, Math.min(viewWidth / texture.width, viewHeight / texture.height));
        int drawWidth = cellSize * texture.width;
        int drawHeight = cellSize * texture.height;
        originX = (viewWidth - drawWidth) / 2;
        originY = (viewHeight - drawHeight) / 2;
        destRect.set(originX, originY, originX + drawWidth, originY + drawHeight);

        canvas.drawBitmap(bitmap, null, destRect, bitmapPaint);

        if (cellSize >= GRID_MIN_CELL) {
            for (int x = 0; x <= texture.width; x++) {
                float lineX = originX + x * cellSize;
                canvas.drawLine(lineX, originY, lineX, originY + drawHeight, gridPaint);
            }
            for (int y = 0; y <= texture.height; y++) {
                float lineY = originY + y * cellSize;
                canvas.drawLine(originX, lineY, originX + drawWidth, lineY, gridPaint);
            }
        }

        canvas.drawRect(destRect, borderPaint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (texture == null) {
            return super.onTouchEvent(event);
        }
        int pixelX = screenToPixelX(event.getX());
        int pixelY = screenToPixelY(event.getY());

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                strokePushedUndo = false;
                lastPixelX = pixelX;
                lastPixelY = pixelY;
                applyTool(pixelX, pixelY, true);
                return true;

            case MotionEvent.ACTION_MOVE:
                if (tool == Tool.FILL || tool == Tool.PICK) {
                    return true;
                }
                drawLine(lastPixelX, lastPixelY, pixelX, pixelY);
                lastPixelX = pixelX;
                lastPixelY = pixelY;
                return true;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                lastPixelX = -1;
                lastPixelY = -1;
                strokePushedUndo = false;
                return true;

            default:
                return super.onTouchEvent(event);
        }
    }

    private int screenToPixelX(float screenX) {
        int x = (int) ((screenX - originX) / Math.max(1, cellSize));
        return Math.max(0, Math.min(texture.width - 1, x));
    }

    private int screenToPixelY(float screenY) {
        int y = (int) ((screenY - originY) / Math.max(1, cellSize));
        return Math.max(0, Math.min(texture.height - 1, y));
    }

    private void applyTool(int x, int y, boolean first) {
        if (x < 0 || y < 0 || x >= texture.width || y >= texture.height) {
            return;
        }
        switch (tool) {
            case PEN:
                editPixel(x, y, colorIndex);
                break;
            case ERASE:
                editPixel(x, y, 0);
                break;
            case FILL:
                floodFill(x, y, colorIndex);
                break;
            case PICK:
                int picked = texture.pixelIndex(x, y);
                colorIndex = picked;
                if (listener != null) {
                    listener.onColorPicked(picked);
                }
                break;
            default:
                break;
        }
    }

    private void editPixel(int x, int y, int index) {
        if (texture.pixelIndex(x, y) == (index & 0xFF)) {
            return;
        }
        if (!strokePushedUndo) {
            pushUndo();
            strokePushedUndo = true;
        }
        texture.setPixelIndex(x, y, index);
        refreshBitmap();
        invalidate();
        fireEdited();
    }

    private void drawLine(int x0, int y0, int x1, int y1) {
        if (x0 < 0 || y0 < 0) {
            return;
        }
        int dx = Math.abs(x1 - x0);
        int dy = Math.abs(y1 - y0);
        int sx = x0 < x1 ? 1 : -1;
        int sy = y0 < y1 ? 1 : -1;
        int err = dx - dy;
        while (true) {
            applyTool(x0, y0, false);
            if (x0 == x1 && y0 == y1) {
                break;
            }
            int err2 = err * 2;
            if (err2 > -dy) {
                err -= dy;
                x0 += sx;
            }
            if (err2 < dx) {
                err += dx;
                y0 += sy;
            }
        }
    }

    private void floodFill(int startX, int startY, int newIndex) {
        int target = texture.pixelIndex(startX, startY);
        if (target == (newIndex & 0xFF)) {
            return;
        }
        pushUndo();
        strokePushedUndo = true;
        int width = texture.width;
        int height = texture.height;
        ArrayDeque<int[]> stack = new ArrayDeque<>();
        stack.push(new int[]{startX, startY});
        while (!stack.isEmpty()) {
            int[] point = stack.pop();
            int x = point[0];
            int y = point[1];
            if (x < 0 || y < 0 || x >= width || y >= height) {
                continue;
            }
            if (texture.pixelIndex(x, y) != target) {
                continue;
            }
            texture.setPixelIndex(x, y, newIndex);
            stack.push(new int[]{x + 1, y});
            stack.push(new int[]{x - 1, y});
            stack.push(new int[]{x, y + 1});
            stack.push(new int[]{x, y - 1});
        }
        refreshBitmap();
        invalidate();
        fireEdited();
    }
}
