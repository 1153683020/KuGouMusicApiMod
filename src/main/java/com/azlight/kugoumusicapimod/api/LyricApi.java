package com.azlight.kugoumusicapimod.api;

import com.azlight.kugoumusicapimod.util.KugouApiClient;
import com.azlight.kugoumusicapimod.util.KugouUtils;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class LyricApi {

    /**
     * 搜索歌词信息（获取歌词 id 和 accesskey）
     * @param keywords     关键词
     * @param hash         歌曲 hash（可选）
     * @param duration     歌曲时长（可选）
     * @param albumAudioId 专辑音频 id（可选）
     * @param man          是否返回多个歌词，默认 "no"
     */
    public static CompletableFuture<KugouApiClient.ApiResponse> searchLyric(String keywords, String hash,
                                                                            int duration, long albumAudioId,
                                                                            String man, Map<String, String> cookie) {
        Map<String, String> params = new HashMap<>();
        params.put("album_audio_id", String.valueOf(albumAudioId));
        params.put("appid", String.valueOf(com.azlight.kugoumusicapimod.util.KugouCrypto.LITE_APPID));
        params.put("clientver", String.valueOf(com.azlight.kugoumusicapimod.util.KugouCrypto.LITE_CLIENTVER));
        params.put("duration", String.valueOf(duration));
        params.put("hash", hash != null ? hash : "");
        params.put("keyword", keywords != null ? keywords : "");
        params.put("lrctxt", "1");
        params.put("man", man != null ? man : "no");

        KugouApiClient.RequestOptions opts = new KugouApiClient.RequestOptions();
        opts.baseURL = "https://lyrics.kugou.com";
        opts.url = "/v1/search";
        opts.method = "GET";
        opts.params = params;
        opts.cookie = cookie;
        opts.encryptType = "android";
        opts.clearDefaultParams = false;
        opts.notSignature = false;
        return KugouApiClient.send(opts).thenApply(resp -> {
            return resp;
        });
    }

    /**
     * 获取歌词内容（原始或解码）
     * @param id        歌词 id（从 searchLyric 返回）
     * @param accesskey 歌词 accesskey（从 searchLyric 返回）
     * @param fmt       歌词格式：krc 或 lrc
     * @param decode    是否解码（krc 需要 XOR+zlib，lrc 直接 base64 转 字符串）
     */
    public static CompletableFuture<KugouApiClient.ApiResponse> getLyric(String id, String accesskey,
                                                                         String fmt, boolean decode,
                                                                         Map<String, String> cookie) {
        Map<String, String> params = new HashMap<>();
        params.put("ver", "1");
        params.put("client", "android");
        params.put("id", id);
        params.put("accesskey", accesskey);
        params.put("fmt", fmt != null ? fmt : "krc");
        params.put("charset", "utf8");

        KugouApiClient.RequestOptions opts = new KugouApiClient.RequestOptions();
        opts.baseURL = "https://lyrics.kugou.com";
        opts.url = "/download";
        opts.method = "GET";
        opts.params = params;
        opts.cookie = cookie;
        opts.encryptType = "android";

        return KugouApiClient.send(opts).thenApply(resp -> {
            if (!decode || resp.status != 200) return resp;
            if (resp.body instanceof JsonObject) {
                JsonObject body = (JsonObject) resp.body;
                if (body.has("content")) {
                    String content = body.get("content").getAsString();
                    // 判断 contenttype 是否为 0 （krc） 并且 fmt 不是 lrc
                    boolean isKrc = true;
                    if (body.has("contenttype") && body.get("contenttype").getAsInt() == 0) {
                        isKrc = false; // 0 表示非 krc? 根据原始 JS: Number(res.body?.contenttype) !== 0 时走 base64 转字符串，否则走 decodeLyrics
                    }
                    // JS 逻辑：fmt == 'lrc' || contenttype != 0 则直接 base64 解码，否则用 decodeLyrics
                    String decoded;
                    if ("lrc".equals(fmt) || (body.has("contenttype") && body.get("contenttype").getAsInt() != 0)) {
                        decoded = new String(Base64.getDecoder().decode(content), java.nio.charset.StandardCharsets.UTF_8);
                    } else {
                        decoded = KugouUtils.decodeLyrics(content);
                    }
                    body.addProperty("decodeContent", decoded);
                }
            }
            return resp;
        });
    }
}