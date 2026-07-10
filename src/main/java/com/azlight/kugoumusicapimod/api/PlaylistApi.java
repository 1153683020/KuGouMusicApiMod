package com.azlight.kugoumusicapimod.api;

import com.azlight.kugoumusicapimod.util.KugouApiClient;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class PlaylistApi {

    // 获取用户歌单列表
    public static CompletableFuture<KugouApiClient.ApiResponse> getUserPlaylists(int page, int pagesize, Map<String, String> cookie) {
        String userid = cookie.getOrDefault("userid", String.valueOf(KugouApiClient.getUserid()));
        String token = cookie.getOrDefault("token", KugouApiClient.getToken());
        // POST body data（与 Node 的 dataMap 一致）
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("userid", userid);
        data.put("token", token);
        data.put("total_ver", 979);
        data.put("type", 2);
        data.put("page", page);
        data.put("pagesize", String.valueOf(pagesize));
        // URL 查询参数：只放 plat，userid/token 由默认参数处理
        Map<String, String> params = new LinkedHashMap<>();
        params.put("plat", "1");
        KugouApiClient.RequestOptions opts = new KugouApiClient.RequestOptions();
        opts.baseURL = "https://gateway.kugou.com";
        opts.url = "/v7/get_all_list";
        opts.method = "POST";
        opts.data = data;
        opts.params = params;
        opts.cookie = cookie;
        // 设置自定义 header
        opts.headers = new HashMap<>();
        opts.headers.put("x-router", "cloudlist.service.kugou.com");
        opts.noAuth = false;   // 需要登录态
        opts.noCookie = false; // 携带Cookie（可选项，建议 false）
        return KugouApiClient.send(opts);
    }

    // 获取歌单所有歌曲（普通版）
    public static CompletableFuture<KugouApiClient.ApiResponse> getPlaylistSongs(String globalCollectionId, int page, int pagesize, Map<String, String> cookie) {
        int beginIdx = (page - 1) * pagesize;
        Map<String, String> params = new HashMap<>();
        params.put("area_code", "1");
        params.put("begin_idx", String.valueOf(beginIdx));
        params.put("plat", "1");
        params.put("type", "1");
        params.put("mode", "1");
        params.put("personal_switch", "1");
        params.put("extend_fields", "abtags,hot_cmt,popularization");
        params.put("pagesize", String.valueOf(pagesize));
        params.put("global_collection_id", globalCollectionId);

        KugouApiClient.RequestOptions opts = new KugouApiClient.RequestOptions();
        opts.url = "/pubsongs/v2/get_other_list_file_nofilt";
        opts.method = "GET";
        opts.params = params;
        opts.encryptType = "android";
        opts.cookie = cookie;

        return KugouApiClient.send(opts);
    }

    // 获取歌单所有歌曲（新版，用于用户自己创建/收藏的歌单）
    public static CompletableFuture<KugouApiClient.ApiResponse> getPlaylistSongsNew(String listId, int page, int pagesize, Map<String, String> cookie) {
        Map<String, Object> data = new HashMap<>();
        data.put("listid", listId);
        data.put("userid", cookie.getOrDefault("userid", "0"));
        data.put("token", cookie.getOrDefault("token", ""));
        data.put("area_code", "1");
        data.put("show_relate_goods", 0);
        data.put("pagesize", pagesize);
        data.put("allplatform", 1);
        data.put("show_cover", 1);
        data.put("type", 0);
        data.put("page", page);

        KugouApiClient.RequestOptions opts = new KugouApiClient.RequestOptions();
        opts.url = "/v4/get_list_all_file";
        opts.method = "POST";
        opts.data = data;
        opts.encryptType = "android";
        opts.cookie = cookie;
        opts.headers = new HashMap<>();
        opts.headers.put("x-router", "cloudlist.service.kugou.com");

        return KugouApiClient.send(opts);
    }
}