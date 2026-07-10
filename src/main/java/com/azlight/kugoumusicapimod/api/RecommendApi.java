package com.azlight.kugoumusicapimod.api;

import com.azlight.kugoumusicapimod.util.KugouApiClient;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class RecommendApi {

    /**
     * 每日歌曲推荐
     * @param platform 平台：ios / android，默认 ios
     * @param cookie   登录态 Cookie（必须包含 token、userid 等）
     */
    public static CompletableFuture<KugouApiClient.ApiResponse> everydayRecommend(String platform, Map<String, String> cookie) {
        Map<String, String> params = new HashMap<>();
        params.put("platform", platform != null ? platform : "ios");

        Map<String, String> headers = new HashMap<>();
        headers.put("x-router", "everydayrec.service.kugou.com");

        KugouApiClient.RequestOptions opts = new KugouApiClient.RequestOptions();
        opts.url = "/everyday_song_recommend";
        opts.method = "POST";
        opts.params = params;
        opts.encryptType = "android";
        opts.headers = headers;
        opts.cookie = cookie;
        // POST 无 data 时可不传

        return KugouApiClient.send(opts);
    }

    /**
     * 每日风格推荐
     * @param tagids  风格标签 ID 列表，逗号分隔，如 "1,2,3"
     * @param cookie  登录态 Cookie
     */
    public static CompletableFuture<KugouApiClient.ApiResponse> everydayStyleRecommend(String tagids, Map<String, String> cookie) {
        Map<String, String> params = new HashMap<>();
        params.put("tagids", tagids != null ? tagids : "");

        // 空请求体
        Map<String, Object> emptyData = new HashMap<>();

        KugouApiClient.RequestOptions opts = new KugouApiClient.RequestOptions();
        opts.url = "/everydayrec.service/everyday_style_recommend";
        opts.method = "POST";
        opts.params = params;
        opts.data = emptyData;   // 必须传空对象，否则签名可能错误
        opts.encryptType = "android";
        opts.cookie = cookie;

        return KugouApiClient.send(opts);
    }
}