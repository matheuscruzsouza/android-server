package com.github.matheuscruzsouza.androidserver.controller;

import android.os.Build;

import com.github.matheuscruzsouza.androidserver.NetworkUtils;
import com.github.matheuscruzsouza.androidserver.log.ServerLogBus;
import com.github.matheuscruzsouza.androidserver.model.User;
import com.github.matheuscruzsouza.androidserver.model.UserFile;
import com.github.matheuscruzsouza.androidserver.repository.ServerEventRepository;
import com.github.matheuscruzsouza.androidserver.service.FileService;
import com.github.matheuscruzsouza.androidserver.service.UserService;
import com.github.matheuscruzsouza.nanospring.annotation.Autowired;
import com.github.matheuscruzsouza.nanospring.annotation.GetMethod;
import com.github.matheuscruzsouza.nanospring.annotation.RestController;
import com.github.matheuscruzsouza.nanospring.cors.CorsConfiguration;
import com.github.matheuscruzsouza.nanospring.discovery.NsdServiceManager;
import com.github.matheuscruzsouza.nanospring.server.Environment;
import com.github.matheuscruzsouza.nanospring.sse.SseEmitter;
import com.github.matheuscruzsouza.nanospring.ui.ModelAndView;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController("")
public class IndexController {

    @Autowired
    private UserService userService;

    @Autowired
    private FileService fileService;

    @Autowired
    private ServerEventRepository eventRepository;

    @Autowired
    private NsdServiceManager nsdServiceManager;

    @Autowired
    private CorsConfiguration corsConfiguration;

    private ServerEventRepository getEventRepository() {
        if (eventRepository == null) {
            eventRepository = new ServerEventRepository();
        }
        return eventRepository;
    }

    private UserService getUserService() {
        if (userService == null) {
            userService = new UserService();
        }
        return userService;
    }

    private FileService getFileService() {
        if (fileService == null) {
            fileService = new FileService();
        }
        return fileService;
    }

    @GetMethod("/")
    public ModelAndView getRoot() {
        return buildIndexModel();
    }

    @GetMethod("/web")
    public ModelAndView getWeb() {
        return buildIndexModel();
    }

    private ModelAndView buildIndexModel() {
        String ip = NetworkUtils.getLocalIpAddress();
        String port = Environment.getProperty("server.port", "9090");
        String serverUrl = "http://" + ip + ":" + port;

        List<User> users = getUserService().getUsers();
        if (users == null) users = Collections.emptyList();
        int userCount = users.size();

        List<UserFile> files = getFileService().getAllFiles();
        if (files == null) files = Collections.emptyList();
        int fileCount = files.size();

        long startTime = ServerLogBus.getServerStartTime();
        String uptime = "00:00:00";
        if (startTime > 0) {
            long diff = (System.currentTimeMillis() - startTime) / 1000;
            long hours = diff / 3600;
            long minutes = (diff % 3600) / 60;
            long seconds = diff % 60;
            uptime = String.format(java.util.Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds);
        }

        Runtime rt = Runtime.getRuntime();
        long totalMemory = rt.totalMemory() / (1024 * 1024);
        long freeMemory = rt.freeMemory() / (1024 * 1024);
        long usedMemory = totalMemory - freeMemory;
        long maxMemory = rt.maxMemory() / (1024 * 1024);

        boolean nsdRegistered = nsdServiceManager != null && nsdServiceManager.isRegistered();
        String nsdName = (nsdServiceManager != null && nsdServiceManager.getRegisteredServiceName() != null)
                ? nsdServiceManager.getRegisteredServiceName()
                : Environment.getProperty("nano.nsd.name", "AndroidServer");
        boolean hostResponderRunning = nsdServiceManager != null
                && nsdServiceManager.getHostResponder() != null
                && nsdServiceManager.getHostResponder().isRunning();
        String nsdHostName = (nsdServiceManager != null && nsdServiceManager.getHostResponder() != null)
                ? nsdServiceManager.getHostResponder().getFullLocalHostname()
                : (nsdName.toLowerCase() + ".local");

        boolean corsActive = corsConfiguration != null ? corsConfiguration.isEnabled() : Boolean.parseBoolean(Environment.getProperty("nano.cors.enabled", "true"));

        return new ModelAndView("index")
                .addObject("appName", "AndroidServer")
                .addObject("frameworkName", "nano-spring")
                .addObject("frameworkVersion", "1.6.0")
                .addObject("deviceModel", Build.MANUFACTURER + " " + Build.MODEL)
                .addObject("androidVersion", Build.VERSION.RELEASE)
                .addObject("sdkInt", String.valueOf(Build.VERSION.SDK_INT))
                .addObject("ipAddress", ip)
                .addObject("port", port)
                .addObject("serverUrl", serverUrl)
                .addObject("uptime", uptime)
                .addObject("requestCount", String.valueOf(ServerLogBus.getRequestCount()))
                .addObject("userCount", String.valueOf(userCount))
                .addObject("fileCount", String.valueOf(fileCount))
                .addObject("usedMemory", String.valueOf(usedMemory))
                .addObject("totalMemory", String.valueOf(totalMemory))
                .addObject("maxMemory", String.valueOf(maxMemory))
                .addObject("nsdEnabled", nsdRegistered || hostResponderRunning)
                .addObject("nsdName", nsdName)
                .addObject("nsdHostName", nsdHostName)
                .addObject("hostResponderRunning", hostResponderRunning)
                .addObject("corsEnabled", corsActive)
                .addObject("hasUsers", userCount > 0)
                .addObject("hasFiles", fileCount > 0)
                .addObject("users", users)
                .addObject("files", files);
    }

    @GetMethod("/api/config/cors")
    public Map<String, Object> getCorsConfig() {
        Map<String, Object> cors = new HashMap<>();
        if (corsConfiguration != null) {
            cors.put("enabled", corsConfiguration.isEnabled());
            cors.put("allowedOrigins", corsConfiguration.getAllowedOrigins());
            cors.put("allowedMethods", corsConfiguration.getAllowedMethods());
            cors.put("allowedHeaders", corsConfiguration.getAllowedHeaders());
            cors.put("maxAge", corsConfiguration.getMaxAge());
        } else {
            cors.put("enabled", Boolean.parseBoolean(Environment.getProperty("nano.cors.enabled", "true")));
            cors.put("allowedOrigins", Environment.getProperty("nano.cors.allowed-origins", "*"));
            cors.put("allowedMethods", Environment.getProperty("nano.cors.allowed-methods", "GET,POST,PUT,DELETE,OPTIONS"));
            cors.put("allowedHeaders", Environment.getProperty("nano.cors.allowed-headers", "Content-Type,Authorization,X-Requested-With,Accept"));
            cors.put("maxAge", Environment.getProperty("nano.cors.max-age", "86400"));
        }
        return cors;
    }

    @GetMethod("/api/discovery/nsd")
    public Map<String, Object> getNsdStatus() {
        Map<String, Object> status = new HashMap<>();
        boolean registered = nsdServiceManager != null && nsdServiceManager.isRegistered();
        String serviceName = (nsdServiceManager != null && nsdServiceManager.getRegisteredServiceName() != null)
                ? nsdServiceManager.getRegisteredServiceName()
                : Environment.getProperty("nano.nsd.name", "AndroidServer");
        boolean hostResponderRunning = nsdServiceManager != null
                && nsdServiceManager.getHostResponder() != null
                && nsdServiceManager.getHostResponder().isRunning();
        String localHostname = (nsdServiceManager != null && nsdServiceManager.getHostResponder() != null)
                ? nsdServiceManager.getHostResponder().getFullLocalHostname()
                : (serviceName.toLowerCase() + ".local");
        String port = Environment.getProperty("server.port", "9090");

        status.put("enabled", Boolean.parseBoolean(Environment.getProperty("nano.nsd.enabled", "false")));
        status.put("serviceName", serviceName);
        status.put("serviceType", Environment.getProperty("nano.nsd.type", "_http._tcp."));
        status.put("port", Integer.parseInt(port));
        status.put("registered", registered);
        status.put("protocol", "DNS-SD / mDNS (Zeroconf)");
        status.put("hostResolutionEnabled", Boolean.parseBoolean(Environment.getProperty("nano.nsd.host-resolution", "true")));
        status.put("hostResponderRunning", hostResponderRunning);
        status.put("hostname", localHostname);
        status.put("browserUrl", "http://" + localHostname + ":" + port + "/");
        return status;
    }

    @GetMethod("/api/database/migrations")
    public List<Map<String, Object>> getMigrations() {
        return getEventRepository().getMigrationsHistory();
    }

    @GetMethod("/api/database/events")
    public List<Map<String, String>> getDatabaseEvents() {
        return getEventRepository().getEvents();
    }

    @GetMethod("/api/events")
    public SseEmitter streamEvents() {
        SseEmitter emitter = new SseEmitter();
        new Thread(() -> {
            try {
                // Stream metrics up to 45 updates (90 seconds)
                for (int i = 0; i < 45; i++) {
                    long startTime = ServerLogBus.getServerStartTime();
                    String uptime = "00:00:00";
                    if (startTime > 0) {
                        long diff = (System.currentTimeMillis() - startTime) / 1000;
                        long hours = diff / 3600;
                        long minutes = (diff % 3600) / 60;
                        long seconds = diff % 60;
                        uptime = String.format(java.util.Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds);
                    }
                    Runtime rt = Runtime.getRuntime();
                    long usedMemory = (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024);
                    long totalMemory = rt.totalMemory() / (1024 * 1024);

                    Map<String, Object> stats = new HashMap<>();
                    stats.put("uptime", uptime);
                    stats.put("requests", ServerLogBus.getRequestCount());
                    stats.put("usedMemoryMb", usedMemory);
                    stats.put("totalMemoryMb", totalMemory);
                    stats.put("timestamp", System.currentTimeMillis());

                    emitter.send("metrics", stats);
                    Thread.sleep(2000);
                }
                emitter.complete();
            } catch (Exception e) {
                try {
                    emitter.completeWithError(e);
                } catch (Exception ignored) {}
            }
        }).start();
        return emitter;
    }
}
