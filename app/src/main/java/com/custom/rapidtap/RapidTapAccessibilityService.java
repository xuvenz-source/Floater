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
    private final Runnable tapRunnable = this::performNextTap;
    private final GestureResultCallback tapCallback = new GestureResultCallback() {
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
    };

    private boolean tapping;
    private boolean paused;
    private volatile boolean gestureInFlight;
    private GestureDescription tapGesture;
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

    public boolean isPaused() {
        return paused;
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
        if (tapping) return;

        intervalMs = Math.max(30, Math.min(1000, interval));
        timingVariationMs = Math.max(0, Math.min(500, variation));
        pauseChancePercent = Math.max(0, Math.min(50, pauseChance));
        pauseMinMs = Math.max(0, Math.min(5000, pauseMin));
        pauseMaxMs = Math.max(pauseMinMs, Math.min(5000, pauseMax));

        Path path = new Path();
        path.moveTo(x, y);
        GestureDescription.StrokeDescription stroke =
                new GestureDescription.StrokeDescription(path, 0, 1);
        tapGesture = new GestureDescription.Builder().addStroke(stroke).build();

        paused = false;
        tapping = true;
        handler.removeCallbacks(tapRunnable);
        handler.post(tapRunnable);
    }

    public void pauseRapidTapping() {
        if (!tapping || paused) return;
        paused = true;
        handler.removeCallbacks(tapRunnable);
    }

    public void resumeRapidTapping() {
        if (!tapping || !paused || tapGesture == null) return;
        paused = false;
        handler.removeCallbacks(tapRunnable);
        handler.post(tapRunnable);
    }

    public void stopRapidTapping() {
        tapping = false;
        paused = false;
        gestureInFlight = false;
        handler.removeCallbacks(tapRunnable);
        tapGesture = null;
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
        if (tapping && !paused && tapGesture != null) {
            handler.postDelayed(tapRunnable, nextDelayMs());
        }
    }

    private void performNextTap() {
        GestureDescription gesture = tapGesture;
        if (!tapping || paused || gesture == null) return;

        gestureInFlight = true;

        boolean accepted = dispatchGesture(gesture, tapCallback, null);
        if (!accepted) {
            gestureInFlight = false;
            scheduleNextTap();
        }
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // Deliberately ignored. Android 10 / ColorOS touch-interaction events are
        // not reliable enough to distinguish real user touches from injected
        // gestures without intrusive touch-exploration behaviour.
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
