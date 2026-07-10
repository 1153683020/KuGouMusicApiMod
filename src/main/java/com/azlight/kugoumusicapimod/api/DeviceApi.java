package com.azlight.kugoumusicapimod.api;

import com.azlight.kugoumusicapimod.util.KugouApiClient;
import com.azlight.kugoumusicapimod.util.KugouConfig;
import com.azlight.kugoumusicapimod.util.KugouCrypto;
import com.azlight.kugoumusicapimod.util.KugouUtils;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class DeviceApi {

    public static CompletableFuture<KugouApiClient.ApiResponse> registerDevice() {
        // 动态生成参数（与 Node 完全一致）
        Map<String, Object> dataMap = new LinkedHashMap<>();
        dataMap.put("availableRamSize", 4983533568L);
        dataMap.put("availableRomSize", 48114719L);
        dataMap.put("availableSDSize", 48114717L);
        dataMap.put("basebandVer", "");
        dataMap.put("batteryLevel", 100);
        dataMap.put("batteryStatus", 3);
        dataMap.put("brand", "Redmi");
        dataMap.put("buildSerial", "unknown");
        dataMap.put("device", "marble");
        dataMap.put("imei", "");
        dataMap.put("imsi", "");
        dataMap.put("manufacturer", "Xiaomi");
        dataMap.put("uuid", "");
        dataMap.put("accelerometer", false);
        dataMap.put("accelerometerValue", "");
        dataMap.put("gravity", false);
        dataMap.put("gravityValue", "");
        dataMap.put("gyroscope", false);
        dataMap.put("gyroscopeValue", "");
        dataMap.put("light", false);
        dataMap.put("lightValue", "");
        dataMap.put("magnetic", false);
        dataMap.put("magneticValue", "");
        dataMap.put("orientation", false);
        dataMap.put("orientationValue", "");
        dataMap.put("pressure", false);
        dataMap.put("pressureValue", "");
        dataMap.put("step_counter", false);
        dataMap.put("step_counterValue", "");
        dataMap.put("temperature", false);
        dataMap.put("temperatureValue", "");

        String aesEncrypt = KugouCrypto.playlistAesEncrypt(dataMap);
        JsonObject encJson = JsonParser.parseString(aesEncrypt).getAsJsonObject();
        String aesStr = encJson.get("str").getAsString();
        String encKey = encJson.get("key").getAsString();

        Map<String, Object> rsaData = new LinkedHashMap<>();
        rsaData.put("aes", encKey);
        rsaData.put("uid", 0);
        rsaData.put("token", "");
        String p = KugouCrypto.rsaEncrypt2(rsaData);

        Map<String, String> params = new LinkedHashMap<>();
        params.put("part", "1");
        params.put("platid", "1");
        params.put("p", p);
        long clienttime = System.currentTimeMillis() / 1000;
        params.put("clienttime", String.valueOf(clienttime));


        KugouApiClient.RequestOptions opts = new KugouApiClient.RequestOptions();
        opts.baseURL = "https://userservice.kugou.com";
        opts.url = "/risk/v2/r_register_dev";
        opts.method = "POST";
        opts.params = params;
        opts.data = aesStr;
        opts.encryptType = "android";
        opts.cookie = new HashMap<>();
        opts.responseType = "arraybuffer";
        opts.clearDefaultParams = false;
        opts.notSignature = false;
        opts.noAuth = true;
        opts.noAndroidHeaders = false;
        opts.noCookie = true;   // 关键：不发送Cookie
        opts.headers = new HashMap<>();
        opts.headers.put("Content-Type", "text/plain; charset=UTF-8");

        return KugouApiClient.send(opts).thenApply(resp -> {
            if (resp.status == 200 && resp.body instanceof byte[]) {
                byte[] raw = (byte[]) resp.body;
                String base64Body = Base64.getEncoder().encodeToString(raw);
                String plainText = new String(raw, StandardCharsets.UTF_8);
                if (plainText.startsWith("{")) {
                    try {
                        JsonObject body = JsonParser.parseString(plainText).getAsJsonObject();
                        resp.body = body;
                        if (body.has("status") && body.get("status").getAsInt() == 1) {
                            JsonElement dataElem = body.get("data");
                            if (dataElem != null && !dataElem.isJsonNull()) {
                                JsonObject data = dataElem.getAsJsonObject();
                                if (data.has("dfid")) {
                                    String dfid = data.get("dfid").getAsString();
                                    KugouApiClient.setDfid(dfid);
                                }
                            }
                        } else {
                            System.err.println("[Kugou] 设备注册失败: " + body);
                        }
                        return resp;
                    } catch (Exception e) {
                        System.err.println("[Kugou] 注册响应解析失败: " + e.getMessage());
                    }
                }
                // 如果不是明文，尝试AES解密
                try {
                    String decrypted = KugouCrypto.playlistAesDecrypt(encKey, base64Body);
                    JsonObject body = JsonParser.parseString(decrypted).getAsJsonObject();
                    resp.body = body;
                    if (body.has("status") && body.get("status").getAsInt() == 1) {
                        JsonElement dataElem = body.get("data");
                        if (dataElem != null && !dataElem.isJsonNull()) {
                            JsonObject data = dataElem.getAsJsonObject();
                            if (data.has("dfid")) {
                                String dfid = data.get("dfid").getAsString();
                                KugouApiClient.setDfid(dfid);
                                KugouConfig config = KugouConfig.getInstance();
                                config.dfid = dfid;
                                config.save();
                                System.out.println("[Kugou] 设备注册成功，dfid: " + dfid);
                            }
                        }
                    } else {
                        System.err.println("[Kugou] 设备注册失败（解密后）: " + body);
                    }
                } catch (Exception ex) {
                    System.err.println("[Kugou] AES 解密失败: " + ex.getMessage());
                }
            }
            return resp;
        }).exceptionally(throwable -> {
            System.err.println("[Kugou] 设备注册异常: " + throwable.getMessage());
            return null;
        });
    }
}