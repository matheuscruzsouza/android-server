package com.github.matheuscruzsouza.androidserver.service;

import com.github.matheuscruzsouza.nanospring.annotation.Service;
import com.github.matheuscruzsouza.nanospring.server.Server;
import com.github.matheuscruzsouza.androidserver.model.AppDatabase;
import com.github.matheuscruzsouza.androidserver.model.User;
import com.github.matheuscruzsouza.androidserver.model.UserDao;

import java.util.List;

@Service
public class UserService {

    private UserDao userDao;

    public UserService() {
        if (Server.getContext() != null) {
            userDao = AppDatabase.getInstance(Server.getContext()).userDao();
        }
    }

    public UserService(android.content.Context context) {
        if (context != null) {
            userDao = AppDatabase.getInstance(context).userDao();
        } else if (Server.getContext() != null) {
            userDao = AppDatabase.getInstance(Server.getContext()).userDao();
        }
    }

    private UserDao getUserDao() {
        if (userDao == null && Server.getContext() != null) {
            userDao = AppDatabase.getInstance(Server.getContext()).userDao();
        }
        return userDao;
    }

    public List<User> getUsers() {
        UserDao dao = getUserDao();
        return dao != null ? dao.getAllUsers() : java.util.Collections.emptyList();
    }

    public User findById(int id) {
        UserDao dao = getUserDao();
        return dao != null ? dao.findById(id) : null;
    }

    public User addUser(User user) {
        UserDao dao = getUserDao();
        if (dao != null) {
            long id = dao.insert(user);
            user.setId((int) id);
        }
        return user;
    }

    public void deleteUser(User user) {
        UserDao dao = getUserDao();
        if (dao != null) {
            dao.delete(user);
        }
    }
}
