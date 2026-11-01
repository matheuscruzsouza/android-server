package com.github.matheuscruzsouza.androidserver.controller;

import android.os.Build;

import com.github.matheuscruzsouza.androidserver.NetworkUtils;
import com.github.matheuscruzsouza.androidserver.log.ServerLogBus;
import com.github.matheuscruzsouza.androidserver.model.IamUser;
import com.github.matheuscruzsouza.androidserver.model.S3ObjectItem;
import com.github.matheuscruzsouza.androidserver.model.User;
import com.github.matheuscruzsouza.androidserver.model.UserFile;
import com.github.matheuscruzsouza.androidserver.repository.ServerEventRepository;
import com.github.matheuscruzsouza.androidserver.service.FileService;
import com.github.matheuscruzsouza.androidserver.service.IamService;
import com.github.matheuscruzsouza.androidserver.service.UserService;
import com.github.matheuscruzsouza.androidserver.storage.FileUtils;
import com.github.matheuscruzsouza.androidserver.storage.StorageManager;
import com.github.matheuscruzsouza.nanospring.annotation.Autowired;
import com.github.matheuscruzsouza.nanospring.annotation.DeleteMethod;
import com.github.matheuscruzsouza.nanospring.annotation.GetMethod;
import com.github.matheuscruzsouza.nanospring.annotation.PathVariable;
import com.github.matheuscruzsouza.nanospring.annotation.PostMethod;
import com.github.matheuscruzsouza.nanospring.annotation.RequestParam;
import com.github.matheuscruzsouza.nanospring.annotation.RestController;
import com.github.matheuscruzsouza.nanospring.annotation.UploadedFile;
import com.github.matheuscruzsouza.nanospring.openapi.annotation.Hidden;
import com.github.matheuscruzsouza.nanospring.battery.BatteryHelper;
import com.github.matheuscruzsouza.nanospring.battery.BatteryStatus;
import com.github.matheuscruzsouza.nanospring.cors.CorsConfiguration;
import com.github.matheuscruzsouza.nanospring.discovery.NsdServiceManager;
import com.github.matheuscruzsouza.nanospring.server.Environment;
import com.github.matheuscruzsouza.nanospring.server.Server;
import com.github.matheuscruzsouza.nanospring.sse.SseEmitter;
import com.github.matheuscruzsouza.nanospring.storage.ByteRangeHandler;
import com.github.matheuscruzsouza.nanospring.ui.ModelAndView;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLDecoder;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import fi.iki.elonen.NanoHTTPD.IHTTPSession;
import fi.iki.elonen.NanoHTTPD.Response;

@Hidden
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

    @Autowired
    private IamService iamService;

    @Autowired
    private StorageManager storageManager;

    private static final List<SseEmitter> activeEmitters = new CopyOnWriteArrayList<>();
    private static final AtomicBoolean sseBroadcasterStarted = new AtomicBoolean(false);
    private static final ScheduledExecutorService sseExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "sse-broadcaster-thread");
        t.setDaemon(true);
        return t;
    });

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

    private IamService getIamService() {
        if (iamService == null) {
            iamService = new IamService();
        }
        return iamService;
    }

    private StorageManager getStorageManager() {
        if (storageManager == null) {
            if (Server.getContext() != null) {
                storageManager = new StorageManager(Server.getContext());
            }
        }
        return storageManager;
    }

    private BatteryStatus getBatteryStatus() {
        if (Server.getContext() != null) {
            try {
                return BatteryHelper.getBatteryStatus(Server.getContext());
            } catch (Exception ignored) {}
        }
        return new BatteryStatus(100, 100, 100, true, true, false, 28.0, 2, false, System.currentTimeMillis());
    }

    private IamUser resolveAuthenticatedUser(IHTTPSession session) {
        return getIamService().authenticate(session);
    }

    private String formatBytes(long bytes) {
        if (bytes <= 0) return "0 B";
        final String[] units = new String[]{"B", "KB", "MB", "GB", "TB"};
        int digitGroups = (int) (Math.log10(bytes) / Math.log10(1024));
        if (digitGroups >= units.length) digitGroups = units.length - 1;
        return String.format(Locale.US, "%.1f %s", bytes / Math.pow(1024, digitGroups), units[digitGroups]);
    }

    private String getFileExt(String filename) {
        if (filename == null) return "FILE";
        int dot = filename.lastIndexOf('.');
        if (dot >= 0 && dot < filename.length() - 1) {
            return filename.substring(dot + 1).toUpperCase();
        }
        return "FILE";
    }

    private String getFileColorClass(String filename) {
        if (filename == null) return "color-default";
        String lower = filename.toLowerCase();
        if (lower.endsWith(".sketch") || lower.endsWith(".fig") || lower.endsWith(".xd") || lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".svg")) {
            return "color-amber";
        } else if (lower.endsWith(".doc") || lower.endsWith(".docx") || lower.endsWith(".pdf") || lower.endsWith(".txt") || lower.endsWith(".md")) {
            return "color-blue";
        } else if (lower.endsWith(".xls") || lower.endsWith(".xlsx") || lower.endsWith(".csv") || lower.endsWith(".sheet")) {
            return "color-green";
        } else if (lower.endsWith(".zip") || lower.endsWith(".tar") || lower.endsWith(".gz") || lower.endsWith(".rar") || lower.endsWith(".7z") || lower.endsWith(".apk")) {
            return "color-purple";
        } else if (lower.endsWith(".json") || lower.endsWith(".xml") || lower.endsWith(".js") || lower.endsWith(".html") || lower.endsWith(".css") || lower.endsWith(".java")) {
            return "color-rose";
        }
        return "color-default";
    }

    @GetMethod("/")
    public ModelAndView getRoot(IHTTPSession session) {
        return buildIndexModel(session);
    }

    @GetMethod("/web")
    public ModelAndView getWeb(IHTTPSession session) {
        return buildIndexModel(session);
    }

    private ModelAndView buildIndexModel(IHTTPSession session) {
        String port = Environment.getProperty("server.port", "9090");
        String scheme = Boolean.parseBoolean(Environment.getProperty("server.ssl.enabled", "false")) ? "https" : "http";
        String nsdHost = (nsdServiceManager != null && nsdServiceManager.getHostResponder() != null)
                ? nsdServiceManager.getHostResponder().getFullLocalHostname()
                : (Environment.getProperty("nano.nsd.name", "s4").toLowerCase() + ".local");
        String serverUrl = scheme + "://" + nsdHost + ":" + port;

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
            uptime = String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds);
        }

        Runtime rt = Runtime.getRuntime();
        long totalMemory = rt.totalMemory() / (1024 * 1024);
        long freeMemory = rt.freeMemory() / (1024 * 1024);
        long usedMemory = totalMemory - freeMemory;
        long maxMemory = rt.maxMemory() / (1024 * 1024);

        boolean nsdRegistered = nsdServiceManager != null && nsdServiceManager.isRegistered();
        String nsdName = (nsdServiceManager != null && nsdServiceManager.getRegisteredServiceName() != null)
                ? nsdServiceManager.getRegisteredServiceName()
                : Environment.getProperty("nano.nsd.name", "s4");
        boolean hostResponderRunning = nsdServiceManager != null
                && nsdServiceManager.getHostResponder() != null
                && nsdServiceManager.getHostResponder().isRunning();
        String nsdHostName = (nsdServiceManager != null && nsdServiceManager.getHostResponder() != null)
                ? nsdServiceManager.getHostResponder().getFullLocalHostname()
                : (nsdName.toLowerCase() + ".local");

        boolean corsActive = corsConfiguration != null ? corsConfiguration.isEnabled() : Boolean.parseBoolean(Environment.getProperty("nano.cors.enabled", "true"));

        BatteryStatus battery = getBatteryStatus();
        int batteryLevel = battery.getPercentage();
        boolean isCharging = battery.isCharging();

        IamUser authUser = resolveAuthenticatedUser(session);
        boolean isAuthenticated = (authUser != null);
        String authUsername = isAuthenticated ? authUser.getName() : "Convidado";
        String authPolicy = isAuthenticated ? authUser.getPolicy() : "Visitante";
        String authAccessKey = isAuthenticated ? authUser.getAccessKey() : "";
        String userInitial = (authUser != null && authUser.getName() != null && !authUser.getName().isEmpty())
                ? authUser.getName().substring(0, 1).toUpperCase() : "A";

        // Storage statistics & folder list (somente carregado se autenticado)
        StorageManager sm = getStorageManager();
        long storageTotalBytes = 0;
        int storageFileCount = 0;
        List<Map<String, Object>> folderList = new ArrayList<>();

        if (isAuthenticated && sm != null && sm.getRootDir() != null && sm.getRootDir().exists()) {
            List<File> buckets = sm.listBucketsForUser(authUser);
            if (buckets != null) {
                for (File b : buckets) {
                    List<S3ObjectItem> objs = sm.listObjectsForUser(authUser, b.getName());
                    int count = objs != null ? objs.size() : 0;
                    Map<String, Object> folderMap = new HashMap<>();
                    folderMap.put("name", b.getName());
                    folderMap.put("count", count);
                    folderList.add(folderMap);
                }
            }
            List<S3ObjectItem> objs = sm.listObjectsForUser(authUser, "");
            storageFileCount = objs != null ? objs.size() : 0;
            if (objs != null) {
                for (S3ObjectItem item : objs) {
                    storageTotalBytes += item.getSize();
                }
            }
        }

        // Ensure default folder exists if empty and authenticated
        if (isAuthenticated && folderList.isEmpty() && sm != null) {
            Map<String, Object> defFolder = new HashMap<>();
            defFolder.put("name", sm.getUserDefaultBucket(authUser));
            defFolder.put("count", storageFileCount);
            folderList.add(defFolder);
        }

        List<Map<String, Object>> sidebarFolders = isAuthenticated
                ? buildSidebarFolders(authUser, "", "")
                : Collections.emptyList();

        return new ModelAndView("index")
                .addObject("appName", "S4")
                .addObject("frameworkName", "nano-spring")
                .addObject("frameworkVersion", "2.2.0")
                .addObject("deviceModel", Build.MANUFACTURER + " " + Build.MODEL)
                .addObject("androidVersion", Build.VERSION.RELEASE)
                .addObject("sdkInt", String.valueOf(Build.VERSION.SDK_INT))
                .addObject("ipAddress", nsdHost)
                .addObject("port", port)
                .addObject("serverUrl", serverUrl)
                .addObject("uptime", uptime)
                .addObject("requestCount", String.valueOf(ServerLogBus.getRequestCount()))
                .addObject("userCount", String.valueOf(userCount))
                .addObject("fileCount", String.valueOf(storageFileCount > 0 ? storageFileCount : fileCount))
                .addObject("storageTotalSize", formatBytes(storageTotalBytes))
                .addObject("usedMemory", String.valueOf(usedMemory))
                .addObject("totalMemory", String.valueOf(totalMemory))
                .addObject("maxMemory", String.valueOf(maxMemory))
                .addObject("nsdEnabled", nsdRegistered || hostResponderRunning)
                .addObject("nsdName", nsdName)
                .addObject("nsdHostName", nsdHostName)
                .addObject("scheme", scheme)
                .addObject("hostResponderRunning", hostResponderRunning)
                .addObject("corsEnabled", corsActive)
                .addObject("hasUsers", isAuthenticated && userCount > 0)
                .addObject("hasFiles", isAuthenticated && fileCount > 0)
                .addObject("users", isAuthenticated ? users : Collections.emptyList())
                .addObject("files", isAuthenticated ? files : Collections.emptyList())
                .addObject("folders", folderList)
                .addObject("sidebarFolders", sidebarFolders)
                .addObject("batteryLevel", String.valueOf(batteryLevel))
                .addObject("isCharging", isCharging)
                .addObject("batteryTemp", String.format(Locale.US, "%.1f", battery.getTemperatureCelsius()))
                .addObject("batteryIcon", isCharging ? "⚡" : (batteryLevel > 20 ? "🔋" : "🪫"))
                .addObject("isAuthenticated", isAuthenticated)
                .addObject("authUsername", authUsername)
                .addObject("authPolicy", authPolicy)
                .addObject("authAccessKey", authAccessKey)
                .addObject("userInitial", userInitial);
    }

    private List<Map<String, Object>> buildSidebarFolders(IamUser user, String activeBucket, String activePath) {
        List<Map<String, Object>> folders = new ArrayList<>();
        StorageManager sm = getStorageManager();
        if (sm == null || user == null) return folders;

        boolean isAdmin = "Admin".equalsIgnoreCase(user.getPolicy()) || "*".equals(user.getPolicy());
        String defaultBucket = sm.getUserDefaultBucket(user);

        List<File> bucketDirs = sm.listBucketsForUser(user);
        if (bucketDirs == null || bucketDirs.isEmpty()) {
            File def = sm.getBucketDir(defaultBucket);
            bucketDirs = Collections.singletonList(def);
        }

        // Filtra para garantir que apenas diretórios raiz de buckets únicos sejam iterados
        Map<String, File> uniqueBuckets = new HashMap<>();
        for (File b : bucketDirs) {
            if (b.getParentFile() != null && b.getParentFile().equals(sm.getRootDir())) {
                uniqueBuckets.putIfAbsent(b.getName(), b);
            } else if (!isAdmin && b.getName().equals(defaultBucket)) {
                uniqueBuckets.putIfAbsent(b.getName(), b);
            }
        }
        if (uniqueBuckets.isEmpty()) {
            uniqueBuckets.put(defaultBucket, sm.getBucketDir(defaultBucket));
        }

        List<File> sortedBuckets = new ArrayList<>(uniqueBuckets.values());
        sortedBuckets.sort((f1, f2) -> f1.getName().compareToIgnoreCase(f2.getName()));

        for (File b : sortedBuckets) {
            String bName = b.getName();
            boolean isCurBucket = (activeBucket != null && !activeBucket.isEmpty())
                    ? bName.equalsIgnoreCase(activeBucket)
                    : bName.equalsIgnoreCase(defaultBucket);

            Map<String, Object> rootFolder = new HashMap<>();
            rootFolder.put("bucket", bName);
            rootFolder.put("path", "");
            rootFolder.put("displayName", bName.toUpperCase(Locale.ROOT));
            rootFolder.put("isRoot", true);
            rootFolder.put("paddingLeft", 10);
            rootFolder.put("isActive", isCurBucket && (activePath == null || activePath.isEmpty()));
            folders.add(rootFolder);

            // Subpastas diretas do bucket com indentação visual via padding
            File[] subs = b.listFiles(File::isDirectory);
            if (subs != null) {
                List<File> subList = new ArrayList<>();
                for (File s : subs) subList.add(s);
                subList.sort((f1, f2) -> f1.getName().compareToIgnoreCase(f2.getName()));

                for (File s : subList) {
                    Map<String, Object> subMap = new HashMap<>();
                    subMap.put("bucket", bName);
                    subMap.put("path", s.getName());
                    subMap.put("displayName", s.getName());
                    subMap.put("isRoot", false);
                    subMap.put("paddingLeft", 26);
                    subMap.put("isActive", isCurBucket && s.getName().equals(activePath));
                    folders.add(subMap);
                }
            }
        }
        return folders;
    }

    // HTMX Fragment: Renders sidebar folders list
    @GetMethod("/web/fragments/sidebar-folders")
    public ModelAndView getSidebarFoldersFragment(IHTTPSession session) {
        IamUser user = resolveAuthenticatedUser(session);
        String bucketFilter = session.getParms() != null ? session.getParms().get("bucket") : null;
        String pathFilter = session.getParms() != null ? session.getParms().get("path") : null;
        List<Map<String, Object>> folders = buildSidebarFolders(user, bucketFilter, pathFilter);

        return new ModelAndView("fragments/sidebar_folders")
                .addObject("folders", folders);
    }

    @GetMethod("/web/fragments/files")
    public ModelAndView getFilesFragment(IHTTPSession session) {
        String bucketFilter = session.getParms() != null ? session.getParms().get("bucket") : null;
        if (bucketFilter == null) bucketFilter = "";
        String pathFilter = session.getParms() != null ? session.getParms().get("path") : null;
        if (pathFilter == null) pathFilter = "";
        while (pathFilter.startsWith("/")) pathFilter = pathFilter.substring(1);
        while (pathFilter.endsWith("/")) pathFilter = pathFilter.substring(0, pathFilter.length() - 1);

        ModelAndView mav = new ModelAndView("fragments/files");

        IamUser user = resolveAuthenticatedUser(session);
        if (user == null) {
            return mav.addObject("restricted", true)
                    .addObject("unavailable", false)
                    .addObject("dirs", Collections.emptyList())
                    .addObject("files", Collections.emptyList())
                    .addObject("breadcrumbs", Collections.emptyList());
        }

        StorageManager sm = getStorageManager();
        if (sm == null) {
            return mav.addObject("restricted", false)
                    .addObject("unavailable", true)
                    .addObject("dirs", Collections.emptyList())
                    .addObject("files", Collections.emptyList())
                    .addObject("breadcrumbs", Collections.emptyList());
        }

        boolean isAdmin = "Admin".equalsIgnoreCase(user.getPolicy()) || "*".equals(user.getPolicy());
        String userDefaultBucket = sm.getUserDefaultBucket(user);
        String activeBucket = (!isAdmin || (bucketFilter.isEmpty() && pathFilter.isEmpty())) ? userDefaultBucket : bucketFilter;
        if (isAdmin && !bucketFilter.isEmpty()) {
            activeBucket = bucketFilter;
        }

        File[] directChildren = sm.listDirectChildren(user, activeBucket, pathFilter);

        List<Map<String, Object>> breadcrumbList = new ArrayList<>();
        if (!pathFilter.isEmpty()) {
            String[] parts = pathFilter.split("/");
            StringBuilder accumulated = new StringBuilder();
            for (String part : parts) {
                if (accumulated.length() > 0) accumulated.append("/");
                accumulated.append(part);
                Map<String, Object> crumb = new HashMap<>();
                crumb.put("name", part);
                crumb.put("accumulatedPath", accumulated.toString());
                breadcrumbList.add(crumb);
            }
        }

        boolean hasParent = !pathFilter.isEmpty();
        String parentPath = "";
        if (hasParent) {
            int lastSlash = pathFilter.lastIndexOf('/');
            parentPath = lastSlash > 0 ? pathFilter.substring(0, lastSlash) : "";
        }

        List<File> dirList = new ArrayList<>();
        List<File> fileList = new ArrayList<>();
        if (directChildren != null) {
            for (File f : directChildren) {
                if (f.isDirectory()) {
                    dirList.add(f);
                } else if (f.isFile()) {
                    fileList.add(f);
                }
            }
        }

        dirList.sort((a, b) -> a.getName().compareToIgnoreCase(b.getName()));
        fileList.sort((a, b) -> a.getName().compareToIgnoreCase(b.getName()));

        List<Map<String, Object>> renderedDirs = new ArrayList<>();
        for (File dir : dirList) {
            String subChildPath = pathFilter.isEmpty() ? dir.getName() : (pathFilter + "/" + dir.getName());
            Map<String, Object> dirMap = new HashMap<>();
            dirMap.put("name", dir.getName());
            dirMap.put("bucket", activeBucket);
            dirMap.put("path", subChildPath);
            renderedDirs.add(dirMap);
        }

        List<Map<String, Object>> renderedFiles = new ArrayList<>();
        for (File file : fileList) {
            String fullKey = pathFilter.isEmpty() ? file.getName() : (pathFilter + "/" + file.getName());
            String formattedSize = formatBytes(file.length());
            String name = file.getName();
            String lower = name.toLowerCase(Locale.ROOT);

            String ext = "";
            int lastDot = name.lastIndexOf('.');
            if (lastDot > 0 && lastDot < name.length() - 1) {
                ext = name.substring(lastDot + 1).toUpperCase(Locale.ROOT);
                if (ext.length() > 4) {
                    ext = ext.substring(0, 4);
                }
            } else {
                ext = "FILE";
            }

            // Colors following the reference sheet
            String badgeBg = "#6B7280";
            if (lower.endsWith(".pdf")) {
                badgeBg = "#E53E3E"; // Vermelho PDF
            } else if (lower.endsWith(".doc") || lower.endsWith(".docx")) {
                badgeBg = "#2B6CB0"; // Azul DOC/DOCX
            } else if (lower.endsWith(".xls") || lower.endsWith(".xlsx") || lower.endsWith(".csv")) {
                badgeBg = "#2F855A"; // Verde XLS/XLSX
            } else if (lower.endsWith(".ppt") || lower.endsWith(".pptx") || lower.endsWith(".key")) {
                badgeBg = "#DD6B20"; // Laranja PPT
            } else if (lower.endsWith(".htm") || lower.endsWith(".html")) {
                badgeBg = "#48BB78"; // Verde HTM
            } else if (lower.endsWith(".css")) {
                badgeBg = "#ECC94B"; // Amarelo/Dourado CSS
            } else if (lower.endsWith(".js") || lower.endsWith(".json")) {
                badgeBg = "#805AD5"; // Roxo/Bordô JS
            } else if (lower.endsWith(".xml")) {
                badgeBg = "#B7791F"; // Caqui/Bege XML
            } else if (lower.endsWith(".php")) {
                badgeBg = "#2C5282"; // Azul Escuro PHP
            } else if (lower.endsWith(".txt") || lower.endsWith(".md") || lower.endsWith(".log")) {
                badgeBg = "#718096"; // Cinza Escuro TXT
            } else if (lower.endsWith(".zip") || lower.endsWith(".rar") || lower.endsWith(".7z") || lower.endsWith(".tar") || lower.endsWith(".gz") || lower.endsWith(".apk")) {
                badgeBg = "#D69E2E"; // Amarelo escuro ZIP
            } else if (lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".gif") || lower.endsWith(".webp") || lower.endsWith(".svg")) {
                badgeBg = "#319795"; // Ciano/Teal Imagem
            }

            boolean isTable = lower.endsWith(".xls") || lower.endsWith(".xlsx") || lower.endsWith(".csv");
            boolean isChart = lower.endsWith(".ppt") || lower.endsWith(".pptx") || lower.endsWith(".key");
            boolean isImage = lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".gif") || lower.endsWith(".webp") || lower.endsWith(".svg");
            boolean isStandard = !isTable && !isChart && !isImage;

            Map<String, Object> fileMap = new HashMap<>();
            fileMap.put("name", name);
            fileMap.put("bucket", activeBucket);
            fileMap.put("key", fullKey);
            fileMap.put("bytes", file.length());
            fileMap.put("sizeFormatted", formattedSize);
            fileMap.put("ext", ext);
            fileMap.put("badgeBg", badgeBg);
            fileMap.put("isTable", isTable);
            fileMap.put("isChart", isChart);
            fileMap.put("isImage", isImage);
            fileMap.put("isStandard", isStandard);
            renderedFiles.add(fileMap);
        }

        boolean isEmpty = renderedDirs.isEmpty() && renderedFiles.isEmpty();

        return mav.addObject("restricted", false)
                .addObject("unavailable", false)
                .addObject("activeBucket", activeBucket)
                .addObject("activeBucketUpper", activeBucket.toUpperCase(Locale.ROOT))
                .addObject("pathFilter", pathFilter)
                .addObject("breadcrumbs", breadcrumbList)
                .addObject("hasParent", hasParent)
                .addObject("parentPath", parentPath)
                .addObject("isEmpty", isEmpty)
                .addObject("dirs", renderedDirs)
                .addObject("files", renderedFiles);
    }

    // HTMX Fragment: Renders activity feed (right sidebar)
    @GetMethod("/web/fragments/activities")
    public ModelAndView getActivitiesFragment() {
        List<String> rawLogs = ServerLogBus.getAllLogs();
        List<Map<String, String>> dbEvents = getEventRepository().getEvents();
        List<Map<String, Object>> activities = new ArrayList<>();

        int count = 0;
        if (dbEvents != null) {
            for (int i = dbEvents.size() - 1; i >= 0 && count < 5; i--) {
                Map<String, String> ev = dbEvents.get(i);
                Map<String, Object> item = new HashMap<>();
                item.put("avatarClass", "color-purple");
                item.put("badge", "SYS");
                item.put("title", "System Event");
                item.put("time", ev.getOrDefault("createdAt", ""));
                item.put("description", ev.getOrDefault("description", ""));
                activities.add(item);
                count++;
            }
        }

        if (rawLogs != null) {
            for (int i = rawLogs.size() - 1; i >= 0 && count < 8; i--) {
                String log = rawLogs.get(i);
                Map<String, Object> item = new HashMap<>();
                item.put("avatarClass", "color-blue");
                item.put("badge", "HTTP");
                item.put("title", "Server Log");
                item.put("time", "recente");
                item.put("description", log);
                activities.add(item);
                count++;
            }
        }

        return new ModelAndView("fragments/activities")
                .addObject("events", activities)
                .addObject("isEmpty", activities.isEmpty());
    }

    // HTMX Action: Create new folder/bucket
    @PostMethod("/web/create-folder")
    public Object createFolder(@RequestParam("name") String name, IHTTPSession session) {
        IamUser user = resolveAuthenticatedUser(session);
        if (user == null) {
            return com.github.matheuscruzsouza.nanospring.http.ResponseEntity
                    .status(com.github.matheuscruzsouza.nanospring.http.HttpStatus.FORBIDDEN)
                    .body("Permissão negada");
        }

        if (name != null && !name.trim().isEmpty()) {
            StorageManager sm = getStorageManager();
            if (sm != null) {
                Map<String, String> parms = session.getParms();
                boolean isAdmin = "Admin".equalsIgnoreCase(user.getPolicy()) || "*".equals(user.getPolicy());
                String userDefaultBucket = sm.getUserDefaultBucket(user);

                String targetBucket = (parms != null && parms.containsKey("bucket")) ? parms.get("bucket") : userDefaultBucket;
                String subPath = (parms != null && parms.containsKey("path")) ? parms.get("path") : "";
                while (subPath.startsWith("/")) subPath = subPath.substring(1);
                while (subPath.endsWith("/")) subPath = subPath.substring(0, subPath.length() - 1);
                if (!isAdmin) {
                    targetBucket = userDefaultBucket;
                }

                if (!getIamService().canPerformInBucket(user, "s3:PutObject", targetBucket, userDefaultBucket)) {
                    return com.github.matheuscruzsouza.nanospring.http.ResponseEntity
                            .status(com.github.matheuscruzsouza.nanospring.http.HttpStatus.FORBIDDEN)
                            .body("Permissão negada");
                }

                String fullFolderPath = subPath.isEmpty() ? name.trim() : (subPath + "/" + name.trim());
                sm.createFolder(targetBucket, fullFolderPath);
            }
        }
        return getFilesFragment(session);
    }

    // HTMX Fragment: Renders battery badge and telemetry status
    @GetMethod("/web/fragments/battery")
    public ModelAndView getBatteryFragment() {
        BatteryStatus battery = getBatteryStatus();
        int level = battery.getPercentage();
        boolean charging = battery.isCharging();
        String icon = charging ? "⚡" : (level > 20 ? "🔋" : "🪫");
        String badgeColor = charging ? "var(--accent-green)" : (level <= 20 ? "var(--accent-red)" : "var(--accent-blue)");
        String badgeBg = charging ? "rgba(16, 185, 129, 0.12)" : (level <= 20 ? "rgba(244, 63, 94, 0.12)" : "rgba(37, 99, 235, 0.12)");

        return new ModelAndView("fragments/battery")
                .addObject("badgeBg", badgeBg)
                .addObject("badgeColor", badgeColor)
                .addObject("icon", icon)
                .addObject("level", level)
                .addObject("charging", charging);
    }

    // HTMX Fragment: Renders auth status
    @GetMethod("/web/fragments/auth-status")
    public ModelAndView getAuthStatusFragment(IHTTPSession session) {
        IamUser user = resolveAuthenticatedUser(session);
        ModelAndView mav = new ModelAndView("fragments/auth_status");
        if (user != null) {
            return mav.addObject("isAuthenticated", true)
                    .addObject("userInitial", user.getName().substring(0, 1).toUpperCase(Locale.ROOT))
                    .addObject("userName", user.getName())
                    .addObject("userRole", user.getPolicy());
        } else {
            return mav.addObject("isAuthenticated", false);
        }
    }

    // Endpoint web para cadastro / definição de nova senha usando token de 6 dígitos
    @PostMethod("/web/set-password")
    public Object handleSetPassword(IHTTPSession session) {
        Map<String, String> parms = session.getParms();
        String username = parms != null ? parms.get("username") : null;
        String token = parms != null ? parms.get("token") : null;
        String newPassword = parms != null ? parms.get("password") : null;

        // Se os parâmetros não vieram pela querystring / parms automáticos, parseia o body
        if (username == null || token == null || newPassword == null) {
            try {
                Map<String, String> files = new HashMap<>();
                session.parseBody(files);
                if (session.getParms() != null) {
                    if (username == null) username = session.getParms().get("username");
                    if (token == null) token = session.getParms().get("token");
                    if (newPassword == null) newPassword = session.getParms().get("password");
                }
                // Se veio JSON no postData
                if (files.containsKey("postData")) {
                    String json = files.get("postData");
                    if (json != null && !json.trim().isEmpty()) {
                        com.google.gson.JsonObject obj = com.google.gson.JsonParser.parseString(json).getAsJsonObject();
                        if (username == null && obj.has("username")) username = obj.get("username").getAsString();
                        if (token == null && obj.has("token")) token = obj.get("token").getAsString();
                        if (newPassword == null && obj.has("password")) newPassword = obj.get("password").getAsString();
                    }
                }
            } catch (Exception ignored) {}
        }

        if (username == null || token == null || newPassword == null || newPassword.trim().isEmpty()) {
            return com.github.matheuscruzsouza.nanospring.http.ResponseEntity
                    .badRequest()
                    .header("Content-Type", "application/json")
                    .body("{\"success\":false,\"message\":\"Preencha todos os campos obrigatórios.\"}");
        }

        boolean updated = getIamService().setPasswordWithToken(username, token, newPassword);
        if (updated) {
            return com.github.matheuscruzsouza.nanospring.http.ResponseEntity
                    .ok()
                    .header("Content-Type", "application/json")
                    .body("{\"success\":true,\"message\":\"Senha definida com sucesso! Você já pode entrar com seu usuário e senha.\"}");
        } else {
            return com.github.matheuscruzsouza.nanospring.http.ResponseEntity
                    .badRequest()
                    .header("Content-Type", "application/json")
                    .body("{\"success\":false,\"message\":\"Token de 6 dígitos inválido ou usuário não encontrado.\"}");
        }
    }

    // HTMX Action: File upload returning updated files
    @PostMethod("/web/upload-file")
    public Object handleHtmxUpload(
            @UploadedFile("file") File uploadedFile,
            @RequestParam("file") String clientFileName,
            IHTTPSession session
    ) {
        IamUser user = resolveAuthenticatedUser(session);
        if (user == null) {
            return com.github.matheuscruzsouza.nanospring.http.ResponseEntity
                    .status(com.github.matheuscruzsouza.nanospring.http.HttpStatus.UNAUTHORIZED)
                    .header("Content-Type", "text/html; charset=UTF-8")
                    .body("<div class='fm-empty-state text-danger'>Autenticação requerida para upload.</div>");
        }

        if (uploadedFile == null || !uploadedFile.exists()) {
            return com.github.matheuscruzsouza.nanospring.http.ResponseEntity
                    .badRequest()
                    .header("Content-Type", "text/html; charset=UTF-8")
                    .body("<div class='fm-empty-state text-danger'>Nenhum arquivo recebido pelo servidor.</div>");
        }

        try {
            StorageManager sm = getStorageManager();
            Map<String, String> parms = session.getParms();
            boolean isAdmin = "Admin".equalsIgnoreCase(user.getPolicy()) || "*".equals(user.getPolicy());
            String userDefaultBucket = sm.getUserDefaultBucket(user);

            String bucket = (parms != null && parms.containsKey("bucket") && !parms.get("bucket").trim().isEmpty())
                    ? parms.get("bucket")
                    : userDefaultBucket;
            String path = (parms != null && parms.containsKey("path") && !parms.get("path").trim().isEmpty())
                    ? parms.get("path").trim()
                    : "";
            while (path.startsWith("/")) path = path.substring(1);
            while (path.endsWith("/")) path = path.substring(0, path.length() - 1);

            // Se não for admin, garante que o upload sempre vá para o bucket da própria conta
            if (!isAdmin) {
                bucket = userDefaultBucket;
            }

            if (!getIamService().canPerformInBucket(user, "s3:PutObject", bucket, userDefaultBucket)) {
                return com.github.matheuscruzsouza.nanospring.http.ResponseEntity
                        .status(com.github.matheuscruzsouza.nanospring.http.HttpStatus.FORBIDDEN)
                        .header("Content-Type", "text/html; charset=UTF-8")
                        .body("<div class='fm-empty-state text-danger'>Permissão negada pela política IAM (" + user.getPolicy() + ").</div>");
            }

            String originalName = clientFileName;
            if (originalName == null || originalName.trim().isEmpty()) {
                originalName = "upload_" + System.currentTimeMillis();
            }
            if (originalName.contains("/") || originalName.contains("\\")) {
                originalName = new File(originalName).getName();
            }

            String targetKey = path.isEmpty() ? originalName : (path + "/" + originalName);
            File target = sm.resolveFile(bucket, targetKey);
            File parentDir = target.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                parentDir.mkdirs();
            }

            try (InputStream in = new FileInputStream(uploadedFile);
                 OutputStream out = new FileOutputStream(target)) {
                FileUtils.copyStream(in, out, Long.MAX_VALUE);
            }
        } catch (Exception e) {
            return com.github.matheuscruzsouza.nanospring.http.ResponseEntity
                    .status(com.github.matheuscruzsouza.nanospring.http.HttpStatus.INTERNAL_SERVER_ERROR)
                    .header("Content-Type", "text/html; charset=UTF-8")
                    .body("<div class='fm-empty-state text-danger'>Erro no upload: " + e.getMessage() + "</div>");
        } finally {
            try {
                if (uploadedFile.exists()) {
                    uploadedFile.delete();
                }
            } catch (Exception ignored) {}
        }

        // Return updated files fragment directly
        return getFilesFragment(session);
    }

    // HTMX Action: Delete file with bucket segment
    @DeleteMethod("/web/files/:bucket/:filename")
    public Object deleteFile(
            @PathVariable("bucket") String bucket,
            @PathVariable("filename") String filename,
            IHTTPSession session
    ) {
        IamUser user = resolveAuthenticatedUser(session);
        if (user == null) {
            return com.github.matheuscruzsouza.nanospring.http.ResponseEntity
                    .status(com.github.matheuscruzsouza.nanospring.http.HttpStatus.FORBIDDEN)
                    .body("Permissão negada");
        }

        try {
            String decodedBucket = URLDecoder.decode(bucket, "UTF-8");
            String decodedFile = URLDecoder.decode(filename, "UTF-8");
            StorageManager sm = getStorageManager();
            boolean isAdmin = "Admin".equalsIgnoreCase(user.getPolicy()) || "*".equals(user.getPolicy());
            String userDefaultBucket = sm.getUserDefaultBucket(user);

            if (!getIamService().canPerformInBucket(user, "s3:DeleteObject", decodedBucket, userDefaultBucket)) {
                return com.github.matheuscruzsouza.nanospring.http.ResponseEntity
                        .status(com.github.matheuscruzsouza.nanospring.http.HttpStatus.FORBIDDEN)
                        .body("Permissão negada");
            }

            if (!isAdmin && !decodedBucket.equals(userDefaultBucket)) {
                return com.github.matheuscruzsouza.nanospring.http.ResponseEntity
                        .status(com.github.matheuscruzsouza.nanospring.http.HttpStatus.FORBIDDEN)
                        .body("Permissão negada para este bucket");
            }

            File file = sm.resolveFile(decodedBucket, decodedFile);
            if (file.exists() && file.isFile()) {
                file.delete();
            }
            return com.github.matheuscruzsouza.nanospring.http.ResponseEntity.ok("");
        } catch (Exception e) {
            return com.github.matheuscruzsouza.nanospring.http.ResponseEntity
                    .status(com.github.matheuscruzsouza.nanospring.http.HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(e.getMessage());
        }
    }

    // HTMX / API Action: Delete item (file or folder) via POST with parameters
    @PostMethod("/web/delete-item")
    public Object deleteItem(IHTTPSession session) {
        IamUser user = resolveAuthenticatedUser(session);
        if (user == null) {
            return com.github.matheuscruzsouza.nanospring.http.ResponseEntity
                    .status(com.github.matheuscruzsouza.nanospring.http.HttpStatus.FORBIDDEN)
                    .body("Permissão negada");
        }

        Map<String, String> parms = session.getParms();
        String bucket = parms != null ? parms.get("bucket") : "";
        String path = parms != null ? parms.get("path") : "";
        StorageManager sm = getStorageManager();

        if (sm != null && bucket != null && !bucket.isEmpty() && path != null && !path.isEmpty()) {
            boolean isAdmin = "Admin".equalsIgnoreCase(user.getPolicy()) || "*".equals(user.getPolicy());
            String userDefaultBucket = sm.getUserDefaultBucket(user);

            if (!getIamService().canPerformInBucket(user, "s3:DeleteObject", bucket, userDefaultBucket)) {
                return com.github.matheuscruzsouza.nanospring.http.ResponseEntity
                        .status(com.github.matheuscruzsouza.nanospring.http.HttpStatus.FORBIDDEN)
                        .body("Permissão negada");
            }

            if (!isAdmin && !bucket.equals(userDefaultBucket)) {
                return com.github.matheuscruzsouza.nanospring.http.ResponseEntity
                        .status(com.github.matheuscruzsouza.nanospring.http.HttpStatus.FORBIDDEN)
                        .body("Permissão negada para este bucket");
            }
            try {
                File target = sm.resolveFile(bucket, path);
                if (target.exists()) {
                    sm.deleteRecursively(target);
                }
            } catch (Exception e) {
                return com.github.matheuscruzsouza.nanospring.http.ResponseEntity
                        .status(com.github.matheuscruzsouza.nanospring.http.HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(e.getMessage());
            }
        }
        return getFilesFragment(session);
    }

    // HTMX / API Action: Rename item (file or folder)
    @PostMethod("/web/rename-item")
    public Object renameItem(IHTTPSession session) {
        IamUser user = resolveAuthenticatedUser(session);
        if (user == null) {
            return com.github.matheuscruzsouza.nanospring.http.ResponseEntity
                    .status(com.github.matheuscruzsouza.nanospring.http.HttpStatus.FORBIDDEN)
                    .body("Permissão negada");
        }

        Map<String, String> parms = session.getParms();
        String bucket = parms != null ? parms.get("bucket") : "";
        String oldPath = parms != null ? parms.get("oldPath") : "";
        String newName = parms != null ? parms.get("newName") : "";
        StorageManager sm = getStorageManager();

        if (sm != null && bucket != null && !bucket.isEmpty() && oldPath != null && !oldPath.isEmpty() && newName != null && !newName.trim().isEmpty()) {
            boolean isAdmin = "Admin".equalsIgnoreCase(user.getPolicy()) || "*".equals(user.getPolicy());
            String userDefaultBucket = sm.getUserDefaultBucket(user);

            if (!getIamService().canPerformInBucket(user, "s3:PutObject", bucket, userDefaultBucket)) {
                return com.github.matheuscruzsouza.nanospring.http.ResponseEntity
                        .status(com.github.matheuscruzsouza.nanospring.http.HttpStatus.FORBIDDEN)
                        .body("Permissão negada");
            }

            if (!isAdmin && !bucket.equals(userDefaultBucket)) {
                return com.github.matheuscruzsouza.nanospring.http.ResponseEntity
                        .status(com.github.matheuscruzsouza.nanospring.http.HttpStatus.FORBIDDEN)
                        .body("Permissão negada para este bucket");
            }

            try {
                File source = sm.resolveFile(bucket, oldPath);
                if (source.exists()) {
                    File parent = source.getParentFile();
                    File dest = new File(parent, newName.trim());
                    if (!dest.exists()) {
                        source.renameTo(dest);
                    }
                }
            } catch (Exception e) {
                return com.github.matheuscruzsouza.nanospring.http.ResponseEntity
                        .status(com.github.matheuscruzsouza.nanospring.http.HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(e.getMessage());
            }
        }
        return getFilesFragment(session);
    }

    // Direct download endpoint with bucket segment
    @GetMethod("/web/download/:bucket/:filename")
    public Object downloadFile(
            @PathVariable("bucket") String bucket,
            @PathVariable("filename") String filename,
            IHTTPSession session
    ) {
        return serveFileContent(bucket, filename, session, true);
    }

    // Direct view / inline preview endpoint with bucket segment
    @GetMethod("/web/view/:bucket/:filename")
    public Object viewFile(
            @PathVariable("bucket") String bucket,
            @PathVariable("filename") String filename,
            IHTTPSession session
    ) {
        return serveFileContent(bucket, filename, session, false);
    }

    private Object serveFileContent(String bucket, String filename, IHTTPSession session, boolean isAttachment) {
        IamUser user = resolveAuthenticatedUser(session);
        if (user == null || !getIamService().canPerform(user, "s3:GetObject")) {
            return com.github.matheuscruzsouza.nanospring.http.ResponseEntity
                    .status(com.github.matheuscruzsouza.nanospring.http.HttpStatus.FORBIDDEN)
                    .body("Acesso restrito");
        }

        try {
            Map<String, String> parms = session.getParms();
            String keyParam = parms != null ? parms.get("key") : null;
            String effectiveKey = (keyParam != null && !keyParam.trim().isEmpty()) ? keyParam.trim() : filename;

            String decodedBucket = URLDecoder.decode(bucket, "UTF-8");
            String decodedFile = URLDecoder.decode(effectiveKey, "UTF-8");
            StorageManager sm = getStorageManager();
            boolean isAdmin = "Admin".equalsIgnoreCase(user.getPolicy()) || "*".equals(user.getPolicy());
            String userDefaultBucket = sm.getUserDefaultBucket(user);

            if (!isAdmin && !decodedBucket.equals(userDefaultBucket)) {
                return com.github.matheuscruzsouza.nanospring.http.ResponseEntity
                        .status(com.github.matheuscruzsouza.nanospring.http.HttpStatus.FORBIDDEN)
                        .body("Acesso restrito a este bucket");
            }

            File file = sm.resolveFile(decodedBucket, decodedFile);
            if (!file.exists() || !file.isFile()) {
                return com.github.matheuscruzsouza.nanospring.http.ResponseEntity
                        .status(com.github.matheuscruzsouza.nanospring.http.HttpStatus.NOT_FOUND)
                        .body("Arquivo não encontrado");
            }

            Response res = ByteRangeHandler.serveFile(session, file, FileUtils.getMimeType(file.getName()));
            String dispositionType = isAttachment ? "attachment" : "inline";
            res.addHeader("Content-Disposition", dispositionType + "; filename=\"" + file.getName() + "\"");
            return res;
        } catch (Exception e) {
            return com.github.matheuscruzsouza.nanospring.http.ResponseEntity
                    .status(com.github.matheuscruzsouza.nanospring.http.HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(e.getMessage());
        }
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
                : Environment.getProperty("nano.nsd.name", "s4");
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

        com.github.matheuscruzsouza.androidserver.service.MyBackendService backend =
                com.github.matheuscruzsouza.androidserver.service.MyBackendService.getInstance();
        com.github.matheuscruzsouza.androidserver.service.NanoLocalTunnel tunnel =
                backend != null ? backend.getLocalTunnel() : null;
        status.put("localTunnelEnabled", Boolean.parseBoolean(Environment.getProperty("nano.localtunnel.enabled", "false")));
        status.put("localTunnelRunning", tunnel != null && tunnel.isRunning());
        status.put("localTunnelUrl", tunnel != null ? tunnel.getPublicUrl() : null);
        status.put("vpnIp", NetworkUtils.getVpnIpAddress());
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
        activeEmitters.add(emitter);
        emitter.onCompletion(() -> activeEmitters.remove(emitter));
        emitter.onError(e -> activeEmitters.remove(emitter));
        emitter.onTimeout(() -> activeEmitters.remove(emitter));

        if (sseBroadcasterStarted.compareAndSet(false, true)) {
            sseExecutor.scheduleWithFixedDelay(this::broadcastSseUpdate, 0, 2, TimeUnit.SECONDS);
        } else {
            // Envia o estado atual na mesma thread única para atualizar o cliente imediatamente ao conectar
            sseExecutor.execute(() -> sendSseUpdateToEmitter(emitter));
        }

        return emitter;
    }

    private void broadcastSseUpdate() {
        if (activeEmitters.isEmpty()) {
            return;
        }
        Map<String, Object> stats = new HashMap<>();
        String batteryHtml = buildTelemetryPayload(stats);

        for (SseEmitter emitter : activeEmitters) {
            if (emitter.isCompleted()) {
                activeEmitters.remove(emitter);
                continue;
            }
            try {
                emitter.send("metrics", stats);
                emitter.send("battery", batteryHtml);
            } catch (Exception e) {
                activeEmitters.remove(emitter);
                try {
                    emitter.completeWithError(e);
                } catch (Exception ignored) {}
            }
        }
    }

    private void sendSseUpdateToEmitter(SseEmitter emitter) {
        if (emitter == null || emitter.isCompleted()) {
            return;
        }
        Map<String, Object> stats = new HashMap<>();
        String batteryHtml = buildTelemetryPayload(stats);
        try {
            emitter.send("metrics", stats);
            emitter.send("battery", batteryHtml);
        } catch (Exception e) {
            activeEmitters.remove(emitter);
            try {
                emitter.completeWithError(e);
            } catch (Exception ignored) {}
        }
    }

    private String buildTelemetryPayload(Map<String, Object> stats) {
        long startTime = ServerLogBus.getServerStartTime();
        String uptime = "00:00:00";
        if (startTime > 0) {
            long diff = (System.currentTimeMillis() - startTime) / 1000;
            long hours = diff / 3600;
            long minutes = (diff % 3600) / 60;
            long seconds = diff % 60;
            uptime = String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds);
        }
        Runtime rt = Runtime.getRuntime();
        long usedMemory = (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024);
        long totalMemory = rt.totalMemory() / (1024 * 1024);

        BatteryStatus battery = getBatteryStatus();
        int batteryLevel = battery.getPercentage();
        boolean isCharging = battery.isCharging();
        String batteryIcon = isCharging ? "⚡" : (batteryLevel > 20 ? "🔋" : "🪫");
        String batteryBadgeColor = isCharging ? "var(--accent-green)" : (batteryLevel <= 20 ? "var(--accent-red)" : "var(--accent-blue)");
        String batteryBadgeBg = isCharging ? "rgba(16, 185, 129, 0.12)" : (batteryLevel <= 20 ? "rgba(244, 63, 94, 0.12)" : "rgba(37, 99, 235, 0.12)");

        stats.put("uptime", uptime);
        stats.put("requests", ServerLogBus.getRequestCount());
        stats.put("usedMemoryMb", usedMemory);
        stats.put("totalMemoryMb", totalMemory);
        stats.put("batteryLevel", batteryLevel);
        stats.put("isCharging", isCharging);
        stats.put("batteryTemp", battery.getTemperatureCelsius());
        stats.put("timestamp", System.currentTimeMillis());

        return String.format(Locale.US,
                "<span class='top-badge' style='background: %s; color: %s; border-color: %s;' id='batteryBadge'>" +
                "%s %d%%%s</span>",
                batteryBadgeBg, batteryBadgeColor, batteryBadgeColor, batteryIcon, batteryLevel, isCharging ? " Chg" : "");
    }
}
