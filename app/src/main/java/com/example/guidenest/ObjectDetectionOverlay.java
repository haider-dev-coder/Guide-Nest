package com.example.guidenest;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import java.util.ArrayList;
import java.util.List;

public class ObjectDetectionOverlay extends View {

    private final Paint paint = new Paint();
    private List<RectF> boundingBoxes = new ArrayList<>();

    public ObjectDetectionOverlay(Context context) {
        super(context);
        init();
    }

    public ObjectDetectionOverlay(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        paint.setColor(ContextCompat.getColor(getContext(), R.color.overlay_stroke));
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(6f);
        paint.setAntiAlias(true);
    }

    public void setBoundingBoxes(List<RectF> boxes) {
        boundingBoxes = boxes == null ? new ArrayList<>() : new ArrayList<>(boxes);
        postInvalidateOnAnimation();
    }

    public void clearBoundingBoxes() {
        boundingBoxes.clear();
        postInvalidateOnAnimation();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        for (RectF box : boundingBoxes) {
            canvas.drawRoundRect(box, 16f, 16f, paint);
        }
    }
}
