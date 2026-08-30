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
    private EditText variationInput;
    private EditText pauseChanceInput;
    private EditText pauseMinInput;
    private EditText pauseMaxInput;

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
        intro.setText("Tap ▶ once to start. Touch anywhere else to pause. While paused, double-tap the floating button to choose a new target. Drag the button to move it.");
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
            saveSettings();
            startFloatingService(FloatingButtonService.ACTION_PICK_TARGET);
        });
        root.addView(target, buttonParams());

        SharedPreferences saved = getSharedPreferences(PREFS, MODE_PRIVATE);

        TextView intervalLabel = label("Base tap interval (milliseconds)");
        intervalLabel.setPadding(0, dp(16), 0, dp(4));
        root.addView(intervalLabel, matchWrap());

        intervalInput = numberField(saved.getInt("interval", 75), "75");
        root.addView(intervalInput, matchWrap());

        TextView intervalHint = hint("Recommended: 50–100 ms. Allowed range: 30–1000 ms.");
        root.addView(intervalHint, matchWrap());

        TextView rhythmTitle = new TextView(this);
        rhythmTitle.setText("Optional rhythm variation");
        rhythmTitle.setTextSize(19);
        rhythmTitle.setTextColor(Color.BLACK);
        rhythmTitle.setPadding(0, dp(18), 0, dp(6));
        root.addView(rhythmTitle, matchWrap());

        TextView rhythmHint = hint("These settings just make the tapping rhythm less mechanically uniform. Set variation and pause chance to 0 for the original fixed rhythm.");
        root.addView(rhythmHint, matchWrap());

        root.addView(label("Timing variation (± milliseconds)"), matchWrap());
        variationInput = numberField(saved.getInt("timing_variation", 0), "0");
        root.addView(variationInput, matchWrap());
        root.addView(hint("Example: 75 ms base + 10 ms variation produces roughly 65–85 ms gaps."), matchWrap());

        root.addView(label("Occasional pause chance (%)"), matchWrap());
        pauseChanceInput = numberField(saved.getInt("pause_chance", 0), "0");
        root.addView(pauseChanceInput, matchWrap());
        root.addView(hint("0 disables pauses. Allowed range: 0–50%."), matchWrap());

        root.addView(label("Pause length minimum (ms)"), matchWrap());
        pauseMinInput = numberField(saved.getInt("pause_min", 150), "150");
        root.addView(pauseMinInput, matchWrap());

        root.addView(label("Pause length maximum (ms)"), matchWrap());
        pauseMaxInput = numberField(saved.getInt("pause_max", 450), "450");
        root.addView(pauseMaxInput, matchWrap());
        root.addView(hint("Allowed pause range: 0–5000 ms."), matchWrap());

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
            saveSettings();
            startFloatingService(FloatingButtonService.ACTION_SHOW);
            Toast.makeText(this,
                    "Single tap ▶ to start; touch elsewhere to pause; double-tap ▶ to retarget.",
                    Toast.LENGTH_LONG).show();
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

    private void saveSettings() {
        int interval = readClamped(intervalInput, 75, 30, 1000);
        int variation = readClamped(variationInput, 0, 0, 500);
        int pauseChance = readClamped(pauseChanceInput, 0, 0, 50);
        int pauseMin = readClamped(pauseMinInput, 150, 0, 5000);
        int pauseMax = readClamped(pauseMaxInput, 450, 0, 5000);
        if (pauseMax < pauseMin) {
            pauseMax = pauseMin;
            pauseMaxInput.setText(String.valueOf(pauseMax));
        }

        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                .putInt("interval", interval)
                .putInt("timing_variation", variation)
                .putInt("pause_chance", pauseChance)
                .putInt("pause_min", pauseMin)
                .putInt("pause_max", pauseMax)
                .apply();
    }

    private int readClamped(EditText field, int fallback, int min, int max) {
        int value = fallback;
        try {
            value = Integer.parseInt(field.getText().toString().trim());
        } catch (Exception ignored) { }
        value = Math.max(min, Math.min(max, value));
        field.setText(String.valueOf(value));
        return value;
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

    private TextView label(String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(16);
        t.setTextColor(Color.BLACK);
        t.setPadding(0, dp(10), 0, dp(4));
        return t;
    }

    private TextView hint(String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(14);
        t.setTextColor(Color.GRAY);
        t.setPadding(0, dp(3), 0, dp(8));
        return t;
    }

    private EditText numberField(int value, String hint) {
        EditText e = new EditText(this);
        e.setInputType(InputType.TYPE_CLASS_NUMBER);
        e.setText(String.valueOf(value));
        e.setHint(hint);
        return e;
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
