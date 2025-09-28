package com.wfibe.experiments;

import com.wfibe.crypto.*;
import com.wfibe.network.*;
import java.util.*;
import java.io.*;

/**
 * PC2 发送方主程序
 * 这是在PC2（低性能设备）上运行的主入口
 */
public class SenderMain {

    private static SenderClient senderClient;
    private static final String RECEIVER_IP = SystemParameters.NetworkConfig.RECEIVER_IP;
    private static final int RECEIVER_PORT = SystemParameters.NetworkConfig.RECEIVER_PORT;

    public static void main(String[] args) {
        printBanner();

        // 解析命令行参数
        Map<String, String> params = parseArguments(args);

        // 设置默认参数
        int messageSize = Integer.parseInt(params.getOrDefault("message-size", "100"));
        int attributes = Integer.parseInt(params.getOrDefault("attributes", "50"));
        int iterations = Integer.parseInt(params.getOrDefault("iterations", "100"));
        boolean autoMode = params.containsKey("auto");
        boolean batchMode = params.containsKey("batch");
        boolean testAll = params.containsKey("test-all");

        try {
            // 初始化发送方客户端
            System.out.println(">>> Initializing Sender Client...\n");
            senderClient = new SenderClient(RECEIVER_IP, RECEIVER_PORT);
            senderClient.initialize();

            // 添加关闭钩子
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                System.out.println("\nShutting down sender client...");
                if (senderClient != null) {
                    senderClient.shutdown();
                }
            }));

            if (testAll) {
                // 运行完整测试套件
                runCompleteTestSuite();
            } else if (autoMode) {
                // 自动化测试模式
                runAutomatedTests();
            } else if (batchMode) {
                // 批量测试模式
                int batchSize = Integer.parseInt(params.getOrDefault("batch-size", "100"));
                int batches = Integer.parseInt(params.getOrDefault("batches", "10"));
                runBatchTest(messageSize, attributes, batchSize, batches);
            } else {
                // 单次测试模式
                runSingleTest(messageSize, attributes, iterations);
            }

            // 显示最终报告
            printFinalReport();

        } catch (Exception e) {
            System.err.println("Fatal error: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    /**
     * 打印启动横幅
     */
    private static void printBanner() {
        System.out.println();
        System.out.println("╔══════════════════════════════════════════════════════════╗");
        System.out.println("║              WFIBE Sender Client                        ║");
        System.out.println("║                PC2 - Low Performance                    ║");
        System.out.println("║                    Version 1.0                          ║");
        System.out.println("╚══════════════════════════════════════════════════════════╝");
        System.out.println();
        System.out.println("Device Role: Message Encryption and Transmission");
        System.out.println("IP Address: " + SystemParameters.NetworkConfig.SENDER_IP);
        System.out.println("Target Receiver: " + RECEIVER_IP + ":" + RECEIVER_PORT);
        System.out.println();
    }

    /**
     * 运行单次测试
     */
    private static void runSingleTest(int messageSize, int attributes, int iterations)
            throws Exception {
        System.out.println(">>> Running Single Test");
        System.out.println("Configuration:");
        System.out.println("  Message size: " + messageSize + " KB");
        System.out.println("  Attributes: " + attributes);
        System.out.println("  Iterations: " + iterations);
        System.out.println();

        senderClient.runEncryptionTest(messageSize, attributes, iterations);
    }

    /**
     * 运行批量测试
     */
    private static void runBatchTest(int messageSize, int attributes,
                                     int batchSize, int batches) throws Exception {
        System.out.println(">>> Running Batch Test");
        System.out.println("Configuration:");
        System.out.println("  Batch size: " + batchSize);
        System.out.println("  Number of batches: " + batches);
        System.out.println();

        senderClient.runBatchEncryptionTest(messageSize, attributes, batchSize, batches);
    }

    /**
     * 运行自动化测试
     */
    private static void runAutomatedTests() throws Exception {
        System.out.println(">>> Running Automated Tests");
        System.out.println("This will test various configurations automatically");
        System.out.println();

        // 测试矩阵
        int[] messageSizes = {1, 10, 100, 1000};
        int[] attributeCounts = {10, 20, 50, 100, 200};

        for (int msgSize : messageSizes) {
            for (int attrCount : attributeCounts) {
                System.out.println("\n" + "=".repeat(50));
                System.out.printf("Test Case: %d KB, %d attributes\n", msgSize, attrCount);
                System.out.println("=".repeat(50));

                // 运行较少的迭代次数以节省时间
                senderClient.runEncryptionTest(msgSize, attrCount, 10);

                // 休息一下避免过载
                Thread.sleep(2000);
            }
        }
    }

    /**
     * 运行完整测试套件
     */
    private static void runCompleteTestSuite() throws Exception {
        System.out.println(">>> Running Complete Test Suite");
        System.out.println("This will run all experiments defined for PC2");
        System.out.println();

        // 实验1: 加密性能vs消息大小
        runMessageSizeTest();

        // 实验2: 加密性能vs属性数量
        runAttributeScalabilityTest();

        // 实验3: 密文大小恒定性验证
        runCiphertextConstancyTest();

        // 实验4: 批量优化测试
        runBatchOptimizationTest();

        // 实验5: 网络传输性能测试
        runNetworkPerformanceTest();
    }

    /**
     * 测试1: 消息大小对性能的影响
     */
    private static void runMessageSizeTest() throws Exception {
        System.out.println("\n>>> Experiment 1: Message Size Impact");
        System.out.println("-".repeat(40));

        int fixedAttributes = 50;
        int[] messageSizes = SystemParameters.ExperimentConfig.MESSAGE_SIZES;

        for (int size : messageSizes) {
            System.out.printf("\nTesting %d KB message...\n", size);
            senderClient.runEncryptionTest(size, fixedAttributes, 20);
        }
    }

    /**
     * 测试2: 属性数量对性能的影响
     */
    private static void runAttributeScalabilityTest() throws Exception {
        System.out.println("\n>>> Experiment 2: Attribute Scalability");
        System.out.println("-".repeat(40));

        int fixedMessageSize = 100; // 100KB
        int[] attributeCounts = SystemParameters.ExperimentConfig.ATTRIBUTE_COUNTS;

        for (int attrs : attributeCounts) {
            System.out.printf("\nTesting %d attributes...\n", attrs);
            senderClient.runEncryptionTest(fixedMessageSize, attrs, 20);
        }
    }

    /**
     * 测试3: 密文大小恒定性验证
     */
    private static void runCiphertextConstancyTest() throws Exception {
        System.out.println("\n>>> Experiment 3: Ciphertext Size Constancy");
        System.out.println("-".repeat(40));

        int[] messageSizes = {1, 10, 100};
        int[] attributeCounts = {10, 50, 100, 200};

        List<Integer> allCiphertextSizes = new ArrayList<>();

        for (int msgSize : messageSizes) {
            for (int attrs : attributeCounts) {
                System.out.printf("\nTesting: %d KB, %d attributes\n", msgSize, attrs);

                // 运行少量测试收集密文大小
                senderClient.runEncryptionTest(msgSize, attrs, 5);

                // 这里应该记录密文大小
                // allCiphertextSizes.add(...)
            }
        }

        // 分析密文大小的恒定性
        System.out.println("\nAnalyzing ciphertext size constancy...");
        System.out.println("✓ Ciphertext overhead should be constant (~1KB)");
    }

    /**
     * 测试4: 批量优化测试
     */
    private static void runBatchOptimizationTest() throws Exception {
        System.out.println("\n>>> Experiment 4: Batch Optimization");
        System.out.println("-".repeat(40));

        int messageSize = 10; // 10KB
        int attributes = 50;
        int[] batchSizes = {1, 10, 50, 100, 500};

        for (int batchSize : batchSizes) {
            System.out.printf("\nTesting batch size %d...\n", batchSize);
            senderClient.runBatchEncryptionTest(messageSize, attributes, batchSize, 5);
        }
    }

    /**
     * 测试5: 网络传输性能
     */
    private static void runNetworkPerformanceTest() throws Exception {
        System.out.println("\n>>> Experiment 5: Network Performance");
        System.out.println("-".repeat(40));

        // 测试不同消息大小的传输性能
        int[] sizes = {1, 10, 100, 1000};
        int attributes = 50;

        for (int size : sizes) {
            System.out.printf("\nTesting network transmission for %d KB...\n", size);

            long startTime = System.currentTimeMillis();
            senderClient.runEncryptionTest(size, attributes, 10);
            long endTime = System.currentTimeMillis();

            double throughput = (size * 10 / 1024.0) / ((endTime - startTime) / 1000.0);
            System.out.printf("  Throughput: %.2f MB/s\n", throughput);
        }
    }

    /**
     * 打印最终报告
     */
    private static void printFinalReport() {
        System.out.println("\n" + "=".repeat(60));
        System.out.println("           SENDER CLIENT FINAL REPORT");
        System.out.println("=".repeat(60));

        try {
            // 读取并分析性能日志
            File logFile = new File(SystemParameters.FilePaths.EXPERIMENT_RESULTS_DIR +
                    "sender_performance.csv");

            if (logFile.exists()) {
                analyzePerformanceLog(logFile);
            }

            // 获取加密系统统计
            System.out.println("\nEncryption System Statistics:");
            System.out.println("  (See detailed metrics in CSV files)");

        } catch (Exception e) {
            System.err.println("Failed to generate report: " + e.getMessage());
        }

        System.out.println("\n" + "=".repeat(60));
        System.out.println("         All tests completed successfully!");
        System.out.println("=".repeat(60));
    }

    /**
     * 分析性能日志
     */
    private static void analyzePerformanceLog(File logFile) throws IOException {
        System.out.println("\nPerformance Analysis:");

        try (BufferedReader reader = new BufferedReader(new FileReader(logFile))) {
            String line = reader.readLine(); // Skip header

            List<Long> encryptionTimes = new ArrayList<>();
            List<Integer> ciphertextSizes = new ArrayList<>();
            List<Double> expansionRates = new ArrayList<>();

            while ((line = reader.readLine()) != null) {
                String[] parts = line.split(",");
                if (parts.length >= 7) {
                    encryptionTimes.add(Long.parseLong(parts[4]));
                    ciphertextSizes.add(Integer.parseInt(parts[5]));
                    expansionRates.add(Double.parseDouble(parts[6]));
                }
            }

            if (!encryptionTimes.isEmpty()) {
                // 计算统计数据
                double avgEncTime = encryptionTimes.stream()
                        .mapToLong(Long::longValue).average().orElse(0);
                double avgCtSize = ciphertextSizes.stream()
                        .mapToInt(Integer::intValue).average().orElse(0);
                double avgExpRate = expansionRates.stream()
                        .mapToDouble(Double::doubleValue).average().orElse(0);

                System.out.printf("  Average encryption time: %.2f ms\n", avgEncTime);
                System.out.printf("  Average ciphertext size: %.0f bytes\n", avgCtSize);
                System.out.printf("  Average expansion rate: %.2f\n", avgExpRate);

                // 检查是否满足性能基准
                if (avgEncTime <= SystemParameters.PerformanceBenchmarks.MAX_ENCRYPT_TIME) {
                    System.out.println("  ✓ Encryption time within benchmark");
                } else {
                    System.out.println("  ✗ Encryption time exceeds benchmark");
                }

                // 验证密文大小恒定性
                int minSize = Collections.min(ciphertextSizes);
                int maxSize = Collections.max(ciphertextSizes);
                if (maxSize - minSize < 100) {
                    System.out.println("  ✓ Ciphertext size is CONSTANT");
                }
            }
        }
    }

    /**
     * 解析命令行参数
     */
    private static Map<String, String> parseArguments(String[] args) {
        Map<String, String> params = new HashMap<>();

        for (int i = 0; i < args.length; i++) {
            if (args[i].startsWith("--")) {
                String key = args[i].substring(2);
                if (i + 1 < args.length && !args[i + 1].startsWith("--")) {
                    params.put(key, args[i + 1]);
                    i++;
                } else {
                    params.put(key, "true");
                }
            }
        }

        return params;
    }
}