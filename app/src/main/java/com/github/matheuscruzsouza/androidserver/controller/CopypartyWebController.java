package com.github.matheuscruzsouza.androidserver.controller;

import com.github.matheuscruzsouza.androidserver.storage.FileUtils;
import com.github.matheuscruzsouza.androidserver.storage.StorageManager;
import com.github.matheuscruzsouza.nanospring.annotation.Autowired;
import com.github.matheuscruzsouza.nanospring.annotation.DeleteMethod;
import com.github.matheuscruzsouza.nanospring.annotation.GetMethod;
import com.github.matheuscruzsouza.nanospring.annotation.PathVariable;
import com.github.matheuscruzsouza.nanospring.annotation.PostMethod;
import com.github.matheuscruzsouza.nanospring.annotation.RestController;

import fi.iki.elonen.NanoHTTPD;
import fi.iki.elonen.NanoHTTPD.IHTTPSession;
import fi.iki.elonen.NanoHTTPD.Response;
import fi.iki.elonen.NanoHTTPD.Response.Status;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;

@RestController("/party")
public class CopypartyWebController {

    @Autowired
    private StorageManager storageManager;

    public CopypartyWebController() {
    }

    public CopypartyWebController(StorageManager storageManager) {
        this.storageManager = storageManager;
    }

    // Interface Web estilo Copyparty moderna
    @GetMethod("")
    public Response index() {
        String html = "<!DOCTYPE html>\n" +
                "<html lang=\"pt-BR\">\n" +
                "<head>\n" +
                "    <meta charset=\"UTF-8\">\n" +
                "    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n" +
                "    <title>Copyparty Android Server</title>\n" +
                "    <style>\n" +
                "        :root {\n" +
                "            --bg-color: #0A0A0C;\n" +
                "            --card-bg: #1C1C1E;\n" +
                "            --card-sub-bg: #2C2C2E;\n" +
                "            --border-color: #3A3A3C;\n" +
                "            --accent-blue: #0A84FF;\n" +
                "            --accent-green: #30D158;\n" +
                "            --accent-orange: #FF9F0A;\n" +
                "            --accent-red: #FF453A;\n" +
                "            --text-primary: #FFFFFF;\n" +
                "            --text-muted: #8E8E93;\n" +
                "        }\n" +
                "        * { box-sizing: border-box; margin: 0; padding: 0; }\n" +
                "        body {\n" +
                "            background-color: var(--bg-color);\n" +
                "            color: var(--text-primary);\n" +
                "            font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif;\n" +
                "            padding: 24px;\n" +
                "            line-height: 1.5;\n" +
                "        }\n" +
                "        .container { max-width: 960px; margin: 0 auto; }\n" +
                "        header {\n" +
                "            display: flex;\n" +
                "            justify-content: space-between;\n" +
                "            align-items: center;\n" +
                "            border-bottom: 1px solid var(--border-color);\n" +
                "            padding-bottom: 18px;\n" +
                "            margin-bottom: 24px;\n" +
                "        }\n" +
                "        .header-title h1 { font-size: 24px; font-weight: 700; display: flex; align-items: center; gap: 10px; }\n" +
                "        .header-title p { font-size: 13px; color: var(--text-muted); margin-top: 4px; }\n" +
                "        .header-actions { display: flex; gap: 10px; align-items: center; }\n" +
                "        .badge {\n" +
                "            font-size: 12px;\n" +
                "            font-weight: 600;\n" +
                "            padding: 4px 10px;\n" +
                "            border-radius: 9999px;\n" +
                "            background: rgba(10, 132, 255, 0.15);\n" +
                "            color: var(--accent-blue);\n" +
                "            border: 1px solid rgba(10, 132, 255, 0.3);\n" +
                "            text-decoration: none;\n" +
                "        }\n" +
                "        .btn-nav {\n" +
                "            font-size: 13px;\n" +
                "            font-weight: 600;\n" +
                "            padding: 8px 14px;\n" +
                "            border-radius: 8px;\n" +
                "            background: var(--card-sub-bg);\n" +
                "            color: var(--text-primary);\n" +
                "            border: 1px solid var(--border-color);\n" +
                "            text-decoration: none;\n" +
                "            transition: 0.2s;\n" +
                "        }\n" +
                "        .btn-nav:hover { background: var(--border-color); }\n" +
                "        .card {\n" +
                "            background: var(--card-bg);\n" +
                "            border: 1px solid var(--border-color);\n" +
                "            border-radius: 12px;\n" +
                "            padding: 20px;\n" +
                "            margin-bottom: 24px;\n" +
                "        }\n" +
                "        .dropzone {\n" +
                "            border: 2px dashed var(--accent-blue);\n" +
                "            border-radius: 10px;\n" +
                "            padding: 36px 20px;\n" +
                "            text-align: center;\n" +
                "            cursor: pointer;\n" +
                "            transition: all 0.2s ease;\n" +
                "            background: rgba(10, 132, 255, 0.04);\n" +
                "        }\n" +
                "        .dropzone.hover {\n" +
                "            background: rgba(10, 132, 255, 0.12);\n" +
                "            border-color: #5ac8fa;\n" +
                "        }\n" +
                "        .dropzone-icon { font-size: 38px; margin-bottom: 8px; }\n" +
                "        .dropzone-title { font-size: 16px; font-weight: 600; }\n" +
                "        .dropzone-desc { font-size: 13px; color: var(--text-muted); margin-top: 4px; }\n" +
                "        progress {\n" +
                "            width: 100%;\n" +
                "            display: none;\n" +
                "            height: 8px;\n" +
                "            border-radius: 4px;\n" +
                "            margin-top: 16px;\n" +
                "            overflow: hidden;\n" +
                "        }\n" +
                "        .file-toolbar {\n" +
                "            display: flex;\n" +
                "            justify-content: space-between;\n" +
                "            align-items: center;\n" +
                "            margin-bottom: 12px;\n" +
                "            font-size: 14px;\n" +
                "        }\n" +
                "        .file-table {\n" +
                "            width: 100%;\n" +
                "            border-collapse: collapse;\n" +
                "            text-align: left;\n" +
                "            font-size: 14px;\n" +
                "        }\n" +
                "        .file-table th {\n" +
                "            padding: 10px 12px;\n" +
                "            color: var(--text-muted);\n" +
                "            font-weight: 600;\n" +
                "            border-bottom: 1px solid var(--border-color);\n" +
                "            font-size: 12px;\n" +
                "            text-transform: uppercase;\n" +
                "        }\n" +
                "        .file-table td {\n" +
                "            padding: 12px;\n" +
                "            border-bottom: 1px solid var(--border-color);\n" +
                "        }\n" +
                "        .file-table tr:hover td { background: var(--card-sub-bg); }\n" +
                "        .file-name-cell {\n" +
                "            display: flex;\n" +
                "            align-items: center;\n" +
                "            gap: 10px;\n" +
                "            font-weight: 500;\n" +
                "            color: var(--text-primary);\n" +
                "            text-decoration: none;\n" +
                "        }\n" +
                "        .file-name-cell:hover { color: var(--accent-blue); }\n" +
                "        .file-actions { display: flex; gap: 8px; justify-content: flex-end; }\n" +
                "        .btn-sm {\n" +
                "            padding: 5px 10px;\n" +
                "            border-radius: 6px;\n" +
                "            font-size: 12px;\n" +
                "            font-weight: 600;\n" +
                "            text-decoration: none;\n" +
                "            cursor: pointer;\n" +
                "            border: none;\n" +
                "        }\n" +
                "        .btn-download { background: var(--accent-blue); color: #fff; }\n" +
                "        .btn-delete { background: rgba(255, 69, 58, 0.15); color: var(--accent-red); border: 1px solid rgba(255, 69, 58, 0.3); }\n" +
                "        .btn-delete:hover { background: var(--accent-red); color: #fff; }\n" +
                "    </style>\n" +
                "</head>\n" +
                "<body>\n" +
                "<div class=\"container\">\n" +
                "    <header>\n" +
                "        <div class=\"header-title\">\n" +
                "            <h1>📦 Copyparty File Server</h1>\n" +
                "            <p>Gerenciador de arquivos integrado com API S3 no AndroidServer</p>\n" +
                "        </div>\n" +
                "        <div class=\"header-actions\">\n" +
                "            <a href=\"/s3/default\" target=\"_blank\" class=\"badge\">⚡ S3: /s3/default</a>\n" +
                "            <a href=\"/\" class=\"btn-nav\">🏠 Dashboard Principal</a>\n" +
                "        </div>\n" +
                "    </header>\n" +
                "    <div class=\"card\">\n" +
                "        <div class=\"dropzone\" id=\"dropzone\">\n" +
                "            <div class=\"dropzone-icon\">📂</div>\n" +
                "            <div class=\"dropzone-title\">Arraste e solte arquivos aqui</div>\n" +
                "            <div class=\"dropzone-desc\">ou clique para selecionar do dispositivo</div>\n" +
                "            <input type=\"file\" id=\"fileInput\" multiple style=\"display: none;\">\n" +
                "            <progress id=\"uploadProgress\" value=\"0\" max=\"100\"></progress>\n" +
                "        </div>\n" +
                "    </div>\n" +
                "    <div class=\"card\">\n" +
                "        <div class=\"file-toolbar\">\n" +
                "            <strong>Arquivos no Bucket (default)</strong>\n" +
                "            <button onclick=\"loadFiles()\" class=\"btn-nav\" style=\"padding: 4px 10px; font-size: 12px;\">🔄 Atualizar</button>\n" +
                "        </div>\n" +
                "        <div style=\"overflow-x: auto;\">\n" +
                "            <table class=\"file-table\">\n" +
                "                <thead>\n" +
                "                    <tr>\n" +
                "                        <th>Nome do Arquivo</th>\n" +
                "                        <th style=\"width: 120px;\">Tamanho</th>\n" +
                "                        <th style=\"width: 150px; text-align: right;\">Ações</th>\n" +
                "                    </tr>\n" +
                "                </thead>\n" +
                "                <tbody id=\"fileList\">\n" +
                "                    <tr><td colspan=\"3\" style=\"text-align: center; color: var(--text-muted);\">Carregando arquivos...</td></tr>\n" +
                "                </tbody>\n" +
                "            </table>\n" +
                "        </div>\n" +
                "    </div>\n" +
                "</div>\n" +
                "<script>\n" +
                "    const dropzone = document.getElementById('dropzone');\n" +
                "    const fileInput = document.getElementById('fileInput');\n" +
                "    const fileList = document.getElementById('fileList');\n" +
                "    const progress = document.getElementById('uploadProgress');\n" +
                "\n" +
                "    function formatBytes(bytes) {\n" +
                "        if (bytes === 0) return '0 B';\n" +
                "        const k = 1024;\n" +
                "        const sizes = ['B', 'KB', 'MB', 'GB'];\n" +
                "        const i = Math.floor(Math.log(bytes) / Math.log(k));\n" +
                "        return (bytes / Math.pow(k, i)).toFixed(1) + ' ' + sizes[i];\n" +
                "    }\n" +
                "\n" +
                "    async function loadFiles() {\n" +
                "        try {\n" +
                "            const res = await fetch('/party/api/files');\n" +
                "            const files = await res.json();\n" +
                "            if (!files.length) {\n" +
                "                fileList.innerHTML = '<tr><td colspan=\"3\" style=\"text-align:center; padding: 24px; color: var(--text-muted);\">Nenhum arquivo armazenado ainda</td></tr>';\n" +
                "                return;\n" +
                "            }\n" +
                "            fileList.innerHTML = files.map(f => `\n" +
                "                <tr>\n" +
                "                    <td>\n" +
                "                        <a href=\"/party/download/${encodeURIComponent(f.name)}\" target=\"_blank\" class=\"file-name-cell\">\n" +
                "                            📄 <span>${f.name}</span>\n" +
                "                        </a>\n" +
                "                    </td>\n" +
                "                    <td style=\"color: var(--text-muted);\">${formatBytes(f.size)}</td>\n" +
                "                    <td>\n" +
                "                        <div class=\"file-actions\">\n" +
                "                            <a href=\"/party/download/${encodeURIComponent(f.name)}\" target=\"_blank\" class=\"btn-sm btn-download\">Baixar</a>\n" +
                "                            <button onclick=\"deleteFile('${encodeURIComponent(f.name)}')\" class=\"btn-sm btn-delete\">Excluir</button>\n" +
                "                        </div>\n" +
                "                    </td>\n" +
                "                </tr>\n" +
                "            `).join('');\n" +
                "        } catch(e) {\n" +
                "            fileList.innerHTML = '<tr><td colspan=\"3\" style=\"text-align:center; color: var(--accent-red);\">Erro ao carregar arquivos</td></tr>';\n" +
                "        }\n" +
                "    }\n" +
                "\n" +
                "    async function deleteFile(filename) {\n" +
                "        if (!confirm('Deseja excluir este arquivo?')) return;\n" +
                "        try {\n" +
                "            await fetch('/party/delete/' + filename, { method: 'DELETE' });\n" +
                "            loadFiles();\n" +
                "        } catch(e) {\n" +
                "            alert('Falha ao excluir arquivo');\n" +
                "        }\n" +
                "    }\n" +
                "\n" +
                "    dropzone.onclick = () => fileInput.click();\n" +
                "    dropzone.ondragover = e => { e.preventDefault(); dropzone.classList.add('hover'); };\n" +
                "    dropzone.ondragleave = () => dropzone.classList.remove('hover');\n" +
                "    dropzone.ondrop = e => { e.preventDefault(); dropzone.classList.remove('hover'); upload(e.dataTransfer.files); };\n" +
                "    fileInput.onchange = () => upload(fileInput.files);\n" +
                "\n" +
                "    async function upload(files) {\n" +
                "        if (!files.length) return;\n" +
                "        progress.style.display = 'block';\n" +
                "        progress.value = 0;\n" +
                "        for (let i = 0; i < files.length; i++) {\n" +
                "            const file = files[i];\n" +
                "            const fd = new FormData();\n" +
                "            fd.append('file', file, file.name);\n" +
                "            await fetch('/party/upload', { method: 'POST', body: fd });\n" +
                "            progress.value = Math.round(((i + 1) / files.length) * 100);\n" +
                "        }\n" +
                "        setTimeout(() => { progress.style.display = 'none'; }, 400);\n" +
                "        loadFiles();\n" +
                "    }\n" +
                "    loadFiles();\n" +
                "</script>\n" +
                "</body>\n" +
                "</html>";

        return NanoHTTPD.newFixedLengthResponse(Status.OK, "text/html; charset=UTF-8", html);
    }

    // Listagem JSON para o Web UI
    @GetMethod("/api/files")
    public Response listFilesJson() {
        File defaultBucket = storageManager.getBucketDir("default");
        File[] files = defaultBucket.listFiles();
        StringBuilder json = new StringBuilder("[");
        if (files != null) {
            boolean first = true;
            for (File f : files) {
                if (f.isFile()) {
                    if (!first) json.append(",");
                    first = false;
                    json.append(String.format("{\"name\":\"%s\",\"size\":%d}", f.getName(), f.length()));
                }
            }
        }
        json.append("]");
        return NanoHTTPD.newFixedLengthResponse(Status.OK, "application/json", json.toString());
    }

    // Upload via formulário Web
    @PostMethod("/upload")
    public Response handleUpload(IHTTPSession session) {
        try {
            Map<String, String> files = new HashMap<>();
            session.parseBody(files);

            Map<String, String> parms = session.getParms();
            for (Map.Entry<String, String> entry : files.entrySet()) {
                if (entry.getKey().startsWith("file")) {
                    String tempPath = entry.getValue();
                    String originalName = parms.getOrDefault("filename", null);
                    if (originalName == null || originalName.trim().isEmpty()) {
                        originalName = parms.getOrDefault("file", "upload_" + System.currentTimeMillis());
                    }
                    if (originalName.contains("/") || originalName.contains("\\")) {
                        originalName = new File(originalName).getName();
                    }

                    File target = storageManager.resolveFile("default", originalName);

                    try (InputStream in = new FileInputStream(tempPath);
                         OutputStream out = new FileOutputStream(target)) {
                        FileUtils.copyStream(in, out, Long.MAX_VALUE);
                    }
                }
            }
            return NanoHTTPD.newFixedLengthResponse(Status.OK, "application/json", "{\"status\":\"ok\"}");
        } catch (Exception e) {
            return NanoHTTPD.newFixedLengthResponse(Status.INTERNAL_ERROR, "text/plain", "Upload falhou: " + e.getMessage());
        }
    }

    // Download direto para a interface Web
    @GetMethod("/download/:filename")
    public Response downloadFile(@PathVariable("filename") String filename) {
        try {
            File file = storageManager.resolveFile("default", filename);
            if (!file.exists() || !file.isFile()) {
                return NanoHTTPD.newFixedLengthResponse(Status.NOT_FOUND, "text/plain", "Arquivo não encontrado");
            }
            FileInputStream fis = new FileInputStream(file);
            Response res = NanoHTTPD.newFixedLengthResponse(Status.OK, FileUtils.getMimeType(filename), fis, file.length());
            res.addHeader("Content-Disposition", "inline; filename=\"" + file.getName() + "\"");
            return res;
        } catch (Exception e) {
            return NanoHTTPD.newFixedLengthResponse(Status.INTERNAL_ERROR, "text/plain", e.getMessage());
        }
    }

    // Exclusão de arquivo pela interface Web
    @DeleteMethod("/delete/:filename")
    public Response deleteFileWeb(@PathVariable("filename") String filename) {
        try {
            File file = storageManager.resolveFile("default", filename);
            if (file.exists() && file.isFile()) {
                file.delete();
            }
            return NanoHTTPD.newFixedLengthResponse(Status.OK, "application/json", "{\"status\":\"deleted\"}");
        } catch (Exception e) {
            return NanoHTTPD.newFixedLengthResponse(Status.INTERNAL_ERROR, "text/plain", e.getMessage());
        }
    }
}
