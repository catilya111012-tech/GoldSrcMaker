package com.goldsrc.maker.scripting;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;

import java.util.Map;

public class BlockGraphView extends View {

    public interface Listener {
        void onNodeTapped(BlockNode node);

        void onNodeLongPressed(BlockNode node);

        void onNodeMoved(BlockNode node);
    }

    private static final float NODE_WIDTH = 460f;
    private static final float NODE_HEIGHT = 160f;
    private static final float CORNER = 20f;

    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paramPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();

    private BlockGraph graph;
    private Listener listener;

    private BlockNode pressedNode;
    private boolean dragging;
    private boolean moved;
    private float dragOffsetX;
    private float dragOffsetY;
    private boolean panning;
    private float scrollX;
    private float scrollY;
    private float lastTouchX;
    private float lastTouchY;
    private float downX;
    private float downY;

    private final Runnable longPressRunnable = new Runnable() {
        @Override
        public void run() {
            if (pressedNode != null && !moved) {
                if (listener != null) {
                    listener.onNodeLongPressed(pressedNode);
                }
                dragging = false;
                pressedNode = null;
            }
        }
    };

    public BlockGraphView(Context context) {
        super(context);
        init();
    }

    private void init() {
        fillPaint.setStyle(Paint.Style.FILL);
        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setStrokeWidth(4f);
        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(32f);
        textPaint.setFakeBoldText(true);
        paramPaint.setColor(0xFFCFD8DC);
        paramPaint.setTextSize(26f);
        setBackgroundColor(0xFF101418);
    }

    public void setGraph(BlockGraph graph) {
        this.graph = graph;
        invalidate();
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    private float worldX(float screenX) {
        return screenX + scrollX;
    }

    private float worldY(float screenY) {
        return screenY + scrollY;
    }

    private BlockNode hitTest(float worldX, float worldY) {
        if (graph == null) {
            return null;
        }
        for (int i = graph.nodes.size() - 1; i >= 0; i--) {
            BlockNode node = graph.nodes.get(i);
            if (worldX >= node.x && worldX <= node.x + NODE_WIDTH
                    && worldY >= node.y && worldY <= node.y + NODE_HEIGHT) {
                return node;
            }
        }
        return null;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (graph == null || graph.nodes.isEmpty()) {
            textPaint.setColor(0xFF607D8B);
            canvas.drawText("Добавьте блок через панель инструментов", 40, 80, textPaint);
            return;
        }

        strokePaint.setColor(0xFF90A4AE);
        for (BlockNode node : graph.nodes) {
            if (node.nextId < 0) {
                continue;
            }
            BlockNode target = graph.findById(node.nextId);
            if (target == null) {
                continue;
            }
            float startX = node.x - scrollX + NODE_WIDTH / 2f;
            float startY = node.y - scrollY + NODE_HEIGHT;
            float endX = target.x - scrollX + NODE_WIDTH / 2f;
            float endY = target.y - scrollY;
            canvas.drawLine(startX, startY, endX, endY, strokePaint);
            canvas.drawLine(endX, endY, endX - 16f, endY - 24f, strokePaint);
            canvas.drawLine(endX, endY, endX + 16f, endY - 24f, strokePaint);
        }

        for (BlockNode node : graph.nodes) {
            float left = node.x - scrollX;
            float top = node.y - scrollY;
            rect.set(left, top, left + NODE_WIDTH, top + NODE_HEIGHT);

            fillPaint.setColor(node.type.color());
            canvas.drawRoundRect(rect, CORNER, CORNER, fillPaint);

            strokePaint.setColor(node.userEdited ? 0xFFFFB300 : 0xFFECEFF1);
            strokePaint.setStrokeWidth(node.userEdited ? 6f : 3f);
            canvas.drawRoundRect(rect, CORNER, CORNER, strokePaint);

            textPaint.setColor(Color.WHITE);
            String title = node.displayTitle();
            float maxTitleWidth = NODE_WIDTH - 48f;
            float titleWidth = textPaint.measureText(title);
            if (titleWidth > maxTitleWidth) {
                textPaint.setTextScaleX(maxTitleWidth / titleWidth);
            }
            canvas.drawText(title, left + 24f, top + 52f, textPaint);
            textPaint.setTextScaleX(1f);

            float paramY = top + 90f;
            int shown = 0;
            for (Map.Entry<String, String> entry : node.params.entrySet()) {
                if (shown >= 2) {
                    break;
                }
                canvas.drawText(entry.getKey() + " = " + entry.getValue(),
                        left + 24f, paramY, paramPaint);
                paramY += 34f;
                shown++;
            }
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        float screenX = event.getX();
        float screenY = event.getY();

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = screenX;
                downY = screenY;
                moved = false;
                pressedNode = hitTest(worldX(screenX), worldY(screenY));
                if (pressedNode != null) {
                    dragging = true;
                    dragOffsetX = worldX(screenX) - pressedNode.x;
                    dragOffsetY = worldY(screenY) - pressedNode.y;
                    postDelayed(longPressRunnable, 550);
                } else {
                    panning = true;
                }
                lastTouchX = screenX;
                lastTouchY = screenY;
                return true;

            case MotionEvent.ACTION_MOVE:
                if (Math.abs(screenX - downX) > 12f || Math.abs(screenY - downY) > 12f) {
                    moved = true;
                    removeCallbacks(longPressRunnable);
                }
                if (dragging && pressedNode != null) {
                    pressedNode.x = worldX(screenX) - dragOffsetX;
                    pressedNode.y = worldY(screenY) - dragOffsetY;
                    invalidate();
                } else if (panning) {
                    scrollX -= screenX - lastTouchX;
                    scrollY -= screenY - lastTouchY;
                    invalidate();
                }
                lastTouchX = screenX;
                lastTouchY = screenY;
                return true;

            case MotionEvent.ACTION_UP:
                removeCallbacks(longPressRunnable);
                if (dragging && pressedNode != null) {
                    if (moved) {
                        if (listener != null) {
                            listener.onNodeMoved(pressedNode);
                        }
                    } else {
                        if (listener != null) {
                            listener.onNodeTapped(pressedNode);
                        }
                    }
                }
                dragging = false;
                panning = false;
                pressedNode = null;
                invalidate();
                return true;

            case MotionEvent.ACTION_CANCEL:
                removeCallbacks(longPressRunnable);
                dragging = false;
                panning = false;
                pressedNode = null;
                return true;

            default:
                return super.onTouchEvent(event);
        }
    }
}
