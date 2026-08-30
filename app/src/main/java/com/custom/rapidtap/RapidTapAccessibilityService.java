package com.custom.rapidtap;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
import android.os.Handler;
import android.os.Looper;
import android.view.accessibility.AccessibilityEvent;

public class RapidTapAccessibilityService extends AccessibilityService {
    private static RapidTapAccessibilityService instance;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean tapping;
    private int tapX;
    private int tapY;
    private int intervalMs = 75;

    public static RapidTapAccessibilityService getInstance() {
        return instance;
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
    }

    public boolean isTapping() {
        return tapping;
    }

    public void startRapidTapping(int x, int y, int interval) {
        tapX = x;
        tapY = y;
        intervalMs = Math.max(30, Math.min(1000, interval));
        if (tapping) return;
        tapping = true;
        handler.post(this::performNextTap);
    }

    public void stopRapidTapping() {
        tapping = false;
        handler.removeCallbacksAndMessages(null);
    }

    private void performNextTap() {
        if (!tapping) return;

        Path path = new Path();
        path.moveTo(tapX, tapY);
        GestureDescription.StrokeDescription stroke =
                new GestureDescription.StrokeDescription(path, 0, 1);
        GestureDescription gesture =
                new GestureDescription.Builder().addStroke(stroke).build();

        boolean accepted = dispatchGesture(gesture, new GestureResultCallback() {
            @Override
            public void onCompleted(GestureDescription gestureDescription) {
                if (tapping) {
                    handler.postDelayed(RapidTapAccessibilityService.this::performNextTap, intervalMs);
                }
            }

            @Override
            public void onCancelled(GestureDescription gestureDescription) {
                if (tapping) {
                    handler.postDelayed(RapidTapAccessibilityService.this::performNextTap, intervalMs);
                }
            }
        }, null);

        if (!accepted && tapping) {
            handler.postDelayed(this::performNextTap, intervalMs);
        }
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // No events are inspected. The service is used only for gesture dispatch.
    }

    @Override
    public void onInterrupt() {
        stopRapidTapping();
    }

    @Override
    public void onDestroy() {
        stopRapidTapping();
        if (instance == this) instance = null;
        super.onDestroy();
    }
}
