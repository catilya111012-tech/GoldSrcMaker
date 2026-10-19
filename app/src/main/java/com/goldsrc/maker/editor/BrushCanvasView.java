package com.goldsrc.maker.editor;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;

public class BrushCanvasView extends View {

    public enum Mode {
        SELECT,
        WALL,
        ROOM,
        ENTITY,
        ERASE
    }

    public interface MapChangeListener {
        void onMapChanged();
    }

    public interface EntitySelectionListener {
        void onEntitySelected(EntityData entity);
    }

    private static final int GRID_UNIT = 16;
    private static final int WALL_THICKNESS = 16;
    private static final int MIN_ROOM_SIZE = 32;
    private static final int UNDO_LIMIT = 40;
    private static final float TAP_SLOP_PX = 16f;
    private static final float HIT_RADIUS_PX = 44f;

    private static final int[] TEXTURE_PALETTE = {
            0xFF546E7A, 0xFF8D6E63, 0xFF789262, 0xFF7E57C2,
            0xFF5C6BC0, 0xFF66BB6A, 0xFFEF6C00, 0xFF8E24AA
    };

    private static final int[] ENTITY_PALETTE = {
            0xFFFFD54F, 0xFFFF7043, 0xFF26C6DA, 0xFFEC407A, 0xFF9CCC65
    };

    private final Paint gridPaint = new Paint();
    private final Paint gridBoldPaint = new Paint();
    private final Paint axisPaint = new Paint();
    private final Paint brushPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint brushStroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint entityPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint entityTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint selectionPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint previewPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final Deque<String> undoStack = new ArrayDeque<>();

    private MapData mapData = new MapData();
    private MapChangeListener changeListener;
    private EntitySelectionListener selectionListener;

    private Mode mode = Mode.WALL;
    private String texture = "NULL";
    private String entityClass = "info_player_start";
    private int floorZ = 0;
    private int wallHeight = 128;

    private float scale = 0.35f;
    private float offsetX = -256f;
    private float offsetY = -256f;

    private float startWorldX;
    private float startWorldY;
    private float currentWorldX;
    private float currentWorldY;
    private boolean drawing;
    private boolean erasing;
    private boolean pinching;
    private float pinchStartDistance;
    private float pinchStartScale;
    private float pinchFocusWorldX;
    private float pinchFocusWorldY;
    private float pinchFocusScreenX;
    private float pinchFocusScreenY;
    private float lastTouchX;
    private float lastTouchY;

    private EntityData selectedEntity;
    private EntityData draggingEntity;
    private boolean dragPushedUndo;
    private int dragStartOriginX;
    private int dragStartOriginY;
    private Brush selectedBrush;
    private Brush draggingBrush;
    private boolean brushDragPushedUndo;
    private int brushStartX1;
    private int brushStartY1;
    private int brushStartX2;
    private int brushStartY2;
    private boolean panning;
    private float downScreenX;
    private float downScreenY;
    private boolean tapCandidate;

    public BrushCanvasView(Context context) {
        super(context);
        init();
    }

    private void init() {
        gridPaint.setColor(0xFF232A31);
        gridPaint.setStrokeWidth(1f);
        gridBoldPaint.setColor(0xFF2F3B44);
        gridBoldPaint.setStrokeWidth(2f);
        axisPaint.setStrokeWidth(3f);

        brushPaint.setStyle(Paint.Style.FILL);
        brushStroke.setStyle(Paint.Style.STROKE);
        brushStroke.setStrokeWidth(3f);
        brushStroke.setColor(0xFFECEFF1);

        entityPaint.setStyle(Paint.Style.FILL);
        entityTextPaint.setColor(Color.WHITE);
        entityTextPaint.setTextSize(22f);

        selectionPaint.setStyle(Paint.Style.STROKE);
        selectionPaint.setStrokeWidth(4f);
        selectionPaint.setColor(0xFFFFC107);

        previewPaint.setStyle(Paint.Style.STROKE);
        previewPaint.setStrokeWidth(4f);
        previewPaint.setColor(0xFFFFC107);

        setBackgroundColor(0xFF15191D);
    }

    public void setMapData(MapData mapData) {
        this.mapData = mapData == null ? new MapData() : mapData;
        undoStack.clear();
        selectedEntity = null;
        draggingEntity = null;
        selectedBrush = null;
        draggingBrush = null;
        panning = false;
        invalidate();
    }

    public MapData getMapData() {
        return mapData;
    }

    public void setChangeListener(MapChangeListener listener) {
        this.changeListener = listener;
    }

    public void setEntitySelectionListener(EntitySelectionListener listener) {
        this.selectionListener = listener;
    }

    public EntityData getSelectedEntity() {
        return selectedEntity;
    }

    public void setSelectedEntity(EntityData entity) {
        selectedEntity = entity;
        invalidate();
    }

    public void deleteSelectedEntity() {
        if (selectedEntity == null || !mapData.entities.contains(selectedEntity)) {
            return;
        }
        pushUndo();
        mapData.entities.remove(selectedEntity);
        selectedEntity = null;
        draggingEntity = null;
        fireChanged();
        invalidate();
    }

    public void duplicateSelectedEntity() {
        if (selectedEntity == null || !mapData.entities.contains(selectedEntity)) {
            return;
        }
        pushUndo();
        EntityData copy = selectedEntity.copy();
        copy.setOrigin(selectedEntity.originX() + 64, selectedEntity.originY() + 64,
                selectedEntity.originZ());
        mapData.entities.add(copy);
        selectedEntity = copy;
        fireChanged();
        invalidate();
    }

    public void entityUpdated() {
        fireChanged();
        invalidate();
    }

    public boolean deleteSelection() {
        if (selectedEntity != null && mapData.entities.contains(selectedEntity)) {
            pushUndo();
            mapData.entities.remove(selectedEntity);
            selectedEntity = null;
            draggingEntity = null;
            fireChanged();
            invalidate();
            return true;
        }
        if (selectedBrush != null && mapData.brushes.contains(selectedBrush)) {
            pushUndo();
            mapData.brushes.remove(selectedBrush);
            selectedBrush = null;
            draggingBrush = null;
            fireChanged();
            invalidate();
            return true;
        }
        return false;
    }

    public void zoomBy(float factor) {
        if (getWidth() <= 0 || getHeight() <= 0) {
            return;
        }
        float centerX = getWidth() / 2f;
        float centerY = getHeight() / 2f;
        float worldX = screenToWorldX(centerX);
        float worldY = screenToWorldY(centerY);
        scale = Math.max(0.05f, Math.min(4f, scale * factor));
        offsetX = worldX - centerX / scale;
        offsetY = worldY - centerY / scale;
        invalidate();
    }

    public void fitToMap() {
        if (getWidth() <= 0 || getHeight() <= 0) {
            return;
        }
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        for (Brush brush : mapData.brushes) {
            minX = Math.min(minX, brush.x1);
            minY = Math.min(minY, brush.y1);
            maxX = Math.max(maxX, brush.x2);
            maxY = Math.max(maxY, brush.y2);
        }
        for (EntityData entity : mapData.entities) {
            minX = Math.min(minX, entity.originX() - 32);
            minY = Math.min(minY, entity.originY() - 32);
            maxX = Math.max(maxX, entity.originX() + 32);
            maxY = Math.max(maxY, entity.originY() + 32);
        }
        if (minX > maxX || minY > maxY) {
            scale = 0.35f;
            offsetX = -256f;
            offsetY = -256f;
            invalidate();
            return;
        }
        float paddingWorld = 24f;
        float scaleX = (getWidth() - paddingWorld * 2f) / Math.max(1f, maxX - minX);
        float scaleY = (getHeight() - paddingWorld * 2f) / Math.max(1f, maxY - minY);
        scale = Math.max(0.05f, Math.min(4f, Math.min(scaleX, scaleY)));
        float centerWorldX = (minX + maxX) / 2f;
        float centerWorldY = (minY + maxY) / 2f;
        offsetX = centerWorldX - getWidth() / 2f / scale;
        offsetY = centerWorldY - getHeight() / 2f / scale;
        invalidate();
    }

    public void setMode(Mode mode) {
        this.mode = mode;
        if (mode != Mode.SELECT) {
            selectedBrush = null;
        }
        invalidate();
    }

    public void setTexture(String texture) {
        if (texture != null && !texture.isEmpty()) {
            this.texture = texture;
        }
    }

    public String getTexture() {
        return texture;
    }

    public void setEntityClass(String entityClass) {
        if (entityClass != null && !entityClass.isEmpty()) {
            this.entityClass = entityClass;
        }
    }

    public void setFloorZ(int floorZ) {
        this.floorZ = floorZ;
    }

    public int getFloorZ() {
        return floorZ;
    }

    public void setWallHeight(int wallHeight) {
        this.wallHeight = Math.max(GRID_UNIT, wallHeight);
    }

    public int getWallHeight() {
        return wallHeight;
    }

    public boolean canUndo() {
        return !undoStack.isEmpty();
    }

    public void undo() {
        if (undoStack.isEmpty()) {
            return;
        }
        try {
            mapData = MapData.fromJson(new JSONObject(undoStack.pop()));
            selectedEntity = null;
            draggingEntity = null;
            selectedBrush = null;
            draggingBrush = null;
        } catch (JSONException ignored) {
        }
        fireChanged();
        invalidate();
    }

    private void pushUndo() {
        try {
            undoStack.push(mapData.toJson().toString());
            while (undoStack.size() > UNDO_LIMIT) {
                undoStack.removeLast();
            }
        } catch (JSONException ignored) {
        }
    }

    private void fireChanged() {
        if (changeListener != null) {
            changeListener.onMapChanged();
        }
    }

    private static int snap(int value) {
        return Math.round(value / (float) GRID_UNIT) * GRID_UNIT;
    }

    private float screenToWorldX(float screenX) {
        return screenX / scale + offsetX;
    }

    private float screenToWorldY(float screenY) {
        return screenY / scale + offsetY;
    }

    private float worldToScreenX(float worldX) {
        return (worldX - offsetX) * scale;
    }

    private float worldToScreenY(float worldY) {
        return (worldY - offsetY) * scale;
    }

    private static int colorFromPalette(int[] palette, String seed) {
        int hash = seed == null ? 0 : seed.hashCode();
        return palette[Math.abs(hash) % palette.length];
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        float worldWidth = getWidth() / scale;
        float worldHeight = getHeight() / scale;

        int step = 64;
        while (step * scale < 24f) {
            step *= 2;
        }

        float firstGridX = (float) Math.floor(offsetX / step) * step;
        for (float worldX = firstGridX; worldX <= offsetX + worldWidth; worldX += step) {
            float screenX = worldToScreenX(worldX);
            canvas.drawLine(screenX, 0, screenX, getHeight(), gridPaint);
        }
        float firstGridY = (float) Math.floor(offsetY / step) * step;
        for (float worldY = firstGridY; worldY <= offsetY + worldHeight; worldY += step) {
            float screenY = worldToScreenY(worldY);
            canvas.drawLine(0, screenY, getWidth(), screenY, gridPaint);
        }

        float boldStep = step * 4f;
        float firstBoldX = (float) Math.floor(offsetX / boldStep) * boldStep;
        for (float worldX = firstBoldX; worldX <= offsetX + worldWidth; worldX += boldStep) {
            float screenX = worldToScreenX(worldX);
            canvas.drawLine(screenX, 0, screenX, getHeight(), gridBoldPaint);
        }
        float firstBoldY = (float) Math.floor(offsetY / boldStep) * boldStep;
        for (float worldY = firstBoldY; worldY <= offsetY + worldHeight; worldY += boldStep) {
            float screenY = worldToScreenY(worldY);
            canvas.drawLine(0, screenY, getWidth(), screenY, gridBoldPaint);
        }

        axisPaint.setColor(0xFF7F2A2A);
        float zeroY = worldToScreenY(0);
        canvas.drawLine(0, zeroY, getWidth(), zeroY, axisPaint);
        axisPaint.setColor(0xFF2A7F39);
        float zeroX = worldToScreenX(0);
        canvas.drawLine(zeroX, 0, zeroX, getHeight(), axisPaint);

        for (Brush brush : mapData.brushes) {
            float left = worldToScreenX(brush.x1);
            float top = worldToScreenY(brush.y1);
            float right = worldToScreenX(brush.x2);
            float bottom = worldToScreenY(brush.y2);
            rect.set(left, top, right, bottom);

            brushPaint.setColor(colorFromPalette(TEXTURE_PALETTE, brush.texture));
            brushPaint.setAlpha(150);
            canvas.drawRect(rect, brushPaint);
            canvas.drawRect(rect, brushStroke);

            if (rect.width() > 90f && rect.height() > 40f) {
                entityTextPaint.setColor(0xFFB0BEC5);
                canvas.drawText(brush.texture, left + 8f, top + 26f, entityTextPaint);
            }
        }

        for (EntityData entity : mapData.entities) {
            float screenX = worldToScreenX(entity.originX());
            float screenY = worldToScreenY(entity.originY());
            entityPaint.setColor(colorFromPalette(ENTITY_PALETTE, entity.classname));
            canvas.drawCircle(screenX, screenY, 12f, entityPaint);
            entityTextPaint.setColor(Color.WHITE);
            canvas.drawText(entity.classname, screenX + 18f, screenY + 8f, entityTextPaint);
        }

        if (selectedEntity != null && mapData.entities.contains(selectedEntity)) {
            float screenX = worldToScreenX(selectedEntity.originX());
            float screenY = worldToScreenY(selectedEntity.originY());
            canvas.drawCircle(screenX, screenY, 20f, selectionPaint);
        }

        if (selectedBrush != null && mapData.brushes.contains(selectedBrush)) {
            rect.set(worldToScreenX(selectedBrush.x1), worldToScreenY(selectedBrush.y1),
                    worldToScreenX(selectedBrush.x2), worldToScreenY(selectedBrush.y2));
            canvas.drawRect(rect, selectionPaint);
        }

        if (drawing) {
            float left = worldToScreenX(Math.min(startWorldX, currentWorldX));
            float top = worldToScreenY(Math.min(startWorldY, currentWorldY));
            float right = worldToScreenX(Math.max(startWorldX, currentWorldX));
            float bottom = worldToScreenY(Math.max(startWorldY, currentWorldY));
            rect.set(left, top, right, bottom);
            canvas.drawRect(rect, previewPaint);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downScreenX = event.getX();
                downScreenY = event.getY();
                tapCandidate = true;
                if (mode == Mode.ERASE) {
                    erasing = true;
                    eraseAt(screenToWorldX(event.getX()), screenToWorldY(event.getY()));
                } else {
                    EntityData hit = hitEntity(event.getX(), event.getY());
                    if (hit != null) {
                        selectedEntity = hit;
                        selectedBrush = null;
                        draggingEntity = hit;
                        dragPushedUndo = false;
                        dragStartOriginX = hit.originX();
                        dragStartOriginY = hit.originY();
                        invalidate();
                    } else if (mode == Mode.SELECT) {
                        Brush brushHit = hitBrush(event.getX(), event.getY());
                        selectedEntity = null;
                        selectedBrush = brushHit;
                        if (brushHit != null) {
                            draggingBrush = brushHit;
                            brushDragPushedUndo = false;
                            brushStartX1 = brushHit.x1;
                            brushStartY1 = brushHit.y1;
                            brushStartX2 = brushHit.x2;
                            brushStartY2 = brushHit.y2;
                        } else {
                            panning = true;
                        }
                        invalidate();
                    } else {
                        drawing = true;
                        startWorldX = snap((int) screenToWorldX(event.getX()));
                        startWorldY = snap((int) screenToWorldY(event.getY()));
                        currentWorldX = startWorldX;
                        currentWorldY = startWorldY;
                        invalidate();
                    }
                }
                lastTouchX = event.getX();
                lastTouchY = event.getY();
                return true;

            case MotionEvent.ACTION_POINTER_DOWN:
                if (event.getPointerCount() >= 2) {
                    drawing = false;
                    erasing = false;
                    draggingEntity = null;
                    dragPushedUndo = false;
                    draggingBrush = null;
                    brushDragPushedUndo = false;
                    panning = false;
                    pinching = true;
                    pinchStartDistance = distance(event);
                    pinchStartScale = scale;
                    pinchFocusScreenX = (event.getX(0) + event.getX(1)) / 2f;
                    pinchFocusScreenY = (event.getY(0) + event.getY(1)) / 2f;
                    pinchFocusWorldX = screenToWorldX(pinchFocusScreenX);
                    pinchFocusWorldY = screenToWorldY(pinchFocusScreenY);
                }
                return true;

            case MotionEvent.ACTION_MOVE:
                if (pinching && event.getPointerCount() >= 2) {
                    float currentDistance = distance(event);
                    if (pinchStartDistance > 1f) {
                        float focusX = (event.getX(0) + event.getX(1)) / 2f;
                        float focusY = (event.getY(0) + event.getY(1)) / 2f;
                        float newScale = pinchStartScale * (currentDistance / pinchStartDistance);
                        scale = Math.max(0.05f, Math.min(4f, newScale));
                        offsetX = pinchFocusWorldX - focusX / scale;
                        offsetY = pinchFocusWorldY - focusY / scale;
                        invalidate();
                    }
                } else if (draggingEntity != null) {
                    float dx = event.getX() - downScreenX;
                    float dy = event.getY() - downScreenY;
                    if (dx * dx + dy * dy > TAP_SLOP_PX * TAP_SLOP_PX) {
                        tapCandidate = false;
                    }
                    if (!tapCandidate) {
                        if (!dragPushedUndo) {
                            pushUndo();
                            dragPushedUndo = true;
                        }
                        int newX = snap((int) (dragStartOriginX + dx / scale));
                        int newY = snap((int) (dragStartOriginY + dy / scale));
                        draggingEntity.setOrigin(newX, newY, draggingEntity.originZ());
                        invalidate();
                    }
                } else if (draggingBrush != null) {
                    float dx = event.getX() - downScreenX;
                    float dy = event.getY() - downScreenY;
                    if (dx * dx + dy * dy > TAP_SLOP_PX * TAP_SLOP_PX) {
                        tapCandidate = false;
                    }
                    if (!tapCandidate) {
                        if (!brushDragPushedUndo) {
                            pushUndo();
                            brushDragPushedUndo = true;
                        }
                        int worldDx = snap(Math.round(dx / scale));
                        int worldDy = snap(Math.round(dy / scale));
                        draggingBrush.x1 = brushStartX1 + worldDx;
                        draggingBrush.x2 = brushStartX2 + worldDx;
                        draggingBrush.y1 = brushStartY1 + worldDy;
                        draggingBrush.y2 = brushStartY2 + worldDy;
                        invalidate();
                    }
                } else if (panning) {
                    float panDx = event.getX() - lastTouchX;
                    float panDy = event.getY() - lastTouchY;
                    if (panDx * panDx + panDy * panDy > TAP_SLOP_PX * TAP_SLOP_PX) {
                        tapCandidate = false;
                    }
                    offsetX -= panDx / scale;
                    offsetY -= panDy / scale;
                    invalidate();
                } else if (erasing) {
                    eraseAt(screenToWorldX(event.getX()), screenToWorldY(event.getY()));
                } else if (drawing) {
                    currentWorldX = snap((int) screenToWorldX(event.getX()));
                    currentWorldY = snap((int) screenToWorldY(event.getY()));
                    invalidate();
                }
                lastTouchX = event.getX();
                lastTouchY = event.getY();
                return true;

            case MotionEvent.ACTION_UP:
                float upDx = event.getX() - downScreenX;
                float upDy = event.getY() - downScreenY;
                boolean tap = tapCandidate && upDx * upDx + upDy * upDy <= TAP_SLOP_PX * TAP_SLOP_PX;

                if (draggingEntity != null) {
                    if (dragPushedUndo) {
                        fireChanged();
                    } else if (tap) {
                        fireEntitySelected(draggingEntity);
                    }
                    draggingEntity = null;
                    dragPushedUndo = false;
                } else if (draggingBrush != null) {
                    if (brushDragPushedUndo) {
                        fireChanged();
                    }
                    draggingBrush = null;
                    brushDragPushedUndo = false;
                } else if (drawing) {
                    if (tap) {
                        if (mode == Mode.ENTITY) {
                            createEntityAt((int) startWorldX, (int) startWorldY);
                        } else {
                            EntityData hit = hitEntity(event.getX(), event.getY());
                            if (hit != null) {
                                selectedEntity = hit;
                                fireEntitySelected(hit);
                            } else {
                                selectedEntity = null;
                            }
                            invalidate();
                        }
                    } else {
                        finishDrawing();
                    }
                }

                drawing = false;
                erasing = false;
                pinching = false;
                panning = false;
                invalidate();
                return true;

            case MotionEvent.ACTION_CANCEL:
                drawing = false;
                erasing = false;
                pinching = false;
                panning = false;
                draggingEntity = null;
                dragPushedUndo = false;
                draggingBrush = null;
                brushDragPushedUndo = false;
                invalidate();
                return true;

            default:
                return super.onTouchEvent(event);
        }
    }

    private EntityData hitEntity(float screenX, float screenY) {
        for (int i = mapData.entities.size() - 1; i >= 0; i--) {
            EntityData entity = mapData.entities.get(i);
            float entityX = worldToScreenX(entity.originX());
            float entityY = worldToScreenY(entity.originY());
            float dx = entityX - screenX;
            float dy = entityY - screenY;
            if (dx * dx + dy * dy <= HIT_RADIUS_PX * HIT_RADIUS_PX) {
                return entity;
            }
        }
        return null;
    }

    private Brush hitBrush(float screenX, float screenY) {
        for (int i = mapData.brushes.size() - 1; i >= 0; i--) {
            Brush brush = mapData.brushes.get(i);
            float left = worldToScreenX(brush.x1);
            float right = worldToScreenX(brush.x2);
            float top = worldToScreenY(brush.y1);
            float bottom = worldToScreenY(brush.y2);
            if (screenX >= left && screenX <= right && screenY >= top && screenY <= bottom) {
                return brush;
            }
        }
        return null;
    }

    private void fireEntitySelected(EntityData entity) {
        if (selectionListener != null) {
            selectionListener.onEntitySelected(entity);
        }
    }

    private void createEntityAt(int x, int y) {
        pushUndo();
        EntityData entity = new EntityData(entityClass);
        for (Map.Entry<String, String> entry : EntityCatalog.defaults(entityClass).entrySet()) {
            entity.setKey(entry.getKey(), entry.getValue());
        }
        entity.setOrigin(x, y, floorZ + 24);
        mapData.entities.add(entity);
        selectedEntity = entity;
        fireEntitySelected(entity);
        fireChanged();
    }

    private float distance(MotionEvent event) {
        float dx = event.getX(0) - event.getX(1);
        float dy = event.getY(0) - event.getY(1);
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    private void finishDrawing() {
        int x1 = snap((int) Math.min(startWorldX, currentWorldX));
        int y1 = snap((int) Math.min(startWorldY, currentWorldY));
        int x2 = snap((int) Math.max(startWorldX, currentWorldX));
        int y2 = snap((int) Math.max(startWorldY, currentWorldY));

        if (mode == Mode.ENTITY) {
            createEntityAt(x1, y1);
            return;
        }

        if (x1 == x2 && y1 == y2) {
            return;
        }

        if (mode == Mode.WALL) {
            pushUndo();
            addWall(x1, y1, x2, y2);
            fireChanged();
            return;
        }

        if (mode == Mode.ROOM) {
            if (x2 - x1 < MIN_ROOM_SIZE) {
                x2 = x1 + MIN_ROOM_SIZE;
            }
            if (y2 - y1 < MIN_ROOM_SIZE) {
                y2 = y1 + MIN_ROOM_SIZE;
            }
            pushUndo();
            addRoom(x1, y1, x2, y2);
            fireChanged();
        }
    }

    private void addWall(int x1, int y1, int x2, int y2) {
        if (Math.abs(x2 - x1) >= Math.abs(y2 - y1)) {
            int centerY = snap((y1 + y2) / 2);
            mapData.brushes.add(new Brush(
                    x1, centerY - WALL_THICKNESS / 2, floorZ,
                    x2, centerY + WALL_THICKNESS / 2, floorZ + wallHeight,
                    texture));
        } else {
            int centerX = snap((x1 + x2) / 2);
            mapData.brushes.add(new Brush(
                    centerX - WALL_THICKNESS / 2, y1, floorZ,
                    centerX + WALL_THICKNESS / 2, y2, floorZ + wallHeight,
                    texture));
        }
    }

    private void addRoom(int x1, int y1, int x2, int y2) {
        int top = floorZ + wallHeight;
        mapData.brushes.add(new Brush(x1, y1, floorZ - WALL_THICKNESS, x2, y2, floorZ, texture));
        mapData.brushes.add(new Brush(x1, y1, floorZ, x2, y1 + WALL_THICKNESS, top, texture));
        mapData.brushes.add(new Brush(x1, y2 - WALL_THICKNESS, floorZ, x2, y2, top, texture));
        mapData.brushes.add(new Brush(x1, y1 + WALL_THICKNESS, floorZ, x1 + WALL_THICKNESS, y2 - WALL_THICKNESS, top, texture));
        mapData.brushes.add(new Brush(x2 - WALL_THICKNESS, y1 + WALL_THICKNESS, floorZ, x2, y2 - WALL_THICKNESS, top, texture));
    }

    private void eraseAt(float worldX, float worldY) {
        int wx = (int) worldX;
        int wy = (int) worldY;

        for (int i = mapData.entities.size() - 1; i >= 0; i--) {
            EntityData entity = mapData.entities.get(i);
            int dx = entity.originX() - wx;
            int dy = entity.originY() - wy;
            if (dx * dx + dy * dy <= 32 * 32) {
                pushUndo();
                if (entity == selectedEntity) {
                    selectedEntity = null;
                }
                if (entity == draggingEntity) {
                    draggingEntity = null;
                }
                mapData.entities.remove(i);
                fireChanged();
                invalidate();
                return;
            }
        }

        for (int i = mapData.brushes.size() - 1; i >= 0; i--) {
            if (mapData.brushes.get(i).containsPointTopView(wx, wy)) {
                pushUndo();
                mapData.brushes.remove(i);
                fireChanged();
                invalidate();
                return;
            }
        }
    }
}
