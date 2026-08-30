package com.custom.rapidtap;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public class FloatingButtonService extends Service {
    public static final String ACTION_SHOW = "com.custom.rapidtap.SHOW";
    public static final String ACTION_HIDE = "com.custom.rapidtap.HIDE";
    public static final String ACTION_PICK_TARGET = "com.custom.rapidtap.PICK_TARGET";

    private static final String PREFS = "rapid_tap_prefs";
    private static final String CHANNEL_ID = "rapid_tap_overlay";
    private static final int NOTIFICATION_ID = 2001;
    private static final long DOUBLE_TAP_MS = 300L;

    private final Handler uiHandler = new Handler(Looper.getMainLooper());
    private Runnable pendingSingleTap;
    private WindowManager windowManager;
    private TextView floatingButton;
    private View pickerView;
    private WindowManager.LayoutParams floatingParams;
    private boolean tapping;

    @Override
    public void onCreate() {
        super.onCreate();
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        createNotificationChannel();
        startForeground(NOTIFICATION_ID, createNotification());
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Overlay permission is required.", Toast.LENGTH_SHORT).show();
            stopSelf();
            return START_NOT_STICKY;
        }

        String action = intent != null ? intent.getAction() : ACTION_SHOW;
        if (ACTION_HIDE.equals(action)) {
            cancelPendingSingleTap();
            stopRapidTap();
            removeFloatingView();
            removePicker();
            stopSelf();
            return START_NOT_STICKY;
        }
        if (ACTION_PICK_TARGET.equals(action)) {
            cancelPendingSingleTap();
            stopRapidTap();
            showTargetPicker();
            return START_STICKY;
        }

        showFloatingButton();
        return START_STICKY;
    }

    private void showFloatingButton() {
        if (floatingButton != null) return;
        removePicker();

        TextView button = new TextView(this);
        button.setText("▶");
        button.setTextColor(Color.WHITE);
        button.setTextSize(23);
        button.setGravity(Gravity.CENTER);
        button.setBackground(circle(Color.argb(235, 55, 95, 165)));
        button.setElevation(dp(6));

        int overlayType = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;

        floatingParams = new WindowManager.LayoutParams(
                dp(56),
                dp(56),
                overlayType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL |
                        WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH |
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        floatingParams.gravity = Gravity.TOP | Gravity.START;

        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        floatingParams.x = prefs.getInt("button_x", dp(24));
        floatingParams.y = prefs.getInt("button_y", dp(220));

        button.setOnTouchListener(new View.OnTouchListener() {
            int initialX;
            int initialY;
            float downRawX;
            float downRawY;
            boolean moved;
            boolean wasTappingAtDown;
            long lastTapUpAt;
            final int slop = dp(8);

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_OUTSIDE:
                        if (tapping) {
                            RapidTapAccessibilityService acc =
                                    RapidTapAccessibilityService.getInstance();
                            if (acc == null || !acc.isDispatchingGesture()) {
                                stopRapidTap();
                                updateButtonAppearance();
                            }
                        }
                        return true;

                    case MotionEvent.ACTION_DOWN:
                        initialX = floatingParams.x;
                        initialY = floatingParams.y;
                        downRawX = event.getRawX();
                        downRawY = event.getRawY();
                        moved = false;
                        wasTappingAtDown = tapping;

                        if (!wasTappingAtDown &&
                                lastTapUpAt > 0 &&
                                SystemClock.uptimeMillis() - lastTapUpAt <= DOUBLE_TAP_MS) {
                            cancelPendingSingleTap();
                        }

                        // Stop immediately when the running bubble is touched so an
                        // accessibility gesture cannot interfere with the stop press.
                        if (wasTappingAtDown) {
                            cancelPendingSingleTap();
                            stopRapidTap();
                            updateButtonAppearance();
                        }
                        return true;

                    case MotionEvent.ACTION_MOVE:
                        float dx = event.getRawX() - downRawX;
                        float dy = event.getRawY() - downRawY;
                        if (!moved && (Math.abs(dx) > slop || Math.abs(dy) > slop)) {
                            moved = true;
                            cancelPendingSingleTap();
                            lastTapUpAt = 0;
                        }
                        if (moved) {
                            floatingParams.x = initialX + Math.round(dx);
                            floatingParams.y = initialY + Math.round(dy);
                            try {
                                windowManager.updateViewLayout(floatingButton, floatingParams);
                            } catch (Exception ignored) { }
                        }
                        return true;

                    case MotionEvent.ACTION_UP:
                        if (moved) {
                            prefs.edit()
                                    .putInt("button_x", floatingParams.x)
                                    .putInt("button_y", floatingParams.y)
                                    .apply();
                        } else if (!wasTappingAtDown) {
                            long now = SystemClock.uptimeMillis();
                            if (lastTapUpAt > 0 && now - lastTapUpAt <= DOUBLE_TAP_MS) {
                                cancelPendingSingleTap();
                                lastTapUpAt = 0;
                                showTargetPicker();
                            } else {
                                lastTapUpAt = now;
                                pendingSingleTap = () -> {
                                    pendingSingleTap = null;
                                    lastTapUpAt = 0;
                                    if (!tapping) {
                                        startRapidTap();
                                        updateButtonAppearance();
                                    }
                                };
                                uiHandler.postDelayed(pendingSingleTap, DOUBLE_TAP_MS);
                            }
                        }
                        updateButtonAppearance();
                        return true;

                    case MotionEvent.ACTION_CANCEL:
                        updateButtonAppearance();
                        return true;
                }
                return false;
            }
        });

        floatingButton = button;
        windowManager.addView(floatingButton, floatingParams);
        updateButtonAppearance();
    }

    private void startRapidTap() {
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        if (!prefs.contains("target_x") || !prefs.contains("target_y")) {
            Toast.makeText(this, "Set a target in Rapid Tap Toggle first.", Toast.LENGTH_SHORT).show();
            return;
        }

        RapidTapAccessibilityService acc = RapidTapAccessibilityService.getInstance();
        if (acc == null) {
            Toast.makeText(this, "Enable Rapid Tap Toggle in Accessibility first.", Toast.LENGTH_LONG).show();
            return;
        }

        int x = prefs.getInt("target_x", 0);
        int y = prefs.getInt("target_y", 0);
        int interval = prefs.getInt("interval", 75);
        int variation = prefs.getInt("timing_variation", 0);
        int pauseChance = prefs.getInt("pause_chance", 0);
        int pauseMin = prefs.getInt("pause_min", 150);
        int pauseMax = prefs.getInt("pause_max", 450);

        acc.startRapidTapping(
                x,
                y,
                interval,
                variation,
                pauseChance,
                pauseMin,
                pauseMax);
        tapping = true;
    }

    private void stopRapidTap() {
        RapidTapAccessibilityService acc = RapidTapAccessibilityService.getInstance();
        if (acc != null) acc.stopRapidTapping();
        tapping = false;
    }

    private void cancelPendingSingleTap() {
        if (pendingSingleTap != null) {
            uiHandler.removeCallbacks(pendingSingleTap);
            pendingSingleTap = null;
        }
    }

    private void updateButtonAppearance() {
        if (floatingButton == null) return;
        if (tapping) {
            floatingButton.setText("■");
            floatingButton.setBackground(circle(Color.argb(235, 175, 55, 55)));
        } else {
            floatingButton.setText("▶");
            floatingButton.setBackground(circle(Color.argb(235, 55, 95, 165)));
        }
    }

    private void showTargetPicker() {
        cancelPendingSingleTap();
        stopRapidTap();
        updateButtonAppearance();
        removePicker();

        LinearLayout picker = new LinearLayout(this);
        picker.setOrientation(LinearLayout.VERTICAL);
        picker.setPadding(dp(7), dp(7), dp(7), dp(7));
        picker.setGravity(Gravity.CENTER_HORIZONTAL);
        picker.setBackground(rounded(Color.argb(230, 30, 30, 30), 14));

        TextView marker = new TextView(this);
        marker.setText("⊕\nDRAG TARGET");
        marker.setTextColor(Color.WHITE);
        marker.setTextSize(14);
        marker.setGravity(Gravity.CENTER);
        marker.setBackground(rounded(Color.argb(235, 125, 50, 50), 12));
        picker.addView(marker, new LinearLayout.LayoutParams(dp(112), dp(82)));

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams actionsLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        actionsLp.setMargins(0, dp(5), 0, 0);
        picker.addView(actions, actionsLp);

        Button set = new Button(this);
        set.setText("SET");
        set.setTextColor(Color.WHITE);
        set.setTextSize(12);
        set.setAllCaps(false);
        set.setBackground(rounded(Color.rgb(45, 100, 55), 10));
        actions.addView(set, new LinearLayout.LayoutParams(dp(72), dp(48)));

        Button cancel = new Button(this);
        cancel.setText("×");
        cancel.setTextColor(Color.WHITE);
        cancel.setTextSize(19);
        cancel.setAllCaps(false);
        cancel.setPadding(0, 0, 0, 0);
        cancel.setBackground(rounded(Color.rgb(65, 65, 65), 10));
        LinearLayout.LayoutParams cancelLp = new LinearLayout.LayoutParams(dp(48), dp(48));
        cancelLp.setMargins(dp(5), 0, 0, 0);
        actions.addView(cancel, cancelLp);

        int overlayType = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                overlayType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.START;

        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        params.x = prefs.getInt("picker_x", dp(100));
        params.y = prefs.getInt("picker_y", dp(220));

        marker.setOnTouchListener(new View.OnTouchListener() {
            int initialX;
            int initialY;
            float initialTouchX;
            float initialTouchY;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        initialX = params.x;
                        initialY = params.y;
                        initialTouchX = event.getRawX();
                        initialTouchY = event.getRawY();
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        params.x = initialX + Math.round(event.getRawX() - initialTouchX);
                        params.y = initialY + Math.round(event.getRawY() - initialTouchY);
                        try {
                            windowManager.updateViewLayout(pickerView, params);
                        } catch (Exception ignored) { }
                        return true;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        prefs.edit()
                                .putInt("picker_x", params.x)
                                .putInt("picker_y", params.y)
                                .apply();
                        return true;
                }
                return false;
            }
        });

        set.setOnClickListener(v -> {
            int[] location = new int[2];
            marker.getLocationOnScreen(location);
            int x = location[0] + marker.getWidth() / 2;
            int y = location[1] + marker.getHeight() / 2;
            prefs.edit().putInt("target_x", x).putInt("target_y", y).apply();
            Toast.makeText(this, "Target set: " + x + ", " + y, Toast.LENGTH_SHORT).show();
            removePicker();
            if (floatingButton == null) stopSelf();
        });

        cancel.setOnClickListener(v -> {
            removePicker();
            if (floatingButton == null) stopSelf();
        });

        pickerView = picker;
        windowManager.addView(pickerView, params);
        Toast.makeText(this,
                "Drag ⊕ over the new target, then press SET.",
                Toast.LENGTH_LONG).show();
    }

    private void removeFloatingView() {
        cancelPendingSingleTap();
        if (floatingButton != null) {
            try {
                windowManager.removeView(floatingButton);
            } catch (Exception ignored) { }
            floatingButton = null;
        }
    }

    private void removePicker() {
        if (pickerView != null) {
            try {
                windowManager.removeView(pickerView);
            } catch (Exception ignored) { }
            pickerView = null;
        }
    }

    @Override
    public void onDestroy() {
        cancelPendingSingleTap();
        stopRapidTap();
        removePicker();
        removeFloatingView();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private Notification createNotification() {
        Intent openApp = new Intent(this, MainActivity.class);
        PendingIntent pending = PendingIntent.getActivity(
                this,
                0,
                openApp,
                Build.VERSION.SDK_INT >= 23
                        ? PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
                        : PendingIntent.FLAG_UPDATE_CURRENT);

        Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);

        return builder
                .setContentTitle("Rapid Tap Toggle")
                .setContentText("Floating rapid-tap toggle is available")
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setOngoing(true)
                .setContentIntent(pending)
                .build();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Rapid Tap Overlay",
                    NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("Keeps the floating rapid-tap button available over other apps.");
            NotificationManager manager =
                    (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            manager.createNotificationChannel(channel);
        }
    }

    private GradientDrawable circle(int color) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.OVAL);
        drawable.setColor(color);
        return drawable;
    }

    private GradientDrawable rounded(int color, int radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radiusDp));
        return drawable;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
