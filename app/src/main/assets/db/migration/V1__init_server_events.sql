CREATE TABLE IF NOT EXISTS server_events (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    event_type TEXT NOT NULL,
    description TEXT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO server_events (event_type, description) VALUES ('SYSTEM_STARTUP', 'Servidor AndroidServer inicializado com nano-spring 1.3.0');
