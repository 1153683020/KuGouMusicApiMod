package com.azlight.kugoumusicapimod.api;

import com.azlight.kugoumusicapimod.util.KugouCrypto;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;

public class WxApi {
    public static final String APPID = "wx72b795aca60ad321"; // lite 版本
    public static final String SECRET = "33e486041e5e25729a4e3d2da7502f9a";

    /**
     * 生成微信二维码，返回 uuid 和 base64 图片（直接使用微信返回的 base64）
     */
    public static CompletableFuture<JsonObject> generateWxQrCode() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // 获取 access_token
                String tokenUrl = "https://api.weixin.qq.com/cgi-bin/token?appid=" + APPID + "&secret=" + SECRET + "&grant_type=client_credential";
                String tokenResp = httpGet(tokenUrl);
                JsonObject tokenJson = JsonParser.parseString(tokenResp).getAsJsonObject();
                if (!tokenJson.has("access_token")) throw new RuntimeException("获取 access_token 失败: " + tokenResp);
                String accessToken = tokenJson.get("access_token").getAsString();

                // 获取 ticket
                String ticketUrl = "https://api.weixin.qq.com/cgi-bin/ticket/getticket?access_token=" + accessToken + "&type=2";
                String ticketResp = httpGet(ticketUrl);
                JsonObject ticketJson = JsonParser.parseString(ticketResp).getAsJsonObject();
                if (ticketJson.get("errcode").getAsInt() != 0) throw new RuntimeException("ticket error: " + ticketResp);
                String ticket = ticketJson.get("ticket").getAsString();

                // 生成签名
                long timestamp = System.currentTimeMillis() / 1000;
                String noncestr = KugouCrypto.cryptoMd5(KugouCrypto.randomString(10));
                String signatureParams = "appid=" + APPID + "&noncestr=" + noncestr + "&sdk_ticket=" + ticket + "&timestamp=" + timestamp;
                String signature = KugouCrypto.cryptoSha1(signatureParams);

                // 请求二维码
                String qrUrl = "https://open.weixin.qq.com/connect/sdk/qrconnect?appid=" + APPID + "&noncestr=" + noncestr + "&timestamp=" + timestamp + "&scope=snsapi_userinfo&signature=" + signature;
                String qrResp = httpGet(qrUrl);
                JsonObject qrJson = JsonParser.parseString(qrResp).getAsJsonObject();
                if (qrJson.get("errcode").getAsInt() != 0) throw new RuntimeException("qr error: " + qrResp);

                String uuid = qrJson.get("uuid").getAsString();
                // 直接使用微信返回的 base64 图片（已包含 data URI 前缀）
                String base64Image = qrJson.getAsJsonObject("qrcode").get("qrcodebase64").getAsString();
                // 确保 base64 有 data URI 前缀
                if (!base64Image.startsWith("data:image")) {
                    base64Image = "data:image/png;base64," + base64Image;
                }

                JsonObject result = new JsonObject();
                result.addProperty("uuid", uuid);
                result.addProperty("qrcode_image", base64Image);
                return result;
            } catch (Exception e) {
                e.printStackTrace();
                return null;
            }
        });
    }

    /**
     * 检查微信二维码状态（轮询）
     */
    public static CompletableFuture<JsonObject> checkWxQrStatus(String uuid) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String url = "https://long.open.weixin.qq.com/connect/l/qrconnect?f=json&uuid=" + uuid;
                String resp = httpGet(url);
                if (resp == null || resp.isEmpty()) return null;
                return JsonParser.parseString(resp).getAsJsonObject();
            } catch (Exception e) {
                System.err.println("[WxApi] 轮询异常: " + e.getMessage());
                e.printStackTrace();
                return null;
            }
        });
    }

    /**
     * HTTP GET 请求，支持系统代理，超时 15 秒，正确处理各种状态码
     */
    private static String httpGet(String urlString) throws Exception {
        URL url = new URL(urlString);
        HttpURLConnection conn;
        java.net.ProxySelector proxySelector = java.net.ProxySelector.getDefault();
        if (proxySelector != null) {
            java.net.Proxy proxy = proxySelector.select(url.toURI()).stream().findFirst().orElse(java.net.Proxy.NO_PROXY);
            conn = (HttpURLConnection) url.openConnection(proxy);
        } else {
            conn = (HttpURLConnection) url.openConnection();
        }
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(15000);  // 增加到 15 秒
        conn.setRequestProperty("Connection", "Keep-Alive");
        conn.setRequestProperty("Accept", "application/json, text/plain, */*");

        int responseCode = conn.getResponseCode();
        InputStream inputStream;
        if (responseCode >= 200 && responseCode < 400) {
            inputStream = conn.getInputStream();
        } else {
            inputStream = conn.getErrorStream();
        }

        if (inputStream == null) {
            // 无内容，直接返回状态码字符串
            conn.disconnect();
            return String.valueOf(responseCode);
        }

        BufferedReader br = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = br.readLine()) != null) sb.append(line);
        br.close();
        conn.disconnect();
        return sb.toString();
    }
}