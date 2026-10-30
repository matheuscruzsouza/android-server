package com.github.matheuscruzsouza.androidserver.repository;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.github.matheuscruzsouza.androidserver.model.IamUser;
import com.github.matheuscruzsouza.nanospring.annotation.Autowired;
import com.github.matheuscruzsouza.nanospring.annotation.Repository;

import java.util.ArrayList;
import java.util.List;

@Repository
public class IamUserRepository {

    @Autowired
    private SQLiteDatabase database;

    public IamUserRepository() {
        initDatabase(com.github.matheuscruzsouza.nanospring.server.Server.getContext());
    }

    public IamUserRepository(android.content.Context context) {
        initDatabase(context != null ? context : com.github.matheuscruzsouza.nanospring.server.Server.getContext());
    }

    private void initDatabase(android.content.Context ctx) {
        if (ctx != null) {
            try {
                this.database = ctx.openOrCreateDatabase("androidserver.db", android.content.Context.MODE_PRIVATE, null);
                if (this.database != null) {
                    this.database.enableWriteAheadLogging();
                }
            } catch (Exception ignored) {}
        }
    }

    public IamUserRepository(SQLiteDatabase database) {
        this.database = database;
    }

    public IamUser findByAccessKey(String accessKey) {
        if (database == null || !database.isOpen() || accessKey == null) return null;

        try (Cursor cursor = database.rawQuery(
                "SELECT id, name, access_key, secret_key, policy, enabled, created_at FROM iam_users WHERE access_key = ? AND enabled = 1 LIMIT 1",
                new String[]{accessKey})) {
            if (cursor.moveToFirst()) {
                return mapUser(cursor);
            }
        } catch (Exception ignored) {}
        return null;
    }

    public IamUser findBySecretKey(String secretKey) {
        if (database == null || !database.isOpen() || secretKey == null) return null;

        try (Cursor cursor = database.rawQuery(
                "SELECT id, name, access_key, secret_key, policy, enabled, created_at FROM iam_users WHERE secret_key = ? AND enabled = 1 LIMIT 1",
                new String[]{secretKey})) {
            if (cursor.moveToFirst()) {
                return mapUser(cursor);
            }
        } catch (Exception ignored) {}
        return null;
    }

    public List<IamUser> findAll() {
        List<IamUser> list = new ArrayList<>();
        if (database == null || !database.isOpen()) return list;

        try (Cursor cursor = database.rawQuery(
                "SELECT id, name, access_key, secret_key, policy, enabled, created_at FROM iam_users ORDER BY id ASC", null)) {
            while (cursor.moveToNext()) {
                list.add(mapUser(cursor));
            }
        } catch (Exception ignored) {}
        return list;
    }

    public boolean save(IamUser user) {
        if (database == null || !database.isOpen() || user == null) return false;

        ContentValues values = new ContentValues();
        values.put("name", user.getName());
        values.put("access_key", user.getAccessKey());
        values.put("secret_key", user.getSecretKey());
        values.put("policy", user.getPolicy() != null ? user.getPolicy() : "ReadOnly");
        values.put("enabled", user.isEnabled() ? 1 : 0);

        long id = database.insertWithOnConflict("iam_users", null, values, SQLiteDatabase.CONFLICT_REPLACE);
        if (id != -1) {
            user.setId(id);
            return true;
        }
        return false;
    }

    public boolean updatePolicy(String accessKey, String policy) {
        if (database == null || !database.isOpen() || accessKey == null) return false;
        ContentValues values = new ContentValues();
        values.put("policy", policy);
        int rows = database.update("iam_users", values, "access_key = ?", new String[]{accessKey});
        return rows > 0;
    }

    public boolean updateEnabled(String accessKey, boolean enabled) {
        if (database == null || !database.isOpen() || accessKey == null) return false;
        ContentValues values = new ContentValues();
        values.put("enabled", enabled ? 1 : 0);
        int rows = database.update("iam_users", values, "access_key = ?", new String[]{accessKey});
        return rows > 0;
    }

    public boolean updateSecretKey(String accessKey, String secretKey) {
        if (database == null || !database.isOpen() || accessKey == null) return false;
        ContentValues values = new ContentValues();
        values.put("secret_key", secretKey);
        int rows = database.update("iam_users", values, "access_key = ?", new String[]{accessKey});
        return rows > 0;
    }

    public boolean deleteByAccessKey(String accessKey) {
        if (database == null || !database.isOpen() || accessKey == null) return false;
        int rows = database.delete("iam_users", "access_key = ?", new String[]{accessKey});
        return rows > 0;
    }

    private IamUser mapUser(Cursor cursor) {
        IamUser user = new IamUser();
        user.setId(cursor.getLong(0));
        user.setName(cursor.getString(1));
        user.setAccessKey(cursor.getString(2));
        user.setSecretKey(cursor.getString(3));
        user.setPolicy(cursor.getString(4));
        user.setEnabled(cursor.getInt(5) == 1);
        user.setCreatedAt(cursor.getString(6));
        return user;
    }
}
