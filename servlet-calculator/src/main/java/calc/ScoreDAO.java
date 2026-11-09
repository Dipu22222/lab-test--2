package calc;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/** Talks to the Math Whiz database (SQLite file math_whiz_db.db) using JDBC. */
public class ScoreDAO {

    public static final Path DB_FILE = Paths.get("math_whiz_db.db").toAbsolutePath();
    private static final String URL = "jdbc:sqlite:" + DB_FILE;

    /** Creates the game_scores table if it does not exist yet. */
    public void init() throws SQLException {
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException e) {
            throw new SQLException("SQLite driver not found", e);
        }
        System.out.println("Math Whiz database file: " + DB_FILE);
        try (Connection c = DriverManager.getConnection(URL);
             Statement s = c.createStatement()) {
            s.executeUpdate("CREATE TABLE IF NOT EXISTS game_scores ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                    + "player_name VARCHAR(100) NOT NULL,"
                    + "score INT NOT NULL,"
                    + "difficulty VARCHAR(20) NOT NULL,"
                    + "played_at TIMESTAMP NOT NULL)");
        }
    }

    /** Saves one finished game. */
    public void save(String playerName, int score, String difficulty) throws SQLException {
        String sql = "INSERT INTO game_scores(player_name, score, difficulty, played_at) "
                + "VALUES (?, ?, ?, datetime('now','localtime'))";
        try (Connection c = DriverManager.getConnection(URL);
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, playerName);
            ps.setInt(2, score);
            ps.setString(3, difficulty);
            ps.executeUpdate();
        }
    }

    /** The ten best scores. Equal scores: the earlier one ranks higher. */
    public List<GameScore> topTen() throws SQLException {
        String sql = "SELECT id, player_name, score, difficulty, played_at FROM game_scores "
                + "ORDER BY score DESC, played_at ASC LIMIT 10";
        List<GameScore> list = new ArrayList<>();
        try (Connection c = DriverManager.getConnection(URL);
             Statement s = c.createStatement();
             ResultSet rs = s.executeQuery(sql)) {
            while (rs.next()) {
                list.add(new GameScore(rs.getInt("id"), rs.getString("player_name"),
                        rs.getInt("score"), rs.getString("difficulty"), rs.getString("played_at")));
            }
        }
        return list;
    }
}
