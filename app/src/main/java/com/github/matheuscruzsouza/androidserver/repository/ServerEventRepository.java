package com.github.matheuscruzsouza.androidserver.repository;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.github.matheuscruzsouza.nanospring.annotation.Autowired;
import com.github.matheuscruzsouza.nanospring.annotation.Repository;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Repository
public class ServerEventRepository {

    @Autowired
    private SQLiteDatabase database;

    public List<Map<String, Object>> getMigrationsHistory() {
        List<Map<String, Object>> list = new ArrayList<>();
        if (database == null || !database.isOpen()) return list;

        try (Cursor cursor = database.rawQuery("SELECT installed_rank, version, description, script, installed_on, execution_time, success FROM nano_schema_history ORDER BY installed_rank ASC", null)) {
            while (cursor.moveToNext()) {
                Map<String, Object> row = new HashMap<>();
                row.put("installedRank", cursor.getInt(0));
                row.put("version", cursor.getInt(1));
                row.put("description", cursor.getString(2));
                row.put("script", cursor.getString(3));
                row.put("installedOn", cursor.getString(4));
                row.put("executionTimeMs", cursor.getLong(5));
                row.put("success", cursor.getInt(6) == 1);
                list.add(row);
            }
        } catch (Exception ignored) {}
        return list;
    }

    public List<Map<String, String>> getEvents() {
        List<Map<String, String>> list = new ArrayList<>();
        if (database == null || !database.isOpen()) return list;

        try (Cursor cursor = database.rawQuery("SELECT id, event_type, description, created_at FROM server_events ORDER BY id DESC LIMIT 10", null)) {
            while (cursor.moveToNext()) {
                Map<String, String> row = new HashMap<>();
                row.put("id", String.valueOf(cursor.getLong(0)));
                row.put("type", cursor.getString(1));
                row.put("description", cursor.getString(2));
                row.put("createdAt", cursor.getString(3));
                list.add(row);
            }
        } catch (Exception ignored) {}
        return list;
    }
}
