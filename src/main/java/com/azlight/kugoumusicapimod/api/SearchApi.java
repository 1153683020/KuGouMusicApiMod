package com.azlight.kugoumusicapimod.api;

import com.azlight.kugoumusicapimod.util.KugouApiClient;
import com.azlight.kugoumusicapimod.util.KugouCrypto;
import com.google.gson.JsonObject;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class SearchApi {

    /**
     * 综合搜索 (complex)
     */
    public static CompletableFuture<KugouApiClient.ApiResponse> searchComplex(String keywords, int page, int pagesize, Map<String, String> cookie) {
        Map<String, String> params = new HashMap<>();
        params.put("platform", "AndroidFilter");
        params.put("keyword", keywords);
        params.put("page", String.valueOf(page));
        params.put("pagesize", String.valueOf(pagesize));
        params.put("cursor", "0");

        KugouApiClient.RequestOptions opts = new KugouApiClient.RequestOptions();
        opts.baseURL = "https://complexsearch.kugou.com";
        opts.url = "/v6/search/complex";
        opts.method = "GET";
        opts.params = params;
        opts.encryptType = "android";
        opts.cookie = cookie;
        return KugouApiClient.send(opts);
    }

    /**
     * 默认搜索词（搜索发现）
     */
    public static CompletableFuture<KugouApiClient.ApiResponse> searchDefault(Map<String, String> cookie) {
        Map<String, Object> data = new HashMap<>();
        data.put("plat", 0);
        data.put("userid", 0); // 可从 cookie 中取
        data.put("tags", "{}");
        data.put("vip_type", 65530);
        data.put("m_type", 0);
        data.put("own_ads", new HashMap<>());
        data.put("ability", "3");
        data.put("sources", new Object[0]);
        data.put("bitmap", 2);
        data.put("mode", "normal");

        Map<String, String> params = new HashMap<>();
        params.put("clientver", "12329");

        KugouApiClient.RequestOptions opts = new KugouApiClient.RequestOptions();
        opts.url = "/searchnofocus/v1/search_no_focus_word";
        opts.method = "POST";
        opts.data = data;
        opts.params = params;
        opts.encryptType = "android";
        opts.cookie = cookie;
        return KugouApiClient.send(opts);
    }

    /**
     * 热搜榜
     */
    public static CompletableFuture<KugouApiClient.ApiResponse> searchHot(Map<String, String> cookie) {
        Map<String, String> params = new HashMap<>();
        params.put("navid", "1");
        params.put("plat", "2");

        Map<String, String> headers = new HashMap<>();
        headers.put("x-router", "msearch.kugou.com");

        KugouApiClient.RequestOptions opts = new KugouApiClient.RequestOptions();
        opts.url = "/api/v3/search/hot_tab";
        opts.method = "GET";
        opts.params = params;
        opts.encryptType = "android";
        opts.cookie = cookie;
        opts.headers = headers;
        return KugouApiClient.send(opts);
    }

    /**
     * 歌词搜索
     */
    public static CompletableFuture<KugouApiClient.ApiResponse> searchLyric(String keywords, String hash, int duration, String albumAudioId, Map<String, String> cookie) {
        Map<String, String> params = new HashMap<>();
        params.put("album_audio_id", albumAudioId == null ? "0" : albumAudioId);
        params.put("appid", String.valueOf(KugouCrypto.LITE_APPID)); // lite 模式
        params.put("clientver", String.valueOf(KugouCrypto.LITE_CLIENTVER));
        params.put("duration", String.valueOf(duration));
        params.put("hash", hash == null ? "" : hash);
        params.put("keyword", keywords);
        params.put("lrctxt", "1");
        params.put("man", "no");

        KugouApiClient.RequestOptions opts = new KugouApiClient.RequestOptions();
        opts.baseURL = "https://lyrics.kugou.com";
        opts.url = "/v1/search";
        opts.method = "GET";
        opts.params = params;
        opts.cookie = cookie;
        opts.encryptType = "android";
        opts.clearDefaultParams = true;  // 清除默认 dfid/mid 等
        opts.notSignature = true;        // 不签名
        return KugouApiClient.send(opts);
    }

    /**
     * 综合搜索 (mixed)
     */
    public static CompletableFuture<KugouApiClient.ApiResponse> searchMixed(String keyword, Map<String, String> cookie) {
        long time = System.currentTimeMillis();
        String requestid = KugouCrypto.cryptoMd5("bdaa53d04e7475feb9024164a47032f9" + time) + "_0";

        Map<String, String> params = new HashMap<>();
        params.put("ab_tag", "0");
        params.put("ability", "511");
        params.put("albumhide", "0");
        params.put("apiver", "22");
        params.put("area_code", "1");
        params.put("clientver", "20125");
        params.put("cursor", "0");
        params.put("is_gpay", "0");
        params.put("iscorrection", "1");
        params.put("keyword", keyword);
        params.put("nocollect", "0");
        params.put("osversion", "16.5");
        params.put("platform", "IOSFilter");
        params.put("recver", "2");
        params.put("req_ai", "1");
        params.put("requestid", requestid);
        params.put("search_ability", "3");
        params.put("sec_aggre", "1");
        params.put("sec_aggre_bitmap", "0");
        params.put("style_type", "3");
        params.put("tag", "em");

        Map<String, String> headers = new HashMap<>();
        headers.put("x-router", "complexsearch.kugou.com");
        headers.put("kg-clienttimems", String.valueOf(time));

        KugouApiClient.RequestOptions opts = new KugouApiClient.RequestOptions();
        opts.url = "/v3/search/mixed";
        opts.method = "GET";
        opts.params = params;
        opts.encryptType = "android";
        opts.headers = headers;
        opts.cookie = cookie;
        return KugouApiClient.send(opts);
    }

    /**
     * 搜索建议 (suggest)
     */
    public static CompletableFuture<KugouApiClient.ApiResponse> searchSuggest(String keywords, int albumTipCount, int correctTipCount, int mvTipCount, int musicTipCount, Map<String, String> cookie) {
        Map<String, String> params = new HashMap<>();
        params.put("keyword", keywords);
        params.put("AlbumTipCount", String.valueOf(albumTipCount));
        params.put("CorrectTipCount", String.valueOf(correctTipCount));
        params.put("MVTipCount", String.valueOf(mvTipCount));
        params.put("MusicTipCount", String.valueOf(musicTipCount));
        params.put("radiotip", "1");

        Map<String, String> headers = new HashMap<>();
        headers.put("x-router", "searchtip.kugou.com");

        KugouApiClient.RequestOptions opts = new KugouApiClient.RequestOptions();
        opts.url = "/v2/getSearchTip";
        opts.method = "GET";
        opts.params = params;
        opts.encryptType = "android";
        opts.cookie = cookie;
        opts.headers = headers;
        return KugouApiClient.send(opts);
    }

    /**
     * 普通搜索（按类型：song/album/author/mv/special/lyric）
     */
    public static CompletableFuture<KugouApiClient.ApiResponse> search(String keywords, int page, int pagesize, String type, Map<String, String> cookie) {
        Map<String, String> params = new HashMap<>();
        params.put("albumhide", "0");
        params.put("iscorrection", "1");
        params.put("keyword", keywords);
        params.put("nocollect", "0");
        params.put("page", String.valueOf(page));
        params.put("pagesize", String.valueOf(pagesize));
        params.put("platform", "AndroidFilter");

        // 类型校验，默认 song
        if (type == null || !List.of("special", "lyric", "song", "album", "author", "mv").contains(type)) {
            type = "song";
        }
        String ver = type.equals("song") ? "v3" : "v1";
        String url = "/" + ver + "/search/" + type;

        Map<String, String> headers = new HashMap<>();
        headers.put("x-router", "complexsearch.kugou.com");

        KugouApiClient.RequestOptions opts = new KugouApiClient.RequestOptions();
        opts.url = url;
        opts.method = "GET";
        opts.params = params;
        opts.encryptType = "android";
        opts.headers = headers;
        opts.cookie = cookie;
        return KugouApiClient.send(opts);
    }
}