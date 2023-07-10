package indi.sophronia.tools.util;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class Property {
    public static Properties properties() {
        Properties properties = new Properties(System.getProperties());
        try (InputStream inputStream = new FileInputStream("config.properties")) {
            properties.load(inputStream);
        } catch (IOException e) {
            System.err.println("fail to read config file");
            e.printStackTrace();
        }
        return properties;
    }
}
