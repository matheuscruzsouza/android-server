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
        SQLiteDatabase db = getDatabase();
        if (db == null || !db.isOpen() || accessKey == null) return null;

        ensureColumnsExist();
        try (Cursor cursor = db.rawQuery(
                "SELECT id, name, access_key, secret_key, policy, enabled, created_at, password_hash, reset_token FROM iam_users WHERE access_key = ? AND enabled = 1 LIMIT 1",
                new String[]{accessKey})) {
            if (cursor.moveToFirst()) {
                return mapUser(cursor);
            }
        } catch (Exception ignored) {}
        return null;
    }

    public IamUser findByNameOrAccessKey(String username) {
        SQLiteDatabase db = getDatabase();
        if (db == null || !db.isOpen() || username == null) return null;

        ensureColumnsExist();
        try (Cursor cursor = db.rawQuery(
                "SELECT id, name, access_key, secret_key, policy, enabled, created_at, password_hash, reset_token FROM iam_users WHERE (access_key = ? OR name = ?) AND enabled = 1 LIMIT 1",
                new String[]{username, username})) {
            if (cursor.moveToFirst()) {
                return mapUser(cursor);
            }
        } catch (Exception ignored) {}
        return null;
    }

    public IamUser findBySecretKey(String secretKey) {
        SQLiteDatabase db = getDatabase();
        if (db == null || !db.isOpen() || secretKey == null) return null;

        ensureColumnsExist();
        try (Cursor cursor = db.rawQuery(
                "SELECT id, name, access_key, secret_key, policy, enabled, created_at, password_hash, reset_token FROM iam_users WHERE secret_key = ? AND enabled = 1 LIMIT 1",
                new String[]{secretKey})) {
            if (cursor.moveToFirst()) {
                return mapUser(cursor);
            }
        } catch (Exception ignored) {}
        return null;
    }

    public List<IamUser> findAll() {
        List<IamUser> list = new ArrayList<>();
        SQLiteDatabase db = getDatabase();
        if (db == null || !db.isOpen()) return list;

        ensureColumnsExist();
        try (Cursor cursor = db.rawQuery(
                "SELECT id, name, access_key, secret_key, policy, enabled, created_at, password_hash, reset_token FROM iam_users ORDER BY id ASC", null)) {
            while (cursor.moveToNext()) {
                list.add(mapUser(cursor));
            }
        } catch (Exception ignored) {}
        return list;
    }

    private SQLiteDatabase getDatabase() {
        if (this.database == null || !this.database.isOpen()) {
            initDatabase(com.github.matheuscruzsouza.nanospring.server.Server.getContext());
        }
        return this.database;
    }

    private void ensureColumnsExist() {
        SQLiteDatabase db = getDatabase();
        if (db == null || !db.isOpen()) return;
        try {
            db.execSQL("ALTER TABLE iam_users ADD COLUMN password_hash TEXT");
        } catch (Exception ignored) {}
        try {
            db.execSQL("ALTER TABLE iam_users ADD COLUMN reset_token TEXT");
        } catch (Exception ignored) {}
    }

    public boolean save(IamUser user) {
        SQLiteDatabase db = getDatabase();
        if (db == null || !db.isOpen() || user == null) return false;
        ensureColumnsExist();

        ContentValues values = new ContentValues();
        values.put("name", user.getName());
        values.put("access_key", user.getAccessKey());
        values.put("secret_key", user.getSecretKey());
        values.put("policy", user.getPolicy() != null ? user.getPolicy() : "ReadOnly");
        values.put("enabled", user.isEnabled() ? 1 : 0);
        if (user.getPasswordHash() != null) {
            values.put("password_hash", user.getPasswordHash());
        }
        if (user.getResetToken() != null) {
            values.put("reset_token", user.getResetToken());
        }

        long id = db.insertWithOnConflict("iam_users", null, values, SQLiteDatabase.CONFLICT_REPLACE);
        if (id != -1) {
            user.setId(id);
            return true;
        }
        return false;
    }

    public boolean updatePassword(String accessKey, String passwordHash) {
        SQLiteDatabase db = getDatabase();
        if (db == null || !db.isOpen() || accessKey == null) return false;
        ensureColumnsExist();
        ContentValues values = new ContentValues();
        values.put("password_hash", passwordHash);
        values.putNull("reset_token");
        int rows = db.update("iam_users", values, "access_key = ?", new String[]{accessKey});
        return rows > 0;
    }

    public boolean updateResetToken(String accessKey, String resetToken) {
        SQLiteDatabase db = getDatabase();
        if (db == null || !db.isOpen() || accessKey == null) return false;
        ensureColumnsExist();
        ContentValues values = new ContentValues();
        values.put("reset_token", resetToken);
        int rows = db.update("iam_users", values, "access_key = ?", new String[]{accessKey});
        return rows > 0;
    }

    public boolean updatePolicy(String accessKey, String policy) {
        SQLiteDatabase db = getDatabase();
        if (db == null || !db.isOpen() || accessKey == null) return false;
        ContentValues values = new ContentValues();
        values.put("policy", policy);
        int rows = db.update("iam_users", values, "access_key = ?", new String[]{accessKey});
        return rows > 0;
    }

    public boolean updateEnabled(String accessKey, boolean enabled) {
        SQLiteDatabase db = getDatabase();
        if (db == null || !db.isOpen() || accessKey == null) return false;
        ContentValues values = new ContentValues();
        values.put("enabled", enabled ? 1 : 0);
        int rows = db.update("iam_users", values, "access_key = ?", new String[]{accessKey});
        return rows > 0;
    }

    public boolean updateSecretKey(String accessKey, String secretKey) {
        SQLiteDatabase db = getDatabase();
        if (db == null || !db.isOpen() || accessKey == null) return false;
        ContentValues values = new ContentValues();
        values.put("secret_key", secretKey);
        int rows = db.update("iam_users", values, "access_key = ?", new String[]{accessKey});
        return rows > 0;
    }

    public boolean deleteByAccessKey(String accessKey) {
        SQLiteDatabase db = getDatabase();
        if (db == null || !db.isOpen() || accessKey == null) return false;
        int rows = db.delete("iam_users", "access_key = ?", new String[]{accessKey});
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
        if (cursor.getColumnCount() > 7) {
            user.setPasswordHash(cursor.getString(7));
        }
        if (cursor.getColumnCount() > 8) {
            user.setResetToken(cursor.getString(8));
        }
        return user;
    }
}
