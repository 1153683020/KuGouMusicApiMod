package com.azlight.kugoumusicapimod.api;

import com.azlight.kugoumusicapimod.util.KugouApiClient;
import com.azlight.kugoumusicapimod.util.KugouCrypto;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.azlight.kugoumusicapimod.util.KugouCrypto.signCloudKey;

public class CloudApi {
    // ==================== 云盘列表接口（已修复所有逻辑错误） ====================
    public static CompletableFuture<KugouApiClient.ApiResponse> getCloudList(int page, int pagesize, Map<String, String> cookie) {
        String userid = cookie.getOrDefault("userid", String.valueOf(KugouApiClient.getUserid()));
        String token = cookie.getOrDefault("token", KugouApiClient.getToken());
        String mid = cookie.getOrDefault("KUGOU_API_MID", KugouApiClient.getMid());
        long clienttime = System.currentTimeMillis() / 1000;

        Map<String, Object> dataMap = new LinkedHashMap<>();
        dataMap.put("page", page);
        dataMap.put("pagesize", pagesize);
        dataMap.put("getkmr", 1);

        String aesEncrypt = KugouCrypto.playlistAesEncrypt(dataMap);
        // 【修复1：解决编译错误】解析AES返回的JSON，提取str和key
        JsonObject encJson = JsonParser.parseString(aesEncrypt).getAsJsonObject();
        String aesStr = encJson.get("str").getAsString();
        String encKey = encJson.get("key").getAsString();

        Map<String, Object> rsaData = new LinkedHashMap<>();
        rsaData.put("aes", encKey);
        // 【修复2：解决UID溢出】用Long类型，和Node.js的JSON.number（双精度）逻辑对齐
        rsaData.put("uid", Long.parseLong(userid));
        rsaData.put("token", token);
        String p = KugouCrypto.rsaEncrypt2(rsaData).toUpperCase();

        // 显式传入lite参数，和Node逻辑完全对齐
        String key = KugouCrypto.signParamsKey(
                String.valueOf(clienttime),
                String.valueOf(KugouCrypto.LITE_APPID),
                String.valueOf(KugouCrypto.LITE_CLIENTVER)
        );

        Map<String, String> params = new LinkedHashMap<>();
        params.put("clienttime", String.valueOf(clienttime));
        params.put("mid", mid);
        params.put("key", key);
        params.put("clientver", String.valueOf(KugouCrypto.LITE_CLIENTVER));
        params.put("appid", String.valueOf(KugouCrypto.LITE_APPID));
        params.put("p", p);

        KugouApiClient.RequestOptions opts = new KugouApiClient.RequestOptions();
        opts.baseURL = "https://mcloudservice.kugou.com";
        opts.url = "/v1/get_list";
        opts.method = "POST";
        opts.params = params;
        // 发送原始二进制数据，和Node.js的Buffer逻辑对齐
        opts.data = Base64.getDecoder().decode(aesStr);
        opts.encryptType = "android";
        opts.cookie = cookie;
        opts.clearDefaultParams = true;
        opts.notSignature = true;
        opts.responseType = "arraybuffer";
        opts.headers = new HashMap<>();
        opts.headers.put("Content-Type", "application/octet-stream");

        return KugouApiClient.send(opts).thenApply(resp -> {
            if (resp.status == 200 && resp.body instanceof byte[]) {
                byte[] raw = (byte[]) resp.body;
                // 兼容明文/密文响应
                if (raw.length > 0 && raw[0] == '{') {
                    try {
                        resp.body = JsonParser.parseString(new String(raw, StandardCharsets.UTF_8)).getAsJsonObject();
                    } catch (Exception e) {
                        resp.body = new String(raw, StandardCharsets.UTF_8);
                    }
                } else {
                    String base64Body = Base64.getEncoder().encodeToString(raw);
                    try {
                        String decrypted = KugouCrypto.playlistAesDecrypt(encKey, base64Body);
                        try {
                            resp.body = JsonParser.parseString(decrypted).getAsJsonObject();
                        } catch (Exception e) {
                            resp.body = decrypted;
                        }
                    } catch (Exception e) {
                        resp.body = "解密失败: " + e.getMessage();
                    }
                }
            }
            return resp;
        });
    }

    // ==================== 云盘音乐URL获取接口 ====================
    public static CompletableFuture<KugouApiClient.ApiResponse> getCloudUrl(String hash, long albumAudioId, long audioId, String name, Map<String, String> cookie) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("hash", hash.toLowerCase());
        params.put("ssa_flag", "is_fromtrack");
        params.put("version", "20102");
        params.put("ssl", "0");
        params.put("album_audio_id", String.valueOf(albumAudioId));
        params.put("pid", "20026");
        params.put("audio_id", String.valueOf(audioId));
        params.put("kv_id", "2");
        params.put("key", KugouCrypto.signCloudKey(hash.toLowerCase(), "20026"));
        params.put("bucket", "musicclound");
        params.put("name", name != null ? name : "");
        params.put("with_res_tag", "0");

        KugouApiClient.RequestOptions opts = new KugouApiClient.RequestOptions();
        // 恢复正确的云盘专用接口路径
        opts.baseURL = "https://gateway.kugou.com";
        opts.url = "/bsstrackercdngz/v2/query_musicclound_url";
        opts.method = "GET";
        opts.params = params;
        opts.encryptType = "android";
        opts.cookie = cookie;
        opts.clearDefaultParams = false;  // 关键：注入dfid, mid等设备参数
        opts.notSignature = false;        // 让SDK自动生成签名
        opts.headers = new HashMap<>();

        return KugouApiClient.send(opts);
    }
}