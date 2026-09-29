package com.github.matheuscruzsouza.androidserver.model;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import java.util.List;

@Dao
public interface UserDao {
    @Query("SELECT * FROM User")
    List<User> getAllUsers();

    @Query("SELECT * FROM User WHERE id = :id LIMIT 1")
    User findById(int id);

    @Insert
    long insert(User user);

    @androidx.room.Delete
    void delete(User user);
}
