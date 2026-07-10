package com.azlight.kugoumusicapimod.util;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.Gson;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public class KugouApiClient {

    private static final Gson gson = new Gson();
    private static final HttpClient httpClient = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(10))
            .proxy(java.net.ProxySelector.getDefault())
            .build();

    // 默认设备标识
    private static String dfid = "-";
    private static String mid = "00000000000000000000000000000000";
    private static String uuid = "-";
    private static String token = "";
    private static long userid = 0;

    public static void setDfid(String dfid) { KugouApiClient.dfid = dfid; }
    public static void setMid(String mid) { KugouApiClient.mid = mid; }
    public static void setUuid(String uuid) { KugouApiClient.uuid = uuid; }
    public static void setToken(String token) { KugouApiClient.token = token; }
    public static void setUserid(long userid) { KugouApiClient.userid = userid; }
    public static String getDfid() { return dfid; }
    public static String getMid() { return mid; }

    public static CompletableFuture<ApiResponse> send(RequestOptions options) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return doSend(options);
            } catch (Exception e) {
                ApiResponse err = new ApiResponse();
                err.status = 502;
                String msg = e.getMessage();
                if (msg == null) msg = "未知错误: " + e.getClass().getSimpleName();
                err.body = gson.toJsonTree(Map.of("status", 0, "msg", msg));
                return err;
            }
        });
    }



    private static ApiResponse doSend(RequestOptions options) throws Exception {
        String requestMethod = options.method.toUpperCase();
        Map<String, String> params = new LinkedHashMap<>();
        Map<String, String> headersMap = new LinkedHashMap<>();

        // 1. 提取 cookie 中的设备信息
        Map<String, String> cookies = options.cookie;
        String mid = cookies != null ? cookies.getOrDefault("KUGOU_API_MID", KugouApiClient.mid) : KugouApiClient.mid;
        if (mid == null || mid.isEmpty()) mid = "-";
        String dfid = cookies != null ? cookies.getOrDefault("dfid", KugouApiClient.dfid) : KugouApiClient.dfid;
        if (dfid == null || dfid.isEmpty()) dfid = "-";
        String token = cookies != null ? cookies.getOrDefault("token", KugouApiClient.token) : KugouApiClient.token;
        long userid = cookies != null ? Long.parseLong(cookies.getOrDefault("userid", String.valueOf(KugouApiClient.userid))) : KugouApiClient.userid;
        long clienttime = System.currentTimeMillis() / 1000;

        // 2. 默认参数
        if (!options.clearDefaultParams) {
            params.put("dfid", dfid);
            params.put("mid", mid);
            params.put("uuid", KugouApiClient.uuid);
            params.put("appid", "3116");
            params.put("clientver", "11440");
            params.put("clienttime", String.valueOf(clienttime));
            if (!options.noAuth) {
                if (!token.isEmpty()) params.put("token", token);
                if (userid != 0) params.put("userid", String.valueOf(userid));
            }
        }

        // 3. 合并自定义参数
        if (options.params != null) {
            params.putAll(options.params);
        }

        // 4. 序列化请求体（修复：支持 byte[]）
        String dataStr = null;
        byte[] dataBytes = null;
        boolean isBinaryData = false;

        if (options.data != null) {
            if (options.data instanceof String) {
                dataStr = (String) options.data;
            } else if (options.data instanceof byte[]) {
                dataBytes = (byte[]) options.data;
                isBinaryData = true;
                // 对于 FormData/二进制数据，不转字符串，保持二进制
                dataStr = null;
            } else {
                dataStr = gson.toJson(options.data);
            }
        }

        // 5. 生成 signKey（如果需要）
        if (options.encryptKey) {
            String key = KugouCrypto.signKey(params.get("hash"), params.get("mid"), params.get("userid"), params.get("appid"));
            params.put("key", key);
        }

        // 6. 签名（当 notSignature 为 false 时生成普通 signature，否则只保留 key，根据平台不同生成不同的签名）
        if (!options.notSignature) {
            Map<String, String> signParams = new TreeMap<>(params);
            String signature;
            switch (options.encryptType) {
                case "register":
                    signature = KugouCrypto.signatureRegisterParams(signParams);
                    break;
                case "web":
                    signature = KugouCrypto.signatureWebParams(signParams);
                    break;
                case "android":
                default:
                    signature = KugouCrypto.signatureAndroidParams(signParams, dataStr);
                    break;
            }
            params.put("signature", signature);
        }


        // 7. 构建请求头
        headersMap.put("User-Agent", "Android15-1070-11083-46-0-DiscoveryDRADProtocol-wifi");
        headersMap.put("kg-rc", "1");
        headersMap.put("kg-thash", "5d816a0");
        headersMap.put("kg-rec", "1");
        headersMap.put("kg-rf", "B9EDA08A64250DEFFBCADDEE00F8F25F");
        headersMap.put("dfid", dfid);
        headersMap.put("mid", mid);
        if (params.containsKey("clienttime") && params.get("clienttime") != null) {
            headersMap.put("clienttime", params.get("clienttime"));
        }

        // Cookie 按需构建
        if (!options.noCookie) {
            StringBuilder cookieBuilder = new StringBuilder("dfid=" + dfid);
            if (mid != null && !mid.isEmpty() && !"-".equals(mid)) cookieBuilder.append("; mid=").append(mid);
            if (token != null && !token.isEmpty()) cookieBuilder.append("; token=").append(token);
            if (userid != 0) cookieBuilder.append("; userid=").append(userid);
            headersMap.put("Cookie", cookieBuilder.toString());
        }

        if (options.headers != null) headersMap.putAll(options.headers);

        // 8. 构建请求 URL
        String baseUrl = options.baseURL != null ? options.baseURL : "https://gateway.kugou.com";
        String path = options.url.startsWith("/") ? options.url : "/" + options.url;
        String fullUrl = baseUrl + path;

        // 构建查询字符串
        if (!params.isEmpty()) {
            String queryString = params.entrySet().stream()
                    .map(e -> e.getKey() + "=" + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
                    .collect(Collectors.joining("&"));
            fullUrl = fullUrl + (fullUrl.contains("?") ? "&" : "?") + queryString;
        }

        // 9. 构造 HttpRequest
        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder().uri(URI.create(fullUrl));

        for (Map.Entry<String, String> entry : headersMap.entrySet()) {
            if (entry.getKey() != null && entry.getValue() != null) {
                requestBuilder.header(entry.getKey(), entry.getValue());
            }
        }

        if ("GET".equals(requestMethod)) {
            requestBuilder.GET();
        } else if ("POST".equals(requestMethod)) {
            HttpRequest.BodyPublisher bodyPublisher;
            if (isBinaryData && dataBytes != null) {
                // 发送原始二进制数据（修复关键点）
                bodyPublisher = HttpRequest.BodyPublishers.ofByteArray(dataBytes);
            } else if (dataStr != null && !dataStr.isEmpty()) {
                bodyPublisher = HttpRequest.BodyPublishers.ofString(dataStr);
            } else {
                bodyPublisher = HttpRequest.BodyPublishers.noBody();
            }
            requestBuilder.method(requestMethod, bodyPublisher);
        } else {
            throw new IllegalArgumentException("Unsupported method: " + requestMethod);
        }

        HttpRequest request = requestBuilder.build();

        // 10. 发送请求并处理响应
        ApiResponse apiResponse = new ApiResponse();
        try {
            if ("arraybuffer".equals(options.responseType)) {
                HttpResponse<byte[]> byteResponse = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
                apiResponse.status = 200;
                apiResponse.body = byteResponse.body();
                apiResponse.headers = byteResponse.headers().map();
                parseCookies(apiResponse, byteResponse.headers().allValues("set-cookie"));
            } else {
                HttpResponse<String> stringResponse = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                apiResponse.status = 200;
                apiResponse.headers = stringResponse.headers().map();
                parseCookies(apiResponse, stringResponse.headers().allValues("set-cookie"));
                String respBody = stringResponse.body();
                try {
                    apiResponse.body = JsonParser.parseString(respBody);
                } catch (Exception e) {
                    apiResponse.body = respBody;
                }
            }
        } catch (Exception e) {
            System.err.println("[Kugou] 请求失败异常: " + e.getClass().getName() + ": " + e.getMessage());
            e.printStackTrace();
            apiResponse.status = 502;
            apiResponse.body = e.getMessage();
        }

        return apiResponse;
    }

    private static void parseCookies(ApiResponse apiResponse, List<String> rawCookies) {
        for (String raw : rawCookies) {
            String clean = raw.split(";")[0];
            String[] parts = clean.split("=", 2);
            if (parts.length == 2) {
                apiResponse.cookies.put(parts[0], parts[1]);
            }
        }
    }

    public static String getToken() { return token; }
    public static long getUserid() { return userid; }

    public static class ApiResponse {
        public int status;
        public Object body;
        public Map<String, String> cookies = new HashMap<>();
        public Map<String, List<String>> headers;
    }

    public static class RequestOptions {
        public String method = "GET";
        public String url;
        public String baseURL;
        public Map<String, String> params;
        public Object data;
        public Map<String, String> headers;
        public String encryptType = "android";
        public boolean encryptKey = false;
        public boolean clearDefaultParams = false;
        public boolean notSignature = false;
        public Map<String, String> cookie;
        public String responseType;
        public boolean noAuth = false;
        public boolean noAndroidHeaders = false;
        public boolean noCookie = false;
    }

    static class ApiErrorException extends RuntimeException {
        ApiResponse response;
        ApiErrorException(ApiResponse r) { this.response = r; }
    }
}