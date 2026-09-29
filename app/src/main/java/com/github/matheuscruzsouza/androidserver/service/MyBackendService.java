package com.github.matheuscruzsouza.androidserver.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.github.matheuscruzsouza.androidserver.MainActivity;
import com.github.matheuscruzsouza.androidserver.NetworkUtils;
import com.github.matheuscruzsouza.androidserver.R;
import com.github.matheuscruzsouza.androidserver.log.ServerLogBus;
import com.github.matheuscruzsouza.nanospring.server.Environment;
import com.github.matheuscruzsouza.nanospring.server.Server;

public class MyBackendService extends Service {

    public static final String ACTION_STOP_SERVER = "com.github.matheuscruzsouza.androidserver.ACTION_STOP";
    public static final String ACTION_UPDATE_NOTIFICATION = "com.github.matheuscruzsouza.androidserver.ACTION_UPDATE_NOTIFICATION";
    public static final String EXTRA_CUSTOM_TEXT = "extra_custom_text";

    private static final String CHANNEL_ID = "android_server_channel";
    private static final int NOTIFICATION_ID = 1;
    private static final int PORT = 8080;

    private static MyBackendService instance;
    private Server server;
    private NotificationManager notificationManager;
    private String customStatusMessage = null;

    private final Handler tickerHandler = new Handler(Looper.getMainLooper());
    private final Runnable tickerRunnable = new Runnable() {
        @Override
        public void run() {
            updateNotification();
            tickerHandler.postDelayed(this, 3000); // Atualiza métricas na notificação a cada 3s
        }
    };

    public static MyBackendService getInstance() {
        return instance;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            String action = intent.getAction();
            if (ACTION_STOP_SERVER.equals(action)) {
                stopSelf();
                return START_NOT_STICKY;
            } else if (ACTION_UPDATE_NOTIFICATION.equals(action)) {
                if (intent.hasExtra(EXTRA_CUSTOM_TEXT)) {
                    this.customStatusMessage = intent.getStringExtra(EXTRA_CUSTOM_TEXT);
                    updateNotification();
                }
                return START_STICKY;
            }
        }

        startForeground(NOTIFICATION_ID, buildNotification());
        tickerHandler.removeCallbacks(tickerRunnable);
        tickerHandler.postDelayed(tickerRunnable, 3000);

        if (server == null) {
            new Thread(() -> {
                Context context = getApplicationContext();
                server = new Server(context, PORT, "com.github.matheuscruzsouza.androidserver");
                ServerLogBus.startServerTimer();
            }).start();
        }

        return START_STICKY;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Servidor Android Ativo",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Notificação de status contínuo do servidor HTTP");
            channel.setShowBadge(false);
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(channel);
            }
        }
    }

    public synchronized void updateNotification() {
        if (notificationManager != null) {
            notificationManager.notify(NOTIFICATION_ID, buildNotification());
        }
    }

    public void setCustomStatusMessage(String message) {
        this.customStatusMessage = message;
        updateNotification();
    }

    private Notification buildNotification() {
        int pendingFlags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            pendingFlags |= PendingIntent.FLAG_IMMUTABLE;
        }

        // 1. Clique para abrir a tela principal
        Intent openAppIntent = new Intent(this, MainActivity.class);
        PendingIntent pOpenApp = PendingIntent.getActivity(this, 0, openAppIntent, pendingFlags);

        // 2. Ação de Parar Servidor direto da notificação
        Intent stopIntent = new Intent(this, MyBackendService.class);
        stopIntent.setAction(ACTION_STOP_SERVER);
        PendingIntent pStop = PendingIntent.getService(this, 1, stopIntent, pendingFlags);

        String ip = NetworkUtils.getLocalIpAddress();
        String port = Environment.getProperty("server.port", String.valueOf(PORT));
        String serverUrl = "http://" + ip + ":" + port;

        int reqs = ServerLogBus.getRequestCount();
        String uptimeStr = calculateUptime();

        String title = "Nano-Spring Server • Online";
        String contentText = customStatusMessage != null ? customStatusMessage : "Acesse: " + serverUrl;
        String subText = reqs + " reqs • " + uptimeStr;

        NotificationCompat.BigTextStyle bigStyle = new NotificationCompat.BigTextStyle()
                .setBigContentTitle(title)
                .bigText(
                        "URL: " + serverUrl + "\n" +
                        "Status: " + (customStatusMessage != null ? customStatusMessage : "Em execução") + "\n" +
                        "Tráfego: " + reqs + " requisições atendidas\n" +
                        "Tempo Ativo: " + uptimeStr
                );

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_notify_sync)
                .setContentTitle(title)
                .setContentText(contentText)
                .setSubText(subText)
                .setContentIntent(pOpenApp)
                .setStyle(bigStyle)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Desligar", pStop);

        return builder.build();
    }

    private String calculateUptime() {
        long startTime = ServerLogBus.getServerStartTime();
        if (startTime <= 0) return "00:00:00";
        long diff = (System.currentTimeMillis() - startTime) / 1000;
        long hours = diff / 3600;
        long minutes = (diff % 3600) / 60;
        long seconds = diff % 60;
        return String.format(java.util.Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds);
    }

    @Override
    public void onDestroy() {
        instance = null;
        tickerHandler.removeCallbacks(tickerRunnable);
        ServerLogBus.stopServerTimer();
        if (server != null) {
            server.stop();
            server = null;
        }
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
