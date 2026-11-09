-- DIPU - MATH WHIZ database
-- The app uses SQLite: the database "math_whiz_db" is the file math_whiz_db.db,
-- created automatically in the project folder the first time the app runs.
-- You do not need to run this file by hand; it is here for documentation.

CREATE TABLE IF NOT EXISTS game_scores (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    player_name VARCHAR(100) NOT NULL,
    score       INT          NOT NULL,
    difficulty  VARCHAR(20)  NOT NULL,
    played_at   TIMESTAMP    NOT NULL
);

-- Save a game
INSERT INTO game_scores (player_name, score, difficulty, played_at)
VALUES ('Dipu', 10, 'NORMAL', datetime('now', 'localtime'));

-- Top 10 leaderboard (higher score first, earlier time wins a tie)
SELECT * FROM game_scores ORDER BY score DESC, played_at ASC LIMIT 10;

-- ---------------------------------------------------------------
-- MySQL version (only if you switch to MySQL):
-- CREATE DATABASE math_whiz_db;
-- USE math_whiz_db;
-- CREATE TABLE game_scores (
--     id          INT AUTO_INCREMENT PRIMARY KEY,
--     player_name VARCHAR(100) NOT NULL,
--     score       INT NOT NULL,
--     difficulty  VARCHAR(20) NOT NULL,
--     played_at   TIMESTAMP DEFAULT CURRENT_TIMESTAMP
-- );
