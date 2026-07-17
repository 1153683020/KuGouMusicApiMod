package com.azlight.kugoumusicapimod.client.command;

import com.azlight.kugoumusicapimod.api.*;
import com.azlight.kugoumusicapimod.client.audio.AudioPlayer;
import com.azlight.kugoumusicapimod.client.lyric.LyricRenderer;
import com.azlight.kugoumusicapimod.client.screen.QrCodeScreen;
import com.azlight.kugoumusicapimod.util.KugouApiClient;
import com.azlight.kugoumusicapimod.util.KugouConfig;
import com.azlight.kugoumusicapimod.util.KugouUtils;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.*;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class MusicCommand {

    // 默认音质存储
    private static String currentQuality = "128";
    private static String lastHash = null;
    private static String lastAlbumAudioId = "0";
    private static boolean paused = false;
    private static String lastAlbumId = "0";
    private static String lastQuality = "128";
    private static final java.util.concurrent.ScheduledExecutorService scheduler = java.util.concurrent.Executors.newScheduledThreadPool(1);
    private static String qrKey = null;
    private static java.util.concurrent.Future<?> pollingTask = null;
    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(
                ClientCommandManager.literal("kugou")
                        .then(ClientCommandManager.literal("search")
                                .then(ClientCommandManager.argument("keyword", StringArgumentType.greedyString())
                                        .executes(ctx -> {
                                            String keyword = StringArgumentType.getString(ctx, "keyword");
                                            searchSongs(ctx.getSource(), keyword);
                                            return 1;
                                        })
                                )
                        )
                        .then(ClientCommandManager.literal("play")
                                .then(ClientCommandManager.argument("params", StringArgumentType.greedyString())
                                        .executes(ctx -> {
                                            String[] parts = StringArgumentType.getString(ctx, "params").split(",");
                                            if (parts.length >= 4) {
                                                playSong(ctx.getSource(), parts[0], parts[1], parts[2], parts[3]);
                                            } else {
                                                ctx.getSource().sendError(Text.literal("参数格式: hash,album_id,album_audio_id,quality"));
                                            }
                                            return 1;
                                        })
                                )
                        )
                        .then(ClientCommandManager.literal("quality")
                                .executes(ctx -> {
                                    ctx.getSource().sendFeedback(Text.literal("§a当前默认音质: " + currentQuality));
                                    return 1;
                                })
                                .then(ClientCommandManager.argument("quality", StringArgumentType.word())
                                        .suggests((context, builder) -> {
                                            String[] options = {"128", "320", "flac", "high", "viper_atmos", "viper_clear", "viper_tape"};
                                            for (String option : options) {
                                                builder.suggest(option);
                                            }
                                            return builder.buildFuture();
                                        })
                                        .executes(ctx -> {
                                            String quality = StringArgumentType.getString(ctx, "quality");
                                            List<String> allowed = Arrays.asList("128", "320", "flac", "high", "viper_atmos", "viper_clear", "viper_tape");
                                            if (allowed.contains(quality)) {
                                                currentQuality = quality;
                                                ctx.getSource().sendFeedback(Text.literal("§a默认音质已设置为 " + quality));
                                            } else {
                                                ctx.getSource().sendError(Text.literal("§c无效的音质选项，可用: " + String.join(", ", allowed)));
                                            }
                                            return 1;
                                        })
                                )
                        )

                        .then(ClientCommandManager.literal("pause")
                                .executes(ctx -> {
                                    AudioPlayer.stop();
                                    paused = true;
                                    LyricRenderer.pause();
                                    ctx.getSource().sendFeedback(Text.literal("已暂停"));
                                    return 1;
                                })
                        )
                        .then(ClientCommandManager.literal("resume")
                                .executes(ctx -> {
                                    if (paused && lastHash != null) {
                                        paused = false;
                                        playSong(ctx.getSource(), lastHash, lastAlbumId, lastAlbumAudioId, lastQuality);
                                        LyricRenderer.resume();
                                        ctx.getSource().sendFeedback(Text.literal("§a继续播放"));
                                    } else {
                                        ctx.getSource().sendError(Text.literal("§c没有暂停的歌曲"));
                                    }
                                    return 1;
                                })
                        )
                        .then(ClientCommandManager.literal("stop")
                                .executes(ctx -> {
                                    AudioPlayer.stop();
                                    paused = false;
                                    LyricRenderer.setLyrics(null);
                                    ctx.getSource().sendFeedback(Text.literal("已停止播放"));
                                    return 1;
                                })
                        )
                        .then(ClientCommandManager.literal("captcha")
                                .then(ClientCommandManager.argument("phone", StringArgumentType.word())
                                        .executes(ctx -> {
                                            String phone = StringArgumentType.getString(ctx, "phone");
                                            FabricClientCommandSource source = ctx.getSource();
                                            source.sendFeedback(Text.literal("正在发送验证码到 " + phone));
                                            LoginApi.sendCaptcha(phone, KugouApiClient.getMid())
                                                    .thenAcceptAsync(response -> {
                                                        if (response.status == 200 && response.body instanceof JsonObject) {
                                                            JsonObject body = (JsonObject) response.body;
                                                            if (body.has("status") && body.get("status").getAsInt() == 1) {
                                                                source.sendFeedback(Text.literal("验证码已发送"));
                                                            } else {
                                                                source.sendError(Text.literal("发送失败: " + body));
                                                            }
                                                        } else {
                                                            source.sendError(Text.literal("发送失败: " + response.body));
                                                        }
                                                    }, MinecraftClient.getInstance())
                                                    .exceptionally(throwable -> {
                                                        MinecraftClient.getInstance().execute(() ->
                                                                source.sendError(Text.literal("发送异常: " + throwable.getMessage()))
                                                        );
                                                        return null;
                                                    });
                                            return 1;
                                        })
                                )
                        )
                        .then(ClientCommandManager.literal("login")
                                .then(ClientCommandManager.argument("phone", StringArgumentType.word())
                                        .then(ClientCommandManager.argument("code", StringArgumentType.word())
                                                .executes(ctx -> {
                                                    String phone = StringArgumentType.getString(ctx, "phone");
                                                    String code = StringArgumentType.getString(ctx, "code");
                                                    login(ctx.getSource(), phone, code);
                                                    return 1;
                                                })


                                        )


                                )

                        )

                        .then(ClientCommandManager.literal("refreshlogin")
                                .executes(ctx -> {
                                    refreshLogin(ctx.getSource());
                                    return 1;
                                })
                        )

                        .then(ClientCommandManager.literal("lyric")
                                .then(ClientCommandManager.literal("off")
                                        .executes(ctx -> {
                                            LyricRenderer.setVisible(false);
                                            ctx.getSource().sendFeedback(Text.literal("§a歌词已关闭"));
                                            return 1;
                                        })
                                )
                                .then(ClientCommandManager.literal("single")
                                        .executes(ctx -> {
                                            LyricRenderer.setBilingualMode(false);
                                            LyricRenderer.setVisible(true);
                                            ctx.getSource().sendFeedback(Text.literal("§a单语歌词模式"));
                                            return 1;
                                        })
                                )
                                .then(ClientCommandManager.literal("bilingual")
                                        .executes(ctx -> {
                                            LyricRenderer.setBilingualMode(true);
                                            LyricRenderer.setVisible(true);
                                            ctx.getSource().sendFeedback(Text.literal("§a双语歌词模式"));
                                            return 1;
                                        })
                                )
                        )
                        .then(ClientCommandManager.literal("vip")
                                .then(ClientCommandManager.literal("status")
                                        .executes(ctx -> {
                                            checkVipStatus(ctx.getSource());
                                            return 1;
                                        })
                                )
                                .then(ClientCommandManager.literal("day")
                                        .executes(ctx -> {
                                            receiveDayVip(ctx.getSource());
                                            return 1;
                                        })
                                )
                                .then(ClientCommandManager.literal("upgrade")
                                        .executes(ctx -> {
                                            upgradeVip(ctx.getSource());
                                            return 1;
                                        })
                                )
                                .then(ClientCommandManager.literal("month")
                                        .executes(ctx -> {
                                            getMonthVipRecord(ctx.getSource());
                                            return 1;
                                        })
                                )
                        )
                        .then(ClientCommandManager.literal("playlist")
                                .then(ClientCommandManager.literal("list")
                                        .executes(ctx -> {
                                            showPlaylists(ctx.getSource());
                                            return 1;
                                        })
                                )
                                .then(ClientCommandManager.literal("songs")
                                        .then(ClientCommandManager.argument("id", StringArgumentType.word())
                                                .executes(ctx -> {
                                                    String id = StringArgumentType.getString(ctx, "id");
                                                    showPlaylistSongs(ctx.getSource(), id);
                                                    return 1;
                                                })
                                        )
                                )
                        )
                        .then(ClientCommandManager.literal("qrlogin")
                                .executes(ctx -> {
                                    startQrLogin(ctx.getSource());
                                    return 1;
                                })
                        )
                        .then(ClientCommandManager.literal("user")
                                .executes(ctx -> {
                                    showUserInfo(ctx.getSource());
                                    return 1;
                                })
                        )
                        .then(ClientCommandManager.literal("cloud")
                                .then(ClientCommandManager.literal("list")
                                        .executes(ctx -> {
                                            showCloudList(ctx.getSource(), 1, 30);
                                            return 1;
                                        })
                                )
                                .then(ClientCommandManager.literal("play")
                                        .then(ClientCommandManager.argument("params", StringArgumentType.greedyString())
                                                .executes(ctx -> {
                                                    String[] parts = StringArgumentType.getString(ctx, "params").split(",");
                                                    if (parts.length >= 3) {
                                                        playCloudSong(ctx.getSource(), parts[0], parts[1], parts[2]);
                                                    } else {
                                                        ctx.getSource().sendError(Text.literal("格式: hash,name,audio_id"));
                                                    }
                                                    return 1;
                                                })
                                        )
                                )
                        )
                        .then(ClientCommandManager.literal("wxlogin")
                                .executes(ctx -> {
                                    startWxLogin(ctx.getSource());
                                    return 1;
                                })
                        )
                        .then(ClientCommandManager.literal("help")
                                .executes(ctx -> {
                                    showHelp(ctx.getSource());
                                    return 1;
                                })
                        )
        );


    }

    private static void login(FabricClientCommandSource source, String phone, String code) {
        source.sendFeedback(Text.literal("§e正在登录..."));
        Map<String, String> cookie = new HashMap<>();
        cookie.put("KUGOU_API_MID", KugouApiClient.getMid());
        cookie.put("KUGOU_API_GUID", UUID.randomUUID().toString());
        cookie.put("KUGOU_API_MAC", "02:00:00:00:00:00");
        cookie.put("KUGOU_API_DEV", KugouUtils.randomString(10).toUpperCase());
        cookie.put("dfid", KugouApiClient.getDfid());
        LoginApi.loginByVerifyCode(phone, code,
                cookie.get("KUGOU_API_GUID"),
                cookie.get("KUGOU_API_MAC"),
                cookie.get("KUGOU_API_DEV"),
                KugouApiClient.getDfid()
        ).thenAccept(resp -> {
            MinecraftClient.getInstance().execute(() -> {
                if (resp.status == 200 && resp.body instanceof JsonObject) {
                    JsonObject body = (JsonObject) resp.body;
                    if (body.has("status") && body.get("status").getAsInt() == 1) {
                        source.sendFeedback(Text.literal("§a登录成功！"));
                    } else {
                        source.sendError(Text.literal("§c登录失败: " + (resp.body != null ? resp.body.toString() : "未知错误")));
                    }
                } else {
                    source.sendError(Text.literal("§c登录失败: " + (resp.body != null ? resp.body.toString() : "服务器无响应")));
                }
            });
        }).exceptionally(e -> {
            MinecraftClient.getInstance().execute(() ->
                    source.sendError(Text.literal("§c登录异常: " + e.getMessage()))
            );
            return null;
        });
    }

    private static void refreshLogin(FabricClientCommandSource source) {
        String token = KugouApiClient.getToken();
        long userid = KugouApiClient.getUserid();
        if (token.isEmpty() || userid == 0) {
            source.sendError(Text.literal("§c没有登录信息，请先扫码登录或使用 /kugou login"));
            return;
        }
        Map<String, String> cookie = new HashMap<>();
        cookie.put("dfid", KugouApiClient.getDfid());
        cookie.put("mid", KugouApiClient.getMid());
        source.sendFeedback(Text.literal("§e正在刷新登录状态..."));
        LoginApi.refreshLogin(token, userid, cookie).thenAcceptAsync(resp -> {
            MinecraftClient.getInstance().execute(() -> {
                if (resp.status == 200 && resp.body instanceof JsonObject) {
                    JsonObject body = (JsonObject) resp.body;
                    if (body.has("status") && body.get("status").getAsInt() == 1) {
                        source.sendFeedback(Text.literal("§a刷新成功，登录态已延长"));
                    } else {
                        source.sendError(Text.literal("§c刷新失败: " + body.toString()));
                    }
                } else {
                    source.sendError(Text.literal("§c刷新失败，服务器返回异常"));
                }
            });
        }, MinecraftClient.getInstance()).exceptionally(e -> {
            MinecraftClient.getInstance().execute(() -> source.sendError(Text.literal("§c刷新异常: " + e.getMessage())));
            return null;
        });
    }

    private static void searchSongs(FabricClientCommandSource source, String keyword) {
        if (KugouApiClient.getDfid().equals("-")) {
            source.sendError(Text.literal("§c设备正在初始化，请稍后再试"));
            return;
        }
        source.sendFeedback(Text.literal("§a正在搜索: " + keyword + " ..."));
        Map<String, String> cookie = new HashMap<>();
        SearchApi.searchComplex(keyword, 1, 5, cookie)
                .thenAcceptAsync(response -> {
                    if (response.status != 200 || !(response.body instanceof JsonObject)) {
                        MinecraftClient.getInstance().execute(() -> source.sendError(Text.literal("§c搜索失败")));
                        System.err.println("[Kugou] 搜索失败，status: " + response.status + ", body: " + response.body);
                        return;
                    }
                    JsonObject body = (JsonObject) response.body;
                    JsonObject data = body.getAsJsonObject("data");

                    // 然后在你遍历 categories 之前，打印分类数量：
                    JsonArray categories = data.getAsJsonArray("lists");
                    for (int i = 0; i < categories.size(); i++) {
                        JsonObject cat = categories.get(i).getAsJsonObject();
                        System.out.println("[Kugou] 分类 " + i + " type: " + cat.get("type").getAsString() + " 歌曲数: " + cat.getAsJsonArray("lists").size());
                    }
                    // 继续原有的查找 song 分类的逻辑...
                    if (data == null || !data.has("lists")) {
                        MinecraftClient.getInstance().execute(() -> source.sendError(Text.literal("§c无搜索结果")));
                        return;
                    }
                    // 查找 type 为 "song" 的分类
                    JsonArray songs = null;
                    for (int i = 0; i < categories.size(); i++) {
                        JsonObject cat = categories.get(i).getAsJsonObject();
                        if ("song".equals(cat.get("type").getAsString())) {
                            songs = cat.getAsJsonArray("lists");
                            break;
                        }
                    }
                    if (songs == null || songs.size() == 0) {
                        MinecraftClient.getInstance().execute(() -> source.sendFeedback(Text.literal("§e未找到相关歌曲")));
                        return;
                    }
                    JsonArray finalSongs = songs;
                    MinecraftClient.getInstance().execute(() -> {
                        source.sendFeedback(Text.literal("§6=== 搜索结果(共" + finalSongs.size() + "首) ==="));
                        for (int i = 0; i < finalSongs.size(); i++) {
                            JsonObject song = finalSongs.get(i).getAsJsonObject();
                            String name = song.get("FileName").getAsString();
                            String singer = song.get("SingerName").getAsString();
                            String hash = song.get("FileHash").getAsString();
                            String albumId = "0";
                            if (song.has("AlbumID") && !song.get("AlbumID").getAsString().isEmpty()) {
                                albumId = song.get("AlbumID").getAsString();
                            }
                            String albumAudioId = "0";
                            if (song.has("MixSongID") && !song.get("MixSongID").getAsString().isEmpty()) {
                                albumAudioId = song.get("MixSongID").getAsString();
                            }
                            String cmd = "/kugou play " + hash + "," + albumId + "," + albumAudioId + "," + currentQuality;
                            Text songLine = Text.literal((i + 1) + ". " + name + " - " + singer + "  §a[播放: " + cmd + "]")
                                    .styled(style -> style
                                            .withColor(Formatting.YELLOW)
                                    );
                            source.sendFeedback(Text.literal("").append(songLine));
                        }
                    });
                }, MinecraftClient.getInstance())
                .exceptionally(e -> {
                    MinecraftClient.getInstance().execute(() -> source.sendError(Text.literal("§c搜索出错: " + e.getCause().getMessage())));
                    return null;
                });
    }

    private static void playSong(FabricClientCommandSource source, String hash, String albumId, String albumAudioId, String quality) {
        Map<String, String> cookie = new HashMap<>();
        cookie.put("dfid", KugouApiClient.getDfid());
        if (KugouApiClient.getToken() != null && !KugouApiClient.getToken().isEmpty())
            cookie.put("token", KugouApiClient.getToken());
        cookie.put("userid", String.valueOf(KugouApiClient.getUserid()));
        int albumIdInt = 0;
        int albumAudioIdInt = 0;
        try {
            if (!albumId.isEmpty()) albumIdInt = Integer.parseInt(albumId);
            if (!albumAudioId.isEmpty()) albumAudioIdInt = Integer.parseInt(albumAudioId);
        } catch (NumberFormatException ignored) {}

        SongApi.getSongUrl(hash, quality, albumIdInt, albumAudioIdInt, cookie)
                .thenAccept(response -> {
                    if (response.status == 200 && response.body instanceof JsonObject) {
                        JsonObject body = (JsonObject) response.body;
                        if (body.has("url") && body.get("url").isJsonArray()) {
                            JsonArray urls = body.getAsJsonArray("url");
                            if (urls.size() > 0) {
                                String url = urls.get(0).getAsString();
                                AudioPlayer.play(url, () -> {
                                    // 播放自然结束的提示（只在不被 stop 打断时执行）
                                    MinecraftClient.getInstance().execute(() -> {
                                        MinecraftClient.getInstance().player.sendMessage(Text.literal("§e播放结束"), false);
                                    });
                                    LyricRenderer.setLyrics(null); // 清空歌词
                                });
                                MinecraftClient.getInstance().execute(() -> {
                                    MinecraftClient.getInstance().player.sendMessage(Text.literal("§a▶ 开始播放"), false);
                                    lastHash = hash;
                                    lastAlbumId = albumId;
                                    lastAlbumAudioId = albumAudioId;
                                    lastQuality = quality;
                                });
                                // 获取歌词
                                fetchLyrics(hash, albumAudioId);
                                return;
                            }
                        }
                    }
                    MinecraftClient.getInstance().execute(() -> {
                        MinecraftClient.getInstance().player.sendMessage(Text.literal("§c无法获取播放地址"), false);
                    });
                })
                .exceptionally(e -> {
                    MinecraftClient.getInstance().execute(() -> {
                        MinecraftClient.getInstance().player.sendMessage(Text.literal("§c播放出错: " + e.getCause().getMessage()), false);
                    });
                    return null;
                });
    }

    // 然后添加以下方法到 MusicCommand 类内部
    private static void checkVipStatus(FabricClientCommandSource source) {
        Map<String, String> cookie = new HashMap<>();
        cookie.put("dfid", KugouApiClient.getDfid());
        cookie.put("token", KugouApiClient.getToken());
        cookie.put("userid", String.valueOf(KugouApiClient.getUserid()));
        VipApi.getUnionVip(cookie).thenAccept(resp -> {
            MinecraftClient.getInstance().execute(() -> {
                if (resp.status == 200 && resp.body instanceof JsonObject) {
                    JsonObject body = (JsonObject) resp.body;
                    JsonObject data = body.getAsJsonObject("data");
                    if (data != null) {
                        StringBuilder sb = new StringBuilder("§aVIP状态：\n");
                        JsonArray busiVip = data.getAsJsonArray("busi_vip");
                        if (busiVip != null) {
                            for (int i = 0; i < busiVip.size(); i++) {
                                JsonObject vip = busiVip.get(i).getAsJsonObject();
                                String type = vip.get("product_type").getAsString();
                                String endTime = vip.get("vip_end_time").getAsString();
                                int isVip = vip.get("is_vip").getAsInt();
                                sb.append("§e- ").append(type).append(": ").append(isVip == 1 ? "§a有效" : "§c无效").append("，到期：").append(endTime).append("\n");
                            }
                        } else {
                            sb.append("§c无法获取VIP信息");
                        }
                        source.sendFeedback(Text.literal(sb.toString()));
                        return;
                    }
                }
                source.sendError(Text.literal("§c查询VIP状态失败"));
            });
        }).exceptionally(e -> {
            MinecraftClient.getInstance().execute(() -> source.sendError(Text.literal("§c查询异常: " + e.getMessage())));
            return null;
        });
    }
    private static void receiveDayVip(FabricClientCommandSource source) {
        Map<String, String> cookie = new HashMap<>();
        cookie.put("dfid", KugouApiClient.getDfid());
        cookie.put("token", KugouApiClient.getToken());
        cookie.put("userid", String.valueOf(KugouApiClient.getUserid()));
        // 领取今天 VIP，日期格式 yyyy-MM-dd
        String today = java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE);
        VipApi.receiveDayVip(today, cookie).thenAccept(resp -> {
            MinecraftClient.getInstance().execute(() -> {
                if (resp.status == 200 && resp.body instanceof JsonObject) {
                    JsonObject body = (JsonObject) resp.body;
                    if (body.has("status") && body.get("status").getAsInt() == 1) {
                        source.sendFeedback(Text.literal("§a领取成功！当天VIP已到账"));
                    } else {
                        source.sendError(Text.literal("§c领取失败: " + body));
                    }
                } else {
                    source.sendError(Text.literal("§c领取失败: 服务器返回异常"));
                }
            });
        }).exceptionally(e -> {
            MinecraftClient.getInstance().execute(() -> source.sendError(Text.literal("§c领取异常: " + e.getMessage())));
            return null;
        });
    }
    private static void upgradeVip(FabricClientCommandSource source) {
        Map<String, String> cookie = new HashMap<>();
        cookie.put("dfid", KugouApiClient.getDfid());
        cookie.put("token", KugouApiClient.getToken());
        cookie.put("userid", String.valueOf(KugouApiClient.getUserid()));
        VipApi.upgradeVip(cookie).thenAccept(resp -> {
            MinecraftClient.getInstance().execute(() -> {
                if (resp.status == 200 && resp.body instanceof JsonObject) {
                    JsonObject body = (JsonObject) resp.body;
                    if (body.has("status") && body.get("status").getAsInt() == 1) {
                        source.sendFeedback(Text.literal("§a升级成功！畅听VIP已激活"));
                    } else {
                        source.sendError(Text.literal("§c升级失败: " + body));
                    }
                } else {
                    source.sendError(Text.literal("§c升级失败: 可能未领取当天VIP或已是SVIP"));
                }
            });
        }).exceptionally(e -> {
            MinecraftClient.getInstance().execute(() -> source.sendError(Text.literal("§c升级异常: " + e.getMessage())));
            return null;
        });
    }
    private static void getMonthVipRecord(FabricClientCommandSource source) {
        Map<String, String> cookie = new HashMap<>();
        cookie.put("dfid", KugouApiClient.getDfid());
        cookie.put("token", KugouApiClient.getToken());
        cookie.put("userid", String.valueOf(KugouApiClient.getUserid()));

        VipApi.getMonthVipRecord(cookie).thenAccept(resp -> {
            MinecraftClient.getInstance().execute(() -> {
                if (resp.status == 200 && resp.body instanceof JsonObject) {
                    JsonObject body = (JsonObject) resp.body;
                    JsonObject data = body.getAsJsonObject("data");
                    if (data != null && data.has("list") && data.has("month")) {
                        String month = data.get("month").getAsString();  // 例如 "2026-07"
                        JsonArray list = data.getAsJsonArray("list");
                        int count = 0;
                        for (int i = 0; i < list.size(); i++) {
                            JsonObject item = list.get(i).getAsJsonObject();
                            if (item.has("day")) {
                                String day = item.get("day").getAsString();
                                if (day.startsWith(month) && item.has("receive_vip") && item.get("receive_vip").getAsInt() == 1) {
                                    count++;
                                }
                            }
                        }
                        source.sendFeedback(Text.literal("§a本月已领取 VIP 天数: " + count));
                        return;
                    }
                }
                source.sendError(Text.literal("§c查询失败"));
            });
        }).exceptionally(e -> {
            MinecraftClient.getInstance().execute(() -> source.sendError(Text.literal("§c查询异常: " + e.getMessage())));
            return null;
        });
    }

    private static void fetchLyrics(String hash, String albumAudioId) {
        Map<String, String> cookie = new HashMap<>();
        cookie.put("dfid", KugouApiClient.getDfid());
        cookie.put("token", KugouApiClient.getToken());
        cookie.put("userid", String.valueOf(KugouApiClient.getUserid()));

        LyricApi.searchLyric(null, hash, 0, Long.parseLong(albumAudioId.isEmpty() ? "0" : albumAudioId), "no", cookie)
                .thenAccept(searchResp -> {
                    if (searchResp.status == 200 && searchResp.body instanceof JsonObject) {
                        JsonObject body = (JsonObject) searchResp.body;
                        if (body.has("candidates") && body.get("candidates").isJsonArray()) {
                            JsonArray candidates = body.getAsJsonArray("candidates");
                            if (candidates.size() > 0) {
                                JsonObject first = candidates.get(0).getAsJsonObject();
                                String id = first.get("id").getAsString();
                                String accesskey = first.get("accesskey").getAsString();
                                // 获取歌词内容
                                LyricApi.getLyric(id, accesskey, "lrc", true, cookie)
                                        .thenAccept(lyricResp -> {
                                            if (lyricResp.status == 200 && lyricResp.body instanceof JsonObject) {
                                                JsonObject lyricBody = (JsonObject) lyricResp.body;
                                                if (lyricBody.has("decodeContent")) {
                                                    String lrcContent = lyricBody.get("decodeContent").getAsString();
                                                    LyricRenderer.setLyrics(lrcContent);
                                                    System.out.println("[Kugou] 歌词设置成功，行数: " + lrcContent.split("\n").length);
                                                } else {
                                                    System.out.println("[Kugou] 响应中缺少 decodeContent");
                                                }
                                            }
                                        }).exceptionally(e -> { e.printStackTrace(); return null; });
                            } else {
                                System.out.println("[Kugou] 歌词候选列表为空");
                            }
                        } else {
                            System.out.println("[Kugou] 歌词搜索响应中没有 candidates 字段");
                        }
                    }
                }).exceptionally(e -> { e.printStackTrace(); return null; });
    }
    private static void showPlaylists(FabricClientCommandSource source) {
        Map<String, String> cookie = new HashMap<>();
        cookie.put("dfid", KugouApiClient.getDfid());
        cookie.put("token", KugouApiClient.getToken());
        cookie.put("userid", String.valueOf(KugouApiClient.getUserid()));

        source.sendFeedback(Text.literal("§e正在获取歌单..."));
        PlaylistApi.getUserPlaylists(1, 500, cookie).thenAcceptAsync(resp -> {
            if (resp.status == 200 && resp.body instanceof JsonObject) {
                JsonObject body = (JsonObject) resp.body;
                if (body.has("data")) {
                    JsonObject data = body.getAsJsonObject("data");
                    if (data.has("info") && data.get("info").isJsonArray()) {
                        JsonArray info = data.getAsJsonArray("info");
                        if (info.size() > 0) {
                            MinecraftClient.getInstance().execute(() -> {
                                source.sendFeedback(Text.literal("§a=== 我的歌单 (" + info.size() + "个) ==="));
                                for (int i = 0; i < info.size(); i++) {
                                    JsonObject pl = info.get(i).getAsJsonObject();
                                    String name = pl.get("name").getAsString();
                                    String globalId = pl.has("global_collection_id") ? pl.get("global_collection_id").getAsString() : "0";
                                    String cmd = "/kugou playlist songs " + globalId;
                                    Text line = Text.literal((i + 1) + ". " + name + " [" + (pl.has("count") ? pl.get("count").getAsInt() : 0) + "首]")
                                            .styled(style -> style
                                                    .withColor(Formatting.YELLOW)
                                            );
                                    source.sendFeedback(Text.literal("").append(line));
                                }
                            });
                            return;
                        }
                    }
                }
            }
            MinecraftClient.getInstance().execute(() -> source.sendError(Text.literal("§c获取歌单失败")));
            MinecraftClient.getInstance().execute(() -> {
                source.sendError(Text.literal("§c获取歌单失败"));
                if (resp.body != null) {
                    System.err.println("[Kugou] 歌单失败详情: " + resp.body.toString());
                }
            });
        }, MinecraftClient.getInstance()).exceptionally(e -> {
            MinecraftClient.getInstance().execute(() -> source.sendError(Text.literal("§c获取歌单出错")));
            return null;
        });
    }

    private static void showPlaylistSongs(FabricClientCommandSource source, String globalCollectionId) {
        Map<String, String> cookie = new HashMap<>();
        cookie.put("dfid", KugouApiClient.getDfid());
        cookie.put("token", KugouApiClient.getToken());
        cookie.put("userid", String.valueOf(KugouApiClient.getUserid()));

        source.sendFeedback(Text.literal("§e正在获取歌曲..."));
        PlaylistApi.getPlaylistSongs(globalCollectionId, 1, 30, cookie).thenAcceptAsync(resp -> {
            if (resp.status == 200 && resp.body instanceof JsonObject) {
                JsonObject body = (JsonObject) resp.body;
                if (body.has("data")) {
                    JsonObject data = body.getAsJsonObject("data");
                    if (data.has("songs") && data.get("songs").isJsonArray()) {
                        JsonArray songs = data.getAsJsonArray("songs");
                        if (songs.size() > 0) {
                            MinecraftClient.getInstance().execute(() -> {
                                source.sendFeedback(Text.literal("§a=== 歌单歌曲 (" + songs.size() + "首) ==="));
                                for (int i = 0; i < songs.size(); i++) {
                                    JsonObject song = songs.get(i).getAsJsonObject();
                                    // 安全获取字段，避免空指针
                                    String name = song.has("name") ? song.get("name").getAsString() : "未知歌曲";
                                    String singer = "";
                                    if (song.has("singerinfo") && song.get("singerinfo").isJsonArray()) {
                                        JsonArray singersArray = song.getAsJsonArray("singerinfo");
                                        if (singersArray.size() > 0) {
                                            JsonObject firstSinger = singersArray.get(0).getAsJsonObject();
                                            if (firstSinger.has("name")) {
                                                singer = firstSinger.get("name").getAsString();
                                            }
                                        }
                                    }
                                    String hash = song.has("hash") ? song.get("hash").getAsString() : "";
                                    String albumId = "0";
                                    if (song.has("album_id") && !song.get("album_id").isJsonNull()) {
                                        albumId = song.get("album_id").getAsString();
                                    }
                                    String mixSongId = "0";
                                    if (song.has("mixsongid") && !song.get("mixsongid").isJsonNull()) {
                                        mixSongId = song.get("mixsongid").getAsString();
                                    }
                                    String cmd = "/kugou play " + hash + "," + albumId + "," + mixSongId + "," + currentQuality;
                                    Text line = Text.literal((i + 1) + ". " + name + " - " + singer)
                                            .styled(style -> style
                                                    .withColor(Formatting.GREEN)
                                            );
                                    source.sendFeedback(Text.literal("").append(line));
                                }
                            });
                            return;
                        }
                    }
                }
            }
            MinecraftClient.getInstance().execute(() -> source.sendError(Text.literal("§c获取歌曲失败或歌单为空")));
        }, MinecraftClient.getInstance()).exceptionally(e -> {
            MinecraftClient.getInstance().execute(() -> source.sendError(Text.literal("§c获取歌曲出错")));
            return null;
        });
    }

    private static void startQrLogin(FabricClientCommandSource source) {
        Map<String, String> cookie = new HashMap<>();
        cookie.put("dfid", KugouApiClient.getDfid());
        cookie.put("mid", KugouApiClient.getMid());
        source.sendFeedback(Text.literal("§e正在生成二维码链接..."));
        LoginApi.generateQrKey(cookie).thenAcceptAsync(resp -> {
            if (resp.status == 200 && resp.body instanceof JsonObject) {
                JsonObject body = (JsonObject) resp.body;
                if (body.has("data")) {
                    JsonElement dataElem = body.get("data");
                    if (dataElem.isJsonObject()) {
                        JsonObject data = dataElem.getAsJsonObject();
                        if (data.has("qrcode")) {
                            String key = data.get("qrcode").getAsString();
                            qrKey = key;
                            String url = "https://h5.kugou.com/apps/loginQRCode/html/index.html?qrcode=" + key;

                            if (data.has("qrcode_img")) {
                                String base64 = data.get("qrcode_img").getAsString();
                                MinecraftClient.getInstance().execute(() -> {
                                    MinecraftClient.getInstance().setScreen(new QrCodeScreen(base64));
                                });
                            }

                            // 开始轮询
                            pollingTask = scheduler.scheduleAtFixedRate(() -> {
                                pollQrStatus(source);
                            }, 0, 3, java.util.concurrent.TimeUnit.SECONDS);
                        } else {
                            MinecraftClient.getInstance().execute(() -> source.sendError(Text.literal("§c响应缺少 qrcode 字段")));
                        }
                    } else {
                        MinecraftClient.getInstance().execute(() -> source.sendError(Text.literal("§c响应 data 不是对象")));
                    }
                } else {
                    MinecraftClient.getInstance().execute(() -> source.sendError(Text.literal("§c响应缺少 data 字段")));
                }
            } else {
                MinecraftClient.getInstance().execute(() -> source.sendError(Text.literal("§c请求二维码 key 失败")));
            }
        }, MinecraftClient.getInstance()).exceptionally(e -> {
            MinecraftClient.getInstance().execute(() -> source.sendError(Text.literal("§c异常: " + e.getMessage())));
            return null;
        });
    }

    private static void pollQrStatus(FabricClientCommandSource source) {
        if (qrKey == null) {
            stopPolling();
            return;
        }
        Map<String, String> emptyCookie = new HashMap<>();
        LoginApi.checkQrStatus(qrKey, emptyCookie).thenAcceptAsync(resp -> {
            if (resp.status == 200 && resp.body instanceof JsonObject) {
                JsonObject body = (JsonObject) resp.body;
                if (body.has("data")) {
                    JsonObject data = body.getAsJsonObject("data");
                    if (data.has("status")) {
                        int status = data.get("status").getAsInt();
                        if (status == 4) {
                            // 登录成功，已自动保存
                            MinecraftClient.getInstance().execute(() -> {
                                MinecraftClient.getInstance().setScreen(null); // 关闭二维码屏幕
                                source.sendFeedback(Text.literal("§a扫码登录成功！"));
                            });
                            stopPolling();
                        } else if (status == 0) {
                            MinecraftClient.getInstance().execute(() -> {
                                source.sendError(Text.literal("§c二维码已过期，请重新执行 /kugou qrlogin"));
                            });
                            stopPolling();
                        }
                        // 其他状态继续轮询
                    }
                }
            }
        }, MinecraftClient.getInstance()).exceptionally(e -> {
            stopPolling();
            return null;
        });
    }

    private static void stopPolling() {
        if (pollingTask != null && !pollingTask.isCancelled()) {
            pollingTask.cancel(true);
            pollingTask = null;
        }
        qrKey = null;
    }
    // 显示云盘音乐列表
    private static void showCloudList(FabricClientCommandSource source, int page, int pagesize) {
        Map<String, String> cookie = new HashMap<>();
        cookie.put("dfid", KugouApiClient.getDfid());
        cookie.put("token", KugouApiClient.getToken());
        cookie.put("userid", String.valueOf(KugouApiClient.getUserid()));
        cookie.put("KUGOU_API_MID", KugouApiClient.getMid());

        source.sendFeedback(Text.literal("§e正在获取云盘音乐列表..."));

        CloudApi.getCloudList(page, pagesize, cookie).thenAcceptAsync(resp -> {
            // 调试日志（可保留用于排查问题）
            // System.out.println("[CloudApi] 响应status: " + resp.status);
            // System.out.println("[CloudApi] 响应body: " + resp.body);

            // 检查响应有效性
            if (resp.status != 200 || !(resp.body instanceof JsonObject)) {
                MinecraftClient.getInstance().execute(() ->
                        source.sendError(Text.literal("§c获取云盘列表失败，状态码: " + resp.status))
                );
                return;
            }

            JsonObject body = (JsonObject) resp.body;

            // 处理最终结果
            MinecraftClient.getInstance().execute(() -> {
                // 接口级错误检查
                if (body.has("error_code") && body.get("error_code").getAsInt() != 0) {
                    source.sendError(Text.literal("§c接口错误: " + body.get("error_code").getAsInt()));
                    return;
                }

                // 正确路径：data → list (空指针安全)
                JsonArray list = null;
                if (body.has("data") && !body.get("data").isJsonNull()) {
                    JsonObject dataObj = body.getAsJsonObject("data");
                    if (dataObj.has("list") && !dataObj.get("list").isJsonNull()) {
                        list = dataObj.getAsJsonArray("list");
                    }
                }

                if (list == null || list.isEmpty()) {
                    source.sendError(Text.literal("§c云盘音乐列表为空"));
                    return;
                }

                // 显示列表
                source.sendFeedback(Text.literal("§a=== 云盘音乐 (" + list.size() + "首) ==="));
                for (int i = 0; i < list.size(); i++) {
                    JsonObject song = list.get(i).getAsJsonObject();
                    // 安全获取字段，避免空指针
                    String name = song.has("name") ? song.get("name").getAsString() : "未知歌曲";
                    String hash = song.has("hash") ? song.get("hash").getAsString() : "";
                    String audioId = song.has("audio_id") ? song.get("audio_id").getAsString() : "0";

                    String cmd = "/kugou cloud play " + hash + "," + name + "," + audioId;
                    Text line = Text.literal((i + 1) + ". " + name)
                            .styled(style -> style
                                    .withColor(Formatting.AQUA)
                            );
                    source.sendFeedback(Text.literal("").append(line));
                }
            });
        }, MinecraftClient.getInstance()).exceptionally(e -> {
            MinecraftClient.getInstance().execute(() ->
                    source.sendError(Text.literal("§c请求异常: " + e.getMessage()))
            );
            return null;
        });
    }
    // 播放云盘音乐（已修复解析逻辑）
    private static void playCloudSong(FabricClientCommandSource source, String hash, String name, String audioId) {
        final Map<String, String> cookie = new HashMap<>();
        cookie.put("dfid", KugouApiClient.getDfid());
        cookie.put("token", KugouApiClient.getToken());
        cookie.put("userid", String.valueOf(KugouApiClient.getUserid()));
        cookie.put("KUGOU_API_MID", KugouApiClient.getMid());
        final long finalAudioId = audioId.isEmpty() ? 0 : Long.parseLong(audioId);
        final String finalHash = hash.toLowerCase();
        final FabricClientCommandSource cmdSource = source;
        cmdSource.sendFeedback(Text.literal("§e正在获取云盘音乐播放地址..."));
        CloudApi.getCloudUrl(finalHash, 0, finalAudioId, name, cookie)
                .thenAcceptAsync(resp -> {
                    if (resp.status != 200 || !(resp.body instanceof JsonObject)) {
                        MinecraftClient.getInstance().execute(() ->
                                cmdSource.sendError(Text.literal("§c获取播放地址失败，状态码: " + resp.status))
                        );
                        return;
                    }
                    JsonObject body = (JsonObject) resp.body;
                    if (body.has("error_code") && body.get("error_code").getAsInt() != 0) {
                        String errMsg = body.has("error_msg") ? body.get("error_msg").getAsString() : "未知错误";
                        MinecraftClient.getInstance().execute(() ->
                                cmdSource.sendError(Text.literal("§c获取播放地址失败: " + errMsg))
                        );
                        return;
                    }
                    String playUrl = null;
                    if (body.has("data") && body.get("data").isJsonObject()) {
                        JsonObject data = body.getAsJsonObject("data");
                        if (data.has("url") && !data.get("url").isJsonNull()) {
                            playUrl = data.get("url").getAsString();
                        } else if (data.has("backup_url") && !data.get("backup_url").isJsonNull()) {
                            playUrl = data.get("backup_url").getAsString();
                        }
                    }
                    if (playUrl == null || playUrl.isEmpty()) {
                        MinecraftClient.getInstance().execute(() ->
                                cmdSource.sendError(Text.literal("§c未获取到有效播放地址"))
                        );
                        return;
                    }
                    final String finalPlayUrl = playUrl;
                    new Thread(() -> {
                        try {
                            AudioPlayer.play(finalPlayUrl, () -> {
                                MinecraftClient.getInstance().execute(() -> {
                                    if (MinecraftClient.getInstance().player != null) {
                                        MinecraftClient.getInstance().player.sendMessage(Text.literal("§e播放结束"), false);
                                    }
                                    LyricRenderer.setLyrics(null);
                                });
                            });
                        } catch (Exception e) {
                            MinecraftClient.getInstance().execute(() ->
                                    cmdSource.sendError(Text.literal("§c播放失败: " + e.getMessage()))
                            );
                            e.printStackTrace();
                        }
                    }, "Kugou-MusicPlayer").start();
                    MinecraftClient.getInstance().execute(() -> {
                        cmdSource.sendFeedback(Text.literal("§a▶ 开始播放"));
                        lastHash = finalHash;
                        lastAlbumId = "0";
                        lastAlbumAudioId = String.valueOf(finalAudioId);
                        lastQuality = "128";
                    });
                }, MinecraftClient.getInstance())
                .exceptionally(e -> {
                    MinecraftClient.getInstance().execute(() ->
                            cmdSource.sendError(Text.literal("§c获取播放地址异常: " + e.getMessage()))
                    );
                    e.printStackTrace();
                    return null;
                });
    }
    // 显示用户信息
    private static void showUserInfo(FabricClientCommandSource source) {
        Map<String, String> cookie = new HashMap<>();
        cookie.put("dfid", KugouApiClient.getDfid());
        cookie.put("token", KugouApiClient.getToken());
        cookie.put("userid", String.valueOf(KugouApiClient.getUserid()));
        cookie.put("KUGOU_API_MID", KugouApiClient.getMid());

        LoginApi.getUserDetail(cookie).thenAcceptAsync(resp -> {
            if (resp.status == 200 && resp.body instanceof JsonObject) {
                JsonObject body = (JsonObject) resp.body;
                JsonObject data = body.getAsJsonObject("data");
                if (data == null) {
                    MinecraftClient.getInstance().execute(() -> source.sendError(Text.literal("§c用户数据为空")));
                    return;
                }
                // 提取非敏感信息
                String nickname = data.has("nickname") ? data.get("nickname").getAsString() : "未知";
                int vipType = data.has("vip_type") ? data.get("vip_type").getAsInt() : 0;
                int follows = data.has("follows") ? data.get("follows").getAsInt() : 0;
                int fans = data.has("fans") ? data.get("fans").getAsInt() : 0;
                int friends = data.has("friends") ? data.get("friends").getAsInt() : 0;
                String gender = data.has("gender") ? (data.get("gender").getAsInt() == 1 ? "男" : "女") : "未知";
                String descri = data.has("descri") ? data.get("descri").getAsString() : "";
                String city = data.has("city") ? data.get("city").getAsString() : "未知";
                String province = data.has("province") ? data.get("province").getAsString() : "未知";
                int visitors = data.has("visitors") ? data.get("visitors").getAsInt() : 0;
                long tExpire = KugouConfig.getInstance().tExpireTime;

                MinecraftClient.getInstance().execute(() -> {
                    source.sendFeedback(Text.literal("§6===== 用户信息 ====="));
                    source.sendFeedback(Text.literal("§e昵称: " + (nickname.isEmpty() ? "未设置" : nickname)));
                    source.sendFeedback(Text.literal("§e性别: " + gender));
                    source.sendFeedback(Text.literal("§e地区: " + province + " " + city));
                    source.sendFeedback(Text.literal("§eVIP: " + (vipType > 0 ? "是" : "否")));
                    source.sendFeedback(Text.literal("§e关注: " + follows + " | 粉丝: " + fans + " | 好友: " + friends));
                    source.sendFeedback(Text.literal("§e主页访问: " + visitors));
                    if (tExpire > 0) {
                        String expireStr = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new java.util.Date(tExpire * 1000));
                        source.sendFeedback(Text.literal("§e登录过期时间: " + expireStr));
                    }
                    if (!descri.isEmpty()) source.sendFeedback(Text.literal("§e简介: " + descri));
                });
            } else {
                String errorMsg = resp.body != null ? resp.body.toString() : "未知错误";
                MinecraftClient.getInstance().execute(() -> source.sendError(Text.literal("获取用户信息失败: " + errorMsg)));
            }
        }, MinecraftClient.getInstance());
    }
    private static ScheduledFuture<?> wxPollingTask;

    private static void startWxLogin(FabricClientCommandSource source) {
        source.sendFeedback(Text.literal("§e正在生成微信二维码..."));
        new Thread(() -> {
            try {
                JsonObject qrData = WxApi.generateWxQrCode().get(); // 阻塞等待
                if (qrData == null || !qrData.has("uuid") || !qrData.has("qrcode_image")) {
                    MinecraftClient.getInstance().execute(() -> source.sendError(Text.literal("§c生成二维码失败")));
                    return;
                }
                String uuid = qrData.get("uuid").getAsString();
                String base64 = qrData.get("qrcode_image").getAsString();

                MinecraftClient.getInstance().execute(() -> {
                    MinecraftClient.getInstance().setScreen(new QrCodeScreen(base64, "微信扫码登录"));
                });

                // 轮询直到成功或失败
                while (true) {
                    Thread.sleep(2000);
                    JsonObject status = WxApi.checkWxQrStatus(uuid).get(); // 同步等待
                    if (status == null) {
                        System.out.println("[WxPoll] 轮询返回 null");
                        continue;
                    }
                    int errcode = status.has("wx_errcode") ? status.get("wx_errcode").getAsInt() : -1;
                    System.out.println("[WxPoll] 状态码: " + errcode);
                    if (errcode == 405) {
                        String wxCode = status.has("wx_code") ? status.get("wx_code").getAsString() : "";
                        System.out.println("[WxPoll] 提取 wx_code: " + wxCode);
                        if (!wxCode.isEmpty()) {
                            Map<String, String> cookie = new HashMap<>();
                            cookie.put("dfid", KugouApiClient.getDfid());
                            cookie.put("mid", KugouApiClient.getMid());
                            cookie.put("KUGOU_API_GUID", KugouConfig.getInstance().guid);
                            cookie.put("KUGOU_API_MAC", KugouConfig.getInstance().mac);
                            cookie.put("KUGOU_API_DEV", KugouConfig.getInstance().devId);
                            KugouApiClient.ApiResponse loginResp = LoginApi.loginByOpenPlat(wxCode, cookie).get();
                            if (loginResp.status == 200) {
                                MinecraftClient.getInstance().execute(() -> {
                                    MinecraftClient.getInstance().setScreen(null);
                                    source.sendFeedback(Text.literal("§a微信登录成功！"));
                                });
                            } else {
                                System.out.println("[WxLogin] 酷狗登录失败: " + loginResp.body);
                            }
                            break; // 登录成功/失败后退出轮询
                        }
                    } else if (errcode == 403 || errcode == 402) {
                        MinecraftClient.getInstance().execute(() -> {
                            MinecraftClient.getInstance().setScreen(null);
                            source.sendError(Text.literal(errcode == 403 ? "§c登录被拒绝" : "§c二维码已过期"));
                        });
                        break;
                    }
                    // 408（等待扫码）或 404（已扫描）继续轮询
                }
            } catch (Exception e) {
                MinecraftClient.getInstance().execute(() -> source.sendError(Text.literal("§c微信登录异常: " + e.getMessage())));
                e.printStackTrace();
            }
        }, "WxLoginThread").start();
    }

    private static void showHelp(FabricClientCommandSource source) {
        source.sendFeedback(Text.literal("§6===== KugouMusicAPI 帮助 ====="));
        source.sendFeedback(Text.literal("§e/kugou search <关键词> - 搜索歌曲"));
        source.sendFeedback(Text.literal("§e/kugou play <hash>,<albumId>,<albumAudioId>,<quality> - 播放指定歌曲"));
        source.sendFeedback(Text.literal("§e/kugou pause / resume / stop - 暂停/继续/停止"));
        source.sendFeedback(Text.literal("§e/kugou quality <128|320|flac|...> - 设置默认音质"));
        source.sendFeedback(Text.literal("§e/kugou lyric off|single|bilingual - 歌词关闭/单语/双语"));
        source.sendFeedback(Text.literal("§e/kugou login <手机号> <验证码> - 验证码登录"));
        source.sendFeedback(Text.literal("§e/kugou qrlogin - 扫码登录"));
        source.sendFeedback(Text.literal("§e/kugou refreshlogin - 刷新登录态"));
        source.sendFeedback(Text.literal("§e/kugou vip status|day|upgrade|month - VIP管理"));
        source.sendFeedback(Text.literal("§e/kugou playlist list - 我的歌单"));
        source.sendFeedback(Text.literal("§e/kugou playlist songs <id> - 歌单歌曲"));
        source.sendFeedback(Text.literal("§e/kugou cloud list - 云盘列表"));
        source.sendFeedback(Text.literal("§e/kugou cloud play <hash>,<name>,<audio_id> - 播放云盘歌曲"));
        source.sendFeedback(Text.literal("§e/kugou user - 用户信息"));
        source.sendFeedback(Text.literal("§e/kugou quality <128|320|flac|high|viper_...> - 设置默认音质，不带参为显示当前音质"));
        source.sendFeedback(Text.literal("§e/kugou wxlogin - 微信扫码登录"));
    }

}