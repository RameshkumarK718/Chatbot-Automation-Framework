package io.qameta.allure.utils;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;
import java.util.logging.Logger;

public class ConfigReader {
    private static final Logger logger = Logger.getLogger(ConfigReader.class.getName());
    private static Properties properties = new Properties();

    static {
        try {
            FileInputStream fis = new FileInputStream("src/test/resources/config.properties");
            properties.load(fis);
            fis.close();
            logger.info("Configuration properties loaded successfully.");
        } catch (IOException e) {
            logger.severe("Failed to load config.properties file: " + e.getMessage());
        }
    }

    public static String getProperty(String key) {
        String value = properties.getProperty(key);
        if (value == null) {
            logger.warning("Property key not found in config.properties: " + key);
        }
        return value;
    }
}
