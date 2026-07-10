package com.azlight.kugoumusicapimod.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.*;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

public class KugouConfig {
    public String dfid = "";
    public String mid = "";
    public String token = "";
    public long userid = 0;
    public String guid = UUID.randomUUID().toString();       // 设备GUID
    public String devId = KugouUtils.randomString(10).toUpperCase(); // 设备开发标识
    public String mac = "02:00:00:00:00:00";                // MAC地址
    public long tExpireTime = 0; // 登录过期时间戳（秒）

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static KugouConfig instance;

    public static KugouConfig getInstance() {
        if (instance == null) {
            instance = load();
        }
        return instance;
    }

    public static Path getConfigPath() {
        return FabricLoader.getInstance().getConfigDir().resolve("kugou_config.json");
    }

    public static KugouConfig load() {
        Path path = getConfigPath();
        if (path.toFile().exists()) {
            try (Reader reader = new FileReader(path.toFile())) {
                return GSON.fromJson(reader, KugouConfig.class);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return new KugouConfig();
    }

    public void save() {
        Path path = getConfigPath();
        try (Writer writer = new FileWriter(path.toFile())) {
            GSON.toJson(this, writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}