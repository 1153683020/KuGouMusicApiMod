package com.azlight.kugoumusicapimod.api;

import com.azlight.kugoumusicapimod.util.KugouApiClient;
import com.azlight.kugoumusicapimod.util.KugouConfig;
import com.azlight.kugoumusicapimod.util.KugouCrypto;
import com.azlight.kugoumusicapimod.util.KugouUtils;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import com.google.gson.Gson;

public class LoginApi {

    // lite 模式专用密钥（与 login_cellphone.js 一致）
    private static final String LITE_T1_KEY = "5e4ef500e9597fe004bd09a46d8add98";
    private static final String LITE_T1_IV  = "04bd09a46d8add98";
    private static final String LITE_T2_KEY = "fd14b35e3f81af3817a20ae7adae7020";
    private static final String LITE_T2_IV  = "17a20ae7adae7020";
    // token 刷新密钥
    private static final String LITE_TOKEN_KEY = "c24f74ca2820225badc01946dba4fdf7";
    private static final String LITE_TOKEN_IV  = "adc01946dba4fdf7";

    /**
     * 发送手机验证码
     */
    public static CompletableFuture<KugouApiClient.ApiResponse> sendCaptcha(String mobile, String mid) {
        String uuid = UUID.randomUUID().toString().replace("-", "").substring(0, 32);
        long clienttime = System.currentTimeMillis() / 1000;
        String dfid = KugouApiClient.getDfid();
        if (dfid == null || dfid.isEmpty() || "-".equals(dfid)) {
            dfid = KugouUtils.randomString(24).toLowerCase();
        }
        Map<String, String> body = new LinkedHashMap<>();
        body.put("plat", "1");
        body.put("businessid", "5");
        body.put("mobile", mobile);
        Map<String, String> queryParams = new LinkedHashMap<>();
        queryParams.put("dfid", dfid);
        queryParams.put("mid", mid);
        queryParams.put("appid", "3116");
        queryParams.put("clientver", "10645");
        queryParams.put("clienttime", String.valueOf(clienttime));
        queryParams.put("uuid", uuid);
        Map<String, String> headers = new HashMap<>();
        headers.put("KG-THash", "4f6125c");
        headers.put("KG-Rec", "1");
        headers.put("KG-RC", "1");
        headers.put("User-Agent", "Android9-1070-10645-18-0-SendMobileCodeProtocolV7-wifi");
        headers.put("Content-Type", "application/json; charset=utf-8");
        KugouApiClient.RequestOptions opts = new KugouApiClient.RequestOptions();
        opts.baseURL = "http://login.user.kugou.com";
        opts.url = "/v7/send_mobile_code";
        opts.method = "POST";
        opts.params = queryParams;
        opts.data = body;
        opts.encryptType = "android";
        opts.noAuth = true;
        opts.headers = headers;
        return KugouApiClient.send(opts);
    }

    /**
     * 手机验证码登录
     */
    public static CompletableFuture<KugouApiClient.ApiResponse> loginByVerifyCode(String mobile, String code, String guid, String mac, String devId, String dfid) {
        long dateTime = System.currentTimeMillis();
        String encryptJson = KugouCrypto.cryptoAesEncrypt(
                Map.of("mobile", mobile, "code", code),
                null, null
        ).toString();
        JsonObject encryptObj = JsonParser.parseString(encryptJson).getAsJsonObject();
        String encStr = encryptObj.get("str").getAsString();
        String aesKey = encryptObj.get("key").getAsString();
        String mobileMask = mobile.substring(0, 2) + "*****" + mobile.substring(10, 11);
        String t2Plain = guid + "|0f607264fc6318a92b9e13c65db7cd3c|" + mac + "|" + devId + "|" + dateTime;
        String t2 = KugouCrypto.cryptoAesEncrypt(t2Plain, LITE_T2_KEY, LITE_T2_IV).toString();
        String t1 = KugouCrypto.cryptoAesEncrypt("|" + dateTime, LITE_T1_KEY, LITE_T2_IV).toString();
        Map<String, Object> pkData = new HashMap<>();
        pkData.put("clienttime_ms", dateTime);
        pkData.put("key", aesKey);
        String pk = KugouCrypto.cryptoRSAEncrypt(pkData, null).toUpperCase();
        String keyParam = KugouCrypto.signParamsKey(String.valueOf(dateTime), null, null);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("plat", 1);
        data.put("support_multi", 1);
        data.put("t1", t1);
        data.put("t2", t2);
        data.put("clienttime_ms", dateTime);
        data.put("mobile", mobileMask);
        data.put("key", keyParam);
        data.put("pk", pk);
        data.put("params", encStr);
        data.put("dfid", dfid);
        data.put("dev", devId);
        data.put("gitversion", "5f0b7c4");
        if (dfid.equals("-")) {
            dfid = KugouUtils.randomString(24);
            KugouApiClient.setDfid(dfid);
        }
        Map<String, String> headers = new HashMap<>();
        headers.put("support-calm", "1");
        headers.put("User-Agent", "Android16-1070-11440-130-0-LOGIN-wifi");
        headers.put("Content-Type", "application/json; charset=utf-8");
        Map<String, String> cookie = new HashMap<>();
        cookie.put("dfid", dfid);
        KugouApiClient.RequestOptions opts = new KugouApiClient.RequestOptions();
        opts.baseURL = "https://loginserviceretry.kugou.com";
        opts.url = "/v7/login_by_verifycode";
        opts.method = "POST";
        opts.data = new Gson().toJson(data);
        opts.encryptType = "android";
        opts.headers = headers;
        opts.cookie = cookie;
        return KugouApiClient.send(opts).thenApply(resp -> {
            if (resp.status == 200 && resp.body instanceof JsonObject) {
                JsonObject body = (JsonObject) resp.body;
                if (body.has("status") && body.get("status").getAsInt() == 1) {
                    JsonObject dataObj = body.has("data") && body.get("data").isJsonObject() ? body.getAsJsonObject("data") : null;
                    if (dataObj != null && dataObj.has("secu_params")) {
                        JsonElement secuElem = dataObj.get("secu_params");
                        if (secuElem.isJsonPrimitive() && secuElem.getAsJsonPrimitive().isString()) {
                            String token = secuElem.getAsString();
                            resp.cookies.put("token", token);
                            KugouApiClient.setToken(token);
                            if (dataObj.has("userid")) {
                                resp.cookies.put("userid", dataObj.get("userid").getAsString());
                                KugouApiClient.setUserid(dataObj.get("userid").getAsLong());
                            }
                            // 保存过期时间
                            saveExpireTime(dataObj);
                            KugouConfig config = KugouConfig.getInstance();
                            config.token = token;
                            config.userid = KugouApiClient.getUserid();
                            config.save();
                            return resp;
                        }
                        String secuParams = dataObj.get("secu_params").getAsString();
                        String decryptKeyHex = KugouCrypto.cryptoMd5(aesKey).substring(0, 32);
                        String decryptIvHex = KugouCrypto.cryptoMd5(aesKey).substring(16, 32);
                        String decrypted = KugouCrypto.cryptoAesDecrypt(secuParams, decryptKeyHex, decryptIvHex);
                        try {
                            JsonObject tokenObj = JsonParser.parseString(decrypted).getAsJsonObject();
                            for (String key : tokenObj.keySet()) {
                                resp.cookies.put(key, tokenObj.get(key).getAsString());
                            }
                        } catch (Exception e) {
                            resp.cookies.put("token", decrypted);
                            KugouApiClient.setToken(decrypted);
                        }
                    }
                    safePutCookie(resp, "t1", dataObj, "t1");
                    safePutCookie(resp, "token", dataObj, "token");
                    safePutCookie(resp, "userid", dataObj, "userid");
                    safePutCookie(resp, "vip_type", dataObj, "vip_type");
                    safePutCookie(resp, "vip_token", dataObj, "vip_token");
                    // 保存过期时间
                    saveExpireTime(dataObj);
                    if (resp.cookies.containsKey("token")) {
                        KugouApiClient.setToken(resp.cookies.get("token"));
                        KugouApiClient.setUserid(Long.parseLong(resp.cookies.getOrDefault("userid", "0")));
                    }
                } else {
                    System.out.println("[Login] 登录失败，错误码: " + body.get("status") + ", 详情: " + body.get("data").getAsString());
                }
            } else {
                System.out.println("[Login] 请求异常，状态码: " + resp.status);
            }
            return resp;
        });
    }

    /**
     * 二维码 key 生成
     */
    private static final int APPID = KugouCrypto.APPID;
    public static CompletableFuture<KugouApiClient.ApiResponse> generateQrKey(Map<String, String> cookie) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("appid", "1001");
        params.put("type", "1");
        params.put("plat", "4");
        params.put("qrcode_txt", "https://h5.kugou.com/apps/loginQRCode/html/index.html?appid=" + APPID + "&");
        params.put("srcappid", "2919");
        params.put("clientver", "20489");
        params.put("dfid", KugouApiClient.getDfid());
        params.put("mid", KugouApiClient.getMid());
        params.put("uuid", "-");
        params.put("clienttime", String.valueOf(System.currentTimeMillis() / 1000));
        KugouApiClient.RequestOptions opts = new KugouApiClient.RequestOptions();
        opts.baseURL = "https://login-user.kugou.com";
        opts.url = "/v2/qrcode";
        opts.method = "GET";
        opts.params = params;
        opts.encryptType = "web";
        opts.cookie = cookie;
        opts.clearDefaultParams = true;
        opts.notSignature = false;
        opts.noAuth = true;
        return KugouApiClient.send(opts);
    }

    /**
     * 检查二维码扫码状态
     */
    public static CompletableFuture<KugouApiClient.ApiResponse> checkQrStatus(String key, Map<String, String> cookie) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("plat", "4");
        params.put("appid", String.valueOf(APPID));
        params.put("srcappid", "2919");
        params.put("qrcode", key);
        params.put("clientver", "20489");
        params.put("dfid", KugouApiClient.getDfid());
        params.put("mid", KugouApiClient.getMid());
        params.put("uuid", "-");
        params.put("clienttime", String.valueOf(System.currentTimeMillis() / 1000));
        KugouApiClient.RequestOptions opts = new KugouApiClient.RequestOptions();
        opts.baseURL = "https://login-user.kugou.com";
        opts.url = "/v2/get_userinfo_qrcode";
        opts.method = "GET";
        opts.params = params;
        opts.encryptType = "web";
        opts.cookie = cookie;
        opts.clearDefaultParams = true;
        opts.notSignature = false;
        opts.noAuth = true;
        return KugouApiClient.send(opts).thenApply(resp -> {
            if (resp.status == 200 && resp.body instanceof JsonObject) {
                JsonObject body = (JsonObject) resp.body;
                JsonElement dataElem = body.get("data");
                if (dataElem != null && dataElem.isJsonObject()) {
                    JsonObject dataObj = dataElem.getAsJsonObject();
                    if (dataObj.has("status") && dataObj.get("status").getAsInt() == 4) {
                        if (dataObj.has("token")) {
                            String token = dataObj.get("token").getAsString();
                            resp.cookies.put("token", token);
                            KugouApiClient.setToken(token);
                        }
                        if (dataObj.has("userid")) {
                            String uid = dataObj.get("userid").getAsString();
                            resp.cookies.put("userid", uid);
                            KugouApiClient.setUserid(Long.parseLong(uid));
                        }
                        // 保存过期时间
                        saveExpireTime(dataObj);
                        KugouConfig config = KugouConfig.getInstance();
                        config.token = resp.cookies.get("token");
                        config.userid = Long.parseLong(resp.cookies.get("userid"));
                        config.save();
                    }
                }
            }
            return resp;
        });
    }

    /**
     * Token 刷新登录
     */
    public static CompletableFuture<KugouApiClient.ApiResponse> refreshLogin(String token, long userid, Map<String, String> cookie) {
        KugouConfig config = KugouConfig.getInstance(); // 只声明一次
        long dateNow = System.currentTimeMillis();
        String encryptToken = KugouCrypto.cryptoAesEncrypt(
                Map.of("clienttime", Math.floor(dateNow / 1000.0), "token", token),
                LITE_TOKEN_KEY, LITE_TOKEN_IV
        ).toString();
        String randomKey = KugouCrypto.randomString(16).toLowerCase();
        String encryptParamsKey = KugouCrypto.cryptoMd5(randomKey).substring(0, 32);
        String encryptParamsIv = KugouCrypto.cryptoMd5(randomKey).substring(16, 32);
        String encryptParamsStr = KugouCrypto.cryptoAesEncrypt("{}", encryptParamsKey, encryptParamsIv).toString();
        String guid = config.guid != null ? config.guid : UUID.randomUUID().toString();
        String mac = config.mac != null ? config.mac : "02:00:00:00:00:00";
        String devId = config.devId != null ? config.devId : KugouUtils.randomString(10).toUpperCase();
        String t2Plain = guid + "|0f607264fc6318a92b9e13c65db7cd3c|" + mac + "|" + devId + "|" + dateNow;
        String t2 = KugouCrypto.cryptoAesEncrypt(t2Plain, LITE_T2_KEY, LITE_T2_IV).toString();
        String t1 = KugouCrypto.cryptoAesEncrypt("|" + dateNow, LITE_T1_KEY, LITE_T1_IV).toString();
        Map<String, Object> pkData = new HashMap<>();
        pkData.put("clienttime_ms", dateNow);
        pkData.put("key", randomKey);
        String pk = KugouCrypto.cryptoRSAEncrypt(pkData, null).toUpperCase();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("dfid", config.dfid);
        data.put("p3", encryptToken);
        data.put("plat", 1);
        data.put("t1", t1);
        data.put("t2", t2);
        data.put("t3", "MCwwLDAsMCwwLDAsMCwwLDA=");
        data.put("pk", pk);
        data.put("params", encryptParamsStr);
        data.put("userid", String.valueOf(userid));
        data.put("clienttime_ms", dateNow);
        data.put("dev", devId);
        Map<String, String> params = new LinkedHashMap<>();
        params.put("dfid", config.dfid);
        params.put("mid", config.mid);
        params.put("uuid", "-");
        KugouApiClient.RequestOptions opts = new KugouApiClient.RequestOptions();
        opts.baseURL = "http://login.user.kugou.com";
        opts.url = "/v5/login_by_token";
        opts.method = "POST";
        opts.data = data;
        opts.params = params;
        opts.encryptType = "android";
        opts.cookie = cookie;
        opts.noAndroidHeaders = false;
        return KugouApiClient.send(opts).thenApply(resp -> {
            if (resp.status == 200 && resp.body instanceof JsonObject) {
                JsonObject body = (JsonObject) resp.body;
                if (body.has("data")) {
                    JsonObject dataObj = body.getAsJsonObject("data");
                    if (dataObj != null && dataObj.has("secu_params")) {
                        String secuParams = dataObj.get("secu_params").getAsString();
                        String decrypted = KugouCrypto.cryptoAesDecrypt(secuParams, encryptParamsKey, encryptParamsIv);
                        try {
                            JsonObject tokenObj = JsonParser.parseString(decrypted).getAsJsonObject();
                            for (String key : tokenObj.keySet()) {
                                resp.cookies.put(key, tokenObj.get(key).getAsString());
                            }
                        } catch (Exception e) {
                            resp.cookies.put("token", decrypted);
                            KugouApiClient.setToken(decrypted);
                        }
                    }
                    if (dataObj.has("token")) resp.cookies.put("token", dataObj.get("token").getAsString());
                    if (dataObj.has("userid")) resp.cookies.put("userid", dataObj.get("userid").getAsString());
                    if (dataObj.has("vip_type")) resp.cookies.put("vip_type", dataObj.get("vip_type").getAsString());
                    // 保存过期时间
                    saveExpireTime(dataObj);
                    if (resp.cookies.containsKey("token")) config.token = resp.cookies.get("token");
                    if (resp.cookies.containsKey("userid")) config.userid = Long.parseLong(resp.cookies.get("userid"));
                    config.save();
                }
            }
            return resp;
        });
    }

    /**
     * 提取 t_expire_time 并保存到配置文件
     */
    private static void saveExpireTime(JsonObject dataObj) {
        if (dataObj.has("t_expire_time") && !dataObj.get("t_expire_time").isJsonNull()) {
            KugouConfig config = KugouConfig.getInstance();
            config.tExpireTime = dataObj.get("t_expire_time").getAsLong();
            config.save();
        }
    }

    private static void safePutCookie(KugouApiClient.ApiResponse resp, String key, JsonObject data, String jsonKey) {
        if (data.has(jsonKey) && !data.get(jsonKey).isJsonNull()) {
            resp.cookies.put(key, data.get(jsonKey).getAsString());
        }
    }

    /**
     * 获取用户详情
     */
    public static CompletableFuture<KugouApiClient.ApiResponse> getUserDetail(Map<String, String> cookie) {
        String token = cookie.getOrDefault("token", KugouApiClient.getToken());
        long userid = Long.parseLong(cookie.getOrDefault("userid", String.valueOf(KugouApiClient.getUserid())));
        long clienttime = System.currentTimeMillis() / 1000;
        Map<String, Object> rsaData = new HashMap<>();
        rsaData.put("token", token);
        rsaData.put("clienttime", clienttime);
        String pk = KugouCrypto.cryptoRSAEncrypt(rsaData, null).toUpperCase();
        Map<String, Object> dataMap = new LinkedHashMap<>();
        dataMap.put("visit_time", clienttime);
        dataMap.put("usertype", 1);
        dataMap.put("p", pk);
        dataMap.put("userid", userid);
        Map<String, String> params = new LinkedHashMap<>();
        params.put("plat", "1");
        KugouApiClient.RequestOptions opts = new KugouApiClient.RequestOptions();
        opts.baseURL = "https://gateway.kugou.com";
        opts.url = "/v3/get_my_info";
        opts.method = "POST";
        opts.data = dataMap;
        opts.params = params;
        opts.encryptType = "android";
        opts.cookie = cookie;
        opts.clearDefaultParams = false;
        opts.notSignature = false;
        opts.headers = new HashMap<>();
        opts.headers.put("x-router", "usercenter.kugou.com");
        return KugouApiClient.send(opts);
    }
    /**
     * 微信登录
     */
    public static CompletableFuture<KugouApiClient.ApiResponse> loginByOpenPlat(String code, Map<String, String> cookie) {
        long dateNow = System.currentTimeMillis();

        // 第一步：用 code 换取 access_token 和 openid
        return CompletableFuture.supplyAsync(() -> {
            try {
                String url = "https://api.weixin.qq.com/sns/oauth2/access_token?appid=" + WxApi.APPID + "&secret=" + WxApi.SECRET + "&code=" + code + "&grant_type=authorization_code";
                String resp = httpGet(url); // 需要实现 httpGet 方法，从 WxApi 中复制
                JsonObject json = JsonParser.parseString(resp).getAsJsonObject();
                return json;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }).thenCompose(tokenJson -> {
            if (!tokenJson.has("access_token") || !tokenJson.has("openid")) {
                throw new RuntimeException("获取微信 token 失败: " + tokenJson);
            }
            String accessToken = tokenJson.get("access_token").getAsString();
            String openid = tokenJson.get("openid").getAsString();

            // 加密 access_token
            String encryptJson = KugouCrypto.cryptoAesEncrypt(Map.of("access_token", accessToken), null, null).toString();
            JsonObject encryptObj = JsonParser.parseString(encryptJson).getAsJsonObject();
            String encStr = encryptObj.get("str").getAsString();
            String aesKey = encryptObj.get("key").getAsString();

            // RSA 加密
            Map<String, Object> rsaData = new HashMap<>();
            rsaData.put("clienttime_ms", dateNow);
            rsaData.put("key", aesKey);
            String pk = KugouCrypto.cryptoRSAEncrypt(rsaData, null).toUpperCase();

            // t1/t2
            KugouConfig config = KugouConfig.getInstance();
            String guid = config.guid != null ? config.guid : UUID.randomUUID().toString();
            String mac = config.mac != null ? config.mac : "02:00:00:00:00:00";
            String devId = config.devId != null ? config.devId : KugouUtils.randomString(10).toUpperCase();
            String t2Plain = guid + "|0f607264fc6318a92b9e13c65db7cd3c|" + mac + "|" + devId + "|" + dateNow;
            String t2 = KugouCrypto.cryptoAesEncrypt(t2Plain, "fd14b35e3f81af3817a20ae7adae7020", "17a20ae7adae7020").toString();
            String t1 = KugouCrypto.cryptoAesEncrypt("|" + dateNow, "5e4ef500e9597fe004bd09a46d8add98", "04bd09a46d8add98").toString();

            // 构造请求体
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("dev", devId);
            data.put("force_login", 1);
            data.put("partnerid", 36);
            data.put("clienttime_ms", dateNow);
            data.put("t1", t1);
            data.put("t2", t2);
            data.put("t3", "MCwwLDAsMCwwLDAsMCwwLDA=");
            data.put("openid", openid);
            data.put("params", encStr);
            data.put("pk", pk);

            KugouApiClient.RequestOptions opts = new KugouApiClient.RequestOptions();
            opts.baseURL = "https://gateway.kugou.com"; // 根据实际调整
            opts.url = "/v6/login_by_openplat";
            opts.method = "POST";
            opts.data = new Gson().toJson(data);
            opts.encryptType = "android";
            opts.cookie = cookie;
            opts.headers = new HashMap<>();
            opts.headers.put("x-router", "login.user.kugou.com");
            opts.clearDefaultParams = false;

            return KugouApiClient.send(opts).thenApply(resp -> {
                if (resp.status == 200 && resp.body instanceof JsonObject) {
                    System.out.println("[WxLogin] 酷狗登录成功");
                    JsonObject body = (JsonObject) resp.body;
                    if (body.has("status") && body.get("status").getAsInt() == 1) {
                        JsonObject dataObj = body.getAsJsonObject("data");
                        if (dataObj != null && dataObj.has("secu_params")) {
                            String secuParams = dataObj.get("secu_params").getAsString();
                            String decryptKey = KugouCrypto.cryptoMd5(aesKey).substring(0, 32);
                            String decryptIv = KugouCrypto.cryptoMd5(aesKey).substring(16, 32);
                            String decrypted = KugouCrypto.cryptoAesDecrypt(secuParams, decryptKey, decryptIv);
                            try {
                                JsonObject tokenObj = JsonParser.parseString(decrypted).getAsJsonObject();
                                for (String key : tokenObj.keySet()) {
                                    resp.cookies.put(key, tokenObj.get(key).getAsString());
                                }
                            } catch (Exception e) {
                                resp.cookies.put("token", decrypted);
                                KugouApiClient.setToken(decrypted);
                            }
                        }
                        safePutCookie(resp, "t1", dataObj, "t1");
                        safePutCookie(resp, "token", dataObj, "token");
                        safePutCookie(resp, "userid", dataObj, "userid");
                        safePutCookie(resp, "vip_type", dataObj, "vip_type");
                        safePutCookie(resp, "vip_token", dataObj, "vip_token");
                        saveExpireTime(dataObj);
                        if (resp.cookies.containsKey("token")) {
                            KugouApiClient.setToken(resp.cookies.get("token"));
                            KugouApiClient.setUserid(Long.parseLong(resp.cookies.getOrDefault("userid", "0")));
                        }
                        config.token = resp.cookies.get("token");
                        config.userid = KugouApiClient.getUserid();
                        config.save();
                    }
                }
                return resp;
            });
        });
    }

    // 辅助方法：复制 WxApi 中的 httpGet
    private static String httpGet(String urlString) throws Exception {
        URL url = new URL(urlString);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(5000);
        BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = br.readLine()) != null) sb.append(line);
        br.close();
        conn.disconnect();
        return sb.toString();
    }
}