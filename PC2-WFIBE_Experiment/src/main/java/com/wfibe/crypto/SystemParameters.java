package com.wfibe.crypto;

/**
 * WFIBE系统参数配置（与PC1相同）
 * 所有设备共享相同的参数配置
 */
public class SystemParameters {

    // Type A配对参数（160位群阶）
    public static final String PAIRING_PARAMS =
            "type a\n" +
                    "q 8780710799663312522437781984754049815806883199414208211028653399266475630880222957078625179422662221423155858769582317459277713367317481324925129998224791\n" +
                    "h 12016012264891146079388821366740534204802954401251311822919615131047207289359704531102844802183906537786776\n" +
                    "r 730750818665451621361119245571504901405976559617\n" +
                    "exp2 159\n" +
                    "exp1 107\n" +
                    "sign1 1\n" +
                    "sign0 1";

    // 实验参数配置
    public static final class ExperimentConfig {
        // 向量维度测试范围
        public static final int[] VECTOR_DIMENSIONS = {64, 128, 256, 512, 1024};

        // 属性数量测试范围
        public static final int[] ATTRIBUTE_COUNTS = {10, 20, 50, 100, 200, 500};

        // 消息大小测试范围（KB）
        public static final int[] MESSAGE_SIZES = {1, 10, 100, 1000, 10240};

        // 每个测试的迭代次数
        public static final int ITERATIONS_PER_TEST = 100;

        // 预热轮数
        public static final int WARMUP_ROUNDS = 10;

        // 默认权重阈值
        public static final int DEFAULT_THRESHOLD = 50;
    }

    // 网络配置
    public static final class NetworkConfig {
        public static final String KGC_IP = "192.168.1.100";
        public static final int KGC_PORT = 8080;

        public static final String SENDER_IP = "192.168.1.101";
        public static final int SENDER_PORT = 8081;

        public static final String RECEIVER_IP = "192.168.1.102";
        public static final int RECEIVER_PORT = 8082;

        // 超时设置（毫秒）
        public static final int CONNECTION_TIMEOUT = 5000;
        public static final int READ_TIMEOUT = 30000;

        // 缓冲区大小
        public static final int BUFFER_SIZE = 65536; // 64KB
    }

    // 文件路径配置
    public static final class FilePaths {
        public static final String EXPERIMENT_RESULTS_DIR = "experiment_results/";
        public static final String LOGS_DIR = "logs/";
        public static final String FIGURES_DIR = "figures/";
        public static final String PUBLIC_PARAMS_FILE = "public_params.dat";

        // CSV文件名
        public static final String SETUP_PERFORMANCE_CSV = "setup_performance.csv";
        public static final String KEYGEN_LOG_CSV = "keygen_log.csv";
        public static final String ENCRYPTION_PERFORMANCE_CSV = "encryption_performance.csv";
        public static final String DECRYPTION_RESULTS_CSV = "decryption_results.csv";
        public static final String COMMUNICATION_OVERHEAD_CSV = "communication_overhead.csv";
        public static final String REAL_TIME_METRICS_CSV = "real_time_metrics.csv";
    }

    // 性能基准值（用于验证）
    public static final class PerformanceBenchmarks {
        // 期望的最大时间（毫秒）
        public static final long MAX_SETUP_TIME = 5000;      // 5秒
        public static final long MAX_KEYGEN_TIME = 200;      // 200ms
        public static final long MAX_ENCRYPT_TIME = 150;     // 150ms
        public static final long MAX_DECRYPT_TIME = 150;     // 150ms

        // 期望的密钥和密文大小（字节）
        public static final int EXPECTED_KEY_SIZE = 1024;    // 约1KB（4个群元素）
        public static final int EXPECTED_CT_OVERHEAD = 1024; // 约1KB固定开销

        // 碰撞概率阈值
        public static final double MAX_COLLISION_PROBABILITY = 1e-9;
    }
}