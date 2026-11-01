package com.github.matheuscruzsouza.androidserver.controller;

import android.os.Environment;

import com.github.matheuscruzsouza.androidserver.model.UserFile;
import com.github.matheuscruzsouza.nanospring.annotation.Autowired;
import com.github.matheuscruzsouza.nanospring.annotation.GetMethod;
import com.github.matheuscruzsouza.nanospring.annotation.PathVariable;
import com.github.matheuscruzsouza.nanospring.annotation.PostMethod;
import com.github.matheuscruzsouza.nanospring.annotation.RequestParam;
import com.github.matheuscruzsouza.nanospring.annotation.RestController;
import com.github.matheuscruzsouza.nanospring.annotation.UploadedFile;
import com.github.matheuscruzsouza.nanospring.ui.ModelAndView;
import com.github.matheuscruzsouza.androidserver.model.User;
import com.github.matheuscruzsouza.androidserver.service.UserService;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

@RestController("/web")
public class WebController {

    @Autowired
    private UserService userService;

    @Autowired
    private com.github.matheuscruzsouza.androidserver.service.FileService fileService;

    private UserService getUserService() {
        if (userService == null) {
            userService = new UserService();
        }
        return userService;
    }

    private com.github.matheuscruzsouza.androidserver.service.FileService getFileService() {
        if (fileService == null) {
            fileService = new com.github.matheuscruzsouza.androidserver.service.FileService();
        }
        return fileService;
    }

    @GetMethod("/profile/:id")
    public ModelAndView getUserProfile(@PathVariable("id") String id) {
        try {
            int userId = Integer.parseInt(id);
            User user = getUserService().findById(userId);
            if (user == null) {
                return new ModelAndView("user_profile")
                        .addObject("id", "")
                        .addObject("userExists", false)
                        .addObject("username", "Não encontrado")
                        .addObject("name", "Usuário #" + id + " não encontrado")
                        .addObject("files", java.util.Collections.emptyList());
            }
            
            java.util.List<com.github.matheuscruzsouza.androidserver.model.UserFile> userFiles = getFileService().getFilesByUser(userId);
            
            return new ModelAndView("user_profile")
                    .addObject("id", user.getId())
                    .addObject("userExists", true)
                    .addObject("username", user.getUsername())
                    .addObject("name", user.getName())
                    .addObject("files", userFiles != null ? userFiles : java.util.Collections.emptyList());
        } catch (Exception e) {
            return new ModelAndView("user_profile")
                    .addObject("id", "")
                    .addObject("userExists", false)
                    .addObject("username", "Inválido")
                    .addObject("name", "Identificador inválido: " + id)
                    .addObject("files", java.util.Collections.emptyList());
        }
    }

    @PostMethod(value="/upload", mimeType="text/html")
    public com.github.matheuscruzsouza.nanospring.http.ResponseEntity<String> handleUpload(
            @UploadedFile("documento") File arquivo,
            @RequestParam("documento") String nomeOriginal,
            @RequestParam("userId") String userIdStr
    ) {
        if (arquivo == null) {
            return com.github.matheuscruzsouza.nanospring.http.ResponseEntity.badRequest()
                    .body(buildFeedbackPage("Nenhum Arquivo Enviado", "Por favor, selecione um arquivo válido antes de submeter o formulário.", null, false, "/", "Voltar ao Início"));
        }
        File arquivoFinal = null;
        try {
            int userId = Integer.parseInt(userIdStr);
            User user = getUserService().findById(userId);
            if (user == null) {
                if (arquivo != null && arquivo.exists()) arquivo.delete();
                return com.github.matheuscruzsouza.nanospring.http.ResponseEntity.status(com.github.matheuscruzsouza.nanospring.http.HttpStatus.NOT_FOUND)
                        .body(buildFeedbackPage("Usuário Inválido", "O usuário associado ao upload não foi encontrado no banco de dados.", null, false, "/", "Voltar ao Início"));
            }

            // Estrutura organizada e legível na raiz do armazenamento: /sdcard/AndroidServer/users/{username}/files
            File rootDir = Environment.getExternalStorageDirectory();
            File pastaUserFiles = new File(rootDir, "AndroidServer/users/" + user.getUsername() + "/files");
            if (!pastaUserFiles.exists()) {
                pastaUserFiles.mkdirs();
            }

            if (nomeOriginal == null || nomeOriginal.isEmpty()) {
                nomeOriginal = "upload_" + System.currentTimeMillis() + ".dat";
            }

            arquivoFinal = new File(pastaUserFiles, nomeOriginal);
            if (arquivoFinal.exists()) {
                String nomeSemExt = nomeOriginal;
                String ext = "";
                int dotIdx = nomeOriginal.lastIndexOf('.');
                if (dotIdx > 0) {
                    nomeSemExt = nomeOriginal.substring(0, dotIdx);
                    ext = nomeOriginal.substring(dotIdx);
                }
                arquivoFinal = new File(pastaUserFiles, nomeSemExt + "_" + System.currentTimeMillis() + ext);
            }

            try (InputStream in = new FileInputStream(arquivo);
                 OutputStream out = new FileOutputStream(arquivoFinal)) {
                byte[] buf = new byte[1024];
                int len;
                while ((len = in.read(buf)) > 0) {
                    out.write(buf, 0, len);
                }
            }
            arquivo.delete();

            UserFile uf = new UserFile(
                    userId, arquivoFinal.getName(), arquivoFinal.getAbsolutePath(), System.currentTimeMillis()
            );
            getFileService().saveRecord(uf);

            return com.github.matheuscruzsouza.nanospring.http.ResponseEntity.status(com.github.matheuscruzsouza.nanospring.http.HttpStatus.CREATED)
                    .body(buildFeedbackPage("Upload Realizado com Sucesso!", "O arquivo foi persistido no armazenamento local do Android.", "📁 <strong>Arquivo:</strong> " + arquivoFinal.getName() + "<br>💾 <strong>Caminho:</strong> " + arquivoFinal.getAbsolutePath(), true, "/web/profile/" + userId, "Ver no Perfil do Usuário"));
        } catch (Exception e) {
            // Rollback transacional: deleta arquivo físico em caso de erro para evitar arquivo órfão
            if (arquivoFinal != null && arquivoFinal.exists()) {
                arquivoFinal.delete();
            }
            if (arquivo != null && arquivo.exists()) {
                arquivo.delete();
            }
            return com.github.matheuscruzsouza.nanospring.http.ResponseEntity.status(com.github.matheuscruzsouza.nanospring.http.HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(buildFeedbackPage("Falha no Processamento", "Ocorreu um erro interno durante o processamento do arquivo.", "Detalhes: " + e.getMessage(), false, "/web/profile/" + userIdStr, "Voltar ao Perfil"));
        }
    }

    @GetMethod(value="/download/:fileId", mimeType="application/octet-stream")
    public com.github.matheuscruzsouza.nanospring.http.ResponseEntity<?> downloadFile(@PathVariable("fileId") String fileId) {
        try {
            int id = Integer.parseInt(fileId);
            com.github.matheuscruzsouza.androidserver.model.UserFile uf = getFileService().getFileById(id);
            if (uf != null) {
                File f = new File(uf.getFilePath());
                if (f.exists()) return com.github.matheuscruzsouza.nanospring.http.ResponseEntity.ok(f);
            }
        } catch (Exception e) { e.printStackTrace(); }
        return com.github.matheuscruzsouza.nanospring.http.ResponseEntity.status(com.github.matheuscruzsouza.nanospring.http.HttpStatus.NOT_FOUND)
                .body(buildFeedbackPage("Arquivo Não Encontrado", "O arquivo solicitado com identificador #" + fileId + " não existe ou foi removido.", null, false, "/", "Voltar ao Início"));
    }

    @GetMethod(value="/html", mimeType="text/html")
    public String getHtmlPage() {
        return buildFeedbackPage(
                "Demonstração HTML Direta",
                "Esta página foi gerada como uma String HTML bruta retornada diretamente por um método anotado com @GetMethod(\"/html\"), sem o uso de templates Mustache.",
                "🌐 <strong>Controlador:</strong> <code>WebController.java</code><br>⚙️ <strong>MimeType:</strong> <code>text/html</code>",
                true,
                "/",
                "Voltar ao Dashboard"
        );
    }

    private String buildFeedbackPage(String title, String message, String detail, boolean isSuccess, String returnUrl, String returnLabel) {
        String icon = isSuccess ? "✅" : "⚠️";
        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html><html lang='pt-BR'><head><meta charset='utf-8'>");
        sb.append("<meta name='viewport' content='width=device-width, initial-scale=1.0'>");
        sb.append("<title>").append(title).append(" • AndroidServer</title>");
        sb.append("<link rel='stylesheet' href='/static/css/style.css'></head><body>");
        sb.append("<div class='container container-narrow'>");
        sb.append("<nav class='nav-bar'>");
        sb.append("<a href='/' class='nav-link'><span style='font-size: 18px;'>←</span> Início</a>");
        sb.append("<div class='badges'><span class='badge badge-live'><span class='badge-pulse'></span> Servidor Ativo</span></div>");
        sb.append("</nav>");
        sb.append("<div class='card' style='text-align: center; padding: 36px 20px;'>");
        sb.append("<div style='font-size: 44px; margin-bottom: 12px;'>").append(icon).append("</div>");
        sb.append("<h1 style='font-size: 22px; margin-bottom: 8px;'>").append(title).append("</h1>");
        sb.append("<p style='color: var(--text-muted); font-size: 15px; margin-bottom: 16px;'>").append(message).append("</p>");
        if (detail != null && !detail.isEmpty()) {
            sb.append("<div class='code-box' style='text-align: left;'>").append(detail).append("</div>");
        }
        sb.append("<div style='display: flex; justify-content: center; gap: 12px; margin-top: 24px; flex-wrap: wrap;'>");
        if (returnUrl != null && !returnUrl.isEmpty()) {
            sb.append("<a href='").append(returnUrl).append("' class='btn-action btn-primary'>").append(returnLabel != null ? returnLabel : "Voltar").append("</a>");
        }
        sb.append("<a href='/' class='btn-action'>Página Inicial</a>");
        sb.append("</div></div>");
        sb.append("<footer class='footer'><p><strong>AndroidServer</strong> • nano-spring 1.6.0</p></footer>");
        sb.append("</div></body></html>");
        return sb.toString();
    }
}
