package net.equestriworlds.horse;

import com.google.gson.Gson;
import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
final class HorseDatabase {
    private final HorsePlugin plugin;
    private Connection cachedConnection = null;

    Connection getConnection() throws SQLException {
        if (this.cachedConnection == null || !this.cachedConnection.isValid(1)) {
            try {
                Class.forName("org.sqlite.JDBC");
            } catch (ClassNotFoundException cnfe) {
                cnfe.printStackTrace();
            }
            File dbdir = this.plugin.getDataFolder();
            dbdir.mkdirs();
            File dbfile = new File(dbdir, "horses.db");
            this.cachedConnection = DriverManager.getConnection("jdbc:sqlite:" + dbfile);
        }
        return this.cachedConnection;
    }

    void createTables() {
        try {
            String sql = "CREATE TABLE IF NOT EXISTS `horses` ("
                + "`id` INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "`data` TEXT"
                + ")";
            getConnection().createStatement().execute(sql);
        } catch (SQLException sqle) {
            sqle.printStackTrace();
        }
    }

    boolean saveHorse(HorseData data) {
        if (data == null) throw new NullPointerException("data cannot be null");
        if (data.getId() >= 0) throw new IllegalArgumentException("saved data appears to exist in database: " + data);
        Gson gson = new Gson();
        String sql = "INSERT INTO `horses` (data) values ('" + gson.toJson(data) + "')";
        try (PreparedStatement statement = getConnection().prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            int ret = statement.executeUpdate();
            if (ret != 1) throw new SQLException("Failed to save horse");
            ResultSet result = statement.getGeneratedKeys();
            if (!result.next()) throw new SQLException("Failed to save horse");
            data.setId(result.getInt(1));
        } catch (SQLException sqle) {
            sqle.printStackTrace();
            return false;
        }
        return true;
    }

    boolean updateHorse(HorseData data) {
        if (data == null) throw new NullPointerException("data cannot be null");
        if (data.getId() < 0) throw new IllegalArgumentException("updated data does not appear to exist in database: " + data);
        Gson gson = new Gson();
        int ret = 0;
        try {
            ret = getConnection().createStatement().executeUpdate("UPDATE `horses` SET `data` = '" + gson.toJson(data) + "' WHERE `id` = " + data.getId());
        } catch (SQLException sqle) {
            sqle.printStackTrace();
        }
        return ret == 1;
    }

    List<HorseData> loadHorses() {
        Gson gson = new Gson();
        List<HorseData> result = new ArrayList<>();
        try {
            ResultSet row = getConnection().createStatement().executeQuery("SELECT * FROM `horses`");
            while (row.next()) {
                HorseData data = gson.fromJson(row.getString("data"), HorseData.class);
                result.add(data);
            }
        } catch (SQLException sqle) {
            sqle.printStackTrace();
        }
        return result;
    }
}
