package cn.lingnc.aethergate.storage;

import cn.lingnc.aethergate.model.Waypoint;

import java.io.File;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class SqliteStorage {

    private final File dbFile;

    public SqliteStorage(File dataFolder) {
        this.dbFile = new File(dataFolder, "aether_gate.db");
    }

    public void init() throws SQLException {
        try (Connection conn = getConnection()) {
            try (Statement st = conn.createStatement()) {
                st.executeUpdate("CREATE TABLE IF NOT EXISTS waypoints (" +
                        "uuid TEXT PRIMARY KEY," +
                        "name TEXT NOT NULL," +
                        "world TEXT NOT NULL," +
                        "x REAL NOT NULL," +
                        "y REAL NOT NULL," +
                        "z REAL NOT NULL," +
                        "owner TEXT NOT NULL," +
                        "charges INTEGER NOT NULL," +
                        "activated INTEGER NOT NULL DEFAULT 1" +
                        ")");
            }
            ensureActivatedColumn(conn);
        }
    }

    private Connection getConnection() throws SQLException {
        return DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());
    }

    public List<Waypoint> loadAllWaypoints() throws SQLException {
        List<Waypoint> list = new ArrayList<>();
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT * FROM waypoints")) {
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                UUID id = UUID.fromString(rs.getString("uuid"));
                String name = rs.getString("name");
                String world = rs.getString("world");
                double x = rs.getDouble("x");
                double y = rs.getDouble("y");
                double z = rs.getDouble("z");
                UUID owner = UUID.fromString(rs.getString("owner"));
                int charges = rs.getInt("charges");
                boolean activated = rs.getInt("activated") != 0;
                list.add(new Waypoint(id, name, world, x, y, z, owner, charges, activated));
            }
        }
        return list;
    }

    public Waypoint findByLocation(String world, int x, int y, int z) throws SQLException {
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT * FROM waypoints WHERE world=? AND x=? AND y=? AND z=?")) {
            ps.setString(1, world);
            ps.setInt(2, x);
            ps.setInt(3, y);
            ps.setInt(4, z);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                UUID id = UUID.fromString(rs.getString("uuid"));
                String name = rs.getString("name");
                String w = rs.getString("world");
                double lx = rs.getDouble("x");
                double ly = rs.getDouble("y");
                double lz = rs.getDouble("z");
                UUID owner = UUID.fromString(rs.getString("owner"));
                int charges = rs.getInt("charges");
                boolean activated = rs.getInt("activated") != 0;
                return new Waypoint(id, name, w, lx, ly, lz, owner, charges, activated);
            }
        }
        return null;
    }

    public void saveOrUpdateWaypoint(Waypoint waypoint) throws SQLException {
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO waypoints(uuid,name,world,x,y,z,owner,charges,activated) VALUES(?,?,?,?,?,?,?,?,?) " +
                             "ON CONFLICT(uuid) DO UPDATE SET name=excluded.name, world=excluded.world, x=excluded.x, y=excluded.y, z=excluded.z, owner=excluded.owner, charges=excluded.charges, activated=excluded.activated")) {
            ps.setString(1, waypoint.getId().toString());
            ps.setString(2, waypoint.getName());
            ps.setString(3, waypoint.getWorldName());
            ps.setDouble(4, waypoint.getX());
            ps.setDouble(5, waypoint.getY());
            ps.setDouble(6, waypoint.getZ());
            ps.setString(7, waypoint.getOwner().toString());
            ps.setInt(8, waypoint.getCharges());
            ps.setInt(9, waypoint.isActivated() ? 1 : 0);
            ps.executeUpdate();
        }
    }

    private void ensureActivatedColumn(Connection conn) throws SQLException {
        DatabaseMetaData metaData = conn.getMetaData();
        try (ResultSet columns = metaData.getColumns(null, null, "waypoints", "activated")) {
            if (columns.next()) {
                return;
            }
        }
        try (Statement alter = conn.createStatement()) {
            alter.executeUpdate("ALTER TABLE waypoints ADD COLUMN activated INTEGER NOT NULL DEFAULT 1");
        }
    }

    public void deleteWaypoint(UUID id) throws SQLException {
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM waypoints WHERE uuid=?")) {
            ps.setString(1, id.toString());
            ps.executeUpdate();
        }
    }

    public void deleteWaypointAt(String world, int x, int y, int z) throws SQLException {
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM waypoints WHERE world=? AND x=? AND y=? AND z=?")) {
            ps.setString(1, world);
            ps.setInt(2, x);
            ps.setInt(3, y);
            ps.setInt(4, z);
            ps.executeUpdate();
        }
    }
}
