package ru.mail.polis.klimova;

import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.NoSuchElementException;

import org.h2.jdbcx.JdbcDataSource;
import org.jetbrains.annotations.NotNull;

import ru.mail.polis.KVDao;

public class DBDao implements KVDao {
    private static final String CONNECTION_WITH_DB_CLOSED = "08006";
    private static final String DATABASE_NOT_FOUND = "XJ004";
    private static final String TABLE_STORAGE = "KVSTORAGE";
    private static final String COL_KEY = "kv_key";
    private static final String COL_VALUE = "kv_value";
    private static final String COL_TIMESTAMP = "kv_timestamp";
    private static final String COL_DELETED = "kv_deleted";

    private Connection connection;

    private JdbcDataSource dataSource;

    public DBDao(File path) throws IOException {
        dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:" + path.getPath() + "/db;mode=MySQL;DB_CLOSE_DELAY=0");

        try {
            connection = dataSource.getConnection();
        } catch (Exception e) {
            throw new IOException(e);
        }

        try {
            createTable();
        } catch (SQLException e) {
            throw new IOException(e);
        }
    }

    private void createTable() throws SQLException {
        DatabaseMetaData databaseMetaData = connection.getMetaData();

        ResultSet resultSet = databaseMetaData.getTables(null, null, TABLE_STORAGE, null);
        if (!resultSet.next()){
            Statement statement = connection.createStatement();
            statement.execute("CREATE TABLE " + TABLE_STORAGE + "(" + COL_KEY+" VARCHAR (256) NOT NULL, " + COL_VALUE + " blob (1024), " + COL_TIMESTAMP + " BIGINT, " + COL_DELETED + " boolean )");
            statement.execute("ALTER TABLE " + TABLE_STORAGE + " ADD PRIMARY KEY (" + COL_KEY + ")");
            statement.close();
        }
    }

    @NotNull
    @Override
    public byte[] get(@NotNull byte[] key) throws NoSuchElementException, IOException {
        String query = "SELECT " + COL_VALUE + " from " + TABLE_STORAGE + " where " + COL_KEY + " = ? and " + COL_DELETED + " = ?";
        try (PreparedStatement statement = connection.prepareStatement(query)){
            statement.setBytes(1, key);
            statement.setBoolean(2, false);
            ResultSet resultSet = statement.executeQuery();
            if (resultSet.next()) {
                return resultSet.getBytes(COL_VALUE);
            } else {
                throw new NoSuchElementException();
            }
        } catch (SQLException e) {
            throw new IOException(e);
        }
    }

    @Override
    public void upsert(@NotNull byte[] key, @NotNull byte[] value) throws IOException {
        String query = "INSERT INTO " + TABLE_STORAGE + " (" + COL_VALUE + ", " + COL_KEY + ", " + COL_DELETED + ", "+ COL_TIMESTAMP + ") values (?, ?, ?, ?) ON DUPLICATE KEY UPDATE " + COL_VALUE + " = ?, " + COL_DELETED + " = ?, " + COL_TIMESTAMP + " = ?";
        try (PreparedStatement statement = connection.prepareStatement(query)){
            long time = System.currentTimeMillis();
            statement.setBytes(1, value);
            statement.setBytes(2, key);
            statement.setBoolean(3, false);
            statement.setLong(4, time);
            statement.setBytes(5, value);
            statement.setBoolean(6, false);
            statement.setLong(7, time);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new IOException(e);
        }
    }

    @Override
    public void remove(@NotNull byte[] key) throws IOException {
        String query = "UPDATE " + TABLE_STORAGE + " set " + COL_VALUE + " = ?, " + COL_TIMESTAMP + " = ?, " + COL_DELETED + " = ? where " + COL_KEY + " = ?";
        try (PreparedStatement statement = connection.prepareStatement(query)){
            statement.setBytes(1, new byte[0]);
            statement.setLong(2, System.currentTimeMillis());
            statement.setBoolean(3, true);
            statement.setBytes(4, key);
            statement.execute();
        } catch (SQLException e) {
            throw new IOException(e);
        }
    }

    @Override
    public long getUpdateTimeMillis(@NotNull byte[] key) throws NoSuchElementException, IOException {
        String query = "SELECT " + COL_TIMESTAMP + " from " + TABLE_STORAGE + " where " + COL_KEY + " = ?";
        try (PreparedStatement statement = connection.prepareStatement(query)){
            statement.setBytes(1, key);
            ResultSet resultSet = statement.executeQuery();
            if (resultSet.next()) {
                return resultSet.getLong(COL_TIMESTAMP);
            } else {
                throw new NoSuchElementException();
            }
        } catch (SQLException e) {
            throw new IOException(e);
        }
    }

    @Override
    public void close() throws IOException {
        try {
            connection.createStatement().execute("SHUTDOWN");
            connection.close();
        } catch (SQLException e){
            e.printStackTrace();
            String state = e.getSQLState();
            if (!state.equals(CONNECTION_WITH_DB_CLOSED) && !state.equals(DATABASE_NOT_FOUND)){
                throw new IOException(e);
            }
        }
    }
}
