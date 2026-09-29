package com.github.matheuscruzsouza.androidserver.log;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ServerLogBus {

    public interface LogListener {
        void onNewLog(String logEntry);
    }

    private static final List<String> logs = Collections.synchronizedList(new ArrayList<>());
    private static final List<LogListener> listeners = Collections.synchronizedList(new ArrayList<>());
    private static int requestCount = 0;
    private static long serverStartTime = 0;

    public static synchronized void recordRequest() {
        requestCount++;
    }

    public static synchronized int getRequestCount() {
        return requestCount;
    }

    public static synchronized void startServerTimer() {
        serverStartTime = System.currentTimeMillis();
    }

    public static synchronized void stopServerTimer() {
        serverStartTime = 0;
    }

    public static synchronized long getServerStartTime() {
        return serverStartTime;
    }

    public static void log(String message) {
        String timestamp = new java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(new java.util.Date());
        String entry = "[" + timestamp + "] " + message;
        logs.add(entry);
        if (logs.size() > 200) {
            logs.remove(0);
        }

        synchronized (listeners) {
            for (LogListener l : listeners) {
                try {
                    l.onNewLog(entry);
                } catch (Exception ignored) {}
            }
        }
    }

    public static List<String> getAllLogs() {
        return new ArrayList<>(logs);
    }

    public static void clearLogs() {
        logs.clear();
    }

    public static void addListener(LogListener listener) {
        listeners.add(listener);
    }

    public static void removeListener(LogListener listener) {
        listeners.remove(listener);
    }
}
