package com.wfibe.crypto;

import it.unisa.dia.gas.jpbc.*;
import it.unisa.dia.gas.jpbc.Element;
import it.unisa.dia.gas.plaf.jpbc.pairing.PairingFactory;
import it.unisa.dia.gas.plaf.jpbc.pairing.a.TypeAPairing;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.io.*;
import java.util.*;
import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import javax.crypto.spec.IvParameterSpec;

/**
 * WFIBE核心加密系统实现
 * 在PC1（KGC服务器）上运行
 */
public class WFIBESystem implements Serializable {

    private static final long serialVersionUID = 1L;

    // 系统参数
    private transient Pairing pairing;
    private transient Field G1, G2, GT, Zp;
    private transient Element g1, g2;
    private int n, m;
    private transient Element Z;

    // 主密钥
    private transient Element[][] B1;
    private transient Element[][] B2;

    // 主公钥
    private transient Element[][] mpk1_h;
    private transient Element[][] mpk2_h;

    // 性能监控
    private long lastSetupTime;
    private long lastKeyGenTime;

    /**
     * 系统初始化（Setup阶段）
     */
    public SystemSetupResult setup(int vectorDim_n, int vectorDim_m, String pairingParams) {
        long startTime = System.nanoTime();

        this.n = vectorDim_n;
        this.m = vectorDim_m;

        // 初始化配对
        this.pairing = PairingFactory.getPairing(pairingParams);
        this.G1 = pairing.getG1();
        this.G2 = pairing.getG2();
        this.GT = pairing.getGT();
        this.Zp = pairing.getZr();

        // 选择生成元
        this.g1 = G1.newRandomElement().getImmutable();
        this.g2 = G2.newRandomElement().getImmutable();

        // 选择公开常量Z
        this.Z = Zp.newRandomElement().getImmutable();

        // 生成正交矩阵B1和B2
        this.B1 = generateOrthogonalMatrix(2, n + 1);
        this.B2 = generateOrthogonalMatrix(2, m + 1);

        // 计算主公钥
        this.mpk1_h = new Element[2][n + 1];
        for (int i = 0; i < 2; i++) {
            for (int j = 0; j <= n; j++) {
                mpk1_h[i][j] = g1.duplicate().powZn(B1[i][j]).getImmutable();
            }
        }

        this.mpk2_h = new Element[2][m + 1];
        for (int i = 0; i < 2; i++) {
            for (int j = 0; j <= m; j++) {
                mpk2_h[i][j] = g1.duplicate().powZn(B2[i][j]).getImmutable();
            }
        }

        long endTime = System.nanoTime();
        lastSetupTime = (endTime - startTime) / 1_000_000;

        SystemSetupResult result = new SystemSetupResult();
        result.setupTime = lastSetupTime;
        result.publicKeySize = calculatePublicKeySize();
        result.masterKeySize = calculateMasterKeySize();
        result.success = true;

        return result;
    }

    /**
     * 密钥生成（KeyGen阶段）
     */
    public KeyGenResult keyGen(int[] attributeVector_SB, int[] policyVector_PA) {
        long startTime = System.nanoTime();

        KeyGenResult result = new KeyGenResult();

        try {
            // 构造y_PA向量
            Element[] y_PA = new Element[n + 1];
            for (int i = 0; i < n; i++) {
                y_PA[i] = Zp.newElement(policyVector_PA[i]);
            }
            y_PA[n] = Zp.newOneElement();

            // 计算sk_PA
            Element k1_PA = Zp.newZeroElement();
            Element k2_PA = Zp.newZeroElement();

            for (int j = 0; j <= n; j++) {
                k1_PA.add(B1[0][j].duplicate().mul(y_PA[j]));
                k2_PA.add(B1[1][j].duplicate().mul(y_PA[j]));
            }

            Element sk_PA_1 = g2.duplicate().powZn(k1_PA).getImmutable();
            Element sk_PA_2 = g2.duplicate().powZn(k2_PA).getImmutable();

            // 构造y_SB向量
            Element[] y_SB = new Element[m + 1];
            for (int i = 0; i < m; i++) {
                y_SB[i] = Zp.newElement(attributeVector_SB[i]);
            }
            y_SB[m] = Zp.newOneElement();

            // 计算sk_SB
            Element k1_SB = Zp.newZeroElement();
            Element k2_SB = Zp.newZeroElement();

            for (int j = 0; j <= m; j++) {
                k1_SB.add(B2[0][j].duplicate().mul(y_SB[j]));
                k2_SB.add(B2[1][j].duplicate().mul(y_SB[j]));
            }

            Element sk_SB_1 = g2.duplicate().powZn(k1_SB).getImmutable();
            Element sk_SB_2 = g2.duplicate().powZn(k2_SB).getImmutable();

            // 组装密钥
            SecretKey sk = new SecretKey();
            sk.sk_PA_1 = sk_PA_1.toBytes();
            sk.sk_PA_2 = sk_PA_2.toBytes();
            sk.sk_SB_1 = sk_SB_1.toBytes();
            sk.sk_SB_2 = sk_SB_2.toBytes();

            result.secretKey = sk;
            result.success = true;

        } catch (Exception e) {
            result.success = false;
            result.errorMessage = e.getMessage();
        }

        long endTime = System.nanoTime();
        lastKeyGenTime = (endTime - startTime) / 1_000_000;

        result.keyGenTime = lastKeyGenTime;
        result.keySize = 4 * G2.getLengthInBytes();

        return result;
    }

    /**
     * 生成正交矩阵
     */
    private Element[][] generateOrthogonalMatrix(int rows, int cols) {
        Element[][] matrix = new Element[rows][cols];
        SecureRandom random = new SecureRandom();

        // 初始化随机矩阵
        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < cols; j++) {
                matrix[i][j] = Zp.newRandomElement();
            }
        }

        // Gram-Schmidt正交化
        for (int i = 0; i < rows; i++) {
            for (int k = 0; k < i; k++) {
                Element dotProduct = Zp.newZeroElement();
                Element norm = Zp.newZeroElement();

                // 计算内积和范数
                for (int j = 0; j < cols; j++) {
                    dotProduct.add(matrix[i][j].duplicate().mul(matrix[k][j]));
                    norm.add(matrix[k][j].duplicate().mul(matrix[k][j]));
                }

                // 投影系数
                Element factor = dotProduct.div(norm);

                // 减去投影
                for (int j = 0; j < cols; j++) {
                    matrix[i][j].sub(matrix[k][j].duplicate().mul(factor));
                }
            }

            // 归一化
            Element norm = Zp.newZeroElement();
            for (int j = 0; j < cols; j++) {
                norm.add(matrix[i][j].duplicate().mul(matrix[i][j]));
            }

            // 简化处理：不做开方，保持在Zp域内
            for (int j = 0; j < cols; j++) {
                matrix[i][j] = matrix[i][j].getImmutable();
            }
        }

        return matrix;
    }

    /**
     * 编码属性集为向量
     */
    public int[] encodeAttributes(Set<String> attributes, int dimension) {
        int[] vector = new int[dimension];

        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");

            for (String attr : attributes) {
                byte[] hash = md.digest(attr.getBytes("UTF-8"));
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
    public int[] encodePolicy(Map<String, Integer> policy, int dimension) {
        int[] vector = new int[dimension];

        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");

            for (Map.Entry<String, Integer> entry : policy.entrySet()) {
                byte[] hash = md.digest(entry.getKey().getBytes("UTF-8"));
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
     * 获取公共参数
     */
    public PublicParameters getPublicParameters() {
        PublicParameters params = new PublicParameters();
        params.n = this.n;
        params.m = this.m;
        params.g1_bytes = this.g1.toBytes();
        params.g2_bytes = this.g2.toBytes();
        params.Z_bytes = this.Z.toBytes();

        // 序列化主公钥
        params.mpk1_h_bytes = new byte[2][n + 1][];
        for (int i = 0; i < 2; i++) {
            for (int j = 0; j <= n; j++) {
                params.mpk1_h_bytes[i][j] = mpk1_h[i][j].toBytes();
            }
        }

        params.mpk2_h_bytes = new byte[2][m + 1][];
        for (int i = 0; i < 2; i++) {
            for (int j = 0; j <= m; j++) {
                params.mpk2_h_bytes[i][j] = mpk2_h[i][j].toBytes();
            }
        }

        params.pairingParams = pairing.toString();

        return params;
    }

    private int calculatePublicKeySize() {
        return 2 * (n + 1 + m + 1) * G1.getLengthInBytes();
    }

    private int calculateMasterKeySize() {
        return 2 * (n + 1 + m + 1) * Zp.getLengthInBytes();
    }

    public int getVectorDim_n() {
        return n;
    }

    public int getVectorDim_m() {
        return m;
    }

    // ==================== 数据结构定义 ====================

    public static class SystemSetupResult implements Serializable {
        public long setupTime;
        public int publicKeySize;
        public int masterKeySize;
        public boolean success;
        public String errorMessage;
    }

    public static class KeyGenResult implements Serializable {
        public SecretKey secretKey;
        public long keyGenTime;
        public int keySize;
        public boolean success;
        public String errorMessage;
    }

    public static class SecretKey implements Serializable {
        public byte[] sk_PA_1;
        public byte[] sk_PA_2;
        public byte[] sk_SB_1;
        public byte[] sk_SB_2;
    }

    public static class PublicParameters implements Serializable {
        public int n;
        public int m;
        public byte[] g1_bytes;
        public byte[] g2_bytes;
        public byte[] Z_bytes;
        public byte[][][] mpk1_h_bytes;
        public byte[][][] mpk2_h_bytes;
        public String pairingParams;
    }
}