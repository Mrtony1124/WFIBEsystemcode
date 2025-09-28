package com.wfibe.experiments;

import com.wfibe.crypto.*;
import com.wfibe.network.*;
import java.util.Scanner;
import java.io.File;

/**
 * PC1 KGC服务器主程序
 * 这是在PC1（高性能设备）上运行的主入口
 */
public class KGCServerMain {

    private static KGCServer server;

    public static void main(String[] args) {
        printBanner();

        // 解析命令行参数
        int n = 256, m = 256;  // 默认向量维度

        if (args.length >= 2) {
            n = Integer.parseInt(args[0]);
            m = Integer.parseInt(args[1]);
        } else {
            // 交互式输入
            Scanner scanner = new Scanner(System.in);

            System.out.print("Enter vector dimension n (default 256): ");
            String nInput = scanner.nextLine().trim();
            if (!nInput.isEmpty()) {
                n = Integer.parseInt(nInput);
            }

            System.out.print("Enter vector dimension m (default 256): ");
            String mInput = scanner.nextLine().trim();
            if (!mInput.isEmpty()) {
                m = Integer.parseInt(mInput);
            }
        }

        // 验证参数
        if (!SystemParameters.validateParameters(n, 100)) {
            System.err.println("Invalid parameters!");
            System.exit(1);
        }

        System.out.println("\nConfiguration:");
        System.out.println("  Vector dimension n: " + n);
        System.out.println("  Vector dimension m: " + m);
        System.out.println("  KGC Port: " + SystemParameters.NetworkConfig.KGC_PORT);
        System.out.println();

        // 创建必要的目录
        createDirectories();

        try {
            // 创建并启动KGC服务器
            server = new KGCServer(SystemParameters.NetworkConfig.KGC_PORT);

            // 添加关闭钩子
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                System.out.println("\nReceived shutdown signal...");
                if (server != null) {
                    server.shutdown();
                }
                generateReport();
            }));

            // 启动服务器
            System.out.println("Starting KGC Server...\n");
            server.start(n, m);

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
        System.out.println("║          WFIBE Key Generation Center (KGC)              ║");
        System.out.println("║                   PC1 - High Performance                 ║");
        System.out.println("║                      Version 1.0                         ║");
        System.out.println("╚══════════════════════════════════════════════════════════╝");
        System.out.println();
        System.out.println("Device Role: KGC Server & Experiment Coordinator");
        System.out.println("IP Address: " + SystemParameters.NetworkConfig.KGC_IP);
        System.out.println();
    }

    /**
     * 创建必要的目录
     */
    private static void createDirectories() {
        String[] dirs = {
                SystemParameters.FilePaths.EXPERIMENT_RESULTS_DIR,
                SystemParameters.FilePaths.LOGS_DIR,
                SystemParameters.FilePaths.FIGURES_DIR
        };

        for (String dir : dirs) {
            File directory = new File(dir);
            if (!directory.exists()) {
                directory.mkdirs();
                System.out.println("✓ Created directory: " + dir);
            }
        }
    }

    /**
     * 生成实验报告
     */
    private static void generateReport() {
        System.out.println("\n>>> Generating experiment report...");

        try {
            // 调用数据分析器
            DataAnalyzer analyzer = new DataAnalyzer();
            analyzer.analyzeAllResults();

            System.out.println("✓ Report generated successfully");
            System.out.println("  Check: " + SystemParameters.FilePaths.EXPERIMENT_RESULTS_DIR);

        } catch (Exception e) {
            System.err.println("Report generation failed: " + e.getMessage());
        }
    }
}