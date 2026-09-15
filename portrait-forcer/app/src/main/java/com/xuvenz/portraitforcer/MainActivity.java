package com.xuvenz.portraitforcer;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
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
        root.setPadding(40, 50, 40, 40);
        root.setBackgroundColor(Color.rgb(20,20,24));

        TextView title = text("Portrait Forcer v1.1", 28, Color.WHITE);
        title.setGravity(Gravity.CENTER); root.addView(title, full());
        TextView info = text("Skylore test build. True Portrait asks Android to keep a portrait display configuration and continuously reasserts portrait while the game is running. This cannot rewrite a game's renderer; games that hard-lock landscape may still letterbox or resist it.", 15, Color.LTGRAY);
        info.setPadding(0,20,0,20); root.addView(info, full());
        status = text("",16,Color.WHITE); status.setPadding(0,0,0,18); root.addView(status, full());

        Button overlay = button("1. Allow display over other apps");
        overlay.setOnClickListener(v -> { if(!Settings.canDrawOverlays(this)) startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:"+getPackageName()))); });
        root.addView(overlay, fullMargin());
        Button write = button("2. Allow system rotation control");
        write.setOnClickListener(v -> { if(!Settings.System.canWrite(this)) startActivity(new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:"+getPackageName()))); });
        root.addView(write, fullMargin());

        Button truePortrait = button("TRUE PORTRAIT — SKYLORE"); truePortrait.setTextSize(19);
        truePortrait.setOnClickListener(v -> startForce(true)); root.addView(truePortrait, fullMargin());
        Button compatibility = button("COMPATIBILITY PORTRAIT (v1.0 method)");
        compatibility.setOnClickListener(v -> startForce(false)); root.addView(compatibility, fullMargin());
        Button restore = button("RESTORE NORMAL DISPLAY");
        restore.setOnClickListener(v -> { Intent s=new Intent(this,OrientationService.class); s.setAction(OrientationService.ACTION_DISABLE); startService(s); refresh(); });
        root.addView(restore, fullMargin());

        TextView adb = text("If Skylore still renders landscape, the next step is an ADB/Shizuku-assisted build. A normal app cannot change another app's framebuffer size or stretch its Surface directly.",14,Color.GRAY);
        adb.setPadding(0,22,0,0); root.addView(adb, full());
        setContentView(root); refresh();
    }

    private void startForce(boolean strong) {
        if(!Settings.canDrawOverlays(this)) { startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:"+getPackageName()))); return; }
        Intent s=new Intent(this,OrientationService.class); s.setAction(strong ? OrientationService.ACTION_ENABLE_STRONG : OrientationService.ACTION_ENABLE); startService(s); refresh();
    }
    @Override protected void onResume(){super.onResume();refresh();}
    private void refresh(){ status.setText("Overlay: "+(Settings.canDrawOverlays(this)?"OK":"REQUIRED")+"\nSystem rotation control: "+(Settings.System.canWrite(this)?"OK":"RECOMMENDED")); }
    private TextView text(String s,int size,int color){TextView v=new TextView(this);v.setText(s);v.setTextSize(size);v.setTextColor(color);return v;}
    private Button button(String s){Button b=new Button(this);b.setText(s);b.setAllCaps(false);return b;}
    private LinearLayout.LayoutParams full(){return new LinearLayout.LayoutParams(-1,-2);}
    private LinearLayout.LayoutParams fullMargin(){LinearLayout.LayoutParams p=full();p.setMargins(0,7,0,7);return p;}
}
