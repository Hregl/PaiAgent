package com.paiagent.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

import javax.sql.DataSource;
import java.io.File;
import java.sql.Connection;
import java.sql.Statement;

@Configuration
public class DataInitConfig {

    @Value("${storage.audio-path:./data/audio}")
    private String audioPath;

    @Bean
    public CommandLineRunner initDatabase(DataSource dataSource, PasswordEncoder passwordEncoder) {
        return args -> {
            // Create data directories
            new File(audioPath).mkdirs();

            // Initialize database schema
            try (Connection conn = dataSource.getConnection();
                 Statement stmt = conn.createStatement()) {

                stmt.execute("CREATE TABLE IF NOT EXISTS users (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "username VARCHAR(50) UNIQUE NOT NULL," +
                    "password VARCHAR(255) NOT NULL," +
                    "role VARCHAR(20) DEFAULT 'admin'," +
                    "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");

                stmt.execute("CREATE TABLE IF NOT EXISTS workflows (" +
                    "id VARCHAR(36) PRIMARY KEY," +
                    "name VARCHAR(100) NOT NULL," +
                    "user_id INTEGER NOT NULL," +
                    "definition TEXT NOT NULL," +
                    "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                    "updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                    "FOREIGN KEY (user_id) REFERENCES users(id))");

                stmt.execute("CREATE TABLE IF NOT EXISTS execution_logs (" +
                    "id VARCHAR(36) PRIMARY KEY," +
                    "workflow_id VARCHAR(36) NOT NULL," +
                    "input TEXT," +
                    "output TEXT," +
                    "status VARCHAR(20)," +
                    "duration_ms INTEGER," +
                    "node_logs TEXT," +
                    "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                    "FOREIGN KEY (workflow_id) REFERENCES workflows(id))");

                // Seed admin user if not exists
                String encodedPassword = passwordEncoder.encode("admin123");
                stmt.execute("INSERT OR IGNORE INTO users (username, password, role) VALUES " +
                    "('admin', '" + encodedPassword + "', 'admin')");
            }
        };
    }
}
