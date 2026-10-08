package com.stockflow.database;

import java.io.FileInputStream;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

/**
 * Manages PostgreSQL JDBC connections for StockFlow.
 * Loads configuration directly from db.properties in the project root directory.
 */
public class DatabaseConnection {

    private static final String CONFIG_FILE_PATH = "db.properties";
    private static final Properties properties = new Properties();
    private static boolean isConfigLoaded = false;

    // Prevent direct instantiation
    private DatabaseConnection() {
    }

    /**
     * Loads the database configuration directly from db.properties in the project root.
     */
    private static synchronized void loadConfiguration() {
        if (isConfigLoaded) {
            return;
        }

        try (FileInputStream input = new FileInputStream(CONFIG_FILE_PATH)) {
            properties.load(input);
            isConfigLoaded = true;
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Database configuration file '" + CONFIG_FILE_PATH + "' could not be loaded. " +
                    "Please ensure it exists in the project root (refer to db.properties.example).",
                    e
            );
        }
    }

    /**
     * Creates and returns a new active Connection to the PostgreSQL database.
     *
     * @return an open java.sql.Connection
     * @throws SQLException if a database access error occurs or credentials fail
     */
    public static Connection getConnection() throws SQLException {
        loadConfiguration();

        String url = properties.getProperty("db.url");
        String user = properties.getProperty("db.user");
        String password = properties.getProperty("db.password");

        if (url == null || url.isBlank()) {
            throw new IllegalStateException("Missing required configuration property: 'db.url'");
        }
        if (user == null || user.isBlank()) {
            throw new IllegalStateException("Missing required configuration property: 'db.user'");
        }
        if (password == null) {
            throw new IllegalStateException("Missing required configuration property: 'db.password'");
        }

        return DriverManager.getConnection(url.trim(), user.trim(), password.trim());
    }
}
