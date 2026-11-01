package com.github.matheuscruzsouza.androidserver.s3;

import com.github.matheuscruzsouza.androidserver.storage.FileUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Gerenciador de uploads divididos (Multipart Upload) do S4 Server.
 * Salva partes em disco (.staging) sem estourar memória no Android.
 */
public class MultipartUploadManager {

    private final File rootDir;

    public MultipartUploadManager(File rootDir) {
        this.rootDir = rootDir;
    }

    private File getStagingDir(String bucket, String uploadId) {
        File dir = new File(new File(rootDir, bucket), ".staging/" + uploadId);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return dir;
    }

    /**
     * Inicia um upload multipart gerando um uploadId único.
     */
    public String initiateMultipartUpload(String bucket, String key) {
        String uploadId = UUID.randomUUID().toString().replace("-", "");
        File staging = getStagingDir(bucket, uploadId);
        File meta = new File(staging, "upload.meta");
        try (FileOutputStream out = new FileOutputStream(meta)) {
            out.write(key.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        } catch (Exception ignored) {}
        return uploadId;
    }

    /**
     * Faz streaming da parte enviada diretamente para o arquivo da parte no disco.
     */
    public String savePart(String bucket, String uploadId, int partNumber, InputStream in, long length) throws Exception {
        File staging = getStagingDir(bucket, uploadId);
        File partFile = new File(staging, String.format("part_%05d.bin", partNumber));
        try (OutputStream out = new FileOutputStream(partFile)) {
            FileUtils.copyStream(in, out, length > 0 ? length : Long.MAX_VALUE);
        }
        return FileUtils.calculateMD5(partFile);
    }

    /**
     * Concatena todas as partes enviadas no arquivo final de destino e remove arquivos temporários.
     */
    public String completeMultipartUpload(String bucket, String key, String uploadId, File destFile) throws Exception {
        File staging = getStagingDir(bucket, uploadId);
        File[] partFiles = staging.listFiles((dir, name) -> name.startsWith("part_") && name.endsWith(".bin"));

        if (partFiles == null || partFiles.length == 0) {
            throw new IllegalStateException("Nenhuma parte encontrada para completar o upload");
        }

        List<File> sortedParts = new ArrayList<>();
        Collections.addAll(sortedParts, partFiles);
        sortedParts.sort((f1, f2) -> f1.getName().compareTo(f2.getName()));

        File parent = destFile.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }

        MessageDigest md5Concat = MessageDigest.getInstance("MD5");

        try (OutputStream out = new FileOutputStream(destFile)) {
            byte[] buf = new byte[8192];
            for (File p : sortedParts) {
                // Incorpora hash de cada parte para formar o ETag de Multipart padrão AWS: (md5(part_md5s)-num_parts)
                byte[] partMd5Bytes = computeFileMd5Bytes(p);
                md5Concat.update(partMd5Bytes);

                try (InputStream in = new FileInputStream(p)) {
                    int n;
                    while ((n = in.read(buf)) != -1) {
                        out.write(buf, 0, n);
                    }
                }
            }
        }

        // Limpeza de staging após sucesso
        abortMultipartUpload(bucket, uploadId);

        byte[] finalDigest = md5Concat.digest();
        StringBuilder hex = new StringBuilder();
        for (byte b : finalDigest) {
            hex.append(String.format("%02x", b));
        }

        return hex.toString() + "-" + sortedParts.size();
    }

    /**
     * Cancela e limpa os arquivos temporários do upload multipart.
     */
    public void abortMultipartUpload(String bucket, String uploadId) {
        File staging = new File(new File(rootDir, bucket), ".staging/" + uploadId);
        if (staging.exists()) {
            deleteRecursively(staging);
        }
    }

    private byte[] computeFileMd5Bytes(File f) throws Exception {
        MessageDigest md = MessageDigest.getInstance("MD5");
        try (InputStream in = new FileInputStream(f)) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) != -1) {
                md.update(buf, 0, n);
            }
        }
        return md.digest();
    }

    private void deleteRecursively(File f) {
        if (f.isDirectory()) {
            File[] subs = f.listFiles();
            if (subs != null) {
                for (File sub : subs) {
                    deleteRecursively(sub);
                }
            }
        }
        f.delete();
    }
}
