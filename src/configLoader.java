package com.example;

import java.io.InputStream;
import java.io.IOException;
import java.util.Properties;

public class configLoader {
    public static Properties getProperties() {
        Properties properties = new Properties();
        // ClassLoader를 사용하여 config.properties 파일을 로드합니다.
        try (InputStream input = configLoader.class.getClassLoader().getResourceAsStream("config.properties")) {
            if (input == null) {
                System.out.println("Sorry, unable to find config.properties");
                return null; // 파일을 찾지 못했을 때
            }
            properties.load(input);
        } catch (IOException ex) {
            ex.printStackTrace();
        }
        return properties;
    }
}
