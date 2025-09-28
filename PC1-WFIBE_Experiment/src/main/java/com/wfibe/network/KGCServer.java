package com.wfibe.network;

import com.wfibe.crypto.*;
import java.net.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.text.SimpleDateFormat;

/**
 * KGC服务器 - 在PC1上运行
 * 负责系统初始化和密钥生成
 */
public class KGCServer {

    private ServerSocket serverSocket;
    private WFIBESystem system;
    private ExecutorService executor;
    private boolean running;
    private int port;

    // 统计信息
    private long totalRequests = 0;
    private long totalKeyGenTime = 0;
    private long successfulRequests = 0;
    private long failedRequests = 0;

    // 日志记录
    private PrintWriter performanceLog;
    private SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");

    public KGCServer(int port) {
        this.port = port;
        this.executor = Executors.newFixedThreadPool(10); // 支持10个并发连接
    }

    /**
     * 启动KGC服务器
     */
    public void start(int vectorDim_n, int vectorDim_m) throws Exception {
        System.out.println("=====================================");
        System.out.println("    KGC Server Starting");
        System.out.println("=====================================");
        System.out.println("Vector dimensions: n=" + vectorDim_n + ", m=" + vectorDim_m);
        System.out.println("Port: " + port);
        System.out.println();

        // 创建日志文件
        initializeLogging();

        // 系统初始化（Setup阶段）
        System.out.println(">>> Initializing WFIBE system...");
        long setupStart = System.currentTimeMillis();

        system = new WFIBESystem();
        WFIBESystem.SystemSetupResult setupResult =
                system.setup(vectorDim_n, vectorDim_m, SystemParameters.PAIRING_PARAMS);

        long setupEnd = System.currentTimeMillis();

        if (!setupResult.success) {
            throw new Exception("System setup failed: " + setupResult.errorMessage);
        }

        System.out.println("✓ System initialized successfully");
        System.out.println("  Setup time: " + setupResult.setupTime + " ms");
        System.out.println("  Public key size: " + setupResult.publicKeySize + " bytes");
        System.out.println("  Master key size: " + setupResult.masterKeySize + " bytes");

        // 记录setup性能
        logSetupPerformance(vectorDim_n, vectorDim_m, setupResult);

        // 保存公共参数
        savePublicParameters();

        // 启动服务器监听
        serverSocket = new ServerSocket(port);
        running = true;

        System.out.println("\n>>> KGC Server listening on port " + port);
        System.out.println(">>> Ready to process key requests\n");

        // 接受连接的主循环
        while (running) {
            try {
                Socket clientSocket = serverSocket.accept();
                clientSocket.setSoTimeout(SystemParameters.NetworkConfig.READ_TIMEOUT);

                // 提交到线程池处理
                executor.submit(() -> handleClient(clientSocket));

            } catch (SocketTimeoutException e) {
                // 超时继续
            } catch (IOException e) {
                if (running) {
                    System.err.println("Accept error: " + e.getMessage());
                }
            }
        }
    }

    /**
     * 处理客户端请求
     */
    private void handleClient(Socket clientSocket) {
        String clientAddress = clientSocket.getInetAddress().getHostAddress();
        long requestId = System.currentTimeMillis();

        System.out.println("[" + requestId + "] Client connected: " + clientAddress);

        try (
                ObjectInputStream in = new ObjectInputStream(
                        new BufferedInputStream(clientSocket.getInputStream()));
                ObjectOutputStream out = new ObjectOutputStream(
                        new BufferedOutputStream(clientSocket.getOutputStream()))
        ) {
            // 读取请求
            KeyRequest request = (KeyRequest) in.readObject();

            System.out.println("[" + requestId + "] Key request received:");
            System.out.println("  Attributes: " + request.attributes.size());
            System.out.println("  Policy size: " + request.policy.size());

            // 生成密钥
            long keyGenStart = System.nanoTime();

            // 编码属性和策略
            int[] attrVector = system.encodeAttributes(
                    request.attributes, system.getVectorDim_m());
            int[] policyVector = system.encodePolicy(
                    request.policy, system.getVectorDim_n());

            // 执行密钥生成
            WFIBESystem.KeyGenResult keyResult = system.keyGen(attrVector, policyVector);

            long keyGenEnd = System.nanoTime();
            long keyGenTime = (keyGenEnd - keyGenStart) / 1_000_000; // ms

            // 准备响应
            KeyResponse response = new KeyResponse();
            response.requestId = requestId;
            response.success = keyResult.success;

            if (keyResult.success) {
                response.secretKey = keyResult.secretKey;
                response.keyGenTime = keyGenTime;
                response.keySize = keyResult.keySize;

                System.out.println("[" + requestId + "] Key generated successfully:");
                System.out.println("  Generation time: " + keyGenTime + " ms");
                System.out.println("  Key size: " + keyResult.keySize + " bytes");

                successfulRequests++;
            } else {
                response.errorMessage = keyResult.errorMessage;
                System.err.println("[" + requestId + "] Key generation failed: " +
                        keyResult.errorMessage);
                failedRequests++;
            }

            // 发送响应
            out.writeObject(response);
            out.flush();

            // 更新统计
            totalRequests++;
            totalKeyGenTime += keyGenTime;

            // 记录性能数据
            logKeyGenPerformance(requestId, request, keyResult, keyGenTime);

        } catch (Exception e) {
            System.err.println("[" + requestId + "] Error handling client: " + e.getMessage());
            e.printStackTrace();
            failedRequests++;
        } finally {
            try {
                clientSocket.close();
            } catch (IOException e) {
                // Ignore
            }
        }
    }

    /**
     * 保存公共参数到文件
     */
    private void savePublicParameters() {
        try {
            File file = new File(SystemParameters.FilePaths.PUBLIC_PARAMS_FILE);

            try (ObjectOutputStream oos = new ObjectOutputStream(
                    new FileOutputStream(file))) {

                WFIBESystem.PublicParameters params = system.getPublicParameters();
                oos.writeObject(params);

                System.out.println("✓ Public parameters saved to: " +
                        file.getAbsolutePath());
            }

        } catch (Exception e) {
            System.err.println("Failed to save public parameters: " + e.getMessage());
        }
    }

    /**
     * 初始化日志记录
     */
    private void initializeLogging() {
        try {
            // 创建目录
            new File(SystemParameters.FilePaths.EXPERIMENT_RESULTS_DIR).mkdirs();
            new File(SystemParameters.FilePaths.LOGS_DIR).mkdirs();

            // 创建性能日志文件
            File logFile = new File(SystemParameters.FilePaths.EXPERIMENT_RESULTS_DIR +
                    SystemParameters.FilePaths.KEYGEN_LOG_CSV);
            boolean isNewFile = !logFile.exists() || logFile.length() == 0;

            performanceLog = new PrintWriter(new FileWriter(logFile, true));

            // 写入CSV头部（如果文件是新的）
            if (isNewFile) {
                performanceLog.println("Timestamp,RequestId,AttributeCount,PolicySize," +
                        "KeyGenTime(ms),KeySize(bytes),Success");
            }

        } catch (IOException e) {
            System.err.println("Failed to initialize logging: " + e.getMessage());
        }
    }

    /**
     * 记录Setup性能
     */
    private void logSetupPerformance(int n, int m, WFIBESystem.SystemSetupResult result) {
        try {
            File file = new File(SystemParameters.FilePaths.EXPERIMENT_RESULTS_DIR +
                    SystemParameters.FilePaths.SETUP_PERFORMANCE_CSV);
            boolean isNewFile = !file.exists() || file.length() == 0;

            try (PrintWriter writer = new PrintWriter(new FileWriter(file, true))) {
                // 检查是否需要写入头部
                if (isNewFile) {
                    writer.println("Timestamp,VectorDim_n,VectorDim_m,SetupTime(ms)," +
                            "PublicKeySize(bytes),MasterKeySize(bytes)");
                }

                writer.printf("%s,%d,%d,%d,%d,%d\n",
                        dateFormat.format(new Date()),
                        n, m,
                        result.setupTime,
                        result.publicKeySize,
                        result.masterKeySize);
            }

        } catch (IOException e) {
            System.err.println("Failed to log setup performance: " + e.getMessage());
        }
    }

    /**
     * 记录KeyGen性能
     */
    private void logKeyGenPerformance(long requestId, KeyRequest request,
                                      WFIBESystem.KeyGenResult result, long keyGenTime) {
        if (performanceLog != null) {
            performanceLog.printf("%s,%d,%d,%d,%d,%d,%b\n",
                    dateFormat.format(new Date()),
                    requestId,
                    request.attributes.size(),
                    request.policy.size(),
                    keyGenTime,
                    result.keySize,
                    result.success);
            performanceLog.flush();
        }
    }

    /**
     * 打印统计信息
     */
    public void printStatistics() {
        System.out.println("\n=====================================");
        System.out.println("    KGC Server Statistics");
        System.out.println("=====================================");
        System.out.println("Total requests: " + totalRequests);
        System.out.println("Successful: " + successfulRequests);
        System.out.println("Failed: " + failedRequests);

        if (totalRequests > 0) {
            double avgKeyGenTime = (double) totalKeyGenTime / totalRequests;
            double successRate = (double) successfulRequests / totalRequests * 100;

            System.out.printf("Average key generation time: %.2f ms\n", avgKeyGenTime);
            System.out.printf("Success rate: %.2f%%\n", successRate);
        }

        System.out.println("=====================================\n");
    }

    /**
     * 关闭服务器
     */
    public void shutdown() {
        System.out.println("\n>>> Shutting down KGC server...");

        running = false;
        executor.shutdown();

        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }

            executor.awaitTermination(5, TimeUnit.SECONDS);
        } catch (Exception e) {
            System.err.println("Shutdown error: " + e.getMessage());
        }

        if (performanceLog != null) {
            performanceLog.close();
        }

        printStatistics();
        System.out.println("✓ KGC server stopped");
    }

    /**
     * 获取服务器状态
     */
    public String getStatus() {
        StringBuilder sb = new StringBuilder();
        sb.append("KGC Server Status:\n");
        sb.append("  Running: ").append(running).append("\n");
        sb.append("  Port: ").append(port).append("\n");
        sb.append("  Total Requests: ").append(totalRequests).append("\n");
        sb.append("  Success Rate: ");

        if (totalRequests > 0) {
            sb.append(String.format("%.2f%%",
                    (double) successfulRequests / totalRequests * 100));
        } else {
            sb.append("N/A");
        }
        sb.append("\n");

        return sb.toString();
    }

    /**
     * 处理控制命令（用于远程控制）
     */
    public void handleCommand(String command) {
        switch (command.toUpperCase()) {
            case "STATUS":
                System.out.println(getStatus());
                break;
            case "STATS":
                printStatistics();
                break;
            case "GC":
                System.gc();
                System.out.println("Garbage collection triggered");
                break;
            case "SHUTDOWN":
                shutdown();
                break;
            default:
                System.out.println("Unknown command: " + command);
        }
    }
}

// ==================== 请求和响应类 ====================

/**
 * 密钥请求
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
 * 密钥响应
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