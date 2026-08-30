package com.custom.rapidtap;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.View;

public class CrosshairView extends View {
    private final Paint shadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public CrosshairView(Context context) {
        super(context);

        shadowPaint.setColor(Color.argb(170, 0, 0, 0));
        shadowPaint.setStyle(Paint.Style.STROKE);
        shadowPaint.setStrokeCap(Paint.Cap.ROUND);
        shadowPaint.setStrokeWidth(dp(5));

        linePaint.setColor(Color.argb(235, 255, 255, 255));
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeCap(Paint.Cap.ROUND);
        linePaint.setStrokeWidth(dp(2));

        setBackgroundColor(Color.TRANSPARENT);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;
        float arm = Math.max(dp(18), Math.min(getWidth(), getHeight()) / 2f - dp(8));
        float gap = dp(8);
        float ring = dp(7);

        drawCrosshair(canvas, cx, cy, arm, gap, ring, shadowPaint);
        drawCrosshair(canvas, cx, cy, arm, gap, ring, linePaint);
    }

    private void drawCrosshair(
            Canvas canvas,
            float cx,
            float cy,
            float arm,
            float gap,
            float ring,
            Paint paint) {
        canvas.drawLine(cx - arm, cy, cx - gap, cy, paint);
        canvas.drawLine(cx + gap, cy, cx + arm, cy, paint);
        canvas.drawLine(cx, cy - arm, cx, cy - gap, paint);
        canvas.drawLine(cx, cy + gap, cx, cy + arm, paint);
        canvas.drawCircle(cx, cy, ring, paint);
    }

    private float dp(int value) {
        return value * getResources().getDisplayMetrics().density;
    }
}
