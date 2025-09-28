package com.wfibe.network;

import com.wfibe.crypto.*;
import java.io.Serializable;
import java.util.*;

/**
 * 网络通信相关类
 * PC2需要这些类与其他设备通信
 */

/**
 * 密钥请求（发送给KGC）
 */
class KeyRequest implements Serializable {
    private static final long serialVersionUID = 1L;

    public Set<String> attributes;
    public Map<String, Integer> policy;
    public String clientId;
    public long timestamp;

    public KeyRequest() {
        this.attributes = new HashSet<>();
        this.policy = new HashMap<>();
        this.timestamp = System.currentTimeMillis();
    }

    public KeyRequest(Set<String> attributes, Map<String, Integer> policy) {
        this.attributes = attributes;
        this.policy = policy;
        this.timestamp = System.currentTimeMillis();
    }

    public KeyRequest(Set<String> attributes, Map<String, Integer> policy, String clientId) {
        this.attributes = attributes;
        this.policy = policy;
        this.clientId = clientId;
        this.timestamp = System.currentTimeMillis();
    }
}

/**
 * 密钥响应（从KGC接收）
 */
class KeyResponse implements Serializable {
    private static final long serialVersionUID = 1L;

    public long requestId;
    public boolean success;
    public WFIBESystem.SecretKey secretKey;
    public long keyGenTime;
    public int keySize;
    public String errorMessage;
    public long timestamp;

    public KeyResponse() {
        this.timestamp = System.currentTimeMillis();
    }
}

/**
 * 密文传输包（发送给接收方）
 */
class CiphertextPacket implements Serializable {
    private static final long serialVersionUID = 1L;

    public int sequenceNumber;
    public EncryptionSystem.Ciphertext ciphertext;
    public String senderId;
    public long timestamp;

    public CiphertextPacket() {
        this.timestamp = System.currentTimeMillis();
    }
}