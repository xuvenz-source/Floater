package com.custom.rapidtap;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
import android.os.Handler;
import android.os.Looper;
import android.view.accessibility.AccessibilityEvent;

import java.util.Random;

public class RapidTapAccessibilityService extends AccessibilityService {
    private static RapidTapAccessibilityService instance;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Random random = new Random();
    private boolean tapping;
    private volatile boolean gestureInFlight;
    private int tapX;
    private int tapY;
    private int intervalMs = 75;
    private int timingVariationMs;
    private int pauseChancePercent;
    private int pauseMinMs = 150;
    private int pauseMaxMs = 450;

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

    public void startRapidTapping(
            int x,
            int y,
            int interval,
            int variation,
            int pauseChance,
            int pauseMin,
            int pauseMax) {
        tapX = x;
        tapY = y;
        intervalMs = Math.max(30, Math.min(1000, interval));
        timingVariationMs = Math.max(0, Math.min(500, variation));
        pauseChancePercent = Math.max(0, Math.min(50, pauseChance));
        pauseMinMs = Math.max(0, Math.min(5000, pauseMin));
        pauseMaxMs = Math.max(pauseMinMs, Math.min(5000, pauseMax));
        if (tapping) return;
        tapping = true;
        handler.post(this::performNextTap);
    }

    public void stopRapidTapping() {
        tapping = false;
        gestureInFlight = false;
        handler.removeCallbacksAndMessages(null);
    }

    private int nextDelayMs() {
        int delay = intervalMs;

        if (timingVariationMs > 0) {
            int spread = timingVariationMs * 2 + 1;
            delay += random.nextInt(spread) - timingVariationMs;
        }

        delay = Math.max(30, delay);

        if (pauseChancePercent > 0 && random.nextInt(100) < pauseChancePercent) {
            int range = pauseMaxMs - pauseMinMs;
            int extraPause = pauseMinMs + (range > 0 ? random.nextInt(range + 1) : 0);
            delay += extraPause;
        }

        return delay;
    }

    private void scheduleNextTap() {
        if (tapping) {
            handler.postDelayed(this::performNextTap, nextDelayMs());
        }
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
                scheduleNextTap();
            }

            @Override
            public void onCancelled(GestureDescription gestureDescription) {
                gestureInFlight = false;
                scheduleNextTap();
            }
        }, null);

        if (!accepted) {
            gestureInFlight = false;
            scheduleNextTap();
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
        if (instance == this) instance = null;
        super.onDestroy();
    }
}
