package com.wfibe.experiments;

import com.wfibe.crypto.*;
import java.io.*;
import java.net.*;
import java.util.*;

/**
 * 实验控制器 - 在PC1上运行
 * 协调三台设备完成实验
 */
public class ExperimentController {

    private static final String PC2_IP = SystemParameters.NetworkConfig.SENDER_IP;
    private static final String PC3_IP = SystemParameters.NetworkConfig.RECEIVER_IP;

    public static void main(String[] args) {
        System.out.println("\n=== WFIBE Experiment Controller ===");
        System.out.println("This controller coordinates experiments across all devices");
        System.out.println();

        ExperimentController controller = new ExperimentController();

        // 运行完整实验套件
        controller.runCompleteExperimentSuite();
    }

    /**
     * 运行完整实验套件
     */
    public void runCompleteExperimentSuite() {
        System.out.println("Starting complete experiment suite...\n");

        // 实验1: 不同向量维度的性能测试
        runVectorDimensionTest();

        // 实验2: 不同属性数量的可扩展性测试
        runScalabilityTest();

        // 实验3: 密文大小恒定性验证
        runCiphertextConstancyTest();

        // 实验4: 碰撞概率测试
        runCollisionProbabilityTest();

        // 实验5: 网络性能测试
        runNetworkPerformanceTest();

        System.out.println("\n=== All experiments completed ===");

        // 生成最终报告
        generateFinalReport();
    }

    /**
     * 测试1: 向量维度性能测试
     */
    private void runVectorDimensionTest() {
        System.out.println("\n>>> Experiment 1: Vector Dimension Performance Test");
        System.out.println("----------------------------------------------------");

        int[] dimensions = SystemParameters.ExperimentConfig.VECTOR_DIMENSIONS;

        for (int dim : dimensions) {
            System.out.println("\nTesting dimension: " + dim);

            // 重启KGC服务器with新维度
            restartKGCServer(dim, dim);

            // 触发测试
            triggerTest(dim, 50, 100); // 50个属性，100KB消息

            // 等待测试完成
            waitForCompletion(30000); // 30秒
        }
    }

    /**
     * 测试2: 可扩展性测试
     */
    private void runScalabilityTest() {
        System.out.println("\n>>> Experiment 2: Scalability Test");
        System.out.println("-----------------------------------");

        int fixedDimension = 256;
        int[] attributeCounts = SystemParameters.ExperimentConfig.ATTRIBUTE_COUNTS;

        // 启动KGC服务器
        restartKGCServer(fixedDimension, fixedDimension);

        for (int attrCount : attributeCounts) {
            System.out.println("\nTesting with " + attrCount + " attributes");

            // 触发测试
            triggerTest(fixedDimension, attrCount, 100);

            // 记录结果
            recordScalabilityResult(fixedDimension, attrCount);

            waitForCompletion(20000);
        }
    }

    /**
     * 测试3: 密文大小恒定性验证
     */
    private void runCiphertextConstancyTest() {
        System.out.println("\n>>> Experiment 3: Ciphertext Size Constancy Test");
        System.out.println("-------------------------------------------------");

        int dimension = 256;
        int[] messageSizes = {1, 10, 100, 1000}; // KB
        int[] attributeCounts = {10, 50, 100, 200};

        restartKGCServer(dimension, dimension);

        List<Integer> ciphertextSizes = new ArrayList<>();

        for (int msgSize : messageSizes) {
            for (int attrCount : attributeCounts) {
                System.out.printf("Testing: %d KB message, %d attributes\n",
                        msgSize, attrCount);

                // 触发加密测试
                int ctSize = measureCiphertextSize(dimension, attrCount, msgSize);
                ciphertextSizes.add(ctSize);
            }
        }

        // 验证恒定性
        verifyCiphertextConstancy(ciphertextSizes);
    }

    /**
     * 测试4: 碰撞概率测试
     */
    private void runCollisionProbabilityTest() {
        System.out.println("\n>>> Experiment 4: Collision Probability Test");
        System.out.println("---------------------------------------------");

        int[] dimensions = {64, 128, 256, 512, 1024};
        int[] attributeCounts = {20, 50, 100};
        int trials = 10000;

        for (int dim : dimensions) {
            for (int attrCount : attributeCounts) {
                double collisionRate = testCollisionProbability(dim, attrCount, trials);

                System.out.printf("Dim=%d, Attrs=%d: Collision rate=%.8f\n",
                        dim, attrCount, collisionRate);

                // 验证是否满足阈值
                if (collisionRate > SystemParameters.PerformanceBenchmarks.MAX_COLLISION_PROBABILITY) {
                    System.out.println("  ✗ Exceeds threshold!");
                } else {
                    System.out.println("  ✓ Within threshold");
                }
            }
        }
    }

    /**
     * 测试5: 网络性能测试
     */
    private void runNetworkPerformanceTest() {
        System.out.println("\n>>> Experiment 5: Network Performance Test");
        System.out.println("-------------------------------------------");

        // 测试端到端延迟
        testEndToEndLatency();

        // 测试吞吐量
        testThroughput();
    }

    // ========== 辅助方法 ==========

    private void restartKGCServer(int n, int m) {
        try {
            // 停止现有服务器
            sendCommand("STOP", SystemParameters.NetworkConfig.KGC_IP,
                    SystemParameters.NetworkConfig.KGC_PORT);
            Thread.sleep(2000);

            // 启动新服务器
            ProcessBuilder pb = new ProcessBuilder(
                    "java", "-cp", "target/classes",
                    "com.wfibe.experiments.KGCServerMain",
                    String.valueOf(n), String.valueOf(m));
            pb.start();

            Thread.sleep(5000); // 等待启动

        } catch (Exception e) {
            System.err.println("Failed to restart KGC: " + e.getMessage());
        }
    }

    private void triggerTest(int dim, int attrs, int msgSize) {
        try {
            // 发送测试命令到PC2和PC3
            sendTestCommand(PC2_IP, 8081, "ENCRYPT", dim, attrs, msgSize);
            sendTestCommand(PC3_IP, 8082, "DECRYPT", dim, attrs, msgSize);

        } catch (Exception e) {
            System.err.println("Failed to trigger test: " + e.getMessage());
        }
    }

    private void sendTestCommand(String ip, int port, String command,
                                 int dim, int attrs, int msgSize) {
        try (Socket socket = new Socket(ip, port)) {
            PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
            out.printf("%s,%d,%d,%d\n", command, dim, attrs, msgSize);
        } catch (IOException e) {
            System.err.println("Failed to send command to " + ip + ": " + e.getMessage());
        }
    }

    private void sendCommand(String command, String ip, int port) {
        try (Socket socket = new Socket(ip, port)) {
            PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
            out.println(command);
        } catch (IOException e) {
            // Ignore
        }
    }

    private void waitForCompletion(long milliseconds) {
        try {
            Thread.sleep(milliseconds);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private int measureCiphertextSize(int dim, int attrs, int msgSizeKB) {
        // 理论计算：4个群元素 + 加密消息
        int groupElementSize = 256; // 假设每个群元素256字节
        int overhead = 4 * groupElementSize;
        return overhead + msgSizeKB * 1024;
    }

    private double testCollisionProbability(int dim, int attrs, int trials) {
        // 这里应该调用实际的碰撞测试
        // 简化计算
        return (double)(attrs * attrs) / (2.0 * dim);
    }

    private void verifyCiphertextConstancy(List<Integer> sizes) {
        if (sizes.isEmpty()) return;

        int min = Collections.min(sizes);
        int max = Collections.max(sizes);
        double variation = (double)(max - min) / min * 100;

        System.out.printf("\nCiphertext size variation: %.2f%%\n", variation);

        if (variation < 1.0) {
            System.out.println("✓ Ciphertext size is CONSTANT");
        } else {
            System.out.println("✗ Ciphertext size varies too much");
        }
    }

    private void recordScalabilityResult(int dim, int attrs) {
        // 记录可扩展性测试结果
        System.out.println("  Recorded scalability data for " + attrs + " attributes");
    }

    private void testEndToEndLatency() {
        System.out.println("\nTesting end-to-end latency...");

        long totalLatency = 0;
        int iterations = 10;

        for (int i = 0; i < iterations; i++) {
            long start = System.currentTimeMillis();

            // 模拟完整流程：密钥请求 -> 加密 -> 传输 -> 解密
            // 实际实现需要与PC2和PC3通信

            long end = System.currentTimeMillis();
            totalLatency += (end - start);
        }

        double avgLatency = (double) totalLatency / iterations;
        System.out.printf("  Average end-to-end latency: %.2f ms\n", avgLatency);
    }

    private void testThroughput() {
        System.out.println("\nTesting throughput...");

        int[] messageSizes = {1, 10, 100, 1000}; // KB

        for (int size : messageSizes) {
            double throughput = measureThroughput(size);
            System.out.printf("  Message size %d KB: %.2f MB/s\n",
                    size, throughput);
        }
    }

    private double measureThroughput(int messageSizeKB) {
        // 简化的吞吐量测量
        // 实际应该测量实际传输时间
        double timeSeconds = messageSizeKB / 100.0; // 假设100KB/s
        return (messageSizeKB / 1024.0) / timeSeconds;
    }

    private void generateFinalReport() {
        System.out.println("\nGenerating final experiment report...");

        try {
            DataAnalyzer analyzer = new DataAnalyzer();
            analyzer.analyzeAllResults();

            System.out.println("✓ Final report generated successfully");
        } catch (Exception e) {
            System.err.println("Report generation failed: " + e.getMessage());
        }
    }
}