package com.rookie.system.monitor;

import com.rookie.system.pojo.vo.ServerMonitorVo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.File;
import java.lang.management.ClassLoadingMXBean;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.lang.management.RuntimeMXBean;
import java.lang.management.ThreadMXBean;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * 服务器监控数据提供者（零依赖实现，不引入 Oshi 等第三方库，便于多项目共用）。
 * <p>
 * 采集口径：
 * <ul>
 *   <li>CPU：核心数（Runtime.availableProcessors）、使用率（{@code com.sun.management.OperatingSystemMXBean#getCpuLoad}，
 *       双采样取第二次，首次调用返回 -1；不可用时返回 null）；</li>
 *   <li>内存：物理内存总量/可用（同一 MXBean），已用 = 总量 - 可用；</li>
 *   <li>磁盘：{@code File.listRoots()} 各分区总量/可用；</li>
 *   <li>系统：os.name / os.version / os.arch / 主机名 / 当前时间；</li>
 *   <li>JVM：RuntimeMXBean（名称/版本/启动时间/运行时长/java.home/user.dir）+ MemoryMXBean（堆/非堆）
 *       + GarbageCollectorMXBeans + ThreadMXBean + ClassLoadingMXBean。</li>
 * </ul>
 * 单项采集失败不影响整体（个别字段为 null，前端按空展示），保证监控页可用性。
 */
@Component
public class ServerMonitorProvider implements MonitorProvider {

    private static final Logger log = LoggerFactory.getLogger(ServerMonitorProvider.class);

    /** CPU 双采样间隔（毫秒）：getCpuLoad 首次调用返回 -1，需两次采样取真实值 */
    private static final long CPU_SAMPLE_INTERVAL_MS = 300L;

    @Override
    public String type() {
        return "server";
    }

    @Override
    public String title() {
        return "服务器";
    }

    @Override
    public ServerMonitorVo collect() {
        ServerMonitorVo vo = new ServerMonitorVo();
        com.sun.management.OperatingSystemMXBean osBean =
                (com.sun.management.OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
        vo.setCpu(collectCpu(osBean));
        vo.setMemory(collectMemory(osBean));
        vo.setDisks(collectDisks());
        vo.setSystem(collectSystem());
        vo.setJvm(collectJvm());
        return vo;
    }

    private ServerMonitorVo.CpuInfo collectCpu(com.sun.management.OperatingSystemMXBean osBean) {
        ServerMonitorVo.CpuInfo cpu = new ServerMonitorVo.CpuInfo();
        cpu.setCores(Runtime.getRuntime().availableProcessors());
        cpu.setUsagePercent(safeSampleCpuLoad(osBean));
        // Windows 下系统负载不可用（返回 -1），置 null 由前端展示占位
        double systemLoad = osBean.getSystemLoadAverage();
        cpu.setSystemLoad(systemLoad >= 0 ? round(systemLoad) : null);
        return cpu;
    }

    /**
     * CPU 使用率双采样：getCpuLoad 首次调用返回 -1，间隔采样一次取真实值；
     * 采样失败或返回 -1 时返回 null（前端显示占位）。
     */
    private Double safeSampleCpuLoad(com.sun.management.OperatingSystemMXBean osBean) {
        try {
            osBean.getCpuLoad();
            Thread.sleep(CPU_SAMPLE_INTERVAL_MS);
            double load = osBean.getCpuLoad();
            return load >= 0 ? round(load * 100) : null;
        } catch (Exception e) {
            log.warn("CPU 使用率采样失败，按不可用处理 >>> {}", e.getMessage());
            return null;
        }
    }

    private ServerMonitorVo.MemoryInfo collectMemory(com.sun.management.OperatingSystemMXBean osBean) {
        ServerMonitorVo.MemoryInfo memory = new ServerMonitorVo.MemoryInfo();
        long total = osBean.getTotalMemorySize();
        long available = osBean.getFreeMemorySize();
        long used = Math.max(total - available, 0);
        memory.setTotal(total);
        memory.setAvailable(available);
        memory.setUsed(used);
        memory.setUsagePercent(total > 0 ? round(used * 100.0 / total) : null);
        return memory;
    }

    private List<ServerMonitorVo.DiskInfo> collectDisks() {
        List<ServerMonitorVo.DiskInfo> disks = new ArrayList<>();
        try {
            File[] roots = File.listRoots();
            if (roots == null) {
                return disks;
            }
            for (File root : roots) {
                ServerMonitorVo.DiskInfo disk = new ServerMonitorVo.DiskInfo();
                disk.setName(root.getPath());
                long total = root.getTotalSpace();
                long available = root.getUsableSpace();
                long used = Math.max(total - available, 0);
                disk.setTotal(total);
                disk.setAvailable(available);
                disk.setUsed(used);
                disk.setUsagePercent(total > 0 ? round(used * 100.0 / total) : null);
                disks.add(disk);
            }
        } catch (Exception e) {
            log.warn("磁盘信息采集失败 >>> {}", e.getMessage());
        }
        return disks;
    }

    private ServerMonitorVo.SystemInfo collectSystem() {
        ServerMonitorVo.SystemInfo system = new ServerMonitorVo.SystemInfo();
        system.setOsName(System.getProperty("os.name"));
        system.setOsVersion(System.getProperty("os.version"));
        system.setOsArch(System.getProperty("os.arch"));
        try {
            system.setHostName(InetAddress.getLocalHost().getHostName());
        } catch (Exception e) {
            system.setHostName(null);
        }
        system.setCurrentTime(new Date());
        return system;
    }

    private ServerMonitorVo.JvmInfo collectJvm() {
        ServerMonitorVo.JvmInfo jvm = new ServerMonitorVo.JvmInfo();
        RuntimeMXBean runtimeBean = ManagementFactory.getRuntimeMXBean();
        jvm.setVmName(runtimeBean.getVmName());
        jvm.setVmVersion(runtimeBean.getVmVersion());
        jvm.setJavaVersion(System.getProperty("java.version"));
        jvm.setJavaHome(System.getProperty("java.home"));
        jvm.setUserDir(System.getProperty("user.dir"));
        jvm.setStartTime(runtimeBean.getStartTime());
        jvm.setUptimeSeconds(runtimeBean.getUptime() / 1000);

        MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
        MemoryUsage heap = memoryBean.getHeapMemoryUsage();
        jvm.setHeapUsed(heap.getUsed());
        jvm.setHeapCommitted(heap.getCommitted());
        jvm.setHeapMax(heap.getMax() >= 0 ? heap.getMax() : null);
        jvm.setNonHeapUsed(memoryBean.getNonHeapMemoryUsage().getUsed());

        ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();
        jvm.setThreadCount(threadBean.getThreadCount());
        jvm.setPeakThreadCount(threadBean.getPeakThreadCount());

        ClassLoadingMXBean classBean = ManagementFactory.getClassLoadingMXBean();
        jvm.setLoadedClassCount(classBean.getLoadedClassCount());

        List<ServerMonitorVo.GcInfo> gcs = new ArrayList<>();
        for (GarbageCollectorMXBean gcBean : ManagementFactory.getGarbageCollectorMXBeans()) {
            ServerMonitorVo.GcInfo gc = new ServerMonitorVo.GcInfo();
            gc.setName(gcBean.getName());
            gc.setCount(gcBean.getCollectionCount());
            gc.setTimeMs(gcBean.getCollectionTime());
            gcs.add(gc);
        }
        jvm.setGcs(gcs);
        return jvm;
    }

    /** 保留两位小数 */
    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
