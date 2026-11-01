package com.github.matheuscruzsouza.androidserver.service;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import com.github.matheuscruzsouza.androidserver.MainActivity;
import com.github.matheuscruzsouza.androidserver.config.ServerConfig;
import com.github.matheuscruzsouza.androidserver.log.ServerLogBus;
import com.github.matheuscruzsouza.nanospring.server.Environment;
import com.github.matheuscruzsouza.nanospring.server.Server;
import com.github.matheuscruzsouza.nanospring.service.NanoSpringService;

public class MyBackendService extends NanoSpringService {

    public static final String ACTION_STOP_SERVER = "com.github.matheuscruzsouza.androidserver.ACTION_STOP";
    public static final String ACTION_UPDATE_NOTIFICATION = "com.github.matheuscruzsouza.androidserver.ACTION_UPDATE_NOTIFICATION";
    public static final String EXTRA_CUSTOM_TEXT = "extra_custom_text";

    private static MyBackendService instance;
    private NanoLocalTunnel localTunnel;
    private String customStatusMessage = null;

    private final Handler tickerHandler = new Handler(Looper.getMainLooper());
    private final Runnable tickerRunnable = new Runnable() {
        @Override
        public void run() {
            updateNotification();
            tickerHandler.postDelayed(this, 3000);
        }
    };

    public static MyBackendService getInstance() {
        return instance;
    }

    public NanoLocalTunnel getLocalTunnel() {
        return localTunnel;
    }

    public synchronized void restartLocalTunnel() {
        new Thread(() -> {
            synchronized (MyBackendService.this) {
                if (localTunnel != null) {
                    try {
                        localTunnel.close();
                    } catch (Exception ignored) {}
                    localTunnel = null;
                }

                Context context = getApplicationContext();
                boolean ltEnabled = ServerConfig.isLocalTunnelEnabled(context);
                if (!ltEnabled) {
                    updateNotification();
                    return;
                }

                int configuredPort = ServerConfig.getServerPort(context);
                String subdomain = ServerConfig.getLocalTunnelSubdomain(context);
                try {
                    localTunnel = new NanoLocalTunnel(configuredPort, subdomain);
                    localTunnel.start();
                    updateNotification();
                } catch (Exception e) {
                    Log.w("MyBackendService", "Falha ao iniciar LocalTunnel: " + e.getMessage());
                    ServerLogBus.log("[LOCALTUNNEL] Erro ao iniciar túnel: " + e.getMessage());
                }
            }
        }).start();
    }

    @Override
    public void onCreate() {
        instance = this;
        super.onCreate();
    }

    @Override
    protected int getPort() {
        Context context = getApplicationContext();
        if (context == null) context = this;
        return ServerConfig.getServerPort(context);
    }

    @Override
    protected String getBasePackage() {
        return "com.github.matheuscruzsouza.androidserver";
    }

    private com.github.matheuscruzsouza.androidserver.storage.StorageManager storageManager;
    private com.github.matheuscruzsouza.androidserver.service.IamService iamService;

    @Override
    protected Server createServer() {
        Context context = getApplicationContext();
        if (context == null) context = this;
        Environment.init(context);
        ServerConfig.syncWithEnvironment(context);
        int configuredPort = ServerConfig.getServerPort(context);

        this.storageManager = new com.github.matheuscruzsouza.androidserver.storage.StorageManager(context);
        this.iamService = new com.github.matheuscruzsouza.androidserver.service.IamService(context);

        return new Server(context, configuredPort, getBasePackage()) {
            @Override
            protected fi.iki.elonen.NanoHTTPD.ClientHandler createClientHandler(
                    final java.net.Socket finalAccept,
                    final java.io.InputStream inputStream) {
                return super.createClientHandler(new FastSocketWrapper(finalAccept), inputStream);
            }

            @Override
            public fi.iki.elonen.NanoHTTPD.Response serve(fi.iki.elonen.NanoHTTPD.IHTTPSession session) {
                // Interceptação de requisições S3 (como HEAD Bucket e HEAD Object)
                com.github.matheuscruzsouza.androidserver.s3.S3RequestDispatcher s3Dispatcher =
                        new com.github.matheuscruzsouza.androidserver.s3.S3RequestDispatcher(storageManager, iamService);
                if (s3Dispatcher.isS3Request(session)) {
                    fi.iki.elonen.NanoHTTPD.Response s3Response = s3Dispatcher.dispatch(session);
                    if (s3Response != null) {
                        return s3Response;
                    }
                }

                fi.iki.elonen.NanoHTTPD.Response res = super.serve(session);
                if (res != null && session.getUri() != null && session.getUri().startsWith("/static/")) {
                    res.addHeader("Cache-Control", "public, max-age=3600");
                }
                return res;
            }
        };
    }

    @Override
    protected void onServerStarted(Server startedServer) {
        super.onServerStarted(startedServer);
        ServerLogBus.startServerTimer();

        Context context = getApplicationContext();
        try {
            com.github.matheuscruzsouza.androidserver.model.AppDatabase.getInstance(context).getOpenHelper().getWritableDatabase();
        } catch (Exception ignored) {}

        boolean ltEnabled = ServerConfig.isLocalTunnelEnabled(context);
        if (ltEnabled) {
            int configuredPort = ServerConfig.getServerPort(context);
            String subdomain = ServerConfig.getLocalTunnelSubdomain(context);
            try {
                localTunnel = new NanoLocalTunnel(configuredPort, subdomain);
                localTunnel.start();
            } catch (Exception e) {
                Log.w("MyBackendService", "Falha ao iniciar LocalTunnel: " + e.getMessage());
                ServerLogBus.log("[LOCALTUNNEL] Erro ao iniciar túnel: " + e.getMessage());
            }
        }
        updateNotification();
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

        int result = super.onStartCommand(intent, flags, startId);
        tickerHandler.removeCallbacks(tickerRunnable);
        tickerHandler.postDelayed(tickerRunnable, 3000);
        updateNotification();
        return result;
    }

    public synchronized void updateNotification() {
        android.app.NotificationManager nm = (android.app.NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) {
            nm.notify(getNotificationId(), buildNotification());
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

        Intent openAppIntent = new Intent(this, MainActivity.class);
        PendingIntent pOpenApp = PendingIntent.getActivity(this, 0, openAppIntent, pendingFlags);

        Intent stopIntent = new Intent(this, MyBackendService.class);
        stopIntent.setAction(ACTION_STOP_SERVER);
        PendingIntent pStop = PendingIntent.getService(this, 1, stopIntent, pendingFlags);

        String serverUrl = ServerConfig.getMdnsUrl(this);
        int reqs = ServerLogBus.getRequestCount();
        String uptimeStr = calculateUptime();

        String title = "S4 Server • Online";
        String tunnelUrl = (localTunnel != null && localTunnel.getPublicUrl() != null) ? localTunnel.getPublicUrl() : null;
        String contentText = customStatusMessage != null ? customStatusMessage : "Acesse: " + serverUrl;
        String subText = reqs + " reqs • " + uptimeStr;

        NotificationCompat.BigTextStyle bigStyle = new NotificationCompat.BigTextStyle()
                .setBigContentTitle(title)
                .bigText(
                        "URL: " + serverUrl + "\n" +
                        (tunnelUrl != null ? "Tunnel: " + tunnelUrl + "\n" : "") +
                        "Status: " + (customStatusMessage != null ? customStatusMessage : "Em execução") + "\n" +
                        "Tráfego: " + reqs + " requisições atendidas\n" +
                        "Tempo Ativo: " + uptimeStr
                );

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, getChannelId())
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
        if (localTunnel != null) {
            localTunnel.close();
            localTunnel = null;
        }
        super.onDestroy();
    }
}
