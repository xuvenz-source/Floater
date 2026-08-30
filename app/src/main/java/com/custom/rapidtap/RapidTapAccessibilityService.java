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
    private static final long SMART_PAUSE_FALLBACK_MS = 300L;
    private static final long SMART_PAUSE_END_DELAY_MS = 20L;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Random random = new Random();
    private final Runnable tapRunnable = this::performNextTap;
    private final Runnable smartPauseFallbackRunnable = this::resumeAfterUserTouch;
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
    private boolean pausedForUserTouch;
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

    public boolean isDispatchingGesture() {
        return gestureInFlight;
    }

    public boolean isPausedForUserTouch() {
        return pausedForUserTouch;
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

        pausedForUserTouch = false;
        tapping = true;
        handler.removeCallbacks(smartPauseFallbackRunnable);
        handler.post(tapRunnable);
    }

    public void pauseForUserTouch() {
        if (!tapping || pausedForUserTouch) return;

        pausedForUserTouch = true;
        handler.removeCallbacks(tapRunnable);

        // Some Android 10/OEM combinations do not reliably deliver
        // TYPE_TOUCH_INTERACTION_END to this kind of service. Keep that event as
        // the preferred fast resume signal, but also arm a short watchdog so the
        // clicker can never become stuck in the paused state indefinitely.
        handler.removeCallbacks(smartPauseFallbackRunnable);
        handler.postDelayed(smartPauseFallbackRunnable, SMART_PAUSE_FALLBACK_MS);
    }

    private void resumeAfterUserTouch() {
        if (!tapping || !pausedForUserTouch || tapGesture == null) return;

        pausedForUserTouch = false;
        handler.removeCallbacks(smartPauseFallbackRunnable);
        handler.removeCallbacks(tapRunnable);
        handler.postDelayed(tapRunnable, SMART_PAUSE_END_DELAY_MS);
    }

    public void stopRapidTapping() {
        tapping = false;
        pausedForUserTouch = false;
        gestureInFlight = false;
        handler.removeCallbacks(tapRunnable);
        handler.removeCallbacks(smartPauseFallbackRunnable);
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
        if (tapping && !pausedForUserTouch && tapGesture != null) {
            handler.postDelayed(tapRunnable, nextDelayMs());
        }
    }

    private void performNextTap() {
        GestureDescription gesture = tapGesture;
        if (!tapping || pausedForUserTouch || gesture == null) return;

        gestureInFlight = true;

        boolean accepted = dispatchGesture(gesture, tapCallback, null);
        if (!accepted) {
            gestureInFlight = false;
            scheduleNextTap();
        }
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event.getEventType() == AccessibilityEvent.TYPE_TOUCH_INTERACTION_END &&
                pausedForUserTouch && !gestureInFlight) {
            handler.removeCallbacks(smartPauseFallbackRunnable);
            handler.removeCallbacks(tapRunnable);
            handler.postDelayed(this::resumeAfterUserTouch, SMART_PAUSE_END_DELAY_MS);
        }
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
