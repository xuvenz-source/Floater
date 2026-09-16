package com.xuvenz.portraitforcer;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.concurrent.TimeUnit;

/** Runs as Shizuku's shell user, not as the application UID. */
public final class ShellUserService extends IShellService.Stub {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private String pendingRestore;
    private final Runnable restore = () -> {
        if (pendingRestore != null) execute(pendingRestore);
        pendingRestore = null;
    };

    public ShellUserService(Context context) { }

    @Override public String execute(String command) {
        StringBuilder result = new StringBuilder("$ ").append(command).append('\n');
        try {
            Process process = new ProcessBuilder("sh", "-c", command).redirectErrorStream(true).start();
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) result.append(line).append('\n');
            boolean exited = process.waitFor(20, TimeUnit.SECONDS);
            if (!exited) { process.destroy(); result.append("TIMEOUT"); }
            else result.append("[exit ").append(process.exitValue()).append(']');
        } catch (Exception e) {
            result.append("ERROR: ").append(e.getClass().getSimpleName()).append(": ").append(e.getMessage());
        }
        return result.toString();
    }

    @Override public void armDisplayRestore(String sizeCommand, long delayMillis) {
        handler.removeCallbacks(restore);
        pendingRestore = sizeCommand;
        handler.postDelayed(restore, delayMillis);
    }

    @Override public String restoreNow(String sizeCommand) {
        handler.removeCallbacks(restore);
        pendingRestore = null;
        return execute(sizeCommand);
    }

    public void destroy() { handler.removeCallbacks(restore); restore.run(); }
}
