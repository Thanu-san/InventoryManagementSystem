package com.stockflow.database;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

/**
 * Manages PostgreSQL JDBC connections for StockFlow.
 * Reads database configuration dynamically from db.properties without hard-coding credentials.
 */
public class DatabaseConnection {

    private static final String CONFIG_FILE_NAME = "db.properties";
    private static final Properties properties = new Properties();
    private static boolean isConfigLoaded = false;

    // Prevent direct instantiation
    private DatabaseConnection() {
    }

    /**
     * Resolves and opens an InputStream to the configuration file,
     * checking classpath first, then local working directory.
     */
    private static InputStream openConfigFile() {
        InputStream input = DatabaseConnection.class.getClassLoader().getResourceAsStream(CONFIG_FILE_NAME);
        if (input != null) {
            return input;
        }

        File localFile = new File(CONFIG_FILE_NAME);
        if (localFile.exists()) {
            try {
                return new FileInputStream(localFile);
            } catch (IOException e) {
                throw new IllegalStateException("Failed to read " + CONFIG_FILE_NAME + ": " + e.getMessage(), e);
            }
        }

        return null;
    }

    /**
     * Loads the database configuration properties.
     */
    private static synchronized void loadConfiguration() {
        if (isConfigLoaded) {
            return;
        }

        InputStream input = openConfigFile();
        if (input == null) {
            throw new IllegalStateException(
                    "Database configuration file '" + CONFIG_FILE_NAME + "' was not found. " +
                    "Please create it based on db.properties.example."
            );
        }

        try (input) {
            properties.load(input);
            isConfigLoaded = true;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load properties from " + CONFIG_FILE_NAME + ": " + e.getMessage(), e);
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
