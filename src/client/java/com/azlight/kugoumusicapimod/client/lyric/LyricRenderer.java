package com.azlight.kugoumusicapimod.client.lyric;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LyricRenderer {
    private static final List<Long> timestamps = new ArrayList<>();
    private static final List<String> lines = new ArrayList<>();
    private static long startTime = 0;
    private static long pauseOffset = 0;
    private static boolean paused = false;
    private static String rawLyrics = null;
    private static boolean bilingualMode = true;
    private static boolean visible = false; // 默认关闭
    public static void setVisible(boolean v) { visible = v; }
    public static boolean isVisible() { return visible; }

    public static void setLyrics(String raw) {
        rawLyrics = raw;
        timestamps.clear();
        lines.clear();
        if (raw == null || raw.isEmpty()) {
            visible = false;
            paused = false;
            return;
        }

        Pattern pattern = Pattern.compile("\\[(\\d{2}):(\\d{2})(?:[.:](\\d{2,3}))?\\]");
        String[] rawLines = raw.split("\n");

        for (int i = 0; i < rawLines.length; i++) {
            String line = rawLines[i].trim();
            Matcher m = pattern.matcher(line);
            if (m.find()) {
                int min = Integer.parseInt(m.group(1));
                int sec = Integer.parseInt(m.group(2));
                String msStr = m.group(3);
                int ms = (msStr != null) ? Integer.parseInt(msStr) : 0;
                if (msStr != null && msStr.length() == 2) ms *= 10;
                long ts = (min * 60 + sec) * 1000L + ms;

                String text = line.replaceAll("\\[.*?\\]", "").trim();
                // 检查下一行是否为翻译（无时间标签）
                if (bilingualMode && i + 1 < rawLines.length) {
                    String next = rawLines[i + 1].trim();
                    if (!pattern.matcher(next).find() && !next.isEmpty()) {
                        text = text + " ‖ " + next;
                        i++; // 跳过翻译行
                    }
                }
                timestamps.add(ts);
                lines.add(text.isEmpty() ? "..." : text);
            }
        }

        if (timestamps.isEmpty()) {
            visible = false;
            return;
        }

        startTime = System.currentTimeMillis();
        pauseOffset = 0;
        paused = false;
    }

    public static void pause() {
        if (!paused && visible) {
            pauseOffset = System.currentTimeMillis() - startTime;
            paused = true;
        }
    }

    public static void resume() {
        if (paused && visible) {
            startTime = System.currentTimeMillis() - pauseOffset;
            paused = false;
        }
    }

    public static void render(DrawContext context, RenderTickCounter tickCounter) {
        if (!visible || lines.isEmpty()) return;

        long elapsed;
        if (paused) {
            elapsed = pauseOffset;
        } else {
            elapsed = System.currentTimeMillis() - startTime;
        }

        int index = 0;
        for (int i = 0; i < timestamps.size(); i++) {
            if (timestamps.get(i) <= elapsed) {
                index = i;
            } else {
                break;
            }
        }

        MinecraftClient client = MinecraftClient.getInstance();
        int screenWidth = client.getWindow().getScaledWidth();
        int screenHeight = client.getWindow().getScaledHeight();
        int y = screenHeight / 3 * 2;

        String currentLine = lines.get(index);
        int textWidth = client.textRenderer.getWidth(currentLine);
        int x = (screenWidth - textWidth) / 2;

        context.drawTextWithShadow(client.textRenderer, Text.literal(currentLine), x, y, 0xFFFFFF);
    }

    public static void setBilingualMode(boolean mode) {
        if (bilingualMode != mode) {
            bilingualMode = mode;
            // 重新解析歌词，但保留原有开始时间以避免进度跳跃
            if (rawLyrics != null) {
                String savedRaw = rawLyrics;
                long oldStart = startTime;
                boolean oldVisible = visible;
                setLyrics(savedRaw);
                // 恢复开始时间（如果仍可见）
                if (visible && oldVisible) {
                    startTime = oldStart;
                }
            }
        }
    }
}