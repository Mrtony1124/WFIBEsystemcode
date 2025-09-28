package com.wfibe.network;

import com.wfibe.crypto.*;
import java.net.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

/**
 * 发送方客户端 - 在PC2上运行
 * 负责加密消息并发送给接收方
 */
public class SenderClient {

    private String receiverIP;
    private int receiverPort;
    private EncryptionSystem encryptionSystem;
    private WFIBESystem.PublicParameters publicParams;

    // 性能统计
    private long totalMessages = 0;
    private long successfulSends = 0;
    private long failedSends = 0;
    private long totalTransmissionTime = 0;

    // 日志记录
    private PrintWriter performanceLog;

    public SenderClient(String receiverIP, int receiverPort) {
        this.receiverIP = receiverIP;
        this.receiverPort = receiverPort;
        this.encryptionSystem = new EncryptionSystem();
    }

    /**
     * 初始化客户端
     */
    public void initialize() throws Exception {
        System.out.println("=== Initializing Sender Client ===");

        // 读取公共参数
        File paramsFile = new File(SystemParameters.FilePaths.PUBLIC_PARAMS_FILE);
        if (!paramsFile.exists()) {
            throw new FileNotFoundException(
                    "Public parameters file not found. Please copy from KGC server.");
        }

        try (ObjectInputStream ois = new ObjectInputStream(
                new FileInputStream(paramsFile))) {
            publicParams = (WFIBESystem.PublicParameters) ois.readObject();
        }

        // 初始化加密系统
        encryptionSystem.initializeFromPublicParams(publicParams);

        // 初始化日志
        initializeLogging();

        System.out.println("✓ Sender client initialized");
        System.out.println("  Target receiver: " + receiverIP + ":" + receiverPort);
    }

    /**
     * 运行加密测试
     */
    public void runEncryptionTest(int messageSizeKB, int attributeCount,
                                  int iterations) throws Exception {
        System.out.println("\n=== Running Encryption Test ===");
        System.out.println("Configuration:");
        System.out.println("  Message size: " + messageSizeKB + " KB");
        System.out.println("  Attributes: " + attributeCount);
        System.out.println("  Iterations: " + iterations);
        System.out.println();

        // 生成测试属性和策略
        Set<String> senderAttributes = generateTestAttributes(attributeCount);
        Map<String, Integer> senderPolicy = generateTestPolicy(attributeCount / 2);
        int threshold = calculateThreshold(senderPolicy);

        List<Long> encryptionTimes = new ArrayList<>();
        List<Long> transmissionTimes = new ArrayList<>();
        List<Integer> ciphertextSizes = new ArrayList<>();

        for (int i = 0; i < iterations; i++) {
            // 生成随机消息
            byte[] message = generateRandomMessage(messageSizeKB * 1024);

            // 加密
            long encStart = System.nanoTime();
            EncryptionSystem.EncryptionResult encResult =
                    encryptionSystem.encrypt(message, senderAttributes, senderPolicy, threshold);
            long encEnd = System.nanoTime();

            if (!encResult.success) {
                System.err.println("Encryption failed: " + encResult.errorMessage);
                failedSends++;
                continue;
            }

            long encTime = (encEnd - encStart) / 1_000_000; // ms
            encryptionTimes.add(encTime);
            ciphertextSizes.add(encResult.ciphertextSize);

            // 发送密文
            long transStart = System.currentTimeMillis();
            boolean sent = sendCiphertext(encResult.ciphertext, i);
            long transEnd = System.currentTimeMillis();

            if (sent) {
                long transTime = transEnd - transStart;
                transmissionTimes.add(transTime);
                successfulSends++;
            } else {
                failedSends++;
            }

            totalMessages++;

            // 进度显示
            if ((i + 1) % 10 == 0) {
                System.out.printf("Progress: %d/%d completed\n", i + 1, iterations);
            }

            // 记录性能数据
            logPerformance(i, messageSizeKB, attributeCount, encTime,
                    encResult.ciphertextSize, encResult.expansionRate);
        }

        // 显示统计结果
        printStatistics(encryptionTimes, transmissionTimes, ciphertextSizes);
    }

    /**
     * 批量加密测试
     */
    public void runBatchEncryptionTest(int messageSizeKB, int attributeCount,
                                       int batchSize, int batches) throws Exception {
        System.out.println("\n=== Running Batch Encryption Test ===");
        System.out.println("Configuration:");
        System.out.println("  Batch size: " + batchSize);
        System.out.println("  Number of batches: " + batches);
        System.out.println();

        Set<String> senderAttributes = generateTestAttributes(attributeCount);
        Map<String, Integer> senderPolicy = generateTestPolicy(attributeCount / 2);
        int threshold = calculateThreshold(senderPolicy);

        for (int b = 0; b < batches; b++) {
            System.out.println("Processing batch " + (b + 1) + "/" + batches);

            // 生成批量消息
            List<byte[]> messages = new ArrayList<>();
            for (int i = 0; i < batchSize; i++) {
                messages.add(generateRandomMessage(messageSizeKB * 1024));
            }

            // 批量加密
            long batchStart = System.nanoTime();
            List<EncryptionSystem.EncryptionResult> results =
                    encryptionSystem.encryptBatch(messages, senderAttributes,
                            senderPolicy, threshold);
            long batchEnd = System.nanoTime();

            long batchTime = (batchEnd - batchStart) / 1_000_000;
            double avgTimePerMessage = (double) batchTime / batchSize;

            System.out.printf("  Batch encryption time: %d ms (%.2f ms/message)\n",
                    batchTime, avgTimePerMessage);

            // 批量发送
            sendBatch(results);
        }
    }

    /**
     * 发送密文到接收方
     */
    private boolean sendCiphertext(EncryptionSystem.Ciphertext ct, int sequenceNum) {
        try (Socket socket = new Socket(receiverIP, receiverPort)) {
            socket.setSoTimeout(SystemParameters.NetworkConfig.READ_TIMEOUT);

            ObjectOutputStream out = new ObjectOutputStream(
                    new BufferedOutputStream(socket.getOutputStream()));

            // 创建密文包 - 使用NetworkClasses中定义的CiphertextPacket
            CiphertextPacket packet = new CiphertextPacket();
            packet.sequenceNumber = sequenceNum;
            packet.ciphertext = ct;
            packet.senderId = "PC2_SENDER";
            packet.timestamp = System.currentTimeMillis();

            // 发送
            out.writeObject(packet);
            out.flush();

            // 等待确认（可选）
            ObjectInputStream in = new ObjectInputStream(
                    new BufferedInputStream(socket.getInputStream()));
            String ack = (String) in.readObject();

            return "ACK".equals(ack);

        } catch (Exception e) {
            System.err.println("Failed to send ciphertext: " + e.getMessage());
            return false;
        }
    }

    /**
     * 批量发送
     */
    private void sendBatch(List<EncryptionSystem.EncryptionResult> results) {
        ExecutorService executor = Executors.newFixedThreadPool(5);
        List<Future<Boolean>> futures = new ArrayList<>();

        for (int i = 0; i < results.size(); i++) {
            final int seq = i;
            final EncryptionSystem.Ciphertext ct = results.get(i).ciphertext;

            Future<Boolean> future = executor.submit(() -> sendCiphertext(ct, seq));
            futures.add(future);
        }

        // 等待所有发送完成
        int successCount = 0;
        for (Future<Boolean> future : futures) {
            try {
                if (future.get()) successCount++;
            } catch (Exception e) {
                // Ignore
            }
        }

        System.out.printf("  Batch send complete: %d/%d successful\n",
                successCount, results.size());

        executor.shutdown();
    }

    /**
     * 生成测试属性
     */
    private Set<String> generateTestAttributes(int count) {
        Set<String> attributes = new HashSet<>();
        Random random = new Random();

        String[] categories = {"dept", "role", "level", "project", "skill"};

        for (int i = 0; i < count; i++) {
            String category = categories[random.nextInt(categories.length)];
            attributes.add(category + "_" + i);
        }

        return attributes;
    }

    /**
     * 生成测试策略
     */
    private Map<String, Integer> generateTestPolicy(int count) {
        Map<String, Integer> policy = new HashMap<>();
        Random random = new Random();

        for (int i = 0; i < count; i++) {
            String attr = "recv_attr_" + i;
            int weight = 1 + random.nextInt(3); // 权重1-3
            policy.put(attr, weight);
        }

        return policy;
    }

    /**
     * 计算阈值
     */
    private int calculateThreshold(Map<String, Integer> policy) {
        int total = policy.values().stream().mapToInt(Integer::intValue).sum();
        return total / 2; // 使用总权重的一半作为阈值
    }

    /**
     * 生成随机消息
     */
    private byte[] generateRandomMessage(int size) {
        byte[] message = new byte[size];
        new Random().nextBytes(message);
        return message;
    }

    /**
     * 初始化日志
     */
    private void initializeLogging() {
        try {
            new File(SystemParameters.FilePaths.EXPERIMENT_RESULTS_DIR).mkdirs();

            performanceLog = new PrintWriter(new FileWriter(
                    SystemParameters.FilePaths.EXPERIMENT_RESULTS_DIR +
                            "sender_performance.csv", true));

            // 写入CSV头
            File logFile = new File(SystemParameters.FilePaths.EXPERIMENT_RESULTS_DIR +
                    "sender_performance.csv");
            if (logFile.length() == 0) {
                performanceLog.println("Timestamp,Iteration,MessageSize(KB),Attributes," +
                        "EncryptTime(ms),CiphertextSize(bytes),ExpansionRate");
            }

        } catch (IOException e) {
            System.err.println("Failed to initialize logging: " + e.getMessage());
        }
    }

    /**
     * 记录性能数据
     */
    private void logPerformance(int iteration, int messageSize, int attributes,
                                long encTime, int ctSize, double expansionRate) {
        if (performanceLog != null) {
            performanceLog.printf("%d,%d,%d,%d,%d,%d,%.3f\n",
                    System.currentTimeMillis(),
                    iteration,
                    messageSize,
                    attributes,
                    encTime,
                    ctSize,
                    expansionRate);
            performanceLog.flush();
        }
    }

    /**
     * 打印统计信息
     */
    private void printStatistics(List<Long> encTimes, List<Long> transTimes,
                                 List<Integer> ctSizes) {
        System.out.println("\n=== Encryption Statistics ===");

        if (!encTimes.isEmpty()) {
            double avgEncTime = encTimes.stream().mapToLong(Long::longValue)
                    .average().orElse(0);
            System.out.printf("Average encryption time: %.2f ms\n", avgEncTime);
        }

        if (!transTimes.isEmpty()) {
            double avgTransTime = transTimes.stream().mapToLong(Long::longValue)
                    .average().orElse(0);
            System.out.printf("Average transmission time: %.2f ms\n", avgTransTime);
        }

        if (!ctSizes.isEmpty()) {
            double avgCtSize = ctSizes.stream().mapToInt(Integer::intValue)
                    .average().orElse(0);
            int minSize = Collections.min(ctSizes);
            int maxSize = Collections.max(ctSizes);

            System.out.printf("Average ciphertext size: %.0f bytes\n", avgCtSize);
            System.out.printf("Size range: %d - %d bytes\n", minSize, maxSize);

            // 验证恒定性
            if (maxSize - minSize < 100) {
                System.out.println("✓ Ciphertext size is CONSTANT");
            } else {
                System.out.println("✗ Warning: Ciphertext size varies significantly");
            }
        }

        System.out.printf("\nTransmission success rate: %.2f%% (%d/%d)\n",
                (double) successfulSends / totalMessages * 100,
                successfulSends, totalMessages);
    }

    /**
     * 关闭客户端
     */
    public void shutdown() {
        if (performanceLog != null) {
            performanceLog.close();
        }

        System.out.println("\n=== Sender Client Shutdown ===");
        System.out.println("Total messages: " + totalMessages);
        System.out.println("Successful: " + successfulSends);
        System.out.println("Failed: " + failedSends);
    }
}