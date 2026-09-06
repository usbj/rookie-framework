package com.rookie.system.pojo.vo;

import java.util.Date;
import java.util.List;

/**
 * 服务器监控信息 VO（只读展示，不落库）。
 * <p>
 * 数据来源（零依赖）：
 * <ul>
 *   <li>CPU / 内存 / 系统负载：{@code com.sun.management.OperatingSystemMXBean}；</li>
 *   <li>磁盘：{@code File.listRoots()}；</li>
 *   <li>系统与 JVM 信息：{@code java.lang.management} 各 MXBean + 系统属性。</li>
 * </ul>
 * 内存/磁盘容量均以字节为单位返回，展示层负责格式化。
 */
public class ServerMonitorVo {

    /** CPU 信息 */
    private CpuInfo cpu;

    /** 内存信息（物理内存） */
    private MemoryInfo memory;

    /** 磁盘信息（各根分区） */
    private List<DiskInfo> disks;

    /** 运行系统基本信息 */
    private SystemInfo system;

    /** JVM 数据 */
    private JvmInfo jvm;

    public ServerMonitorVo() {
    }

    public CpuInfo getCpu() {
        return cpu;
    }

    public void setCpu(CpuInfo cpu) {
        this.cpu = cpu;
    }

    public MemoryInfo getMemory() {
        return memory;
    }

    public void setMemory(MemoryInfo memory) {
        this.memory = memory;
    }

    public List<DiskInfo> getDisks() {
        return disks;
    }

    public void setDisks(List<DiskInfo> disks) {
        this.disks = disks;
    }

    public SystemInfo getSystem() {
        return system;
    }

    public void setSystem(SystemInfo system) {
        this.system = system;
    }

    public JvmInfo getJvm() {
        return jvm;
    }

    public void setJvm(JvmInfo jvm) {
        this.jvm = jvm;
    }

    /**
     * CPU 信息。
     */
    public static class CpuInfo {

        /** 可用处理器核心数 */
        private Integer cores;

        /** CPU 使用率（%，双采样；不可用为 null） */
        private Double usagePercent;

        /** 系统 1 分钟平均负载（Windows 不可用为 null） */
        private Double systemLoad;

        public CpuInfo() {
        }

        public Integer getCores() {
            return cores;
        }

        public void setCores(Integer cores) {
            this.cores = cores;
        }

        public Double getUsagePercent() {
            return usagePercent;
        }

        public void setUsagePercent(Double usagePercent) {
            this.usagePercent = usagePercent;
        }

        public Double getSystemLoad() {
            return systemLoad;
        }

        public void setSystemLoad(Double systemLoad) {
            this.systemLoad = systemLoad;
        }
    }

    /**
     * 物理内存信息（字节）。
     */
    public static class MemoryInfo {

        /** 内存总量 */
        private Long total;

        /** 已用内存（total - free，不含 swap） */
        private Long used;

        /** 可用内存 */
        private Long available;

        /** 使用率（%） */
        private Double usagePercent;

        public MemoryInfo() {
        }

        public Long getTotal() {
            return total;
        }

        public void setTotal(Long total) {
            this.total = total;
        }

        public Long getUsed() {
            return used;
        }

        public void setUsed(Long used) {
            this.used = used;
        }

        public Long getAvailable() {
            return available;
        }

        public void setAvailable(Long available) {
            this.available = available;
        }

        public Double getUsagePercent() {
            return usagePercent;
        }

        public void setUsagePercent(Double usagePercent) {
            this.usagePercent = usagePercent;
        }
    }

    /**
     * 单个磁盘分区信息（字节）。
     */
    public static class DiskInfo {

        /** 盘符 / 挂载点（如 C:\ 或 /） */
        private String name;

        /** 分区总量 */
        private Long total;

        /** 已用空间 */
        private Long used;

        /** 可用空间 */
        private Long available;

        /** 使用率（%） */
        private Double usagePercent;

        public DiskInfo() {
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public Long getTotal() {
            return total;
        }

        public void setTotal(Long total) {
            this.total = total;
        }

        public Long getUsed() {
            return used;
        }

        public void setUsed(Long used) {
            this.used = used;
        }

        public Long getAvailable() {
            return available;
        }

        public void setAvailable(Long available) {
            this.available = available;
        }

        public Double getUsagePercent() {
            return usagePercent;
        }

        public void setUsagePercent(Double usagePercent) {
            this.usagePercent = usagePercent;
        }
    }

    /**
     * 运行系统基本信息。
     */
    public static class SystemInfo {

        /** 操作系统名称（如 Windows 10 / Linux） */
        private String osName;

        /** 操作系统版本 */
        private String osVersion;

        /** 系统架构（如 amd64 / aarch64） */
        private String osArch;

        /** 主机名 */
        private String hostName;

        /** 服务器当前时间 */
        private Date currentTime;

        public SystemInfo() {
        }

        public String getOsName() {
            return osName;
        }

        public void setOsName(String osName) {
            this.osName = osName;
        }

        public String getOsVersion() {
            return osVersion;
        }

        public void setOsVersion(String osVersion) {
            this.osVersion = osVersion;
        }

        public String getOsArch() {
            return osArch;
        }

        public void setOsArch(String osArch) {
            this.osArch = osArch;
        }

        public String getHostName() {
            return hostName;
        }

        public void setHostName(String hostName) {
            this.hostName = hostName;
        }

        public Date getCurrentTime() {
            return currentTime;
        }

        public void setCurrentTime(Date currentTime) {
            this.currentTime = currentTime;
        }
    }

    /**
     * JVM 数据。
     */
    public static class JvmInfo {

        /** JVM 名称（如 OpenJDK 64-Bit Server VM） */
        private String vmName;

        /** JVM 版本 */
        private String vmVersion;

        /** Java 版本（java.version） */
        private String javaVersion;

        /** Java 安装路径（java.home） */
        private String javaHome;

        /** 应用启动路径（user.dir） */
        private String userDir;

        /** JVM 启动时间（毫秒时间戳） */
        private Long startTime;

        /** JVM 已运行时长（秒） */
        private Long uptimeSeconds;

        /** 堆内存已用（字节） */
        private Long heapUsed;

        /** 堆内存已提交（字节） */
        private Long heapCommitted;

        /** 堆内存最大（字节） */
        private Long heapMax;

        /** 非堆内存已用（字节） */
        private Long nonHeapUsed;

        /** 当前线程数 */
        private Integer threadCount;

        /** 峰值线程数 */
        private Integer peakThreadCount;

        /** 已加载类数量 */
        private Integer loadedClassCount;

        /** GC 统计（各收集器名称/次数/耗时） */
        private List<GcInfo> gcs;

        public JvmInfo() {
        }

        public String getVmName() {
            return vmName;
        }

        public void setVmName(String vmName) {
            this.vmName = vmName;
        }

        public String getVmVersion() {
            return vmVersion;
        }

        public void setVmVersion(String vmVersion) {
            this.vmVersion = vmVersion;
        }

        public String getJavaVersion() {
            return javaVersion;
        }

        public void setJavaVersion(String javaVersion) {
            this.javaVersion = javaVersion;
        }

        public String getJavaHome() {
            return javaHome;
        }

        public void setJavaHome(String javaHome) {
            this.javaHome = javaHome;
        }

        public String getUserDir() {
            return userDir;
        }

        public void setUserDir(String userDir) {
            this.userDir = userDir;
        }

        public Long getStartTime() {
            return startTime;
        }

        public void setStartTime(Long startTime) {
            this.startTime = startTime;
        }

        public Long getUptimeSeconds() {
            return uptimeSeconds;
        }

        public void setUptimeSeconds(Long uptimeSeconds) {
            this.uptimeSeconds = uptimeSeconds;
        }

        public Long getHeapUsed() {
            return heapUsed;
        }

        public void setHeapUsed(Long heapUsed) {
            this.heapUsed = heapUsed;
        }

        public Long getHeapCommitted() {
            return heapCommitted;
        }

        public void setHeapCommitted(Long heapCommitted) {
            this.heapCommitted = heapCommitted;
        }

        public Long getHeapMax() {
            return heapMax;
        }

        public void setHeapMax(Long heapMax) {
            this.heapMax = heapMax;
        }

        public Long getNonHeapUsed() {
            return nonHeapUsed;
        }

        public void setNonHeapUsed(Long nonHeapUsed) {
            this.nonHeapUsed = nonHeapUsed;
        }

        public Integer getThreadCount() {
            return threadCount;
        }

        public void setThreadCount(Integer threadCount) {
            this.threadCount = threadCount;
        }

        public Integer getPeakThreadCount() {
            return peakThreadCount;
        }

        public void setPeakThreadCount(Integer peakThreadCount) {
            this.peakThreadCount = peakThreadCount;
        }

        public Integer getLoadedClassCount() {
            return loadedClassCount;
        }

        public void setLoadedClassCount(Integer loadedClassCount) {
            this.loadedClassCount = loadedClassCount;
        }

        public List<GcInfo> getGcs() {
            return gcs;
        }

        public void setGcs(List<GcInfo> gcs) {
            this.gcs = gcs;
        }
    }

    /**
     * 单个 GC 收集器统计。
     */
    public static class GcInfo {

        /** 收集器名称（如 G1 Young Generation） */
        private String name;

        /** 收集次数 */
        private Long count;

        /** 累计耗时（毫秒） */
        private Long timeMs;

        public GcInfo() {
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public Long getCount() {
            return count;
        }

        public void setCount(Long count) {
            this.count = count;
        }

        public Long getTimeMs() {
            return timeMs;
        }

        public void setTimeMs(Long timeMs) {
            this.timeMs = timeMs;
        }
    }
}
