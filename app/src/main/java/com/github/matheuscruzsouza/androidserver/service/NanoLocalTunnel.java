package com.github.matheuscruzsouza.androidserver.service;

import android.util.Log;

import com.github.matheuscruzsouza.androidserver.log.ServerLogBus;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.Socket;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Cliente LocalTunnel nativo em Java puro compatível com Android API 25+ (Nougat).
 * Conecta no broker localtunnel.me e faz ponte TCP (pipe) para a porta HTTP local do nano-spring.
 */
public class NanoLocalTunnel implements AutoCloseable {

    private static final String TAG = "NanoLocalTunnel";
    private static final String LT_HOST = "localtunnel.me";
    private static final int BUFFER_SIZE = 8192;

    private final int localPort;
    private final String subdomain;
    private final ExecutorService executor = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "nano-localtunnel-worker");
        t.setDaemon(true);
        return t;
    });

    private volatile boolean running = false;
    private volatile String publicUrl = null;

    public NanoLocalTunnel(int localPort, String subdomain) {
        this.localPort = localPort;
        this.subdomain = subdomain;
    }

    public String getPublicUrl() {
        return publicUrl;
    }

    public boolean isRunning() {
        return running;
    }

    private static final int MAX_SUBDOMAIN_RETRIES = 3;
    private static final long RETRY_DELAY_MS = 2500L;

    public String start() throws Exception {
        String desiredSubdomain = (subdomain != null && !subdomain.trim().isEmpty())
                ? subdomain.trim().toLowerCase()
                : null;

        String json = null;
        Exception lastException = null;

        if (desiredSubdomain != null) {
            String customEndpoint = "https://" + LT_HOST + "/" + desiredSubdomain;
            for (int attempt = 1; attempt <= MAX_SUBDOMAIN_RETRIES; attempt++) {
                try {
                    String candidateJson = requestTunnelJson(customEndpoint);
                    String assignedId = extractString(candidateJson, "id");
                    if (desiredSubdomain.equalsIgnoreCase(assignedId)) {
                        json = candidateJson;
                        break;
                    } else {
                        throw new IOException("Servidor retornou subdomínio diferente (" + assignedId + ") em vez de '" + desiredSubdomain + "'");
                    }
                } catch (Exception e) {
                    lastException = e;
                    Log.w(TAG, "Tentativa " + attempt + "/" + MAX_SUBDOMAIN_RETRIES + " para subdomínio '" + desiredSubdomain + "' falhou: " + e.getMessage());
                    if (attempt < MAX_SUBDOMAIN_RETRIES) {
                        ServerLogBus.log("[LOCALTUNNEL] Subdomínio '" + desiredSubdomain + "' indisponível (tentativa " + attempt + "/" + MAX_SUBDOMAIN_RETRIES + "). Aguardando...");
                        try {
                            Thread.sleep(RETRY_DELAY_MS * attempt);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            throw ie;
                        }
                    }
                }
            }
        }

        // Fallback para subdomínio aleatório (/?new) caso o subdomínio fixo falhe ou não tenha sido definido
        if (json == null) {
            if (desiredSubdomain != null) {
                ServerLogBus.log("[LOCALTUNNEL] Fallback: solicitando subdomínio aleatório após falha em '" + desiredSubdomain + "'...");
            }
            String randomEndpoint = "https://" + LT_HOST + "/?new";
            for (int attempt = 1; attempt <= MAX_SUBDOMAIN_RETRIES; attempt++) {
                try {
                    json = requestTunnelJson(randomEndpoint);
                    break;
                } catch (Exception e) {
                    lastException = e;
                    if (attempt < MAX_SUBDOMAIN_RETRIES) {
                        try {
                            Thread.sleep(RETRY_DELAY_MS * attempt);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            throw ie;
                        }
                    }
                }
            }
        }

        if (json == null) {
            throw lastException != null ? lastException : new IOException("Não foi possível registrar túnel no LocalTunnel.");
        }

        this.publicUrl = extractString(json, "url");
        int remotePort = Integer.parseInt(extractNumber(json, "port"));
        int maxConn = 10;
        try {
            maxConn = Integer.parseInt(extractNumber(json, "max_conn_count"));
        } catch (Exception ignored) {}

        this.running = true;

        int tunnelThreads = Math.min(Math.max(maxConn, 4), 10);
        for (int i = 0; i < tunnelThreads; i++) {
            executor.submit(() -> maintainTunnelConnection(remotePort));
        }

        String msg = "[LOCALTUNNEL] Túnel público ativo: " + publicUrl + " -> 127.0.0.1:" + localPort;
        Log.i(TAG, msg);
        ServerLogBus.log(msg);
        return publicUrl;
    }

    private String requestTunnelJson(String endpoint) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(endpoint).openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Accept", "application/json");
        conn.setRequestProperty("User-Agent", "S4-AndroidServer/2.4.0");
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(10000);

        int status = conn.getResponseCode();
        InputStream respStream = (status >= 200 && status < 300) ? conn.getInputStream() : conn.getErrorStream();
        String body = readStreamToString(respStream);
        conn.disconnect();

        if (status != 200) {
            throw new IOException("Falha ao registrar túnel no LocalTunnel: HTTP " + status + " - " + body);
        }
        return body;
    }

    private void maintainTunnelConnection(int remotePort) {
        while (running) {
            try (Socket remoteSocket = new Socket(LT_HOST, remotePort)) {
                remoteSocket.setKeepAlive(true);
                remoteSocket.setTcpNoDelay(true);

                InputStream remoteIn = remoteSocket.getInputStream();
                byte[] initialBuf = new byte[BUFFER_SIZE];
                int initialRead = remoteIn.read(initialBuf);
                if (initialRead == -1) {
                    continue;
                }

                try (Socket localSocket = new Socket("127.0.0.1", localPort)) {
                    localSocket.setTcpNoDelay(true);
                    OutputStream localOut = localSocket.getOutputStream();
                    InputStream localIn = localSocket.getInputStream();
                    OutputStream remoteOut = remoteSocket.getOutputStream();

                    localOut.write(initialBuf, 0, initialRead);
                    localOut.flush();

                    Thread upstream = new Thread(() -> pipe(remoteIn, localOut, localSocket, true), "lt-up");
                    Thread downstream = new Thread(() -> pipe(localIn, remoteOut, remoteSocket, true), "lt-down");

                    upstream.start();
                    downstream.start();

                    downstream.join();
                    try { remoteSocket.close(); } catch (IOException ignored) {}
                    try { localSocket.close(); } catch (IOException ignored) {}
                    upstream.join(2000);
                }
            } catch (Exception e) {
                if (running) {
                    try {
                        Thread.sleep(500);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }
    }

    private void pipe(InputStream in, OutputStream out, Socket targetSocket, boolean halfCloseOutput) {
        byte[] buffer = new byte[BUFFER_SIZE];
        int bytesRead;
        try {
            while ((bytesRead = in.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
                out.flush();
            }
        } catch (IOException ignored) {
        } finally {
            if (halfCloseOutput && targetSocket != null && !targetSocket.isClosed()) {
                try {
                    targetSocket.shutdownOutput();
                } catch (IOException ignored) {}
            }
        }
    }

    private String readStreamToString(InputStream in) throws IOException {
        if (in == null) return "";
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
        }
        return sb.toString();
    }

    private String extractString(String json, String key) {
        Matcher m = Pattern.compile("\"" + key + "\"\\s*:\\s*\"([^\"]+)\"").matcher(json);
        if (m.find()) return m.group(1);
        throw new IllegalArgumentException("Campo não encontrado no JSON: " + key);
    }

    private String extractNumber(String json, String key) {
        Matcher m = Pattern.compile("\"" + key + "\"\\s*:\\s*(\\d+)").matcher(json);
        if (m.find()) return m.group(1);
        throw new IllegalArgumentException("Campo numérico não encontrado no JSON: " + key);
    }

    @Override
    public void close() {
        running = false;
        publicUrl = null;
        executor.shutdownNow();
    }
}
