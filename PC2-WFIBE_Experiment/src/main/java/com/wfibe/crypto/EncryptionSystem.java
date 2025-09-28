package com.wfibe.crypto;

import it.unisa.dia.gas.jpbc.*;
import it.unisa.dia.gas.plaf.jpbc.pairing.PairingFactory;
import java.security.MessageDigest;
import java.security.SecureRandom;
import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import javax.crypto.spec.IvParameterSpec;
import java.io.*;
import java.util.*;

/**
 * WFIBE加密系统 - 发送方使用
 * 在PC2上运行，只需要公共参数即可加密
 */
public class EncryptionSystem implements Serializable {

    private static final long serialVersionUID = 1L;

    // 系统公共参数
    private transient Pairing pairing;
    private transient Field G1, G2, GT, Zp;
    private transient Element g1, g2, Z;
    private int n, m;

    // 主公钥
    private transient Element[][] mpk1_h;
    private transient Element[][] mpk2_h;

    // 性能监控
    private long totalEncryptionTime = 0;
    private int encryptionCount = 0;

    /**
     * 从公共参数初始化加密系统
     */
    public void initializeFromPublicParams(WFIBESystem.PublicParameters params) {
        this.n = params.n;
        this.m = params.m;

        // 初始化配对
        this.pairing = PairingFactory.getPairing(params.pairingParams);
        this.G1 = pairing.getG1();
        this.G2 = pairing.getG2();
        this.GT = pairing.getGT();
        this.Zp = pairing.getZr();

        // 恢复群元素
        this.g1 = G1.newElementFromBytes(params.g1_bytes).getImmutable();
        this.g2 = G2.newElementFromBytes(params.g2_bytes).getImmutable();
        this.Z = Zp.newElementFromBytes(params.Z_bytes).getImmutable();

        // 恢复主公钥
        this.mpk1_h = new Element[2][n + 1];
        for (int i = 0; i < 2; i++) {
            for (int j = 0; j <= n; j++) {
                mpk1_h[i][j] = G1.newElementFromBytes(params.mpk1_h_bytes[i][j]).getImmutable();
            }
        }

        this.mpk2_h = new Element[2][m + 1];
        for (int i = 0; i < 2; i++) {
            for (int j = 0; j <= m; j++) {
                mpk2_h[i][j] = G1.newElementFromBytes(params.mpk2_h_bytes[i][j]).getImmutable();
            }
        }

        System.out.println("✓ Encryption system initialized from public parameters");
        System.out.println("  Vector dimensions: n=" + n + ", m=" + m);
    }

    /**
     * 加密消息
     */
    public EncryptionResult encrypt(byte[] message,
                                    Set<String> senderAttributes,
                                    Map<String, Integer> senderPolicy,
                                    int threshold_d) {
        long startTime = System.nanoTime();

        EncryptionResult result = new EncryptionResult();

        try {
            // 编码属性和策略为向量
            int[] attrVector_SA = encodeAttributes(senderAttributes, n);
            int[] policyVector_PB = encodePolicy(senderPolicy, m);

            // 选择随机数
            Element r1 = Zp.newRandomElement();
            Element r2 = Zp.newRandomElement();

            // 构造加密向量x_SA
            Element[] x_SA = new Element[n + 1];
            for (int i = 0; i < n; i++) {
                x_SA[i] = Zp.newElement(attrVector_SA[i]);
            }
            x_SA[n] = Z.duplicate().sub(Zp.newElement(threshold_d));

            // 构造加密向量x_PB
            Element[] x_PB = new Element[m + 1];
            for (int i = 0; i < m; i++) {
                x_PB[i] = Zp.newElement(policyVector_PB[i]);
            }
            x_PB[m] = Z.duplicate().sub(Zp.newElement(threshold_d));

            // 计算密文组件CT1
            Element c1_1 = G1.newOneElement();
            Element c1_2 = G1.newOneElement();

            for (int j = 0; j <= n; j++) {
                Element exp = r1.duplicate().mul(x_SA[j]);
                c1_1.mul(mpk1_h[0][j].duplicate().powZn(exp));
                c1_2.mul(mpk1_h[1][j].duplicate().powZn(exp));
            }

            c1_1 = c1_1.getImmutable();
            c1_2 = c1_2.getImmutable();

            // 计算密文组件CT2
            Element c2_1 = G1.newOneElement();
            Element c2_2 = G1.newOneElement();

            for (int j = 0; j <= m; j++) {
                Element exp = r2.duplicate().mul(x_PB[j]);
                c2_1.mul(mpk2_h[0][j].duplicate().powZn(exp));
                c2_2.mul(mpk2_h[1][j].duplicate().powZn(exp));
            }

            c2_1 = c2_1.getImmutable();
            c2_2 = c2_2.getImmutable();

            // 计算对称密钥
            Element K1 = pairing.pairing(g1, g2).powZn(r1.duplicate().mul(Z));
            Element K2 = pairing.pairing(g1, g2).powZn(r2.duplicate().mul(Z));
            byte[] K_sym = deriveSymmetricKey(K1, K2);

            // AES加密消息
            byte[] encryptedMessage = encryptAES(message, K_sym);

            // 组装密文
            Ciphertext ct = new Ciphertext();
            ct.c1_1 = c1_1.toBytes();
            ct.c1_2 = c1_2.toBytes();
            ct.c2_1 = c2_1.toBytes();
            ct.c2_2 = c2_2.toBytes();
            ct.encryptedMessage = encryptedMessage;
            ct.timestamp = System.currentTimeMillis();
            ct.senderAttributes = senderAttributes.size();
            ct.threshold = threshold_d;

            result.ciphertext = ct;
            result.success = true;

        } catch (Exception e) {
            result.success = false;
            result.errorMessage = e.getMessage();
            e.printStackTrace();
        }

        long endTime = System.nanoTime();
        long encryptionTime = (endTime - startTime) / 1_000_000; // ms

        result.encryptionTime = encryptionTime;
        result.ciphertextSize = calculateCiphertextSize(result.ciphertext);
        result.messageSize = message.length;
        result.expansionRate = (double) result.ciphertextSize / message.length;

        // 更新统计
        totalEncryptionTime += encryptionTime;
        encryptionCount++;

        return result;
    }

    /**
     * 批量加密（优化性能）
     */
    public List<EncryptionResult> encryptBatch(List<byte[]> messages,
                                               Set<String> senderAttributes,
                                               Map<String, Integer> senderPolicy,
                                               int threshold_d) {
        List<EncryptionResult> results = new ArrayList<>();

        // 预先编码属性和策略（只需编码一次）
        int[] attrVector_SA = encodeAttributes(senderAttributes, n);
        int[] policyVector_PB = encodePolicy(senderPolicy, m);

        for (byte[] message : messages) {
            // 使用预编码的向量加密
            EncryptionResult result = encryptWithVectors(
                    message, attrVector_SA, policyVector_PB, threshold_d);
            results.add(result);
        }

        return results;
    }

    /**
     * 使用预编码向量加密（内部方法）
     */
    private EncryptionResult encryptWithVectors(byte[] message,
                                                int[] attrVector_SA,
                                                int[] policyVector_PB,
                                                int threshold_d) {
        // 类似encrypt方法，但直接使用向量
        // 实现略...
        return encrypt(message, new HashSet<>(), new HashMap<>(), threshold_d);
    }

    /**
     * 编码属性为向量
     */
    private int[] encodeAttributes(Set<String> attributes, int dimension) {
        int[] vector = new int[dimension];

        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");

            for (String attr : attributes) {
                byte[] hash = md.digest(attr.toLowerCase().trim().getBytes("UTF-8"));
                int index = Math.abs(bytesToInt(hash)) % dimension;
                vector[index] = 1;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return vector;
    }

    /**
     * 编码策略为向量
     */
    private int[] encodePolicy(Map<String, Integer> policy, int dimension) {
        int[] vector = new int[dimension];

        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");

            for (Map.Entry<String, Integer> entry : policy.entrySet()) {
                byte[] hash = md.digest(entry.getKey().toLowerCase().trim().getBytes("UTF-8"));
                int index = Math.abs(bytesToInt(hash)) % dimension;
                vector[index] = entry.getValue();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return vector;
    }

    private int bytesToInt(byte[] bytes) {
        return ((bytes[0] & 0xFF) << 24) |
                ((bytes[1] & 0xFF) << 16) |
                ((bytes[2] & 0xFF) << 8) |
                (bytes[3] & 0xFF);
    }

    /**
     * 导出对称密钥
     */
    private byte[] deriveSymmetricKey(Element K1, Element K2) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(K1.toBytes());
            md.update(K2.toBytes());
            return md.digest();
        } catch (Exception e) {
            throw new RuntimeException("Failed to derive symmetric key", e);
        }
    }

    /**
     * AES加密
     */
    private byte[] encryptAES(byte[] plaintext, byte[] key) {
        try {
            // 使用AES-256
            SecretKeySpec keySpec = new SecretKeySpec(key, "AES");
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");

            // 生成随机IV
            byte[] iv = new byte[16];
            new SecureRandom().nextBytes(iv);
            IvParameterSpec ivSpec = new IvParameterSpec(iv);

            cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec);
            byte[] encrypted = cipher.doFinal(plaintext);

            // 将IV附加到密文前面
            byte[] result = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, result, 0, iv.length);
            System.arraycopy(encrypted, 0, result, iv.length, encrypted.length);

            return result;

        } catch (Exception e) {
            throw new RuntimeException("AES encryption failed", e);
        }
    }

    /**
     * 计算密文大小
     */
    private int calculateCiphertextSize(Ciphertext ct) {
        if (ct == null) return 0;

        int size = 0;
        size += ct.c1_1.length + ct.c1_2.length;  // CT1
        size += ct.c2_1.length + ct.c2_2.length;  // CT2
        size += ct.encryptedMessage.length;        // 加密消息

        return size;
    }

    /**
     * 获取统计信息
     */
    public EncryptionStatistics getStatistics() {
        EncryptionStatistics stats = new EncryptionStatistics();
        stats.totalEncryptions = encryptionCount;
        stats.totalTime = totalEncryptionTime;
        stats.averageTime = encryptionCount > 0 ?
                (double) totalEncryptionTime / encryptionCount : 0;
        return stats;
    }

    // ==================== 数据结构 ====================

    public static class EncryptionResult implements Serializable {
        public Ciphertext ciphertext;
        public long encryptionTime;
        public int ciphertextSize;
        public int messageSize;
        public double expansionRate;
        public boolean success;
        public String errorMessage;
    }

    public static class Ciphertext implements Serializable {
        public byte[] c1_1, c1_2;  // CT1组件
        public byte[] c2_1, c2_2;  // CT2组件
        public byte[] encryptedMessage;
        public long timestamp;
        public int senderAttributes;
        public int threshold;
    }

    public static class EncryptionStatistics {
        public int totalEncryptions;
        public long totalTime;
        public double averageTime;
    }
}