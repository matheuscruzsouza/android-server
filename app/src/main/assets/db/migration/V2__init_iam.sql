CREATE TABLE IF NOT EXISTS iam_users (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    name TEXT NOT NULL,
    access_key TEXT UNIQUE NOT NULL,
    secret_key TEXT NOT NULL,
    policy TEXT NOT NULL DEFAULT 'ReadOnly',
    enabled INTEGER NOT NULL DEFAULT 1,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO iam_users (name, access_key, secret_key, policy)
VALUES ('Admin Master', 'AKIA_ADMIN_ANDROID', 'secret_admin_super_key_2026', 'Admin');

INSERT INTO iam_users (name, access_key, secret_key, policy)
VALUES ('ReadOnly Client', 'AKIA_READONLY_ANDROID', 'secret_readonly_key_2026', 'ReadOnly');

INSERT INTO iam_users (name, access_key, secret_key, policy)
VALUES ('Backup Service', 'AKIA_BACKUP_ANDROID', 'secret_backup_key_2026', 'WriteOnly');
