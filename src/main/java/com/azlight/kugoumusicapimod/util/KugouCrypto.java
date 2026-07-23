package com.azlight.kugoumusicapimod.util;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.*;
import java.util.stream.Collectors;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public class KugouCrypto {
    private static final Gson gson = new GsonBuilder()
            .serializeNulls()
            .disableHtmlEscaping()
            .create();

    // ============ 内置 RSA 公钥 ============
    public static final String PUBLIC_RAS_KEY = "-----BEGIN PUBLIC KEY-----\nMIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQDIAG7QOELSYoIJvTFJhMpe1s/gbjDJX51HBNnEl5HXqTW6lQ7LC8jr9fWZTwusknp+sVGzwd40MwP6U5yDE27M/X1+UR4tvOGOqp94TJtQ1EPnWGWXngpeIW5GxoQGao1rmYWAu6oi1z9XkChrsUdC6DJE5E221wf/4WLFxwAtRQIDAQAB\n-----END PUBLIC KEY-----";
    public static final String PUBLIC_LITE_RAS_KEY = "-----BEGIN PUBLIC KEY-----\nMIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQDECi0Np2UR87scwrvTr72L6oO01rBbbBPriSDFPxr3Z5syug0O24QyQO8bg27+0+4kBzTBTBOZ/WWU0WryL1JSXRTXLgFVxtzIY41Pe7lPOgsfTCn5kZcvKhYKJesKnnJDNr5/abvTGf+rHG3YRwsCHcQ08/q6ifSioBszvb3QiwIDAQAB\n-----END PUBLIC KEY-----";
    // ========== HTTP 相关 ==========
    private static boolean isLite = true;
    public static final int APPID = 1005;
    public static final int LITE_APPID = 3116;
    public static final int CLIENTVER = 20489;
    public static final int LITE_CLIENTVER = 11440;

    // ========== 工具函数 ==========

    /**
     * 生成指定长度的随机小写字母字符串
     */
    public static String randomString(int length) {
        String chars = "abcdefghijklmnopqrstuvwxyz";
        SecureRandom random = new SecureRandom();
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }
        return sb.toString();
    }

    /**
     * 将字节数组转为十六进制字符串
     */
    public static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    /**
     * 十六进制字符串转字节数组
     */
    public static byte[] hexToBytes(String hex) {
        int len = hex.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                    + Character.digit(hex.charAt(i + 1), 16));
        }
        return data;
    }

    /**
     * UTF-8 编码
     */
    public static byte[] encodeUtf8(String str) {
        return str.getBytes(StandardCharsets.UTF_8);
    }

    /**
     * UTF-8 解码
     */
    public static String decodeUtf8(byte[] bytes) {
        return new String(bytes, StandardCharsets.UTF_8);
    }

    /**
     * 将对象转为 JSON 字符串
     */
    public static String toJsonString(Object obj) {
        if (obj instanceof String) return (String) obj;
        return gson.toJson(obj);
    }

    /**
     * 将多种输入统一为字节数组
     */
    public static byte[] normalizeBuffer(Object data) {
        if (data instanceof byte[]) {
            return (byte[]) data;
        }
        String str = (data instanceof String) ? (String) data : toJsonString(data);
        return encodeUtf8(str);
    }

    // ========== 底层加密原语 ==========

    /**
     * MD5
     */
    public static String cryptoMd5(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * SHA1
     */
    public static String cryptoSha1(Object data) {
        String input = (data instanceof String) ? (String) data : toJsonString(data);
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(digest);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // ========== AES 加密/解密 ==========

    /**
     * AES 加密 (CBC, PKCS5Padding)
     */
    public static Object cryptoAesEncrypt(Object data, String keyHex, String ivHex) {
        byte[] plainBytes = normalizeBuffer(data);
        String actualKey;
        String actualIv;
        String tempKey = null;

        if (keyHex != null && ivHex != null) {
            actualKey = keyHex;
            actualIv = ivHex;
        } else {
            tempKey = randomString(16).toLowerCase();
            actualKey = cryptoMd5(tempKey).substring(0, 32);
            actualIv = cryptoMd5(tempKey).substring(16, 32);
        }

        try {
            byte[] keyBytes = actualKey.getBytes(StandardCharsets.UTF_8);
            byte[] ivBytes = actualIv.getBytes(StandardCharsets.UTF_8);
            SecretKeySpec keySpec = new SecretKeySpec(keyBytes, "AES");
            IvParameterSpec ivSpec = new IvParameterSpec(ivBytes);
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec);
            byte[] encrypted = cipher.doFinal(plainBytes);
            String hex = bytesToHex(encrypted);
            if (keyHex != null && ivHex != null) {
                return hex;
            } else {
                return "{\"str\":\"" + hex + "\",\"key\":\"" + tempKey + "\"}";
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * AES 解密
     */
    public static String cryptoAesDecrypt(String data, String originalKey, String iv) {
        // 处理 key 和 iv：Node 将 md5 后的 hex 字符串直接作为普通字符串使用
        String keyHex;
        if (iv != null) {
            keyHex = originalKey; // 外部已提供完整 hex key
        } else {
            keyHex = cryptoMd5(originalKey).substring(0, 32);
        }
        String ivHex = (iv != null) ? iv : keyHex.substring(16, 32);
        try {
            byte[] keyBytes = keyHex.getBytes(StandardCharsets.UTF_8);  // 直接 UTF-8 字节
            byte[] ivBytes = ivHex.getBytes(StandardCharsets.UTF_8);   // 直接 UTF-8 字节
            byte[] cipherBytes = hexToBytes(data);   // 密文仍然是 hex 字符串
            SecretKeySpec keySpec = new SecretKeySpec(keyBytes, "AES");
            IvParameterSpec ivSpec = new IvParameterSpec(ivBytes);
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec);
            byte[] decrypted = cipher.doFinal(cipherBytes);
            String text = decodeUtf8(decrypted);
            try {
                return text;  // 如果 JSON 解析成功直接返回字符串，上层自行判断
            } catch (Exception e) {
                return text;
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // ========== RSA 相关 ==========

    /**
     * 从 PEM 字符串中提取公钥对象
     */
    private static PublicKey getPublicKeyFromPem(String pem) {
        try {
            String publicKeyPEM = pem
                    .replace("-----BEGIN PUBLIC KEY-----", "")
                    .replace("-----END PUBLIC KEY-----", "")
                    .replaceAll("\\s", "");
            byte[] encoded = Base64.getDecoder().decode(publicKeyPEM);
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            X509EncodedKeySpec keySpec = new X509EncodedKeySpec(encoded);
            return keyFactory.generatePublic(keySpec);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * RSA 原始加密（BigInteger modPow）
     */
    public static String rsaRawEncrypt(byte[] buffer, PublicKey publicKey) {
        try {
            java.security.interfaces.RSAPublicKey rsaKey = (java.security.interfaces.RSAPublicKey) publicKey;
            int keyLength = rsaKey.getModulus().bitLength() / 8;
            java.math.BigInteger message = new java.math.BigInteger(1, buffer);
            java.math.BigInteger encrypted = message.modPow(rsaKey.getPublicExponent(), rsaKey.getModulus());
            String hex = encrypted.toString(16);
            if (hex.length() < keyLength * 2) {
                StringBuilder padded = new StringBuilder();
                for (int i = 0; i < keyLength * 2 - hex.length(); i++) {
                    padded.append('0');
                }
                padded.append(hex);
                hex = padded.toString();
            }
            return hex;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * RSA 加密（默认使用主公钥）
     */
    public static String cryptoRSAEncrypt(Object data, String publicKeyPem) {
        boolean useLite = isLite;
        byte[] buffer = normalizeBuffer(data);
        String pem = publicKeyPem != null ? publicKeyPem : (useLite ? PUBLIC_LITE_RAS_KEY : PUBLIC_RAS_KEY);
        PublicKey publicKey = getPublicKeyFromPem(pem);
        int keyLength = ((java.security.interfaces.RSAPublicKey) publicKey).getModulus().bitLength() / 8;
        if (buffer.length > keyLength) {
            throw new IllegalArgumentException("Data length exceeds key size");
        }
        byte[] padded = new byte[keyLength];
        System.arraycopy(buffer, 0, padded, 0, buffer.length);
        return rsaRawEncrypt(padded, publicKey);
    }

    /**
     * RSA PKCS#1 v1.5 加密（对应 node-forge 的 RSAES-PKCS1-V1_5）
     */
    public static String rsaEncrypt2(Object data) {
        byte[] buffer = normalizeBuffer(data);
        String inputJson = new String(buffer, StandardCharsets.UTF_8);
        String pem = isLite ? PUBLIC_LITE_RAS_KEY : PUBLIC_RAS_KEY;
        try {
            PublicKey publicKey = getPublicKeyFromPem(pem);
            Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
            cipher.init(Cipher.ENCRYPT_MODE, publicKey);
            byte[] encrypted = cipher.doFinal(buffer);
            String hex = bytesToHex(encrypted);
            return hex;
        } catch (Exception e) {
            throw new RuntimeException("RSA encrypt2 failed", e);
        }
    }

    // ========== 歌单专用 AES ==========

    public static String playlistAesEncrypt(Object data) {
        String useData = (data instanceof String) ? (String) data : toJsonString(data);
        String key = randomString(6).toLowerCase();
        String encryptKey = cryptoMd5(key).substring(0, 16);
        String iv = cryptoMd5(key).substring(16, 32);
        try {
            byte[] keyBytes = encryptKey.getBytes(StandardCharsets.UTF_8);
            byte[] ivBytes = iv.getBytes(StandardCharsets.UTF_8);
            byte[] plain = encodeUtf8(useData);
            SecretKeySpec keySpec = new SecretKeySpec(keyBytes, "AES");
            IvParameterSpec ivSpec = new IvParameterSpec(ivBytes);
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec);
            byte[] encrypted = cipher.doFinal(plain);
            // 正确：使用 Base64 编码
            String base64 = Base64.getEncoder().encodeToString(encrypted);
            return "{\"key\":\"" + key + "\",\"str\":\"" + base64 + "\"}";
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static String playlistAesDecrypt(String originalKey, String base64Str) {
        String encryptKey = cryptoMd5(originalKey).substring(0, 16);
        String iv = cryptoMd5(originalKey).substring(16, 32);
        try {
            byte[] keyBytes = encryptKey.getBytes(StandardCharsets.UTF_8);
            byte[] ivBytes = iv.getBytes(StandardCharsets.UTF_8);
            byte[] cipherBytes = Base64.getDecoder().decode(base64Str);

            SecretKeySpec keySpec = new SecretKeySpec(keyBytes, "AES");
            IvParameterSpec ivSpec = new IvParameterSpec(ivBytes);
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec);
            byte[] decrypted = cipher.doFinal(cipherBytes);
            return decodeUtf8(decrypted);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // ========== 平台设置 ==========

    public static void setLite(boolean lite) {
        isLite = lite;
    }

    // ========== 签名方法 ==========

    /**
     * Web 签名
     */
    public static String signatureWebParams(Map<String, String> params) {
        String str = "NVPh5oo715z5DIWAeQlhMDsWXXQV4hwt";
        String joined = params.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> e.getKey() + "=" + e.getValue())
                .collect(Collectors.joining());
        return cryptoMd5(str + joined + str);
    }

    /**
     * Android 签名 (lite 模式)
     */
    /**
     * Android 签名 - 完全按 Node.js 实现
     */
    public static String signatureAndroidParams(Map<String, String> params, String data) {
        // 所有签名都使用 lite 版本（概念版）
        String str = "LnT6xpN3khm36zse0QzvmgTZ3waWdRSA";
        // 使用 TreeMap 按 key 排序（与 Node 的 Object.keys(params).sort() 一致）
        TreeMap<String, String> sortedParams = new TreeMap<>(params);
        // 拼接: key=value 按排序后的顺序
        StringBuilder paramsString = new StringBuilder();
        for (Map.Entry<String, String> entry : sortedParams.entrySet()) {
            paramsString.append(entry.getKey()).append("=").append(entry.getValue());
        }
        // 构建完整签名原文: str + paramsString + data + str
        String raw = str + paramsString.toString() + (data != null ? data : "") + str;
        return cryptoMd5(raw);
    }
    /**
     * 注册签名
     */
    public static String signatureRegisterParams(Map<String, String> params) {
        String joined = params.values().stream()
                .sorted()
                .collect(Collectors.joining());
        return cryptoMd5("1014" + joined + "1014");
    }

    /**
     * sign 签名
     */
    public static String signParams(Map<String, String> params, String data) {
        String str = "R6snCXJgbCaj9WFRJKefTMIFp0ey6Gza";
        String joined = params.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> e.getKey() + e.getValue())
                .collect(Collectors.joining());
        return cryptoMd5(joined + (data != null ? data : "") + str);
    }

    /**
     * signKey (lite)
     */
    public static String signKey(String hash, String mid, String userid, String appid) {
        String str = isLite ? "185672dd44712f60bb1736df5a377e82" : "57ae12eb6890223e355ccfcb74edf70d";
        String finalAppid = (appid != null && !appid.isEmpty()) ? appid : String.valueOf(LITE_APPID);
        String finalUserid = (userid != null && !userid.isEmpty()) ? userid : "0";
        return cryptoMd5(hash + str + finalAppid + mid + finalUserid);
    }

    /**
     * 云盘 key
     */
    public static String signCloudKey(String hash, String pid) {
        String str = "ebd1ac3134c880bda6a2194537843caa0162e2e7";
        return cryptoMd5("musicclound" + hash + pid + str);
    }

    /**
     * signParamsKey (lite)
     */
    public static String signParamsKey(String data, String appid, String clientver) {
        String str = isLite ? "LnT6xpN3khm36zse0QzvmgTZ3waWdRSA" : "OIlwieks28dk2k092lksi2UIkp";
        String finalAppid = (appid != null && !appid.isEmpty()) ? appid : String.valueOf(LITE_APPID);
        String finalClientver = (clientver != null && !clientver.isEmpty()) ? clientver : String.valueOf(LITE_CLIENTVER);
        return cryptoMd5(finalAppid + str + finalClientver + data);
    }
}