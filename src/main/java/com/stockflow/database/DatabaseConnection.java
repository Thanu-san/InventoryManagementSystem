package com.stockflow.database;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

/**
 * Manages PostgreSQL JDBC connections for StockFlow.
 * Supports configuration via:
 * 1. Java System properties (-Ddb.url, -Ddb.user, -Ddb.password)
 * 2. Environment variables (STOCKFLOW_DB_URL, STOCKFLOW_DB_USER, STOCKFLOW_DB_PASSWORD)
 * 3. Configuration file (db.properties in the project root)
 */
public class DatabaseConnection {

    private static final String CONFIG_FILE_PATH = "db.properties";
    private static final Properties properties = new Properties();
    private static boolean isConfigLoaded = false;

    // Prevent direct instantiation
    private DatabaseConnection() {
    }

    /**
     * Loads the database configuration directly from db.properties in the project root if present.
     */
    private static synchronized void loadConfiguration() {
        if (isConfigLoaded) {
            return;
        }

        File configFile = new File(CONFIG_FILE_PATH);
        if (configFile.exists()) {
            try (FileInputStream input = new FileInputStream(configFile)) {
                properties.load(input);
            } catch (IOException e) {
                System.err.println("Warning: Could not read configuration file '" + CONFIG_FILE_PATH + "': " + e.getMessage());
            }
        }
        isConfigLoaded = true;
    }

    private static String resolveProperty(String key, String envVar) {
        String sysProp = System.getProperty(key);
        if (sysProp != null && !sysProp.isBlank()) {
            return sysProp.trim();
        }
        String envVal = System.getenv(envVar);
        if (envVal != null && !envVal.isBlank()) {
            return envVal.trim();
        }
        String propVal = properties.getProperty(key);
        if (propVal != null && !propVal.isBlank()) {
            return propVal.trim();
        }
        return null;
    }

    /**
     * Creates and returns a new active Connection to the PostgreSQL database.
     *
     * @return an open java.sql.Connection
     * @throws SQLException if a database access error occurs or credentials fail
     */
    public static Connection getConnection() throws SQLException {
        loadConfiguration();

        String url = resolveProperty("db.url", "STOCKFLOW_DB_URL");
        String user = resolveProperty("db.user", "STOCKFLOW_DB_USER");
        String password = resolveProperty("db.password", "STOCKFLOW_DB_PASSWORD");

        if (url == null || url.isBlank()) {
            throw new IllegalStateException("Missing required configuration property: 'db.url' (or env 'STOCKFLOW_DB_URL')");
        }
        if (user == null || user.isBlank()) {
            throw new IllegalStateException("Missing required configuration property: 'db.user' (or env 'STOCKFLOW_DB_USER')");
        }
        if (password == null) {
            throw new IllegalStateException("Missing required configuration property: 'db.password' (or env 'STOCKFLOW_DB_PASSWORD')");
        }

        return DriverManager.getConnection(url, user, password);
    }
}
