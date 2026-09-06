/**
 * 文件作用：
 * 定义服务监控模块的接口类型，与后端 MonitorItemVo / ServerMonitorVo 结构保持一致。
 */

/**
 * 监控数据项（聚合接口返回单元），与后端 MonitorItemVo 对齐。
 * type 标识监控源类型（server 等），data 按 type 对应解析（服务器数据见 ServerMonitorRecord）。
 */
export interface MonitorItemRecord {
  /** 监控源类型（全局唯一，前端据此选择渲染组件，未知类型有占位兜底） */
  type: string
  /** 监控源展示名称（如「服务器」） */
  title: string
  /** 该监控源的实时数据（结构由各提供者自定） */
  data: unknown
}

/**
 * 服务器监控信息，与后端 ServerMonitorVo 对齐。
 */
export interface ServerMonitorRecord {
  cpu?: CpuMonitorInfo
  memory?: MemoryMonitorInfo
  disks?: DiskMonitorInfo[]
  system?: SystemMonitorInfo
  jvm?: JvmMonitorInfo
}

/** CPU 信息 */
export interface CpuMonitorInfo {
  /** 可用处理器核心数 */
  cores?: number
  /** CPU 使用率（%，不可用为 undefined） */
  usagePercent?: number
  /** 系统 1 分钟平均负载（Windows 不可用为 undefined） */
  systemLoad?: number
}

/** 物理内存信息（字节） */
export interface MemoryMonitorInfo {
  /** 内存总量 */
  total?: number
  /** 已用内存 */
  used?: number
  /** 可用内存 */
  available?: number
  /** 使用率（%） */
  usagePercent?: number
}

/** 单个磁盘分区信息（字节） */
export interface DiskMonitorInfo {
  /** 盘符 / 挂载点 */
  name?: string
  /** 分区总量 */
  total?: number
  /** 已用空间 */
  used?: number
  /** 可用空间 */
  available?: number
  /** 使用率（%） */
  usagePercent?: number
}

/** 运行系统基本信息 */
export interface SystemMonitorInfo {
  /** 操作系统名称 */
  osName?: string
  /** 操作系统版本 */
  osVersion?: string
  /** 系统架构 */
  osArch?: string
  /** 主机名 */
  hostName?: string
  /** 服务器当前时间 */
  currentTime?: string
}

/** JVM 数据 */
export interface JvmMonitorInfo {
  /** JVM 名称 */
  vmName?: string
  /** JVM 版本 */
  vmVersion?: string
  /** Java 版本 */
  javaVersion?: string
  /** Java 安装路径 */
  javaHome?: string
  /** 应用启动路径 */
  userDir?: string
  /** JVM 启动时间（毫秒时间戳） */
  startTime?: number
  /** JVM 已运行时长（秒） */
  uptimeSeconds?: number
  /** 堆内存已用（字节） */
  heapUsed?: number
  /** 堆内存已提交（字节） */
  heapCommitted?: number
  /** 堆内存最大（字节） */
  heapMax?: number
  /** 非堆内存已用（字节） */
  nonHeapUsed?: number
  /** 当前线程数 */
  threadCount?: number
  /** 峰值线程数 */
  peakThreadCount?: number
  /** 已加载类数量 */
  loadedClassCount?: number
  /** GC 统计 */
  gcs?: GcMonitorInfo[]
}

/** 单个 GC 收集器统计 */
export interface GcMonitorInfo {
  /** 收集器名称 */
  name?: string
  /** 收集次数 */
  count?: number
  /** 累计耗时（毫秒） */
  timeMs?: number
}
