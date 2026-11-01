package com.github.matheuscruzsouza.androidserver.storage;

import android.content.Context;
import android.os.Environment;

import com.github.matheuscruzsouza.androidserver.model.IamUser;
import com.github.matheuscruzsouza.androidserver.model.S3ObjectItem;
import com.github.matheuscruzsouza.nanospring.annotation.Service;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

@Service
public class StorageManager {

    private final File rootDir;

    public StorageManager(Context context) {
        File externalStorage = Environment.getExternalStorageDirectory();
        if (externalStorage != null && externalStorage.canWrite()) {
            this.rootDir = new File(externalStorage, "ServerStorage");
        } else {
            this.rootDir = new File(context.getExternalFilesDir(null), "ServerStorage");
        }
        if (!this.rootDir.exists()) {
            this.rootDir.mkdirs();
        }
    }

    public File getRootDir() {
        return rootDir;
    }

    public String getUserDefaultBucket(IamUser user) {
        if (user == null) return "default";
        if (user.getAccessKey() != null && !user.getAccessKey().trim().isEmpty()) {
            return sanitizeBucketName(user.getAccessKey().toLowerCase());
        }
        return "default";
    }

    public static String sanitizeBucketName(String name) {
        if (name == null || name.trim().isEmpty()) return "default";
        String s = name.toLowerCase().replaceAll("[^a-z0-9_-]", "_");
        while (s.startsWith("_")) s = s.substring(1);
        while (s.endsWith("_")) s = s.substring(0, s.length() - 1);
        return s.isEmpty() ? "default" : s;
    }

    public boolean bucketExists(String bucketName) {
        if (bucketName == null || bucketName.trim().isEmpty()) return false;
        String sanitized = sanitizeBucketName(bucketName);
        File bucket = new File(rootDir, sanitized);
        return bucket.exists() && bucket.isDirectory();
    }

    public boolean createBucket(String bucketName) {
        if (bucketName == null || bucketName.trim().isEmpty()) return false;
        String sanitized = sanitizeBucketName(bucketName);
        File bucket = new File(rootDir, sanitized);
        if (bucket.exists()) {
            return false;
        }
        return bucket.mkdirs();
    }

    public boolean deleteBucket(String bucketName) {
        if (!bucketExists(bucketName)) return false;
        File bucket = new File(rootDir, sanitizeBucketName(bucketName));
        File[] contents = bucket.listFiles();
        if (contents != null && contents.length > 0) {
            return false; // Não vazio
        }
        return bucket.delete();
    }

    public File getBucketDir(String bucketName) {
        String sanitized = sanitizeBucketName(bucketName);
        File bucket = new File(rootDir, sanitized);
        if (!bucket.exists()) {
            bucket.mkdirs();
        }
        return bucket;
    }

    public File resolveFile(String bucketName, String key) {
        File bucket = getBucketDir(bucketName);
        if (key != null && key.startsWith("/")) {
            key = key.substring(1);
        }
        File resolved = new File(bucket, key != null ? key : "");
        if (!resolved.getAbsolutePath().startsWith(bucket.getAbsolutePath())) {
            throw new SecurityException("Acesso negado: fora do diretório do bucket");
        }
        return resolved;
    }

    public boolean createFolder(String bucketName, String folderPath) {
        if (folderPath == null || folderPath.trim().isEmpty()) return false;
        File targetDir = resolveFile(bucketName, folderPath.trim());
        if (!targetDir.exists()) {
            return targetDir.mkdirs();
        }
        return true;
    }

    public File[] listDirectChildren(IamUser user, String bucketName, String subPath) {
        if (user == null || !rootDir.exists()) return new File[0];
        boolean isAdmin = "Admin".equalsIgnoreCase(user.getPolicy()) || "*".equals(user.getPolicy());
        String userBucket = getUserDefaultBucket(user);
        if (!isAdmin && (bucketName == null || bucketName.isEmpty())) {
            bucketName = userBucket;
        }

        if (isAdmin && (bucketName == null || bucketName.isEmpty())) {
            File[] buckets = rootDir.listFiles(File::isDirectory);
            return buckets != null ? buckets : new File[0];
        }

        File targetDir = resolveFile(bucketName, subPath != null ? subPath : "");
        if (targetDir.exists() && targetDir.isDirectory()) {
            File[] files = targetDir.listFiles();
            return files != null ? files : new File[0];
        }
        return new File[0];
    }

    public List<S3ObjectItem> listObjectsForUser(IamUser user, String bucketFilter) {
        List<S3ObjectItem> result = new ArrayList<>();
        if (!rootDir.exists() || !rootDir.isDirectory() || user == null) return result;

        boolean isAdmin = "Admin".equalsIgnoreCase(user.getPolicy()) || "*".equals(user.getPolicy());
        String userBucket = getUserDefaultBucket(user);

        if (isAdmin) {
            if (bucketFilter != null && !bucketFilter.isEmpty() && !bucketFilter.equals("all")) {
                File bDir = getBucketDir(bucketFilter);
                collectFiles(bDir, bDir, bDir.getName(), result);
            } else {
                File[] bucketDirs = rootDir.listFiles(File::isDirectory);
                if (bucketDirs != null) {
                    for (File bDir : bucketDirs) {
                        collectFiles(bDir, bDir, bDir.getName(), result);
                    }
                }
            }
        } else {
            // Conta não-admin: estritamente restrita ao seu próprio bucket ou subpasta
            File userRoot = getBucketDir(userBucket);
            if (bucketFilter != null && !bucketFilter.isEmpty() && !bucketFilter.equals("all") && !bucketFilter.equals(userBucket)) {
                File subDir = new File(userRoot, bucketFilter);
                if (subDir.exists() && subDir.isDirectory()) {
                    collectFiles(subDir, userRoot, userBucket, result);
                } else {
                    collectFiles(userRoot, userRoot, userBucket, result);
                }
            } else {
                collectFiles(userRoot, userRoot, userBucket, result);
            }
        }

        return result;
    }

    public List<File> listBucketsForUser(IamUser user) {
        List<File> list = new ArrayList<>();
        if (!rootDir.exists() || !rootDir.isDirectory() || user == null) return list;

        boolean isAdmin = "Admin".equalsIgnoreCase(user.getPolicy()) || "*".equals(user.getPolicy());
        String userBucket = getUserDefaultBucket(user);

        if (isAdmin) {
            File[] bucketDirs = rootDir.listFiles(File::isDirectory);
            if (bucketDirs != null) {
                for (File b : bucketDirs) {
                    list.add(b);
                }
            }
        } else {
            File userDir = getBucketDir(userBucket);
            list.add(userDir);
            // Inclui subdiretórios criados dentro do bucket do usuário como pastas
            File[] subDirs = userDir.listFiles(File::isDirectory);
            if (subDirs != null) {
                for (File sd : subDirs) {
                    list.add(sd);
                }
            }
        }

        return list;
    }

    public List<S3ObjectItem> listAllObjects() {
        List<S3ObjectItem> result = new ArrayList<>();
        if (!rootDir.exists() || !rootDir.isDirectory()) return result;

        File[] bucketDirs = rootDir.listFiles(File::isDirectory);
        if (bucketDirs != null) {
            for (File bDir : bucketDirs) {
                collectFiles(bDir, bDir, bDir.getName(), result);
            }
        }
        return result;
    }

    public boolean deleteRecursively(File fileOrDir) {
        if (fileOrDir == null || !fileOrDir.exists()) return false;
        if (fileOrDir.isDirectory()) {
            File[] children = fileOrDir.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursively(child);
                }
            }
        }
        return fileOrDir.delete();
    }

    private void collectFiles(File currentDir, File bucketRoot, String bucketName, List<S3ObjectItem> list) {
        File[] files = currentDir.listFiles();
        if (files == null) return;

        for (File f : files) {
            if (f.isDirectory()) {
                collectFiles(f, bucketRoot, bucketName, list);
            } else if (f.isFile()) {
                String relPath = f.getAbsolutePath().substring(bucketRoot.getAbsolutePath().length());
                if (relPath.startsWith("/")) {
                    relPath = relPath.substring(1);
                }
                list.add(S3ObjectItem.fromFile(bucketName, relPath, f));
            }
        }
    }
}
