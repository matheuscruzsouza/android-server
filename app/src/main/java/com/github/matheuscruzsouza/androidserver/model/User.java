package com.github.matheuscruzsouza.androidserver.model;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity
public class User {

    @PrimaryKey(autoGenerate = true)
    private int id;
    private String username;
    private String name;

    public User() {}

    @androidx.room.Ignore
    public User(String username, String name) {
        this.username = username;
        this.name = name;
    }

    public int getId() { return this.id; }
    public void setId(int id) { this.id = id; }

    public String getUsername() { return this.username; }
    public void setUsername(String username) { this.username = username; }

    public String getName() { return this.name; }
    public void setName(String name) { this.name = name; }
}
