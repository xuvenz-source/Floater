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
    private static final long SMART_PAUSE_NO_EVENT_FALLBACK_MS = 2000L;
    private static final long SMART_PAUSE_STALE_TOUCH_FALLBACK_MS = 15000L;
    private static final long SMART_PAUSE_END_DELAY_MS = 20L;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Random random = new Random();
    private final Runnable tapRunnable = this::performNextTap;
    private final Runnable smartPauseFallbackRunnable = this::resumeIfTouchLifecycleMissing;
    private final Runnable staleTouchFallbackRunnable = this::resumeIfTouchEndMissing;
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
    private boolean userTouchInteractionActive;
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
        userTouchInteractionActive = false;
        tapping = true;
        handler.removeCallbacks(smartPauseFallbackRunnable);
        handler.removeCallbacks(staleTouchFallbackRunnable);
        handler.post(tapRunnable);
    }

    public void pauseForUserTouch() {
        if (!tapping) return;

        if (!pausedForUserTouch) {
            pausedForUserTouch = true;
            handler.removeCallbacks(tapRunnable);
        }

        // ACTION_OUTSIDE can arrive even if an OEM does not provide a complete
        // accessibility touch lifecycle. Give TYPE_TOUCH_INTERACTION_START a
        // chance to take ownership first; only use this fallback when that
        // lifecycle never appears at all.
        if (!userTouchInteractionActive) {
            handler.removeCallbacks(smartPauseFallbackRunnable);
            handler.postDelayed(
                    smartPauseFallbackRunnable,
                    SMART_PAUSE_NO_EVENT_FALLBACK_MS);
        }
    }

    private void resumeAfterUserTouch() {
        if (!tapping || !pausedForUserTouch || tapGesture == null) return;

        pausedForUserTouch = false;
        userTouchInteractionActive = false;
        handler.removeCallbacks(smartPauseFallbackRunnable);
        handler.removeCallbacks(staleTouchFallbackRunnable);
        handler.removeCallbacks(tapRunnable);
        handler.postDelayed(tapRunnable, SMART_PAUSE_END_DELAY_MS);
    }

    private void resumeIfTouchLifecycleMissing() {
        if (!tapping || !pausedForUserTouch || tapGesture == null) return;

        // If Android told us a real touch interaction started, never let the
        // short watchdog resume underneath the user's finger. Wait for the
        // matching END event instead.
        if (userTouchInteractionActive) return;

        resumeAfterUserTouch();
    }

    private void resumeIfTouchEndMissing() {
        if (!tapping || !pausedForUserTouch || tapGesture == null) return;

        // Last-resort protection against an OEM dropping the END event after it
        // did send START. Kept deliberately long so ordinary presses, holds and
        // drags cannot resume underneath the user's finger.
        userTouchInteractionActive = false;
        resumeAfterUserTouch();
    }

    public void stopRapidTapping() {
        tapping = false;
        pausedForUserTouch = false;
        userTouchInteractionActive = false;
        gestureInFlight = false;
        handler.removeCallbacks(tapRunnable);
        handler.removeCallbacks(smartPauseFallbackRunnable);
        handler.removeCallbacks(staleTouchFallbackRunnable);
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
        int type = event.getEventType();

        if (type == AccessibilityEvent.TYPE_TOUCH_INTERACTION_START &&
                tapping && !gestureInFlight) {
            userTouchInteractionActive = true;
            if (!pausedForUserTouch) {
                pausedForUserTouch = true;
                handler.removeCallbacks(tapRunnable);
            }

            // A genuine START event means we can wait for the real release
            // instead of using the short timer.
            handler.removeCallbacks(smartPauseFallbackRunnable);
            handler.removeCallbacks(staleTouchFallbackRunnable);
            handler.postDelayed(
                    staleTouchFallbackRunnable,
                    SMART_PAUSE_STALE_TOUCH_FALLBACK_MS);
            return;
        }

        if (type == AccessibilityEvent.TYPE_TOUCH_INTERACTION_END &&
                pausedForUserTouch && !gestureInFlight) {
            userTouchInteractionActive = false;
            handler.removeCallbacks(smartPauseFallbackRunnable);
            handler.removeCallbacks(staleTouchFallbackRunnable);
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
