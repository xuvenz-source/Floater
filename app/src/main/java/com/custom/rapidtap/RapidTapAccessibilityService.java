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
    private volatile boolean gestureInFlight;
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

    public boolean isDispatchingGesture() {
        return gestureInFlight;
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

        // The floating overlay watches touches outside itself so it can pause when
        // the user interacts with the game. Mark our own generated gesture so the
        // overlay can distinguish it from a real user touch.
        gestureInFlight = true;

        boolean accepted = dispatchGesture(gesture, new GestureResultCallback() {
            @Override
            public void onCompleted(GestureDescription gestureDescription) {
                gestureInFlight = false;
                if (tapping) {
                    handler.postDelayed(RapidTapAccessibilityService.this::performNextTap, intervalMs);
                }
            }

            @Override
            public void onCancelled(GestureDescription gestureDescription) {
                gestureInFlight = false;
                if (tapping) {
                    handler.postDelayed(RapidTapAccessibilityService.this::performNextTap, intervalMs);
                }
            }
        }, null);

        if (!accepted) {
            gestureInFlight = false;
            if (tapping) {
                handler.postDelayed(this::performNextTap, intervalMs);
            }
        }
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // No screen content is inspected. The service is used only for gesture dispatch.
    }

    @Override
    public void onInterrupt() {
        stopRapidTapping();
    }

    @Override
    public void onDestroy() {
        stopRapidTapping();
        gestureInFlight = false;
        if (instance == this) instance = null;
        super.onDestroy();
    }
}
