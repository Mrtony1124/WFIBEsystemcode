package com.wfibe.experiments;

import java.util.*;
import java.security.SecureRandom;
import java.nio.charset.StandardCharsets;

/**
 * 测试消息生成器
 * 生成不同类型和大小的测试消息
 */
public class MessageGenerator {

    private static final SecureRandom random = new SecureRandom();

    /**
     * 生成随机字节消息
     */
    public static byte[] generateRandomBytes(int size) {
        byte[] data = new byte[size];
        random.nextBytes(data);
        return data;
    }

    /**
     * 生成文本消息
     */
    public static byte[] generateTextMessage(int sizeKB) {
        StringBuilder sb = new StringBuilder();
        String[] words = {
                "Lorem", "ipsum", "dolor", "sit", "amet", "consectetur",
                "adipiscing", "elit", "sed", "do", "eiusmod", "tempor",
                "incididunt", "ut", "labore", "et", "dolore", "magna"
        };

        int targetSize = sizeKB * 1024;
        while (sb.length() < targetSize) {
            sb.append(words[random.nextInt(words.length)]).append(" ");
            if (random.nextDouble() < 0.1) {
                sb.append("\n");
            }
        }

        String text = sb.substring(0, targetSize);
        return text.getBytes(StandardCharsets.UTF_8);
    }

    /**
     * 生成JSON格式消息
     */
    public static byte[] generateJsonMessage(int records) {
        StringBuilder json = new StringBuilder();
        json.append("{\n");
        json.append("  \"timestamp\": ").append(System.currentTimeMillis()).append(",\n");
        json.append("  \"sender\": \"PC2\",\n");
        json.append("  \"records\": [\n");

        for (int i = 0; i < records; i++) {
            json.append("    {\n");
            json.append("      \"id\": ").append(i).append(",\n");
            json.append("      \"value\": ").append(random.nextDouble()).append(",\n");
            json.append("      \"data\": \"").append(generateRandomString(50)).append("\"\n");
            json.append("    }");
            if (i < records - 1) json.append(",");
            json.append("\n");
        }

        json.append("  ]\n");
        json.append("}");

        return json.toString().getBytes(StandardCharsets.UTF_8);
    }

    /**
     * 生成可压缩消息（测试压缩率）
     */
    public static byte[] generateCompressibleMessage(int sizeKB) {
        int targetSize = sizeKB * 1024;
        byte[] data = new byte[targetSize];

        // 填充重复模式（高压缩率）
        byte[] pattern = "ABCDEFGHIJ".getBytes();
        for (int i = 0; i < targetSize; i++) {
            data[i] = pattern[i % pattern.length];
        }

        // 添加一些随机性
        for (int i = 0; i < targetSize / 100; i++) {
            data[random.nextInt(targetSize)] = (byte) random.nextInt(256);
        }

        return data;
    }

    /**
     * 生成不可压缩消息（已加密或随机）
     */
    public static byte[] generateIncompressibleMessage(int sizeKB) {
        return generateRandomBytes(sizeKB * 1024);
    }

    /**
     * 生成带有特定模式的消息（用于验证）
     */
    public static byte[] generatePatternMessage(int sizeKB, String pattern) {
        int targetSize = sizeKB * 1024;
        byte[] patternBytes = pattern.getBytes(StandardCharsets.UTF_8);
        byte[] data = new byte[targetSize];

        for (int i = 0; i < targetSize; i++) {
            data[i] = patternBytes[i % patternBytes.length];
        }

        return data;
    }

    /**
     * 生成批量测试消息
     */
    public static List<byte[]> generateBatchMessages(int count, int sizeKB) {
        List<byte[]> messages = new ArrayList<>();

        for (int i = 0; i < count; i++) {
            // 混合不同类型的消息
            if (i % 3 == 0) {
                messages.add(generateTextMessage(sizeKB));
            } else if (i % 3 == 1) {
                messages.add(generateJsonMessage(sizeKB * 10));
            } else {
                messages.add(generateRandomBytes(sizeKB * 1024));
            }
        }

        return messages;
    }

    /**
     * 生成随机字符串
     */
    private static String generateRandomString(int length) {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < length; i++) {
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }

        return sb.toString();
    }

    /**
     * 计算消息熵（随机性度量）
     */
    public static double calculateEntropy(byte[] data) {
        if (data == null || data.length == 0) return 0;

        // 统计字节频率
        int[] freq = new int[256];
        for (byte b : data) {
            freq[b & 0xFF]++;
        }

        // 计算熵
        double entropy = 0;
        double len = data.length;

        for (int f : freq) {
            if (f > 0) {
                double p = f / len;
                entropy -= p * Math.log(p) / Math.log(2);
            }
        }

        return entropy;
    }

    /**
     * 验证消息完整性
     */
    public static boolean verifyMessage(byte[] original, byte[] decrypted) {
        if (original.length != decrypted.length) {
            return false;
        }

        for (int i = 0; i < original.length; i++) {
            if (original[i] != decrypted[i]) {
                return false;
            }
        }

        return true;
    }
}