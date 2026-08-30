package com.custom.rapidtap;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {
    private static final String PREFS = "rapid_tap_prefs";

    private TextView permissionStatus;
    private TextView targetText;
    private EditText intervalInput;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(buildUi());
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshStatus();
    }

    private View buildUi() {
        int pad = dp(20);
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, pad, pad, pad);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        scroll.addView(root);

        TextView title = new TextView(this);
        title.setText("Rapid Tap Toggle");
        title.setTextSize(28);
        title.setTextColor(Color.BLACK);
        title.setPadding(0, 0, 0, dp(8));
        root.addView(title, matchWrap());

        TextView intro = new TextView(this);
        intro.setText("Choose one screen target, then use a small floating button. Tap ▶ once to start rapid tapping; tap ■ to stop. Drag the button to move it.");
        intro.setTextSize(16);
        intro.setTextColor(Color.DKGRAY);
        intro.setPadding(0, 0, 0, dp(14));
        root.addView(intro, matchWrap());

        permissionStatus = new TextView(this);
        permissionStatus.setTextSize(15);
        permissionStatus.setTextColor(Color.DKGRAY);
        permissionStatus.setPadding(0, 0, 0, dp(10));
        root.addView(permissionStatus, matchWrap());

        Button overlay = button("1. Grant overlay permission");
        overlay.setOnClickListener(v -> requestOverlayPermission());
        root.addView(overlay, buttonParams());

        Button accessibility = button("2. Enable accessibility service");
        accessibility.setOnClickListener(v -> {
            startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
            Toast.makeText(this, "Enable ‘Rapid Tap Toggle’ in Accessibility.", Toast.LENGTH_LONG).show();
        });
        root.addView(accessibility, buttonParams());

        targetText = new TextView(this);
        targetText.setTextSize(16);
        targetText.setTextColor(Color.BLACK);
        targetText.setPadding(0, dp(12), 0, dp(8));
        root.addView(targetText, matchWrap());

        Button target = button("3. Show target picker");
        target.setOnClickListener(v -> {
            if (!Settings.canDrawOverlays(this)) {
                Toast.makeText(this, "Grant overlay permission first.", Toast.LENGTH_SHORT).show();
                requestOverlayPermission();
                return;
            }
            saveInterval();
            startFloatingService(FloatingButtonService.ACTION_PICK_TARGET);
        });
        root.addView(target, buttonParams());

        TextView intervalLabel = new TextView(this);
        intervalLabel.setText("Tap interval (milliseconds)");
        intervalLabel.setTextSize(16);
        intervalLabel.setTextColor(Color.BLACK);
        intervalLabel.setPadding(0, dp(16), 0, dp(4));
        root.addView(intervalLabel, matchWrap());

        intervalInput = new EditText(this);
        intervalInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        int savedInterval = getSharedPreferences(PREFS, MODE_PRIVATE).getInt("interval", 75);
        intervalInput.setText(String.valueOf(savedInterval));
        intervalInput.setHint("75");
        root.addView(intervalInput, matchWrap());

        TextView hint = new TextView(this);
        hint.setText("Recommended: 50–100 ms. Allowed range: 30–1000 ms.");
        hint.setTextSize(14);
        hint.setTextColor(Color.GRAY);
        hint.setPadding(0, dp(4), 0, dp(14));
        root.addView(hint, matchWrap());

        Button show = button("4. Show floating toggle");
        show.setOnClickListener(v -> {
            if (!Settings.canDrawOverlays(this)) {
                Toast.makeText(this, "Grant overlay permission first.", Toast.LENGTH_SHORT).show();
                requestOverlayPermission();
                return;
            }
            if (!hasTarget()) {
                Toast.makeText(this, "Set a target first.", Toast.LENGTH_SHORT).show();
                return;
            }
            saveInterval();
            startFloatingService(FloatingButtonService.ACTION_SHOW);
            Toast.makeText(this, "Tap ▶ to start, ■ to stop. Drag the circle to move it.", Toast.LENGTH_LONG).show();
        });
        root.addView(show, buttonParams());

        Button hide = button("Hide floating toggle");
        hide.setOnClickListener(v -> startFloatingService(FloatingButtonService.ACTION_HIDE));
        root.addView(hide, buttonParams());

        TextView privacy = new TextView(this);
        privacy.setText("No internet permission is requested. Accessibility is used only to dispatch taps to the target you choose.");
        privacy.setTextSize(14);
        privacy.setTextColor(Color.DKGRAY);
        privacy.setPadding(0, dp(20), 0, dp(8));
        root.addView(privacy, matchWrap());

        refreshStatus();
        return scroll;
    }

    private void refreshStatus() {
        if (permissionStatus != null) {
            permissionStatus.setText(
                    "Overlay: " + (Settings.canDrawOverlays(this) ? "ON" : "OFF") +
                    "   •   Accessibility: " + (isAccessibilityEnabled() ? "ON" : "OFF"));
        }
        if (targetText != null) {
            SharedPreferences p = getSharedPreferences(PREFS, MODE_PRIVATE);
            if (hasTarget()) {
                targetText.setText("Target: x=" + p.getInt("target_x", 0) + ", y=" + p.getInt("target_y", 0));
            } else {
                targetText.setText("Target: not set yet");
            }
        }
    }

    private boolean hasTarget() {
        SharedPreferences p = getSharedPreferences(PREFS, MODE_PRIVATE);
        return p.contains("target_x") && p.contains("target_y");
    }

    private boolean isAccessibilityEnabled() {
        String enabled = Settings.Secure.getString(getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (enabled == null) return false;
        ComponentName target = new ComponentName(this, RapidTapAccessibilityService.class);
        String flat = target.flattenToString();
        String shortFlat = target.flattenToShortString();
        for (String service : enabled.split(":")) {
            if (service.equalsIgnoreCase(flat) || service.equalsIgnoreCase(shortFlat)) return true;
        }
        return false;
    }

    private void saveInterval() {
        int value = 75;
        try {
            value = Integer.parseInt(intervalInput.getText().toString().trim());
        } catch (Exception ignored) { }
        value = Math.max(30, Math.min(1000, value));
        intervalInput.setText(String.valueOf(value));
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putInt("interval", value).apply();
    }

    private void requestOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            startActivity(intent);
        } else {
            Toast.makeText(this, "Overlay permission is already enabled.", Toast.LENGTH_SHORT).show();
        }
    }

    private void startFloatingService(String action) {
        Intent i = new Intent(this, FloatingButtonService.class);
        i.setAction(action);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(i);
        else startService(i);
    }

    private Button button(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextSize(16);
        return b;
    }

    private LinearLayout.LayoutParams buttonParams() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(52));
        lp.setMargins(0, dp(5), 0, dp(5));
        return lp;
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
