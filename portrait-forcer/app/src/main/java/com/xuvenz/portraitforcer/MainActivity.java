package com.xuvenz.portraitforcer;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public class MainActivity extends Activity {
    private TextView status;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(40, 60, 40, 40);
        root.setBackgroundColor(Color.rgb(20, 20, 24));

        TextView title = new TextView(this);
        title.setText("Portrait Forcer");
        title.setTextColor(Color.WHITE);
        title.setTextSize(28);
        title.setGravity(Gravity.CENTER);
        root.addView(title, new LinearLayout.LayoutParams(-1, -2));

        TextView info = new TextView(this);
        info.setText("Forces a portrait-orientation overlay over games. Some games may letterbox or resist forced rotation depending on their engine.");
        info.setTextColor(Color.LTGRAY);
        info.setTextSize(16);
        info.setPadding(0, 24, 0, 24);
        root.addView(info, new LinearLayout.LayoutParams(-1, -2));

        status = new TextView(this);
        status.setTextColor(Color.WHITE);
        status.setTextSize(17);
        status.setPadding(0, 0, 0, 22);
        root.addView(status, new LinearLayout.LayoutParams(-1, -2));

        Button overlayPermission = makeButton("1. Allow display over other apps");
        overlayPermission.setOnClickListener(v -> {
            if (!Settings.canDrawOverlays(this)) {
                Intent i = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getPackageName()));
                startActivity(i);
            }
        });
        root.addView(overlayPermission, buttonParams());

        Button writeSettings = makeButton("2. Allow system rotation control (optional)");
        writeSettings.setOnClickListener(v -> {
            if (!Settings.System.canWrite(this)) {
                Intent i = new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS,
                        Uri.parse("package:" + getPackageName()));
                startActivity(i);
            }
        });
        root.addView(writeSettings, buttonParams());

        Button forcePortrait = makeButton("FORCE PORTRAIT");
        forcePortrait.setTextSize(20);
        forcePortrait.setOnClickListener(v -> {
            if (!Settings.canDrawOverlays(this)) {
                Intent i = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getPackageName()));
                startActivity(i);
                return;
            }
            Intent service = new Intent(this, OrientationService.class);
            service.setAction(OrientationService.ACTION_ENABLE);
            startService(service);
            refreshStatus();
        });
        root.addView(forcePortrait, buttonParams());

        Button restore = makeButton("RESTORE NORMAL ROTATION");
        restore.setOnClickListener(v -> {
            Intent service = new Intent(this, OrientationService.class);
            service.setAction(OrientationService.ACTION_DISABLE);
            startService(service);
            refreshStatus();
        });
        root.addView(restore, buttonParams());

        setContentView(root);
        refreshStatus();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshStatus();
    }

    private void refreshStatus() {
        String overlay = Settings.canDrawOverlays(this) ? "Overlay permission: OK" : "Overlay permission: REQUIRED";
        String settings = Settings.System.canWrite(this) ? "Rotation control: OK" : "Rotation control: not granted";
        status.setText(overlay + "\n" + settings);
    }

    private Button makeButton(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        return b;
    }

    private LinearLayout.LayoutParams buttonParams() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.setMargins(0, 8, 0, 8);
        return p;
    }
}
