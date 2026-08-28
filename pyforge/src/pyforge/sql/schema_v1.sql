CREATE TABLE IF NOT EXISTS sessions (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    slice_id TEXT NOT NULL,
    started_at TEXT,
    ended_at TEXT,
    tags TEXT NOT NULL DEFAULT '[]'
);

CREATE TABLE IF NOT EXISTS slices (
    slice_id TEXT PRIMARY KEY,
    title TEXT NOT NULL,
    tags TEXT NOT NULL DEFAULT '[]'
);

CREATE TABLE IF NOT EXISTS gate_attempts (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    gate_name TEXT NOT NULL,
    slice_id TEXT NOT NULL,
    ok INTEGER NOT NULL,
    error TEXT,
    UNIQUE (gate_name, slice_id)
);

CREATE TABLE IF NOT EXISTS learning_events (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    slice_id TEXT NOT NULL,
    kind TEXT NOT NULL,
    week INTEGER NOT NULL,
    payload TEXT NOT NULL DEFAULT ''
);
