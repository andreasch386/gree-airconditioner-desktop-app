package com.gree.config;

import java.io.*;
import java.nio.file.*;
import java.util.*;

public class Config {

    private final Properties props = new Properties();

    private static final String APP_NAME = "GreeAirConditioner";
    private static final String CONFIG_FILE = "config.properties";

    public Config() throws IOException {
        Path userConfigPath = getUserConfigPath();
        Path installConfigPath = getInstallConfigPath();

        if (Files.exists(userConfigPath)) {
            try (InputStream in = Files.newInputStream(userConfigPath)) {
                props.load(in);
                System.out.println("Loaded config from user dir: " + userConfigPath);
            }
        } else if (Files.exists(installConfigPath)) {
            try (InputStream in = Files.newInputStream(installConfigPath)) {
                props.load(in);
                System.out.println("Loaded config from install dir: " + installConfigPath);
            }
        } else {
            System.out.println("No config found, creating empty config in user dir...");
            save();
        }
    }

    private Path getUserConfigPath() {
        String appData = System.getenv("APPDATA"); // e.g. C:\Users\<user>\AppData\Roaming
        return Path.of(appData, APP_NAME, CONFIG_FILE);
    }

    private Path getInstallConfigPath() {
        String userDir = System.getProperty("user.dir");
        return Path.of(userDir, CONFIG_FILE);
    }

    /** Save to user config file */
    public void save() throws IOException {
        Path userConfigPath = getUserConfigPath();
        Files.createDirectories(userConfigPath.getParent());
        try (OutputStream out = Files.newOutputStream(userConfigPath)) {
            props.store(out, APP_NAME + " Configuration");
        }
    }

    /** Add or update a MAC → Name mapping */
    public void setDeviceName(String macAddress, String name) {
        props.setProperty(macAddress, name);
    }

    /** Get name for a given MAC, or fallback to MAC */
    public String getDeviceName(String macAddress) {
        return props.getProperty(macAddress, macAddress);
    }

    /** Get all device mappings */
    public Map<String, String> getAllDevices() {
        Map<String, String> devices = new LinkedHashMap<>();
        for (String key : props.stringPropertyNames()) {
            if (!key.equals("groups.count") && !key.equals("logging.level") && !key.equals("theme")) {
                devices.put(key, props.getProperty(key));
            }
        }
        return devices;
    }

    /** Remove device */
    public void removeDevice(String macAddress) {
        props.remove(macAddress);
    }
}
