package com.wfibe.experiments;

import java.lang.management.*;
import com.sun.management.OperatingSystemMXBean;
import java.util.*;
import java.io.*;

/**
 * 性能监控器 - 监控PC2的资源使用
 */
public class PerformanceMonitor {

    private static PerformanceMonitor instance;

    private MemoryMXBean memoryBean;
    private OperatingSystemMXBean osBean;
    private ThreadMXBean threadBean;
    private RuntimeMXBean runtimeBean;

    private List<PerformanceSnapshot> snapshots;
    private boolean monitoring;
    private Thread monitorThread;

    private PerformanceMonitor() {
        this.memoryBean = ManagementFactory.getMemoryMXBean();
        // 使用com.sun.management.OperatingSystemMXBean获取CPU信息
        this.osBean = (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
        this.threadBean = ManagementFactory.getThreadMXBean();
        this.runtimeBean = ManagementFactory.getRuntimeMXBean();
        this.snapshots = new ArrayList<>();
    }

    public static PerformanceMonitor getInstance() {
        if (instance == null) {
            instance = new PerformanceMonitor();
        }
        return instance;
    }

    /**
     * 开始监控
     */
    public void startMonitoring(int intervalMs) {
        if (monitoring) return;

        monitoring = true;
        monitorThread = new Thread(() -> {
            while (monitoring) {
                captureSnapshot();
                try {
                    Thread.sleep(intervalMs);
                } catch (InterruptedException e) {
                    break;
                }
            }
        });
        monitorThread.setDaemon(true);
        monitorThread.start();

        System.out.println("Performance monitoring started (interval: " + intervalMs + "ms)");
    }

    /**
     * 停止监控
     */
    public void stopMonitoring() {
        monitoring = false;
        if (monitorThread != null) {
            monitorThread.interrupt();
        }
        System.out.println("Performance monitoring stopped");
    }

    /**
     * 捕获性能快照
     */
    public PerformanceSnapshot captureSnapshot() {
        PerformanceSnapshot snapshot = new PerformanceSnapshot();

        snapshot.timestamp = System.currentTimeMillis();

        // 内存使用
        MemoryUsage heapUsage = memoryBean.getHeapMemoryUsage();
        snapshot.heapUsed = heapUsage.getUsed();
        snapshot.heapMax = heapUsage.getMax();
        snapshot.heapPercent = (heapUsage.getMax() > 0) ?
                (double) heapUsage.getUsed() / heapUsage.getMax() * 100 : 0;

        // CPU使用 - 使用com.sun.management.OperatingSystemMXBean的方法
        snapshot.cpuLoad = osBean.getProcessCpuLoad() * 100;
        if (snapshot.cpuLoad < 0) {
            // 如果getProcessCpuLoad返回负值，尝试使用系统CPU负载
            snapshot.cpuLoad = osBean.getSystemCpuLoad() * 100;
        }
        snapshot.availableProcessors = osBean.getAvailableProcessors();

        // 线程信息
        snapshot.threadCount = threadBean.getThreadCount();
        snapshot.peakThreadCount = threadBean.getPeakThreadCount();

        // 系统信息
        snapshot.freeMemory = Runtime.getRuntime().freeMemory();
        snapshot.totalMemory = Runtime.getRuntime().totalMemory();
        snapshot.maxMemory = Runtime.getRuntime().maxMemory();

        // JVM运行时间
        snapshot.uptime = runtimeBean.getUptime();

        snapshots.add(snapshot);
        return snapshot;
    }

    /**
     * 获取当前性能状态
     */
    public String getCurrentStatus() {
        PerformanceSnapshot current = captureSnapshot();

        StringBuilder sb = new StringBuilder();
        sb.append("=== Performance Status ===\n");
        sb.append(String.format("Heap Memory: %.2f MB / %.2f MB (%.1f%%)\n",
                current.heapUsed / 1048576.0,
                current.heapMax / 1048576.0,
                current.heapPercent));
        sb.append(String.format("CPU Load: %.1f%% (%d cores)\n",
                current.cpuLoad, current.availableProcessors));
        sb.append(String.format("Threads: %d (peak: %d)\n",
                current.threadCount, current.peakThreadCount));
        sb.append(String.format("JVM Uptime: %.1f seconds\n",
                current.uptime / 1000.0));

        return sb.toString();
    }

    /**
     * 获取性能统计
     */
    public PerformanceStatistics getStatistics() {
        if (snapshots.isEmpty()) {
            return new PerformanceStatistics();
        }

        PerformanceStatistics stats = new PerformanceStatistics();

        // 计算平均值
        double totalHeapPercent = 0;
        double totalCpuLoad = 0;
        int validCpuCount = 0;

        for (PerformanceSnapshot snapshot : snapshots) {
            totalHeapPercent += snapshot.heapPercent;
            if (snapshot.cpuLoad >= 0) {
                totalCpuLoad += snapshot.cpuLoad;
                validCpuCount++;
            }
        }

        stats.avgHeapPercent = totalHeapPercent / snapshots.size();
        stats.avgCpuLoad = validCpuCount > 0 ? totalCpuLoad / validCpuCount : 0;

        // 找出峰值
        stats.peakHeapUsed = snapshots.stream()
                .mapToLong(s -> s.heapUsed)
                .max()
                .orElse(0);

        stats.peakCpuLoad = snapshots.stream()
                .mapToDouble(s -> s.cpuLoad)
                .filter(cpu -> cpu >= 0)
                .max()
                .orElse(0);

        stats.totalSnapshots = snapshots.size();

        return stats;
    }

    /**
     * 保存性能数据到CSV
     */
    public void saveToCSV(String filename) throws IOException {
        try (PrintWriter writer = new PrintWriter(new FileWriter(filename))) {
            // 写入头部
            writer.println("Timestamp,HeapUsed(MB),HeapMax(MB),HeapPercent,CPULoad,ThreadCount,Uptime(s)");

            // 写入数据
            for (PerformanceSnapshot snapshot : snapshots) {
                writer.printf("%d,%.2f,%.2f,%.2f,%.2f,%d,%.1f\n",
                        snapshot.timestamp,
                        snapshot.heapUsed / 1048576.0,
                        snapshot.heapMax / 1048576.0,
                        snapshot.heapPercent,
                        snapshot.cpuLoad,
                        snapshot.threadCount,
                        snapshot.uptime / 1000.0);
            }
        }

        System.out.println("Performance data saved to: " + filename);
    }

    /**
     * 清除历史数据
     */
    public void clearHistory() {
        snapshots.clear();
    }

    /**
     * 检查内存压力
     */
    public boolean isMemoryPressure() {
        PerformanceSnapshot current = captureSnapshot();
        return current.heapPercent > 80; // 超过80%认为有内存压力
    }

    /**
     * 触发垃圾回收
     */
    public void suggestGC() {
        System.gc();
        System.out.println("Garbage collection suggested");
    }

    /**
     * 获取简单的CPU使用率（备用方法）
     */
    private double getSimpleCpuUsage() {
        // 如果无法获取进程CPU使用率，使用这个简单的方法
        long startTime = System.nanoTime();
        long startCpuTime = getCurrentCpuTime();

        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            return 0;
        }

        long endTime = System.nanoTime();
        long endCpuTime = getCurrentCpuTime();

        long timeDiff = endTime - startTime;
        long cpuDiff = endCpuTime - startCpuTime;

        if (timeDiff > 0) {
            return (double) cpuDiff / timeDiff * 100;
        }
        return 0;
    }

    /**
     * 获取当前CPU时间
     */
    private long getCurrentCpuTime() {
        ThreadMXBean bean = ManagementFactory.getThreadMXBean();
        if (!bean.isCurrentThreadCpuTimeSupported()) {
            return 0;
        }

        long cpuTime = 0;
        long[] threadIds = bean.getAllThreadIds();
        for (long id : threadIds) {
            long time = bean.getThreadCpuTime(id);
            if (time > 0) {
                cpuTime += time;
            }
        }
        return cpuTime;
    }

    // ==================== 数据结构 ====================

    public static class PerformanceSnapshot {
        public long timestamp;
        public long heapUsed;
        public long heapMax;
        public double heapPercent;
        public double cpuLoad;
        public int availableProcessors;
        public int threadCount;
        public int peakThreadCount;
        public long freeMemory;
        public long totalMemory;
        public long maxMemory;
        public long uptime;
    }

    public static class PerformanceStatistics {
        public double avgHeapPercent;
        public double avgCpuLoad;
        public long peakHeapUsed;
        public double peakCpuLoad;
        public int totalSnapshots;

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("=== Performance Statistics ===\n");
            sb.append(String.format("Average Heap Usage: %.1f%%\n", avgHeapPercent));
            sb.append(String.format("Average CPU Load: %.1f%%\n", avgCpuLoad));
            sb.append(String.format("Peak Heap Used: %.2f MB\n", peakHeapUsed / 1048576.0));
            sb.append(String.format("Peak CPU Load: %.1f%%\n", peakCpuLoad));
            sb.append(String.format("Total Snapshots: %d\n", totalSnapshots));
            return sb.toString();
        }
    }

    /**
     * 测试主方法
     */
    public static void main(String[] args) {
        PerformanceMonitor monitor = PerformanceMonitor.getInstance();

        // 开始监控
        monitor.startMonitoring(1000); // 每秒采样一次

        // 显示当前状态
        System.out.println(monitor.getCurrentStatus());

        // 运行一段时间
        try {
            Thread.sleep(5000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        // 停止监控
        monitor.stopMonitoring();

        // 显示统计
        System.out.println(monitor.getStatistics());

        // 保存数据
        try {
            monitor.saveToCSV("performance_data.csv");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}