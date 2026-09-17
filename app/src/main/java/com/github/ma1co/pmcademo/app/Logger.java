package com.github.ma1co.pmcademo.app;

import android.os.Environment;

import java.io.*;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class Logger {
    public static File getFile() {
        return new File(Environment.getExternalStorageDirectory(), "ASTROFISH/LOG.TXT");
    }

    protected static synchronized void log(String msg) {
        try {
            getFile().getParentFile().mkdirs();
            BufferedWriter writer = new BufferedWriter(new FileWriter(getFile(), true));
            String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(new Date());
            writer.append(timestamp).append(" ").append(msg);
            writer.newLine();
            writer.close();
        } catch (IOException e) {}
    }
    protected static void log(String type, String msg) { log("[" + type + "] " + msg); }

    public static void info(String msg) { log("INFO", msg); }
    public static void error(String msg) { log("ERROR", msg); }
}
