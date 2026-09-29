package com.github.matheuscruzsouza.androidserver.model;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import java.util.List;

@Dao
public interface UserFileDao {
    @Insert
    void insert(UserFile userFile);

    @Query("SELECT * FROM UserFile WHERE userId = :userId")
    List<UserFile> getFilesByUser(int userId);
    @Query("SELECT * FROM UserFile WHERE id = :id")
    UserFile getFileById(int id);

    @Query("SELECT * FROM UserFile ORDER BY uploadTimestamp DESC")
    List<UserFile> getAllFiles();

    @androidx.room.Delete
    void delete(UserFile userFile);
}
