package com.azlight.kugoumusicapimod.util;

import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.UUID;
import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.zip.Inflater;

public class KugouUtils {

    private static final String RANDOM_CHARS = "1234567890ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final String NUMBER_CHARS = "1234567890";

    /**
     * 生成随机字符串（大写字母+数字）
     * 字符集与 Node 版一致：1234567890ABCDEFGHIJKLMNOPQRSTUVWXYZ
     * @param len 长度，默认16
     */
    public static String randomString(int len) {
        SecureRandom random = new SecureRandom();
        StringBuilder sb = new StringBuilder(len);
        for (int i = 0; i < len; i++) {
            sb.append(RANDOM_CHARS.charAt(random.nextInt(RANDOM_CHARS.length())));
        }
        return sb.toString();
    }

    // 默认16位
    public static String randomString() {
        return randomString(16);
    }

    /**
     * 生成随机数字字符串
     * @param len 长度，默认16
     */
    public static String randomNumber(int len) {
        SecureRandom random = new SecureRandom();
        StringBuilder sb = new StringBuilder(len);
        for (int i = 0; i < len; i++) {
            sb.append(NUMBER_CHARS.charAt(random.nextInt(NUMBER_CHARS.length())));
        }
        return sb.toString();
    }

    public static String randomNumber() {
        return randomNumber(16);
    }

    /**
     * 计算设备 MID
     * 1. 对输入字符串取 MD5（32位小写hex）
     * 2. 将 hex 当作 16 进制大整数转成十进制字符串
     */
    public static String calculateMid(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(input.getBytes("UTF-8"));
            String hex = bytesToHex(digest); // 32位小写hex
            BigInteger bigInt = new BigInteger(hex, 16);
            return bigInt.toString();
        } catch (Exception e) {
            throw new RuntimeException("calculateMid failed", e);
        }
    }

    /**
     * 生成随机 GUID (UUID v4) 并计算 MID
     * 这是最常用的方式：UUID.randomUUID() 就是 v4 格式
     */
    public static String generateMid() {
        String guid = UUID.randomUUID().toString();
        return calculateMid(guid);
    }

    // 辅助：字节数组转小写十六进制字符串
    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
    /**
     * KRC 歌词解码
     * 1. 支持 Base64 编码的字符串、或者直接字节数组
     * 2. 跳过前 4 字节头部
     * 3. 使用固定 16 字节密钥进行 XOR 解密
     * 4. 使用 zlib 解压得到明文歌词
     */
    public static String decodeLyrics(String base64Encoded) {
        try {
            byte[] bytes = Base64.getDecoder().decode(base64Encoded);
            return decodeLyrics(bytes);
        } catch (Exception e) {
            return "";
        }
    }
    public static String decodeLyrics(byte[] bytes) {
        if (bytes == null || bytes.length <= 4) return "";
        // 固定 XOR 密钥（与 Node 版完全一致）
        int[] enKey = {64, 71, 97, 119, 94, 50, 116, 71, 81, 54, 49, 45, 206, 210, 110, 105};
        // 跳过前 4 字节
        byte[] krc = new byte[bytes.length - 4];
        System.arraycopy(bytes, 4, krc, 0, krc.length);
        // XOR 解密
        for (int i = 0; i < krc.length; i++) {
            krc[i] = (byte) (krc[i] ^ enKey[i % enKey.length]);
        }
        // zlib 解压
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            Inflater inflater = new Inflater();
            inflater.setInput(krc);
            byte[] buf = new byte[1024];
            while (!inflater.finished()) {
                int len = inflater.inflate(buf);
                bos.write(buf, 0, len);
            }
            inflater.end();
            return bos.toString("UTF-8");
        } catch (Exception e) {
            return "";
        }
    }
}