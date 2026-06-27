/*
 * Copyright (c) 2024-2026 GeyserMC & NetherGamesMC
 * Licensed under the MIT license. See the Geyser LICENSE for details.
 */

package org.geysermc.extension.proxytransport;

import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Map;

/**
 * Configuration for the ProxyTransport extension, loaded from {@code config.yml} in the extension's data folder.
 */
public final class ProxyTransportConfig {
    private final String address;
    private final boolean tcpEnabled;
    private final int tcpPort;
    private final boolean quicEnabled;
    private final int quicPort;
    private final boolean disableRaknet;

    private ProxyTransportConfig(Map<String, Object> data) {
        this.address = string(data, "address", "0.0.0.0");
        Map<String, Object> tcp = section(data, "tcp");
        this.tcpEnabled = bool(tcp, "enabled", true);
        this.tcpPort = integer(tcp, "port", 19132);
        Map<String, Object> quic = section(data, "quic");
        this.quicEnabled = bool(quic, "enabled", false);
        this.quicPort = integer(quic, "port", 19133);
        this.disableRaknet = bool(data, "disable-raknet", true);
    }

    public String address() {
        return this.address;
    }

    public boolean tcpEnabled() {
        return this.tcpEnabled;
    }

    public int tcpPort() {
        return this.tcpPort;
    }

    public boolean quicEnabled() {
        return this.quicEnabled;
    }

    public int quicPort() {
        return this.quicPort;
    }

    /**
     * Whether Geyser's built-in RakNet listener should be disabled. When Geyser runs purely behind a
     * ProxyTransport proxy there is no reason to also expose RakNet.
     */
    public boolean disableRaknet() {
        return this.disableRaknet;
    }

    /**
     * Loads the config from {@code dataFolder/config.yml}, writing the bundled default first if it is absent.
     */
    @SuppressWarnings("unchecked")
    public static ProxyTransportConfig load(Path dataFolder) throws IOException {
        Files.createDirectories(dataFolder);
        Path configFile = dataFolder.resolve("config.yml");

        if (Files.notExists(configFile)) {
            try (InputStream defaults = ProxyTransportConfig.class.getResourceAsStream("/config.yml")) {
                if (defaults != null) {
                    Files.copy(defaults, configFile);
                }
            }
        }

        Map<String, Object> data = Collections.emptyMap();
        if (Files.exists(configFile)) {
            try (InputStream in = Files.newInputStream(configFile)) {
                Object loaded = new Yaml().load(in);
                if (loaded instanceof Map<?, ?> map) {
                    data = (Map<String, Object>) map;
                }
            }
        }
        return new ProxyTransportConfig(data);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> section(Map<String, Object> data, String key) {
        Object value = data.get(key);
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : Collections.emptyMap();
    }

    private static String string(Map<String, Object> data, String key, String def) {
        Object value = data.get(key);
        return value != null ? value.toString() : def;
    }

    private static boolean bool(Map<String, Object> data, String key, boolean def) {
        Object value = data.get(key);
        return value instanceof Boolean b ? b : def;
    }

    private static int integer(Map<String, Object> data, String key, int def) {
        Object value = data.get(key);
        return value instanceof Number n ? n.intValue() : def;
    }
}
