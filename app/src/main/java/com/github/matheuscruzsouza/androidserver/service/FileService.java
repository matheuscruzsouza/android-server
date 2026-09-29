package com.github.matheuscruzsouza.androidserver.service;

import com.github.matheuscruzsouza.nanospring.annotation.Service;
import com.github.matheuscruzsouza.nanospring.server.Server;
import com.github.matheuscruzsouza.androidserver.model.AppDatabase;
import com.github.matheuscruzsouza.androidserver.model.UserFile;
import com.github.matheuscruzsouza.androidserver.model.UserFileDao;

@Service
public class FileService {

    private UserFileDao userFileDao;

    public FileService() {
        if (Server.getContext() != null) {
            userFileDao = AppDatabase.getInstance(Server.getContext()).userFileDao();
        }
    }

    public FileService(android.content.Context context) {
        if (context != null) {
            userFileDao = AppDatabase.getInstance(context).userFileDao();
        } else if (Server.getContext() != null) {
            userFileDao = AppDatabase.getInstance(Server.getContext()).userFileDao();
        }
    }

    public java.util.List<UserFile> getFilesByUser(int userId) {
        if (userFileDao != null) return userFileDao.getFilesByUser(userId);
        return java.util.Collections.emptyList();
    }

    public UserFile getFileById(int id) {
        if (userFileDao != null) return userFileDao.getFileById(id);
        return null;
    }

    public java.util.List<UserFile> getAllFiles() {
        if (userFileDao != null) return userFileDao.getAllFiles();
        return java.util.Collections.emptyList();
    }

    public void deleteFile(UserFile file) {
        if (userFileDao != null) {
            userFileDao.delete(file);
        }
    }

    public void saveRecord(UserFile file) {
        if (userFileDao != null) {
            userFileDao.insert(file);
        }
    }
}
