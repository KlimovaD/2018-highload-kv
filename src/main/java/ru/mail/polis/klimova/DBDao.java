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
            statement.execute("CREATE TABLE " + TABLE_STORAGE + "(" + COL_KEY+" VARCHAR (256) NOT NULL, " + COL_VALUE+" blob (1024))");
            statement.execute("ALTER TABLE " + TABLE_STORAGE + " ADD PRIMARY KEY (" + COL_KEY + ")");
            statement.close();
        }
    }

    @NotNull
    @Override
    public byte[] get(@NotNull byte[] key) throws NoSuchElementException, IOException {
        String query = "SELECT " + COL_VALUE + " from " + TABLE_STORAGE + " where " + COL_KEY + " = ?";
        try (PreparedStatement statement = connection.prepareStatement(query)){
            statement.setBytes(1, key);
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
        String query = "INSERT INTO " + TABLE_STORAGE + " (" + COL_VALUE + ", " + COL_KEY + ") values (?, ?) ON DUPLICATE KEY UPDATE " + COL_VALUE + " = ? ";
        try (PreparedStatement statement = connection.prepareStatement(query)){
            statement.setBytes(1, value);
            statement.setBytes(2, key);
            statement.setBytes(3, value);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new IOException(e);
        }
    }

    @Override
    public void remove(@NotNull byte[] key) throws IOException {
        String query = "DELETE FROM " + TABLE_STORAGE + " where " + COL_KEY + " = ?";
        try (PreparedStatement statement = connection.prepareStatement(query)){
            statement.setBytes(1, key);
            statement.execute();
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
