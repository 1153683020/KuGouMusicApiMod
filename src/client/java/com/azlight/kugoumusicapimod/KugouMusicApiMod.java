package com.azlight.kugoumusicapimod;

import com.azlight.kugoumusicapimod.api.DeviceApi;
import com.azlight.kugoumusicapimod.client.command.MusicCommand;
import com.azlight.kugoumusicapimod.client.lyric.LyricRenderer;
import com.azlight.kugoumusicapimod.util.KugouApiClient;
import com.azlight.kugoumusicapimod.util.KugouConfig;
import com.azlight.kugoumusicapimod.util.KugouUtils;
import net.fabricmc.api.ClientModInitializer;
import com.azlight.kugoumusicapimod.client.audio.AudioPlayer;
import com.azlight.kugoumusicapimod.client.lyric.LyricRenderer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;

public class KugouMusicApiMod implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        // 加载配置
        KugouConfig config = KugouConfig.getInstance();

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
        AudioPlayer.stop();
        LyricRenderer.setLyrics(null);
        });

        String mid;
        if (!config.mid.isEmpty()) {
            mid = config.mid;
        } else {
            mid = KugouUtils.generateMid();
            config.mid = mid;
            config.save();
        }
        KugouApiClient.setMid(mid);

        if (!config.dfid.isEmpty()) {
            KugouApiClient.setDfid(config.dfid);
            System.out.println("[Kugou] 使用已保存的 dfid: " + config.dfid);
        } else {
            // 异步注册设备
            System.out.println("[Kugou] 开始设备注册...");
            DeviceApi.registerDevice().thenAccept(resp -> {
                if (resp != null && resp.status == 200 && resp.body instanceof com.google.gson.JsonObject) {
                    com.google.gson.JsonObject body = (com.google.gson.JsonObject) resp.body;
                    if (body.has("data")) {
                        com.google.gson.JsonElement dataElem = body.get("data");
                        if (dataElem != null && !dataElem.isJsonNull()) {
                            com.google.gson.JsonObject data = dataElem.getAsJsonObject();
                            if (data.has("dfid")) {
                                String dfid = data.get("dfid").getAsString();
                                KugouApiClient.setDfid(dfid);
                                config.dfid = dfid;
                                config.save();
                                System.out.println("[Kugou] 设备注册成功，dfid: " + dfid);
                            }
                        }
                    }
                } else {
                    System.err.println("[Kugou] 设备注册失败，使用手动 dfid");
                }
            }).exceptionally(e -> {
                System.err.println("[Kugou] 设备注册异常: " + e.getMessage());
                return null;
            });
        }

        // 设置 token 和 userid（从配置读取，后续可通过指令修改）
        if (!config.token.isEmpty()) {
            KugouApiClient.setToken(config.token);
        }
        if (config.userid != 0) {
            KugouApiClient.setUserid(config.userid);
        }

        // 注册命令
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            MusicCommand.register(dispatcher);
        });

        // 歌词HUD
        HudRenderCallback.EVENT.register((drawContext, tickCounter) -> {
            LyricRenderer.render(drawContext, tickCounter);
        });
    }
}
