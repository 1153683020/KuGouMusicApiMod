package com.azlight.kugoumusicapimod.api;

import com.azlight.kugoumusicapimod.util.KugouApiClient;
import com.azlight.kugoumusicapimod.util.KugouUtils;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class SongApi {

    public static CompletableFuture<KugouApiClient.ApiResponse> getSongUrl(String hash, String quality,
                                                                           int albumId, int albumAudioId,
                                                                           Map<String, String> cookie) {
        String dfid = KugouUtils.randomString(24);
        if (quality != null && List.of("piano","acappella","subwoofer","ancient","dj","surnay").contains(quality)) {
            quality = "magic_" + quality;
        } else if (quality == null) {
            quality = "128";
        }

        Map<String, String> params = new HashMap<>();
        params.put("album_id", String.valueOf(albumId));
        params.put("area_code", "1");
        params.put("hash", hash.toLowerCase());
        params.put("ssa_flag", "is_fromtrack");
        params.put("version", "11430");
        params.put("page_id", "967177915");    // Lite
        params.put("quality", quality);
        params.put("album_audio_id", String.valueOf(albumAudioId));
        params.put("behavior", "play");
        params.put("pid", "411");              // Lite
        params.put("cmd", "26");
        params.put("pidversion", "3001");
        params.put("IsFreePart", "0");
        params.put("ppage_id", "356753938");   // Lite
        params.put("cdnBackup", "1");
        params.put("module", "");
        params.put("clientver", "11430");

        Map<String, String> requestCookie = new HashMap<>(cookie != null ? cookie : Map.of());
        requestCookie.putIfAbsent("dfid", dfid);

        KugouApiClient.RequestOptions opts = new KugouApiClient.RequestOptions();
        opts.baseURL = "https://gateway.kugou.com";
        opts.url = "/v5/url";
        opts.method = "GET";
        opts.params = params;
        opts.encryptType = "android";
        opts.encryptKey = true;
        opts.notSignature = false;
        opts.headers = new HashMap<>();
        opts.headers.put("x-router", "trackercdn.kugou.com");
        opts.cookie = requestCookie;

        return KugouApiClient.send(opts);
    }
}