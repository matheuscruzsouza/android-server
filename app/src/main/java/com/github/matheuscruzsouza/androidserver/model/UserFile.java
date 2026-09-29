package com.github.matheuscruzsouza.androidserver.model;

import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(tableName = "UserFile",
        foreignKeys = @ForeignKey(entity = User.class,
                                  parentColumns = "id",
                                  childColumns = "userId",
                                  onDelete = ForeignKey.CASCADE),
        indices = {@Index("userId")})
public class UserFile {
    @PrimaryKey(autoGenerate = true)
    private int id;
    private int userId;
    private String fileName;
    private String filePath;
    private long uploadTimestamp;

    public UserFile(int userId, String fileName, String filePath, long uploadTimestamp) {
        this.userId = userId;
        this.fileName = fileName;
        this.filePath = filePath;
        this.uploadTimestamp = uploadTimestamp;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public int getUserId() { return userId; }
    public void setUserId(int userId) { this.userId = userId; }

    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }

    public String getFilePath() { return filePath; }
    public void setFilePath(String filePath) { this.filePath = filePath; }

    public long getUploadTimestamp() { return uploadTimestamp; }
    public void setUploadTimestamp(long uploadTimestamp) { this.uploadTimestamp = uploadTimestamp; }
}
