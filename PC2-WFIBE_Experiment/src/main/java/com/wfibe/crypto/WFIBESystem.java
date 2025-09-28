package com.wfibe.crypto;

import java.io.Serializable;

/**
 * WFIBE系统共享数据结构
 * PC2需要这些类定义来处理公共参数和密钥请求
 */
public class WFIBESystem {

    /**
     * 公共参数（从PC1接收）
     */
    public static class PublicParameters implements Serializable {
        private static final long serialVersionUID = 1L;

        public int n;                    // 向量维度n
        public int m;                    // 向量维度m
        public byte[] g1_bytes;          // 生成元g1
        public byte[] g2_bytes;          // 生成元g2
        public byte[] Z_bytes;           // 公开常量Z
        public byte[][][] mpk1_h_bytes; // 主公钥mpk1
        public byte[][][] mpk2_h_bytes; // 主公钥mpk2
        public String pairingParams;     // 配对参数
    }

    /**
     * 密钥结构（用于密钥请求和响应）
     */
    public static class SecretKey implements Serializable {
        private static final long serialVersionUID = 1L;

        public byte[] sk_PA_1;  // 策略PA的密钥组件1
        public byte[] sk_PA_2;  // 策略PA的密钥组件2
        public byte[] sk_SB_1;  // 属性SB的密钥组件1
        public byte[] sk_SB_2;  // 属性SB的密钥组件2
    }

    /**
     * 系统设置结果（用于日志记录）
     */
    public static class SystemSetupResult implements Serializable {
        private static final long serialVersionUID = 1L;

        public long setupTime;
        public int publicKeySize;
        public int masterKeySize;
        public boolean success;
        public String errorMessage;
    }

    /**
     * 密钥生成结果（用于日志记录）
     */
    public static class KeyGenResult implements Serializable {
        private static final long serialVersionUID = 1L;

        public SecretKey secretKey;
        public long keyGenTime;
        public int keySize;
        public boolean success;
        public String errorMessage;
    }
}