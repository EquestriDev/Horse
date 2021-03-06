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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
            String sql;
            sql = "CREATE TABLE IF NOT EXISTS `horses` ("
                + "`id` INTEGER PRIMARY KEY AUTOINCREMENT, "
                + "`created` DATETIME NOT NULL, "
                + "`data` TEXT"
                + ")";
            getConnection().createStatement().execute(sql);
            sql = "CREATE TABLE IF NOT EXISTS `brands` ("
                + "`id` INTEGER PRIMARY KEY AUTOINCREMENT, "
                + "`created` DATETIME NOT NULL, "
                + "`owner` VARCHAR(40) NOT NULL UNIQUE, "
                + "`format` VARCHAR(255) NOT NULL"
                + ")";
            getConnection().createStatement().execute(sql);
            sql = "CREATE TABLE IF NOT EXISTS `extra` ("
                + "`id` INTEGER PRIMARY KEY AUTOINCREMENT, "
                + "`horse_id` INTEGER NOT NULL, "
                + "`key` VARCHAR(40) NOT NULL, "
                + "`data` TEXT, "
                + "CONSTRAINT `unique_key` UNIQUE (`horse_id`, `key`) ON CONFLICT REPLACE)";
            getConnection().createStatement().execute(sql);
        } catch (SQLException sqle) {
            sqle.printStackTrace();
        }
    }

    boolean saveHorse(HorseData data) {
        if (data == null) throw new NullPointerException("data cannot be null");
        if (data.getId() >= 0) throw new IllegalArgumentException("saved data appears to exist in database: " + data);
        Gson gson = new Gson();
        String sql = "INSERT INTO `horses` (`created`, `data`) values (DATETIME('NOW'), ?)";
        try (PreparedStatement statement = getConnection().prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, gson.toJson(data));
            int ret = statement.executeUpdate();
            if (ret != 1) throw new SQLException("Failed to save horse");
            ResultSet result = statement.getGeneratedKeys();
            if (!result.next()) throw new SQLException("Failed to save horse");
            data.setId(result.getInt(1));
        } catch (SQLException sqle) {
            throw new IllegalStateException(sqle);
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
        ArrayList<HorseData> result = new ArrayList<>();
        try {
            ResultSet row = getConnection().createStatement().executeQuery("SELECT * FROM `horses`");
            while (row.next()) {
                try {
                    HorseData data = gson.fromJson(row.getString("data"), HorseData.class);
                    result.add(data);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        } catch (SQLException sqle) {
            sqle.printStackTrace();
        }
        return result;
    }

    boolean saveHorseBrand(HorseBrand brand) {
        if (brand == null) throw new NullPointerException("brand cannot be null");
        String sql = "INSERT INTO `brands` (created, owner, format) values (DATETIME('now'), ?, ?)";
        try (PreparedStatement statement = getConnection().prepareStatement(sql)) {
            statement.setString(1, brand.getOwner().toString());
            statement.setString(2, brand.getFormat());
            return 1 == statement.executeUpdate();
        } catch (SQLException sqle) {
            throw new IllegalStateException(sqle);
        }
    }

    List<HorseBrand> loadHorseBrands() {
        ArrayList<HorseBrand> result = new ArrayList<>();
        try {
            ResultSet row = getConnection().createStatement().executeQuery("SELECT * FROM `brands`");
            while (row.next()) {
                try {
                    UUID owner = UUID.fromString(row.getString("owner"));
                    String format = row.getString("format");
                    HorseBrand horseBrand = new HorseBrand(owner, format);
                    result.add(horseBrand);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        } catch (SQLException sqle) {
            sqle.printStackTrace();
        }
        return result;
    }

    boolean deleteHorseBrand(UUID owner) {
        if (owner == null) throw new NullPointerException("owner cannot be null");
        String sql = "DELETE FROM `brands` WHERE owner = ?";
        try (PreparedStatement statement = getConnection().prepareStatement(sql)) {
            statement.setString(1, owner.toString());
            return 1 == statement.executeUpdate();
        } catch (SQLException sqle) {
            throw new IllegalStateException(sqle);
        }
    }

    // --- Extra Data

    boolean saveExtraData(int horseId, String key, String value) {
        if (horseId < 0) throw new IllegalArgumentException("horse id must be positive: " + horseId);
        if (key == null) throw new IllegalArgumentException("key cannot be null");
        String sql = "INSERT INTO `extra` (`horse_id`, `key`, `data`) VALUES (?, ?, ?)";
        try (PreparedStatement statement = getConnection().prepareStatement(sql)) {
            statement.setInt(1, horseId);
            statement.setString(2, key);
            statement.setString(3, value);
            return 1 == statement.executeUpdate();
        } catch (SQLException sqle) {
            throw new IllegalStateException(sqle);
        }
    }

    Map<String, String> loadExtraData(int horseId) {
        String sql = "SELECT * FROM `extra` WHERE horse_id = ?";
        Map<String, String> result = new HashMap<>();
        try (PreparedStatement statement = getConnection().prepareStatement(sql)) {
            statement.setInt(1, horseId);
            ResultSet resultSet = statement.executeQuery();
            while (resultSet.next()) {
                String key = resultSet.getString("key");
                String value = resultSet.getString("data");
                result.put(key, value);
            }
        } catch (SQLException sqle) {
            throw new IllegalStateException(sqle);
        }
        return result;
    }
}
