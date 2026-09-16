package com.xuvenz.portraitforcer;

import android.app.Activity;
import android.content.ComponentName;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import rikka.shizuku.Shizuku;

public class MainActivity extends Activity {
    private static final int REQUEST_SHIZUKU = 71;
    private static final String EXPECTED = "com.aigrind.skylore";
    private final Shizuku.UserServiceArgs serviceArgs = new Shizuku.UserServiceArgs(
            new ComponentName("com.xuvenz.portraitforcer", ShellUserService.class.getName()))
            .daemon(false).processNameSuffix("shell").debuggable(false).version(1);
    private IShellService shell;
    private TextView state, log;
    private String gamePackage;
    private SharedPreferences saved;

    private final ServiceConnection connection = new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName name, IBinder binder) {
            shell = IShellService.Stub.asInterface(binder); append("Shizuku shell UserService connected."); detectGame(); refresh();
        }
        @Override public void onServiceDisconnected(ComponentName name) { shell = null; append("UserService disconnected."); refresh(); }
    };
    private final Shizuku.OnRequestPermissionResultListener permissionListener = (code, result) -> {
        append(result == PackageManager.PERMISSION_GRANTED ? "Shizuku permission granted." : "Shizuku permission denied.");
        if (result == PackageManager.PERMISSION_GRANTED) bindShell();
        refresh();
    };
    private final Shizuku.OnBinderReceivedListener binderReceivedListener = () -> { append("Shizuku binder is available."); connectIfAllowed(); refresh(); };
    private final Shizuku.OnBinderDeadListener binderDeadListener = () -> { shell = null; append("Shizuku stopped or disconnected."); refresh(); };

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b); saved = getSharedPreferences("restore", MODE_PRIVATE);
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(32, 38, 32, 40); root.setBackgroundColor(Color.rgb(18, 20, 25));
        TextView title = text("Portrait Forcer v1.2 — Shizuku lab", 25, Color.WHITE); title.setGravity(Gravity.CENTER); root.addView(title, full());
        TextView warning = text("Android cannot stretch another app's Surface. These shell-level experiments instead change Skylore's task or logical display configuration so it can recreate its own Surface with portrait dimensions. Results depend on Skylore and Oppo firmware.", 14, 0xffdddddd); warning.setPadding(0,18,0,12); root.addView(warning, full());
        state = text("", 15, 0xff8ee6a8); root.addView(state, full());
        root.addView(button("GRANT SHIZUKU PERMISSION", v -> requestPermission()), margin());
        root.addView(button("MODE A — RESIZABLE + PORTRAIT TASK", v -> runTaskMode()), margin());
        root.addView(button("MODE B — LOGICAL PORTRAIT DISPLAY (2 MIN)", v -> runDisplayMode()), margin());
        Button restore = button("RESTORE NORMAL DISPLAY", v -> restoreAll()); restore.setTextColor(0xffffffff); restore.setBackgroundColor(0xffb3261e); root.addView(restore, margin());
        root.addView(button("REFRESH DIAGNOSTICS", v -> diagnostics()), margin());
        TextView reboot = text("NON-ROOT ANDROID 10: Shizuku must be started again with ADB after every phone reboot. Mode B has a two-minute safety restore, but always return here and tap RESTORE NORMAL DISPLAY when finished.", 14, 0xffffc878); reboot.setPadding(0,18,0,12); root.addView(reboot, full());
        log = text("Operation log\n", 12, 0xffd2d7df); log.setTextIsSelectable(true); root.addView(log, full());
        ScrollView scroll = new ScrollView(this); scroll.addView(root); setContentView(scroll);
        Shizuku.addRequestPermissionResultListener(permissionListener); connectIfAllowed(); refresh();
        Shizuku.addBinderReceivedListenerSticky(binderReceivedListener);
        Shizuku.addBinderDeadListener(binderDeadListener);
    }

    private void requestPermission() {
        if (!Shizuku.pingBinder()) { append("Shizuku is not running. Start it from the Shizuku app using ADB."); refresh(); return; }
        if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) bindShell();
        else Shizuku.requestPermission(REQUEST_SHIZUKU);
    }
    private void connectIfAllowed() { if (Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) bindShell(); }
    private void bindShell() { if (shell == null) try { Shizuku.bindUserService(serviceArgs, connection); } catch (Exception e) { append("Bind failed: " + e); } }

    private void detectGame() {
        String packages = run("pm list packages | grep -i skylore");
        Matcher m = Pattern.compile("package:([A-Za-z0-9._-]+)").matcher(packages);
        gamePackage = m.find() ? m.group(1) : null;
        if (packages.contains("package:" + EXPECTED)) gamePackage = EXPECTED;
        append(gamePackage == null ? "Skylore package was not found; install/open it and refresh." : "Detected Skylore package: " + gamePackage);
    }

    private void saveOriginals() {
        if (saved.getBoolean("armed", false)) return;
        String size = run("wm size");
        String force = value(run("settings get global force_resizable_activities"));
        String freeform = value(run("settings get global enable_freeform_support"));
        String override = null;
        Matcher matcher = Pattern.compile("Override size: (\\d+x\\d+)").matcher(size);
        if (matcher.find()) override = matcher.group(1);
        saved.edit().putBoolean("armed", true).putString("size", override == null ? "reset" : override)
                .putString("force", force).putString("freeform", freeform).apply();
        append("Saved original display/settings before modification.");
    }

    private void runTaskMode() {
        if (!ready()) return; saveOriginals();
        append(run("settings put global force_resizable_activities 1; settings put global enable_freeform_support 1"));
        String component = value(run("cmd package resolve-activity --brief " + gamePackage));
        if (!component.contains("/")) { append("Could not resolve Skylore launcher activity; not starting it."); return; }
        append(run("am force-stop " + gamePackage + "; am start -W -n " + component));
        append(run("sleep 2; am stack list"));
        String stacks = run("am stack list");
        Matcher sm = Pattern.compile("Stack id=(\\d+)[^\\n]*(?:\\n(?!Stack id=)[^\\n]*)*" + Pattern.quote(gamePackage), Pattern.CASE_INSENSITIVE).matcher(stacks);
        if (sm.find()) {
            int width = getResources().getDisplayMetrics().widthPixels, height = getResources().getDisplayMetrics().heightPixels;
            int portraitWidth = Math.min(width, height), portraitHeight = Math.max(width, height);
            append(run("am stack resize " + sm.group(1) + " 0 0 " + portraitWidth + " " + portraitHeight));
        } else append("No Skylore stack id found. Oppo may hide/disable the Android 10 stack resize command.");
        diagnostics();
    }

    private void runDisplayMode() {
        if (!ready()) return; saveOriginals();
        String current = run("wm size"); Matcher physical = Pattern.compile("Physical size: (\\d+)x(\\d+)").matcher(current);
        if (!physical.find()) { append("Cannot determine physical display size; aborted safely."); return; }
        int a=Integer.parseInt(physical.group(1)), b=Integer.parseInt(physical.group(2));
        String portrait = Math.min(a,b) + "x" + Math.max(a,b);
        String restoreCommand = sizeRestoreCommand();
        try { shell.armDisplayRestore(restoreCommand, 120000); } catch (RemoteException e) { append("Could not arm safety restore; aborted: " + e); return; }
        append("Safety restore armed for 2 minutes."); append(run("wm size " + portrait));
        String component=value(run("cmd package resolve-activity --brief " + gamePackage));
        if (component.contains("/")) append(run("am force-stop " + gamePackage + "; am start -W -n " + component));
        diagnostics();
    }

    private void restoreAll() {
        if (!ready()) return;
        String command = sizeRestoreCommand() + "; " + restoreSetting("force_resizable_activities", saved.getString("force", "null")) + "; " + restoreSetting("enable_freeform_support", saved.getString("freeform", "null"));
        try { append(shell.restoreNow(command)); saved.edit().clear().apply(); append("Normal display restore completed."); } catch (RemoteException e) { append("RESTORE FAILED: " + e); }
        diagnostics();
    }
    private String sizeRestoreCommand() { String size=saved.getString("size", "reset"); return "wm size " + ("reset".equals(size) ? "reset" : size); }
    private String restoreSetting(String key,String value) { return "null".equals(value) || value.length()==0 ? "settings delete global " + key : "settings put global " + key + " " + value; }
    private void diagnostics() { if (!ready()) return; if(gamePackage==null) detectGame(); append(run("wm size; wm density; settings get global force_resizable_activities; settings get global enable_freeform_support; dumpsys activity activities | grep -E 'mResumedActivity|" + (gamePackage == null ? EXPECTED : gamePackage) + "' | head -20")); refresh(); }
    private boolean ready() { if(shell==null){append("No shell service. Start Shizuku, grant permission, then retry."); requestPermission(); return false;} if(gamePackage==null){detectGame(); if(gamePackage==null)return false;} return true; }
    private String run(String command) { if(shell==null)return "[not connected]"; try{return shell.execute(command);}catch(RemoteException e){return "Remote error: "+e;} }
    private String value(String output) { String[] lines=output.split("\\n"); return lines.length>1 ? lines[1].trim() : ""; }
    private void refresh() { boolean running=Shizuku.pingBinder(); String permission=!running?"unavailable":(Shizuku.checkSelfPermission()==PackageManager.PERMISSION_GRANTED?"granted":"not granted"); state.setText("Shizuku: "+(running?"RUNNING":"NOT RUNNING")+"\nPermission: "+permission+"\nShell service: "+(shell!=null?"CONNECTED":"not connected")+"\nSkylore: "+(gamePackage==null?"not detected":gamePackage)); }
    private void append(String s) { runOnUiThread(() -> log.append("\n"+s+"\n")); }
    private TextView text(String s,int size,int color){TextView v=new TextView(this);v.setText(s);v.setTextSize(size);v.setTextColor(color);return v;}
    private Button button(String s, android.view.View.OnClickListener l){Button b=new Button(this);b.setText(s);b.setAllCaps(false);b.setOnClickListener(l);return b;}
    private LinearLayout.LayoutParams full(){return new LinearLayout.LayoutParams(-1,-2);}
    private LinearLayout.LayoutParams margin(){LinearLayout.LayoutParams p=full();p.setMargins(0,7,0,7);return p;}
    @Override protected void onDestroy(){
        Shizuku.removeRequestPermissionResultListener(permissionListener);
        Shizuku.removeBinderReceivedListener(binderReceivedListener);
        Shizuku.removeBinderDeadListener(binderDeadListener);
        super.onDestroy();
    }
}
