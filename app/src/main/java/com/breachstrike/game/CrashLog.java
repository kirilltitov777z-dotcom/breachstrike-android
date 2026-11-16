package com.breachstrike.game;

import android.content.Context;
import android.os.Build;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public final class CrashLog {
    private CrashLog() {}

    public static void install(final Context ctx) {
        final Thread.UncaughtExceptionHandler prev = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            @Override
            public void uncaughtException(Thread t, Throwable e) {
                write(ctx, "Uncaught on " + t.getName(), e);
                if (prev != null) prev.uncaughtException(t, e);
            }
        });
    }

    public static void write(Context c, String tag, Throwable t) {
        try {
            File dir = c.getExternalFilesDir(null);
            if (dir == null) dir = c.getFilesDir();
            if (dir != null && !dir.exists()) dir.mkdirs();
            File f = new File(dir, "crash.txt");
            FileWriter fw = new FileWriter(f, false);
            PrintWriter pw = new PrintWriter(fw);
            pw.println("Breach Strike crash report");
            pw.println("time: " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date()));
            pw.println("device: " + Build.MANUFACTURER + " " + Build.MODEL + " / Android " + Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")");
            pw.println("tag: " + tag);
            pw.println();
            StringWriter sw = new StringWriter();
            t.printStackTrace(new PrintWriter(sw));
            pw.println(sw.toString());
            pw.close();
        } catch (Throwable ignored) {}
    }

    public static String read(Context c) {
        try {
            File dir = c.getExternalFilesDir(null);
            if (dir == null) dir = c.getFilesDir();
            File f = new File(dir, "crash.txt");
            if (!f.exists()) return null;
            FileReader fr = new FileReader(f);
            char[] buf = new char[(int) Math.min(200000, f.length())];
            int n = fr.read(buf);
            fr.close();
            return new String(buf, 0, Math.max(0, n));
        } catch (Throwable e) {
            return null;
        }
    }

    public static void clear(Context c) {
        try {
            File dir = c.getExternalFilesDir(null);
            if (dir == null) dir = c.getFilesDir();
            File f = new File(dir, "crash.txt");
            if (f.exists()) f.delete();
        } catch (Throwable ignored) {}
    }
}
