package com.azlight.kugoumusicapimod.client.audio;

import javazoom.jl.decoder.JavaLayerException;
import javazoom.jl.player.Player;
import net.minecraft.client.MinecraftClient;

import javax.sound.sampled.*;
import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class AudioPlayer {
    private static Player currentPlayer;
    private static Thread playThread;
    private static volatile boolean paused = false;
    private static volatile boolean stopped = false;
    private static String currentUrl = null;
    private static Runnable onComplete = null;

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
                InputStream input = conn.getInputStream();
                AudioInputStream audioIn = AudioSystem.getAudioInputStream(new BufferedInputStream(input));
                AudioFormat baseFormat = audioIn.getFormat();
                AudioFormat decodedFormat = new AudioFormat(
                        AudioFormat.Encoding.PCM_SIGNED,
                        baseFormat.getSampleRate(),
                        16,
                        baseFormat.getChannels(),
                        baseFormat.getChannels() * 2,
                        baseFormat.getSampleRate(),
                        false
                );
                AudioInputStream decodedIn = AudioSystem.getAudioInputStream(decodedFormat, audioIn);
                DataLine.Info info = new DataLine.Info(SourceDataLine.class, decodedFormat);
                try (SourceDataLine line = (SourceDataLine) AudioSystem.getLine(info)) {
                    line.open(decodedFormat);
                    line.start();
                    byte[] buffer = new byte[4096];
                    int bytesRead;
                    while (!stopped && (bytesRead = decodedIn.read(buffer, 0, buffer.length)) != -1) {
                        line.write(buffer, 0, bytesRead);
                    }
                    line.drain();
                }
            } catch (UnsupportedAudioFileException e) {
                System.err.println("[Audio] 音频格式不支持: " + e.getMessage());
                e.printStackTrace();
            } catch (Exception e) {
                System.err.println("[Audio] 播放异常: " + e.getMessage());
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

    public static void pause() {
        paused = true;
        if (currentPlayer != null) {
            currentPlayer.close();
            currentPlayer = null;
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
        if (playThread != null && playThread.isAlive()) {
            playThread.interrupt();
            playThread = null;
        }
        currentUrl = null;
        onComplete = null;
    }

    public static boolean isPaused() { return paused; }
    public static boolean isPlaying() { return currentUrl != null && !paused; }
}