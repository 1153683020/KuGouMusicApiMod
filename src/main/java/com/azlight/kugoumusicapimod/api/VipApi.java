package com.azlight.kugoumusicapimod.api;

import com.azlight.kugoumusicapimod.util.KugouApiClient;
import com.google.gson.JsonObject;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class VipApi {

    /**
     * 查询用户 VIP 状态（用于判断是否为 SVIP）
     * 注意：此接口可能返回多种 VIP 类型，包含 svip 等
     */
    public static CompletableFuture<KugouApiClient.ApiResponse> getUnionVip(Map<String, String> cookie) {
        Map<String, String> params = new HashMap<>();
        params.put("busi_type", "concept");
        params.put("opt_product_types", "dvip,qvip");
        params.put("product_type", "svip");

        KugouApiClient.RequestOptions opts = new KugouApiClient.RequestOptions();
        opts.baseURL = "https://kugouvip.kugou.com";
        opts.url = "/v1/get_union_vip";
        opts.method = "GET";
        opts.params = params;
        opts.encryptType = "android";
        opts.cookie = cookie;

        return KugouApiClient.send(opts);
    }

    /**
     * 辅助方法：检查用户是否为 SVIP，返回 true/false
     */
    public static CompletableFuture<Boolean> isSvip(Map<String, String> cookie) {
        return getUnionVip(cookie).thenApply(resp -> {
            if (resp.status == 200 && resp.body instanceof JsonObject) {
                JsonObject body = (JsonObject) resp.body;
                if (body.has("data")) {
                    JsonObject data = body.getAsJsonObject("data");
                    if (data.has("busi_vip")) {
                        com.google.gson.JsonArray busiVip = data.getAsJsonArray("busi_vip");
                        for (int i = 0; i < busiVip.size(); i++) {
                            JsonObject vip = busiVip.get(i).getAsJsonObject();
                            if (vip.has("is_vip") && vip.get("is_vip").getAsInt() == 1 &&
                                    vip.has("product_type") && "svip".equals(vip.get("product_type").getAsString())) {
                                return true;
                            }
                        }
                    }
                }
            }
            return false;
        });
    }

    /**
     * 领取一天 VIP（需要登录，非 SVIP 用户）
     * @param receiveDay 日期，格式：2026-01-30
     */
    public static CompletableFuture<KugouApiClient.ApiResponse> receiveDayVip(String receiveDay, Map<String, String> cookie) {
        // 先检查是否为 SVIP，是则拒绝
        return isSvip(cookie).thenCompose(isSvip -> {
            if (isSvip) {
                KugouApiClient.ApiResponse err = new KugouApiClient.ApiResponse();
                err.status = 502;
                err.body = new com.google.gson.JsonParser().parse("{\"error\":\"SVIP user not allowed to claim free VIP\"}").getAsJsonObject();
                return CompletableFuture.completedFuture(err);
            }
            Map<String, String> params = new HashMap<>();
            params.put("source_id", "90139");
            params.put("receive_day", receiveDay);

            Map<String, String> headers = new HashMap<>();
            headers.put("content-type", "application/x-www-form-urlencoded");

            KugouApiClient.RequestOptions opts = new KugouApiClient.RequestOptions();
            opts.url = "/youth/v1/recharge/receive_vip_listen_song";
            opts.method = "POST";
            opts.params = params;
            opts.headers = headers;
            opts.encryptType = "android";
            opts.cookie = cookie;

            return KugouApiClient.send(opts);
        });
    }

    /**
     * 升级 VIP（需要先领取一天 VIP，非 SVIP 用户）
     */
    public static CompletableFuture<KugouApiClient.ApiResponse> upgradeVip(Map<String, String> cookie) {
        return isSvip(cookie).thenCompose(isSvip -> {
            if (isSvip) {
                KugouApiClient.ApiResponse err = new KugouApiClient.ApiResponse();
                err.status = 502;
                err.body = new com.google.gson.JsonParser().parse("{\"error\":\"SVIP user cannot upgrade\"}").getAsJsonObject();
                return CompletableFuture.completedFuture(err);
            }
            Map<String, String> params = new HashMap<>();
            params.put("kugouid", cookie.getOrDefault("userid", "0"));
            params.put("ad_type", "1");

            KugouApiClient.RequestOptions opts = new KugouApiClient.RequestOptions();
            opts.url = "/youth/v1/listen_song/upgrade_vip_reward";
            opts.method = "POST";
            opts.params = params;
            opts.encryptType = "android";
            opts.cookie = cookie;

            return KugouApiClient.send(opts);
        });
    }

    /**
     * 获取当月已领取 VIP 记录
     */
    public static CompletableFuture<KugouApiClient.ApiResponse> getMonthVipRecord(Map<String, String> cookie) {
        Map<String, String> params = new HashMap<>();
        params.put("latest_limit", "100");

        KugouApiClient.RequestOptions opts = new KugouApiClient.RequestOptions();
        opts.url = "/youth/v1/activity/get_month_vip_record";
        opts.method = "GET";
        opts.params = params;
        opts.encryptType = "android";
        opts.cookie = cookie;

        return KugouApiClient.send(opts);
    }

    /**
     * 获取用户 VIP 详情（通用接口，可判断是否 SVIP）
     */
    public static CompletableFuture<KugouApiClient.ApiResponse> getUserVipDetail(Map<String, String> cookie) {
        Map<String, String> params = new HashMap<>();
        params.put("busi_type", "concept");

        KugouApiClient.RequestOptions opts = new KugouApiClient.RequestOptions();
        opts.baseURL = "https://kugouvip.kugou.com";
        opts.url = "/v1/get_union_vip";
        opts.method = "GET";
        opts.params = params;
        opts.encryptType = "android";
        opts.cookie = cookie;

        return KugouApiClient.send(opts);
    }
}