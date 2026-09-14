package com.xuvenz.portraitforcer;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.IBinder;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;

public class OrientationService extends Service {
    public static final String ACTION_ENABLE = "com.xuvenz.portraitforcer.ENABLE";
    public static final String ACTION_DISABLE = "com.xuvenz.portraitforcer.DISABLE";

    private static final String CHANNEL_ID = "portrait_forcer";
    private static final int NOTIFICATION_ID = 42;

    private WindowManager windowManager;
    private View orientationOverlay;
    private int previousAccelerometerRotation = 1;
    private int previousUserRotation = 0;
    private boolean savedSystemRotation = false;

    @Override
    public void onCreate() {
        super.onCreate();
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? ACTION_ENABLE : intent.getAction();
        if (ACTION_DISABLE.equals(action)) {
            disablePortrait();
            stopForeground(true);
            stopSelf();
            return START_NOT_STICKY;
        }

        startForeground(NOTIFICATION_ID, buildNotification());
        enablePortrait();
        return START_STICKY;
    }

    private void enablePortrait() {
        if (orientationOverlay != null) return;

        if (Settings.System.canWrite(this)) {
            try {
                previousAccelerometerRotation = Settings.System.getInt(
                        getContentResolver(), Settings.System.ACCELEROMETER_ROTATION, 1);
                previousUserRotation = Settings.System.getInt(
                        getContentResolver(), Settings.System.USER_ROTATION, 0);
                savedSystemRotation = true;
                Settings.System.putInt(getContentResolver(), Settings.System.ACCELEROMETER_ROTATION, 0);
                Settings.System.putInt(getContentResolver(), Settings.System.USER_ROTATION, 0);
            } catch (Exception ignored) {
            }
        }

        orientationOverlay = new View(this);
        orientationOverlay.setBackgroundColor(0x00000000);

        int type = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                1,
                1,
                type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.START;
        params.x = 0;
        params.y = 0;
        params.screenOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;

        try {
            windowManager.addView(orientationOverlay, params);
        } catch (Exception e) {
            orientationOverlay = null;
        }
    }

    private void disablePortrait() {
        if (orientationOverlay != null) {
            try {
                windowManager.removeView(orientationOverlay);
            } catch (Exception ignored) {
            }
            orientationOverlay = null;
        }

        if (savedSystemRotation && Settings.System.canWrite(this)) {
            try {
                Settings.System.putInt(getContentResolver(), Settings.System.ACCELEROMETER_ROTATION,
                        previousAccelerometerRotation);
                Settings.System.putInt(getContentResolver(), Settings.System.USER_ROTATION,
                        previousUserRotation);
            } catch (Exception ignored) {
            }
        }
        savedSystemRotation = false;
    }

    private Notification buildNotification() {
        Intent openIntent = new Intent(this, MainActivity.class);
        PendingIntent openPending = PendingIntent.getActivity(
                this, 0, openIntent,
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0);

        Intent disableIntent = new Intent(this, OrientationService.class);
        disableIntent.setAction(ACTION_DISABLE);
        PendingIntent disablePending = PendingIntent.getService(
                this, 1, disableIntent,
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0);

        Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);

        return builder
                .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
                .setContentTitle("Portrait Forcer active")
                .setContentText("Games are being forced toward portrait orientation")
                .setContentIntent(openPending)
                .setOngoing(true)
                .addAction(new Notification.Action.Builder(
                        android.R.drawable.ic_menu_revert,
                        "Restore rotation",
                        disablePending).build())
                .build();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Portrait Forcer",
                    NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("Keeps the portrait orientation controller running");
            NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            manager.createNotificationChannel(channel);
        }
    }

    @Override
    public void onDestroy() {
        disablePortrait();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
