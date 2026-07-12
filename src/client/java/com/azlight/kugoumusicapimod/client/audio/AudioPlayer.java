package com.azlight.kugoumusicapimod.client.audio;

import javazoom.jl.player.Player;
import org.jflac.FLACDecoder;
import org.jflac.frame.Frame;
import org.jflac.util.ByteData;
import net.minecraft.client.MinecraftClient;

import javax.sound.sampled.*;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;

public class AudioPlayer {
    private static volatile boolean stopped = false;
    private static volatile boolean paused = false;
    private static String currentUrl = null;
    private static Runnable onComplete = null;
    private static Thread playThread;
    private static Player currentPlayer;
    private static SourceDataLine line;

    public static void play(String audioUrl, Runnable callback) {
        stop();
        stopped = false;
        paused = false;
        currentUrl = audioUrl;
        onComplete = callback;
        playThread = new Thread(() -> {
            try {
                URL url = new URL(audioUrl);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
                conn.setRequestProperty("Accept-Encoding", "identity");
                conn.connect();

                // 将整个音频数据读入内存
                byte[] data = new BufferedInputStream(conn.getInputStream(), 8192).readAllBytes();
                if (data.length == 0) {
                    System.err.println("[Audio] 下载的数据为空");
                    return;
                }

                // 根据文件头判断格式
                if (data.length >= 4 && data[0] == 'f' && data[1] == 'L' && data[2] == 'a' && data[3] == 'C') {
                    // FLAC 格式
                    playFLAC(data);
                } else {
                    // 默认作为 MP3 处理
                    playMP3(data);
                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                if (!stopped && onComplete != null) {
                    MinecraftClient.getInstance().execute(onComplete);
                }
                currentUrl = null;
            }
        });
        playThread.setDaemon(true);
        playThread.start();
    }

    private static void playMP3(byte[] data) throws Exception {
        // 跳过 ID3v2 标签（如果存在）
        ByteArrayInputStream byteInput = new ByteArrayInputStream(data);
        byteInput.mark(3);
        byte[] id3Header = new byte[3];
        byteInput.read(id3Header);
        if (id3Header[0] == 'I' && id3Header[1] == 'D' && id3Header[2] == '3') {
            byteInput.reset();
            byteInput.skip(6); // 跳过 ID3 头和大小字段
            int size = ((byteInput.read() & 0x7f) << 21) | ((byteInput.read() & 0x7f) << 14) | ((byteInput.read() & 0x7f) << 7) | (byteInput.read() & 0x7f);
            byteInput.skip(size);
        } else {
            byteInput.reset();
        }
        currentPlayer = new Player(byteInput);
        currentPlayer.play();
    }

    private static void playFLAC(byte[] data) throws Exception {
        ByteArrayInputStream byteInput = new ByteArrayInputStream(data);
        FLACDecoder decoder = new FLACDecoder(byteInput);
        decoder.readMetadata(); // 必须先读取元数据才能获取 StreamInfo
        AudioFormat format = new AudioFormat(
                decoder.getStreamInfo().getSampleRate(),
                16,
                decoder.getStreamInfo().getChannels(),
                true,
                false
        );
        DataLine.Info info = new DataLine.Info(SourceDataLine.class, format);
        line = (SourceDataLine) AudioSystem.getLine(info);
        line.open(format);
        line.start();

        Frame frame;
        ByteData byteData;
        while (!stopped && (frame = decoder.readNextFrame()) != null) {
            byteData = decoder.decodeFrame(frame, null);
            line.write(byteData.getData(), 0, byteData.getLen());
        }
        line.drain();
        line.stop();
        line.close();
    }

    public static void pause() {
        paused = true;
        if (currentPlayer != null) {
            currentPlayer.close();
            currentPlayer = null;
        }
        if (line != null) {
            line.stop();
        }
        if (playThread != null && playThread.isAlive()) {
            playThread.interrupt();
        }
    }

    public static void resume() {
        if (currentUrl != null && paused) {
            paused = false;
            play(currentUrl, onComplete);
        }
    }

    public static void stop() {
        stopped = true;
        paused = false;
        if (currentPlayer != null) {
            currentPlayer.close();
            currentPlayer = null;
        }
        if (line != null) {
            line.stop();
            line.close();
            line = null;
        }
        if (playThread != null && playThread.isAlive()) {
            playThread.interrupt();
            playThread = null;
        }
        currentUrl = null;
        onComplete = null;
    }

    public static boolean isPaused() { return paused; }
    public static boolean isPlaying() { return currentUrl != null && !stopped; }
}