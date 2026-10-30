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
import com.github.matheuscruzsouza.nanospring.battery.BatteryHelper;
import com.github.matheuscruzsouza.nanospring.battery.BatteryStatus;
import com.github.matheuscruzsouza.nanospring.cors.CorsConfiguration;
import com.github.matheuscruzsouza.nanospring.discovery.NsdServiceManager;
import com.github.matheuscruzsouza.nanospring.server.Environment;
import com.github.matheuscruzsouza.nanospring.server.Server;
import com.github.matheuscruzsouza.nanospring.sse.SseEmitter;
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

import fi.iki.elonen.NanoHTTPD;
import fi.iki.elonen.NanoHTTPD.IHTTPSession;
import fi.iki.elonen.NanoHTTPD.Response;
import fi.iki.elonen.NanoHTTPD.Response.Status;

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

    private String getFileSvg(String filename, boolean isDir) {
        if (isDir) {
            // Pasta em perspectiva 3D isométrica aberta/linha grossa (idêntica ao print de referência "Projeto_Frontend_v2")
            return "<svg viewBox='0 0 48 48' class='win-icon' fill='none' stroke='#FFFFFF' stroke-width='2' stroke-linecap='round' stroke-linejoin='round'>" +
                    "<path d='M6 16L18 8L36 12L24 20Z'/>" +
                    "<path d='M6 16V36L24 44V20L6 16Z'/>" +
                    "<path d='M24 44L42 36V14L24 20'/>" +
                    "<path d='M18 8V14'/>" +
                    "</svg>";
        }
        String lower = filename != null ? filename.toLowerCase(Locale.ROOT) : "";
        if (lower.endsWith(".pdf")) {
            // PDF estilo Windows (folha com dobra superior e grelha/linhas de texto verticais conforme "Contrato_Servicos.pdf")
            return "<svg viewBox='0 0 48 48' class='win-icon' fill='none' stroke='#FFFFFF' stroke-width='2' stroke-linecap='round' stroke-linejoin='round'>" +
                    "<path d='M10 6H30L40 16V42H10V6Z'/>" +
                    "<path d='M30 6V16H40'/>" +
                    "<rect x='16' y='22' width='16' height='14'/>" +
                    "<line x1='20' y1='22' x2='20' y2='36'/>" +
                    "<line x1='24' y1='22' x2='24' y2='36'/>" +
                    "<line x1='28' y1='22' x2='28' y2='36'/>" +
                    "</svg>";
        } else if (lower.endsWith(".doc") || lower.endsWith(".docx")) {
            // DOCX em perspectiva com linhas horizontais estilizadas
            return "<svg viewBox='0 0 48 48' class='win-icon' fill='none' stroke='#FFFFFF' stroke-width='2' stroke-linecap='round' stroke-linejoin='round'>" +
                    "<path d='M14 6L36 10L32 42L10 38Z'/>" +
                    "<line x1='16' y1='15' x2='30' y2='17'/>" +
                    "<line x1='15' y1='21' x2='29' y2='23'/>" +
                    "<line x1='14' y1='27' x2='28' y2='29'/>" +
                    "<line x1='13' y1='33' x2='27' y2='35'/>" +
                    "</svg>";
        } else if (lower.endsWith(".xls") || lower.endsWith(".xlsx") || lower.endsWith(".csv")) {
            // Planilha Excel estilo Windows com colunas de gráfico de barras
            return "<svg viewBox='0 0 48 48' class='win-icon' fill='none' stroke='#FFFFFF' stroke-width='2' stroke-linecap='round' stroke-linejoin='round'>" +
                    "<rect x='8' y='8' width='32' height='32'/>" +
                    "<rect x='13' y='22' width='4' height='14'/>" +
                    "<rect x='21' y='14' width='4' height='22'/>" +
                    "<rect x='29' y='18' width='4' height='18'/>" +
                    "</svg>";
        } else if (lower.endsWith(".key") || lower.endsWith(".ppt") || lower.endsWith(".pptx")) {
            // Apresentação/Filme com rolo de slides e tela
            return "<svg viewBox='0 0 48 48' class='win-icon' fill='none' stroke='#FFFFFF' stroke-width='2' stroke-linecap='round' stroke-linejoin='round'>" +
                    "<rect x='8' y='18' width='32' height='22'/>" +
                    "<circle cx='18' cy='12' r='4'/>" +
                    "<circle cx='30' cy='12' r='4'/>" +
                    "<polygon points='20 25 30 29 20 33 20 25'/>" +
                    "</svg>";
        } else if (lower.endsWith(".zip") || lower.endsWith(".rar") || lower.endsWith(".7z") || lower.endsWith(".tar") || lower.endsWith(".gz") || lower.endsWith(".apk")) {
            // Arquivo compactado com morsa/prensa (idêntico ao clássico do Windows "Assets_Icons.zip")
            return "<svg viewBox='0 0 48 48' class='win-icon' fill='none' stroke='#FFFFFF' stroke-width='2' stroke-linecap='round' stroke-linejoin='round'>" +
                    "<rect x='16' y='16' width='16' height='20'/>" +
                    "<line x1='12' y1='10' x2='36' y2='10' stroke-width='3'/>" +
                    "<line x1='24' y1='10' x2='24' y2='16'/>" +
                    "<line x1='12' y1='40' x2='36' y2='40' stroke-width='3'/>" +
                    "<line x1='24' y1='36' x2='24' y2='40'/>" +
                    "<circle cx='12' cy='10' r='2' fill='#FFFFFF'/>" +
                    "<circle cx='36' cy='10' r='2' fill='#FFFFFF'/>" +
                    "<line x1='16' y1='22' x2='32' y2='22'/>" +
                    "<line x1='16' y1='28' x2='32' y2='28'/>" +
                    "</svg>";
        } else if (lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".gif") || lower.endsWith(".webp") || lower.endsWith(".svg")) {
            // Imagem estilo moldura de quadro clássica do Windows ("Screenshot_Layout.png")
            return "<svg viewBox='0 0 48 48' class='win-icon' fill='none' stroke='#FFFFFF' stroke-width='2' stroke-linecap='round' stroke-linejoin='round'>" +
                    "<rect x='10' y='8' width='28' height='32' rx='2'/>" +
                    "<circle cx='24' cy='18' r='5'/>" +
                    "<path d='M16 34C16 28 20 26 24 26C28 26 32 28 32 34'/>" +
                    "</svg>";
        } else {
            // Documento de texto/genérico com linhas ("notas_reuniao.txt")
            return "<svg viewBox='0 0 48 48' class='win-icon' fill='none' stroke='#FFFFFF' stroke-width='2' stroke-linecap='round' stroke-linejoin='round'>" +
                    "<path d='M12 6H28L36 14V42H12V6Z'/>" +
                    "<path d='M28 6V14H36'/>" +
                    "<line x1='18' y1='20' x2='30' y2='20'/>" +
                    "<line x1='18' y1='26' x2='30' y2='26'/>" +
                    "<line x1='18' y1='32' x2='26' y2='32'/>" +
                    "</svg>";
        }
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

        return new ModelAndView("index")
                .addObject("appName", "S4")
                .addObject("frameworkName", "nano-spring")
                .addObject("frameworkVersion", "2.2.0")
                .addObject("deviceModel", Build.MANUFACTURER + " " + Build.MODEL)
                .addObject("androidVersion", Build.VERSION.RELEASE)
                .addObject("sdkInt", String.valueOf(Build.VERSION.SDK_INT))
                .addObject("ipAddress", ip)
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
                .addObject("hostResponderRunning", hostResponderRunning)
                .addObject("corsEnabled", corsActive)
                .addObject("hasUsers", isAuthenticated && userCount > 0)
                .addObject("hasFiles", isAuthenticated && fileCount > 0)
                .addObject("users", isAuthenticated ? users : Collections.emptyList())
                .addObject("files", isAuthenticated ? files : Collections.emptyList())
                .addObject("folders", folderList)
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

    // HTMX Fragment: Renders folder pills
    @GetMethod("/web/fragments/folders")
    public Response getFoldersFragment(IHTTPSession session) {
        StorageManager sm = getStorageManager();
        StringBuilder sb = new StringBuilder();

        IamUser user = resolveAuthenticatedUser(session);
        if (sm != null && user != null) {
            List<File> buckets = sm.listBucketsForUser(user);
            if (!buckets.isEmpty()) {
                for (File b : buckets) {
                    List<S3ObjectItem> objs = sm.listObjectsForUser(user, b.getName());
                    int count = objs.size();
                    sb.append("<div class='folder-card' onclick=\"filterBucket('").append(b.getName()).append("')\">");
                    sb.append("<div class='folder-icon'>📁</div>");
                    sb.append("<div class='folder-info'>");
                    sb.append("<span class='folder-name'>").append(b.getName()).append("</span>");
                    sb.append("<span class='folder-count'>").append(count).append("</span>");
                    sb.append("</div></div>");
                }
            } else {
                String defName = sm.getUserDefaultBucket(user);
                sb.append("<div class='folder-card' onclick=\"filterBucket('").append(defName).append("')\"><div class='folder-icon'>📁</div><div class='folder-info'><span class='folder-name'>").append(defName).append("</span><span class='folder-count'>0</span></div></div>");
            }
        }
        sb.append("<button class='folder-card add-folder-btn' onclick='promptNewFolder()'>");
        sb.append("<span class='add-icon'>+</span> <span class='folder-name'>Add new folder</span>");
        sb.append("</button>");

        return NanoHTTPD.newFixedLengthResponse(Status.OK, "text/html; charset=UTF-8", sb.toString());
    }

    // HTMX Fragment: Renders unified Windows Explorer style files and folders list
    @GetMethod("/web/fragments/files")
    public Response getFilesFragment(IHTTPSession session) {
        String bucketFilter = session.getParms() != null ? session.getParms().get("bucket") : null;
        if (bucketFilter == null) bucketFilter = "";
        String pathFilter = session.getParms() != null ? session.getParms().get("path") : null;
        if (pathFilter == null) pathFilter = "";
        while (pathFilter.startsWith("/")) pathFilter = pathFilter.substring(1);
        while (pathFilter.endsWith("/")) pathFilter = pathFilter.substring(0, pathFilter.length() - 1);

        IamUser user = resolveAuthenticatedUser(session);
        if (user == null) {
            String html = "<div class='fm-empty-state'>" +
                    "<div class='fm-empty-icon'>🔒</div>" +
                    "<h3>Acesso Restrito</h3>" +
                    "<p>Conecte-se com sua chave IAM para visualizar e interagir com os arquivos.</p>" +
                    "<button class='btn-tetrisly btn-primary' onclick='logout()'>Ir para Login</button>" +
                    "</div>";
            return NanoHTTPD.newFixedLengthResponse(Status.OK, "text/html; charset=UTF-8", html);
        }

        StorageManager sm = getStorageManager();
        if (sm == null) {
            return NanoHTTPD.newFixedLengthResponse(Status.OK, "text/html; charset=UTF-8",
                    "<div class='fm-empty-state'><p>Armazenamento indisponível</p></div>");
        }

        boolean isAdmin = "Admin".equalsIgnoreCase(user.getPolicy()) || "*".equals(user.getPolicy());
        String userDefaultBucket = sm.getUserDefaultBucket(user);
        String activeBucket = (!isAdmin || (bucketFilter.isEmpty() && pathFilter.isEmpty())) ? userDefaultBucket : bucketFilter;
        if (isAdmin && !bucketFilter.isEmpty()) {
            activeBucket = bucketFilter;
        }

        File[] directChildren = sm.listDirectChildren(user, activeBucket, pathFilter);

        StringBuilder sb = new StringBuilder();

        // Breadcrumb bar Windows Explorer style
        sb.append("<div class='win-breadcrumb-bar'>");
        sb.append("<span class='win-breadcrumb-root' onclick=\"navigateToPath('").append(activeBucket).append("', '')\">");
        sb.append("📁 ").append(activeBucket.toUpperCase(Locale.ROOT)).append("</span>");

        if (!pathFilter.isEmpty()) {
            String[] parts = pathFilter.split("/");
            StringBuilder accumulated = new StringBuilder();
            for (String part : parts) {
                if (accumulated.length() > 0) accumulated.append("/");
                accumulated.append(part);
                String currentAcc = accumulated.toString();
                sb.append("<span class='win-breadcrumb-sep'>›</span>");
                sb.append("<span class='win-breadcrumb-crumb' onclick=\"navigateToPath('").append(activeBucket).append("', '").append(currentAcc.replace("'", "\\'")).append("')\">");
                sb.append(part).append("</span>");
            }
        }
        sb.append("</div>");

        // Action Toolbar Windows Style (Voltar se dentro de pasta, Nova Pasta, etc.)
        sb.append("<div class='win-explorer-toolbar'>");
        if (!pathFilter.isEmpty()) {
            int lastSlash = pathFilter.lastIndexOf('/');
            String parentPath = lastSlash > 0 ? pathFilter.substring(0, lastSlash) : "";
            sb.append("<button class='win-btn' onclick=\"navigateToPath('").append(activeBucket).append("', '").append(parentPath.replace("'", "\\'")).append("')\" title='Subir um nível'>");
            sb.append("⬆️ Voltar</button>");
        }
        sb.append("<button class='win-btn' onclick='promptNewFolder()' title='Criar nova pasta nesta localização'>📁 Nova Pasta</button>");
        sb.append("<button class='win-btn' onclick=\"document.getElementById('fileInput').click()\" title='Carregar arquivo para esta pasta'>⬆️ Enviar Arquivo</button>");
        sb.append("</div>");

        // Unified Windows Explorer View List
        sb.append("<div class='win-explorer-grid' oncontextmenu='handleGridContextMenu(event)'>");

        List<File> dirList = new ArrayList<>();
        List<File> fileList = new ArrayList<>();
        for (File f : directChildren) {
            if (f.isDirectory()) {
                dirList.add(f);
            } else if (f.isFile()) {
                fileList.add(f);
            }
        }

        // Sort alphabetically
        dirList.sort((a, b) -> a.getName().compareToIgnoreCase(b.getName()));
        fileList.sort((a, b) -> a.getName().compareToIgnoreCase(b.getName()));

        if (dirList.isEmpty() && fileList.isEmpty()) {
            sb.append("<div class='fm-empty-state win-empty-state'>");
            sb.append("<div class='fm-empty-icon'>📂</div>");
            sb.append("<h3>Esta pasta está vazia</h3>");
            sb.append("<p>Clique em <strong>Enviar Arquivo</strong> ou <strong>Nova Pasta</strong> para começar.</p>");
            sb.append("</div>");
        } else {
            // Render Directories first
            for (File dir : dirList) {
                String subChildPath = pathFilter.isEmpty() ? dir.getName() : (pathFilter + "/" + dir.getName());
                String escapedBucket = activeBucket.replace("'", "\\'");
                String escapedSubChild = subChildPath.replace("'", "\\'");
                String escapedName = dir.getName().replace("'", "\\'");

                sb.append("<div class='win-item win-folder-item' ")
                        .append("onclick=\"navigateToPath('").append(escapedBucket).append("', '").append(escapedSubChild).append("')\" ")
                        .append("oncontextmenu=\"handleItemContextMenu(event, 'folder', '").append(escapedBucket).append("', '").append(escapedSubChild).append("', '").append(escapedName).append("')\" ")
                        .append("title='Pasta: ").append(dir.getName()).append(" (Clique para abrir, botão direito para opções)'>");
                sb.append("<div class='win-item-icon'>").append(getFileSvg(dir.getName(), true)).append("</div>");
                sb.append("<span class='win-item-name'>").append(dir.getName()).append("</span>");
                sb.append("</div>");
            }

            // Render Files
            for (File file : fileList) {
                String fullKey = pathFilter.isEmpty() ? file.getName() : (pathFilter + "/" + file.getName());
                String escapedBucket = activeBucket.replace("'", "\\'");
                String escapedKey = fullKey.replace("'", "\\'");
                String escapedName = file.getName().replace("'", "\\'");
                String formattedSize = formatBytes(file.length());

                sb.append("<div class='win-item win-file-item' ")
                        .append("onclick=\"openPreviewModal('").append(escapedBucket).append("', '").append(escapedKey).append("', '").append(formattedSize).append("')\" ")
                        .append("data-bytes='").append(file.length()).append("' ")
                        .append("data-size='").append(formattedSize).append("' ")
                        .append("data-name='").append(escapedName).append("' ")
                        .append("oncontextmenu=\"handleItemContextMenu(event, 'file', '").append(escapedBucket).append("', '").append(escapedKey).append("', '").append(escapedName).append("')\" ")
                        .append("title='").append(file.getName()).append(" (").append(formattedSize).append(")'>");
                sb.append("<div class='win-item-icon'>").append(getFileSvg(file.getName(), false)).append("</div>");
                sb.append("<span class='win-item-name'>").append(file.getName()).append("</span>");
                sb.append("</div>");
            }
        }

        sb.append("</div>");
        return NanoHTTPD.newFixedLengthResponse(Status.OK, "text/html; charset=UTF-8", sb.toString());
    }

    // HTMX Fragment: Renders activity feed (right sidebar)
    @GetMethod("/web/fragments/activities")
    public Response getActivitiesFragment() {
        List<String> rawLogs = ServerLogBus.getAllLogs();
        List<Map<String, String>> dbEvents = getEventRepository().getEvents();
        StringBuilder sb = new StringBuilder();

        sb.append("<div class='activity-timeline'>");

        // Combine logs and db events
        int count = 0;
        if (dbEvents != null) {
            for (int i = dbEvents.size() - 1; i >= 0 && count < 5; i--) {
                Map<String, String> ev = dbEvents.get(i);
                sb.append("<div class='activity-item'>");
                sb.append("<div class='activity-avatar color-purple'>SYS</div>");
                sb.append("<div class='activity-content'>");
                sb.append("<div class='activity-author'>System Event <span class='activity-time'>").append(ev.getOrDefault("createdAt", "")).append("</span></div>");
                sb.append("<p class='activity-text'>").append(ev.getOrDefault("description", "")).append("</p>");
                sb.append("</div></div>");
                count++;
            }
        }

        if (rawLogs != null) {
            for (int i = rawLogs.size() - 1; i >= 0 && count < 8; i--) {
                String log = rawLogs.get(i);
                sb.append("<div class='activity-item'>");
                sb.append("<div class='activity-avatar color-blue'>HTTP</div>");
                sb.append("<div class='activity-content'>");
                sb.append("<div class='activity-author'>Server Log <span class='activity-time'>recente</span></div>");
                sb.append("<p class='activity-text'>").append(log).append("</p>");
                sb.append("</div></div>");
                count++;
            }
        }

        if (count == 0) {
            sb.append("<div class='empty-activity'><p>Nenhuma atividade registrada ainda.</p></div>");
        }

        sb.append("</div>");
        return NanoHTTPD.newFixedLengthResponse(Status.OK, "text/html; charset=UTF-8", sb.toString());
    }

    // HTMX Action: Create new folder/bucket
    @PostMethod("/web/create-folder")
    public Response createFolder(@RequestParam("name") String name, IHTTPSession session) {
        IamUser user = resolveAuthenticatedUser(session);
        if (user == null || !getIamService().canPerform(user, "s3:PutObject")) {
            return NanoHTTPD.newFixedLengthResponse(Status.FORBIDDEN, "text/plain", "Permissão negada");
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

                String fullFolderPath = subPath.isEmpty() ? name.trim() : (subPath + "/" + name.trim());
                sm.createFolder(targetBucket, fullFolderPath);
            }
        }
        return getFilesFragment(session);
    }

    // HTMX Fragment: Renders battery badge and telemetry status
    @GetMethod("/web/fragments/battery")
    public Response getBatteryFragment() {
        BatteryStatus battery = getBatteryStatus();
        int level = battery.getPercentage();
        boolean charging = battery.isCharging();
        String icon = charging ? "⚡" : (level > 20 ? "🔋" : "🪫");
        String badgeColor = charging ? "var(--accent-green)" : (level <= 20 ? "var(--accent-red)" : "var(--accent-blue)");
        String badgeBg = charging ? "rgba(16, 185, 129, 0.12)" : (level <= 20 ? "rgba(244, 63, 94, 0.12)" : "rgba(37, 99, 235, 0.12)");

        String html = String.format(Locale.US,
                "<span class='top-badge' style='background: %s; color: %s; border-color: %s;' id='batteryBadge'>" +
                "%s %d%%%s</span>",
                badgeBg, badgeColor, badgeColor, icon, level, charging ? " Chg" : "");

        return NanoHTTPD.newFixedLengthResponse(Status.OK, "text/html; charset=UTF-8", html);
    }

    // HTMX Fragment: Renders auth status
    @GetMethod("/web/fragments/auth-status")
    public Response getAuthStatusFragment(IHTTPSession session) {
        IamUser user = resolveAuthenticatedUser(session);
        StringBuilder sb = new StringBuilder();
        if (user != null) {
            sb.append("<div class='user-profile-pill'>");
            sb.append("<div class='avatar-circle'>").append(user.getName().substring(0, 1).toUpperCase()).append("</div>");
            sb.append("<div class='user-meta'>");
            sb.append("<span class='user-name'>").append(user.getName()).append("</span>");
            sb.append("<span class='user-role'>").append(user.getPolicy()).append("</span>");
            sb.append("</div>");
            sb.append("<button class='logout-btn' onclick='logout()' title='Sair'>✕</button>");
            sb.append("</div>");
        } else {
            sb.append("<button class='btn-tetrisly btn-outline' onclick='logout()'>🔑 Entrar</button>");
        }
        return NanoHTTPD.newFixedLengthResponse(Status.OK, "text/html; charset=UTF-8", sb.toString());
    }

    // HTMX Action: File upload returning updated files
    @PostMethod("/web/upload-file")
    public Response handleHtmxUpload(
            @UploadedFile("file") File uploadedFile,
            @RequestParam("file") String clientFileName,
            IHTTPSession session
    ) {
        IamUser user = resolveAuthenticatedUser(session);
        if (user == null) {
            return NanoHTTPD.newFixedLengthResponse(Status.UNAUTHORIZED, "text/html; charset=UTF-8",
                    "<div class='fm-empty-state text-danger'>Autenticação requerida para upload.</div>");
        }

        if (!getIamService().canPerform(user, "s3:PutObject")) {
            return NanoHTTPD.newFixedLengthResponse(Status.FORBIDDEN, "text/html; charset=UTF-8",
                    "<div class='fm-empty-state text-danger'>Permissão negada pela política IAM (" + user.getPolicy() + ").</div>");
        }

        if (uploadedFile == null || !uploadedFile.exists()) {
            return NanoHTTPD.newFixedLengthResponse(Status.BAD_REQUEST, "text/html; charset=UTF-8",
                    "<div class='fm-empty-state text-danger'>Nenhum arquivo recebido pelo servidor.</div>");
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
            return NanoHTTPD.newFixedLengthResponse(Status.INTERNAL_ERROR, "text/html; charset=UTF-8",
                    "<div class='fm-empty-state text-danger'>Erro no upload: " + e.getMessage() + "</div>");
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
    public Response deleteFile(
            @PathVariable("bucket") String bucket,
            @PathVariable("filename") String filename,
            IHTTPSession session
    ) {
        IamUser user = resolveAuthenticatedUser(session);
        if (user == null || !getIamService().canPerform(user, "s3:DeleteObject")) {
            return NanoHTTPD.newFixedLengthResponse(Status.FORBIDDEN, "text/plain", "Permissão negada");
        }

        try {
            String decodedBucket = URLDecoder.decode(bucket, "UTF-8");
            String decodedFile = URLDecoder.decode(filename, "UTF-8");
            StorageManager sm = getStorageManager();
            boolean isAdmin = "Admin".equalsIgnoreCase(user.getPolicy()) || "*".equals(user.getPolicy());
            String userDefaultBucket = sm.getUserDefaultBucket(user);

            if (!isAdmin && !decodedBucket.equals(userDefaultBucket)) {
                return NanoHTTPD.newFixedLengthResponse(Status.FORBIDDEN, "text/plain", "Permissão negada para este bucket");
            }

            File file = sm.resolveFile(decodedBucket, decodedFile);
            if (file.exists() && file.isFile()) {
                file.delete();
            }
            return NanoHTTPD.newFixedLengthResponse(Status.OK, "text/html", "");
        } catch (Exception e) {
            return NanoHTTPD.newFixedLengthResponse(Status.INTERNAL_ERROR, "text/plain", e.getMessage());
        }
    }

    // HTMX / API Action: Delete item (file or folder) via POST with parameters
    @PostMethod("/web/delete-item")
    public Response deleteItem(IHTTPSession session) {
        IamUser user = resolveAuthenticatedUser(session);
        if (user == null || !getIamService().canPerform(user, "s3:DeleteObject")) {
            return NanoHTTPD.newFixedLengthResponse(Status.FORBIDDEN, "text/plain", "Permissão negada");
        }

        Map<String, String> parms = session.getParms();
        String bucket = parms != null ? parms.get("bucket") : "";
        String path = parms != null ? parms.get("path") : "";
        StorageManager sm = getStorageManager();

        if (sm != null && bucket != null && !bucket.isEmpty() && path != null && !path.isEmpty()) {
            boolean isAdmin = "Admin".equalsIgnoreCase(user.getPolicy()) || "*".equals(user.getPolicy());
            String userDefaultBucket = sm.getUserDefaultBucket(user);
            if (!isAdmin && !bucket.equals(userDefaultBucket)) {
                return NanoHTTPD.newFixedLengthResponse(Status.FORBIDDEN, "text/plain", "Permissão negada para este bucket");
            }
            try {
                File target = sm.resolveFile(bucket, path);
                if (target.exists()) {
                    sm.deleteRecursively(target);
                }
            } catch (Exception e) {
                return NanoHTTPD.newFixedLengthResponse(Status.INTERNAL_ERROR, "text/plain", e.getMessage());
            }
        }
        return getFilesFragment(session);
    }

    // Direct download endpoint with bucket segment
    @GetMethod("/web/download/:bucket/:filename")
    public Response downloadFile(
            @PathVariable("bucket") String bucket,
            @PathVariable("filename") String filename,
            IHTTPSession session
    ) {
        return serveFileContent(bucket, filename, session, true);
    }

    // Direct view / inline preview endpoint with bucket segment
    @GetMethod("/web/view/:bucket/:filename")
    public Response viewFile(
            @PathVariable("bucket") String bucket,
            @PathVariable("filename") String filename,
            IHTTPSession session
    ) {
        return serveFileContent(bucket, filename, session, false);
    }

    private Response serveFileContent(String bucket, String filename, IHTTPSession session, boolean isAttachment) {
        IamUser user = resolveAuthenticatedUser(session);
        if (user == null || !getIamService().canPerform(user, "s3:GetObject")) {
            return NanoHTTPD.newFixedLengthResponse(Status.FORBIDDEN, "text/plain", "Acesso restrito");
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
                return NanoHTTPD.newFixedLengthResponse(Status.FORBIDDEN, "text/plain", "Acesso restrito a este bucket");
            }

            File file = sm.resolveFile(decodedBucket, decodedFile);
            if (!file.exists() || !file.isFile()) {
                return NanoHTTPD.newFixedLengthResponse(Status.NOT_FOUND, "text/plain", "Arquivo não encontrado");
            }

            FileInputStream fis = new FileInputStream(file);
            Response res = NanoHTTPD.newFixedLengthResponse(Status.OK, FileUtils.getMimeType(file.getName()), fis, file.length());
            String dispositionType = isAttachment ? "attachment" : "inline";
            res.addHeader("Content-Disposition", dispositionType + "; filename=\"" + file.getName() + "\"");
            return res;
        } catch (Exception e) {
            return NanoHTTPD.newFixedLengthResponse(Status.INTERNAL_ERROR, "text/plain", e.getMessage());
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
                // Stream metrics and battery up to 60 updates (120 seconds)
                for (int i = 0; i < 60; i++) {
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

                    Map<String, Object> stats = new HashMap<>();
                    stats.put("uptime", uptime);
                    stats.put("requests", ServerLogBus.getRequestCount());
                    stats.put("usedMemoryMb", usedMemory);
                    stats.put("totalMemoryMb", totalMemory);
                    stats.put("batteryLevel", batteryLevel);
                    stats.put("isCharging", isCharging);
                    stats.put("batteryTemp", battery.getTemperatureCelsius());
                    stats.put("timestamp", System.currentTimeMillis());

                    // Send metrics event
                    emitter.send("metrics", stats);

                    // Send battery html fragment event for HTMX SSE extension
                    String batteryHtml = String.format(Locale.US,
                            "<span class='top-badge' style='background: %s; color: %s; border-color: %s;' id='batteryBadge'>" +
                            "%s %d%%%s</span>",
                            batteryBadgeBg, batteryBadgeColor, batteryBadgeColor, batteryIcon, batteryLevel, isCharging ? " Chg" : "");
                    emitter.send("battery", batteryHtml);

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
