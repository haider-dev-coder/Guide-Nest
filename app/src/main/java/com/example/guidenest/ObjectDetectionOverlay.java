package com.example.guidenest;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;
import java.util.ArrayList;
import java.util.List;

public class ObjectDetectionOverlay extends View {

    private List<RectF> boundingBoxes = new ArrayList<>();
    private Paint paint;

    public ObjectDetectionOverlay(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public ObjectDetectionOverlay(Context context) {
        super(context);
        init();
    }

    private void init() {
        paint = new Paint();
        paint.setColor(Color.RED);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(5);
    }

    public void setBoundingBoxes(List<RectF> boxes) {
        this.boundingBoxes = boxes;
        if (isAttachedToWindow()) {
            invalidate();
        }
    }

    public void clearBoundingBoxes() {
        this.boundingBoxes.clear();
        if (isAttachedToWindow()) {
            invalidate();
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        for (RectF box : boundingBoxes) {
            canvas.drawRect(box, paint);
        }
    }
}