-- Migracao de usuarios legados do app para a estrutura IAM
INSERT OR IGNORE INTO iam_users (name, access_key, secret_key, policy, enabled)
VALUES ('Matheus Cruz de Souza', 'matheuscruzsouza', 'secret_matheuscruzsouza_2026', 'Admin', 1);

INSERT OR IGNORE INTO iam_users (name, access_key, secret_key, policy, enabled)
VALUES ('Wagner Crespo', 'wagnercrespo', 'secret_wagnercrespo_2026', 'Admin', 1);
