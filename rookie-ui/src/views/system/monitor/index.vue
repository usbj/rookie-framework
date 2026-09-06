/**
 * 文件作用：
 * 承接系统监控目录下的服务监控页面，
 * 以可插拔监控源（MonitorProvider）方式展示各监控源实时数据：
 * - server：服务器 CPU / 内存 / 磁盘 / 系统 / JVM（ECharts 图表 + 文字明细）；
 * - 未知类型监控源：占位卡片兜底（后端新增中间件监控后，在前端登记渲染组件即可扩展）。
 * 本页支持手动刷新与每 30 秒自动刷新（默认开启）。
 */
<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { ElButton } from 'element-plus'
import { getMonitorItemsApi } from '@/api/system/monitor'
import BaseCard from '@/components/BaseCard.vue'
import { formatDateTime, formatDuration, formatFileSize } from '@/utils/format'
import type { MonitorItemRecord, ServerMonitorRecord } from '@/types/api/system/monitor'
import MonitorGauge from './components/MonitorGauge.vue'
import MonitorRing from './components/MonitorRing.vue'

const monitorItems = ref<MonitorItemRecord[]>([])
const loading = ref(false)
const lastRefreshTime = ref<string>('--')
/** 自动刷新开关（默认开启，每 30 秒拉取一次） */
const autoRefreshEnabled = ref(true)

/** 自动刷新定时器句柄（页面卸载时清理） */
let refreshTimer: number | undefined

/** 服务器监控项（type=server，由后端 ServerMonitorProvider 提供） */
const serverItem = computed(() => monitorItems.value.find((item) => item.type === 'server'))
const serverData = computed<ServerMonitorRecord | null>(
  () => (serverItem.value?.data as ServerMonitorRecord | undefined) ?? null,
)

/** 尚未登记展示组件的监控源（预留扩展：后端新增 Provider 后前端补映射即可） */
const unknownItems = computed(() => monitorItems.value.filter((item) => item.type !== 'server'))

const cpu = computed(() => serverData.value?.cpu)
const memory = computed(() => serverData.value?.memory)
const disks = computed(() => serverData.value?.disks ?? [])
const system = computed(() => serverData.value?.system)
const jvm = computed(() => serverData.value?.jvm)

/**
 * 方法效果：
 * 拉取全部监控源数据，并更新上次刷新时间。
 * 参数：
 * - 无。
 * 返回值：
 * - 无返回值；副作用是刷新页面监控数据。
 */
const fetchMonitor = async () => {
  loading.value = true
  try {
    const result = await getMonitorItemsApi()
    monitorItems.value = result.data ?? []
    lastRefreshTime.value = formatDateTime(new Date())
  } finally {
    loading.value = false
  }
}

/**
 * 方法效果：
 * 手动刷新监控数据。
 * 参数：
 * - 无。
 * 返回值：
 * - 无返回值；副作用是重新拉取监控数据。
 */
const handleRefresh = async () => {
  await fetchMonitor()
}

/**
 * 方法效果：
 * 切换自动刷新开关：开启时立即刷新并启动定时器，关闭时清理定时器。
 * 参数：
 * - 无。
 * 返回值：
 * - 无返回值；副作用是更新开关状态与定时器。
 */
const toggleAutoRefresh = async () => {
  autoRefreshEnabled.value = !autoRefreshEnabled.value
  if (autoRefreshEnabled.value) {
    await fetchMonitor()
    startAutoRefresh()
  } else {
    stopAutoRefresh()
  }
}

const startAutoRefresh = () => {
  stopAutoRefresh()
  refreshTimer = window.setInterval(() => {
    fetchMonitor().catch(() => undefined)
  }, 30_000)
}

const stopAutoRefresh = () => {
  if (refreshTimer !== undefined) {
    window.clearInterval(refreshTimer)
    refreshTimer = undefined
  }
}

onMounted(async () => {
  await fetchMonitor()
  if (autoRefreshEnabled.value) {
    startAutoRefresh()
  }
})

onUnmounted(() => {
  stopAutoRefresh()
})
</script>

<template>
  <section class="monitor-view">
    <header class="monitor-view__toolbar">
      <div class="monitor-view__toolbar-copy">
        <strong>服务器实时状态</strong>
        <span>上次刷新：{{ lastRefreshTime }}</span>
      </div>
      <div class="monitor-view__toolbar-actions">
        <button
          class="monitor-view__auto-btn"
          type="button"
          :class="{ 'is-on': autoRefreshEnabled }"
          @click="toggleAutoRefresh"
        >
          自动刷新{{ autoRefreshEnabled ? '：开' : '：关' }}
        </button>
        <ElButton :loading="loading" @click="handleRefresh">刷新</ElButton>
      </div>
    </header>

    <!-- 服务器监控（type=server） -->
    <template v-if="serverItem">
      <div class="monitor-view__grid">
        <!-- CPU -->
        <BaseCard title="CPU" description="处理器使用情况">
          <MonitorGauge :percent="cpu?.usagePercent ?? 0" label="CPU 使用率" />
          <dl class="monitor-view__desc-list">
            <div class="monitor-view__desc-row">
              <dt>核心数</dt>
              <dd>{{ cpu?.cores ?? '--' }}</dd>
            </div>
            <div class="monitor-view__desc-row">
              <dt>系统负载</dt>
              <dd>{{ cpu?.systemLoad ?? '--' }}</dd>
            </div>
          </dl>
        </BaseCard>

        <!-- 内存 -->
        <BaseCard title="内存" description="物理内存使用情况">
          <MonitorGauge :percent="memory?.usagePercent ?? 0" label="内存使用率" />
          <dl class="monitor-view__desc-list">
            <div class="monitor-view__desc-row">
              <dt>总量</dt>
              <dd>{{ formatFileSize(memory?.total) }}</dd>
            </div>
            <div class="monitor-view__desc-row">
              <dt>已用</dt>
              <dd>{{ formatFileSize(memory?.used) }}</dd>
            </div>
            <div class="monitor-view__desc-row">
              <dt>可用</dt>
              <dd>{{ formatFileSize(memory?.available) }}</dd>
            </div>
          </dl>
        </BaseCard>

        <!-- 磁盘 -->
        <BaseCard title="磁盘" description="磁盘分区使用情况">
          <div v-if="disks.length === 0" class="monitor-view__empty">暂无磁盘信息</div>
          <div v-for="disk in disks" :key="disk.name" class="monitor-view__disk">
            <div class="monitor-view__disk-chart">
              <MonitorRing
                :used="disk.used"
                :total="disk.total"
                :label="disk.name || '磁盘'"
              />
              <p class="monitor-view__disk-detail">
                已用 {{ formatFileSize(disk.used) }} · 可用 {{ formatFileSize(disk.available) }} ·
                共 {{ formatFileSize(disk.total) }}
              </p>
            </div>
          </div>
        </BaseCard>

        <!-- 系统 -->
        <BaseCard title="系统" description="运行系统基本信息">
          <dl class="monitor-view__desc-list">
            <div class="monitor-view__desc-row">
              <dt>操作系统</dt>
              <dd>{{ system?.osName ?? '--' }}</dd>
            </div>
            <div class="monitor-view__desc-row">
              <dt>系统版本</dt>
              <dd>{{ system?.osVersion ?? '--' }}</dd>
            </div>
            <div class="monitor-view__desc-row">
              <dt>系统架构</dt>
              <dd>{{ system?.osArch ?? '--' }}</dd>
            </div>
            <div class="monitor-view__desc-row">
              <dt>主机名</dt>
              <dd>{{ system?.hostName ?? '--' }}</dd>
            </div>
            <div class="monitor-view__desc-row">
              <dt>当前时间</dt>
              <dd>{{ formatDateTime(system?.currentTime) }}</dd>
            </div>
          </dl>
        </BaseCard>

        <!-- JVM（跨两列） -->
        <BaseCard class="monitor-view__card-wide" title="JVM" description="Java 虚拟机运行数据">
          <div class="monitor-view__jvm">
            <dl class="monitor-view__desc-list">
              <div class="monitor-view__desc-row">
                <dt>JVM 名称</dt>
                <dd>{{ jvm?.vmName ?? '--' }}</dd>
              </div>
              <div class="monitor-view__desc-row">
                <dt>JVM 版本</dt>
                <dd>{{ jvm?.vmVersion ?? '--' }}</dd>
              </div>
              <div class="monitor-view__desc-row">
                <dt>Java 版本</dt>
                <dd>{{ jvm?.javaVersion ?? '--' }}</dd>
              </div>
              <div class="monitor-view__desc-row">
                <dt>启动时间</dt>
                <dd>{{ jvm?.startTime != null ? formatDateTime(new Date(jvm.startTime)) : '--' }}</dd>
              </div>
              <div class="monitor-view__desc-row">
                <dt>运行时长</dt>
                <dd>{{ formatDuration(jvm?.uptimeSeconds) }}</dd>
              </div>
              <div class="monitor-view__desc-row">
                <dt>Java Home</dt>
                <dd class="monitor-view__desc-ellipsis" :title="jvm?.javaHome">{{ jvm?.javaHome ?? '--' }}</dd>
              </div>
              <div class="monitor-view__desc-row">
                <dt>启动路径</dt>
                <dd class="monitor-view__desc-ellipsis" :title="jvm?.userDir">{{ jvm?.userDir ?? '--' }}</dd>
              </div>
              <div class="monitor-view__desc-row">
                <dt>线程 / 峰值</dt>
                <dd>{{ jvm?.threadCount ?? '--' }} / {{ jvm?.peakThreadCount ?? '--' }}</dd>
              </div>
              <div class="monitor-view__desc-row">
                <dt>已加载类</dt>
                <dd>{{ jvm?.loadedClassCount ?? '--' }}</dd>
              </div>
            </dl>

            <div class="monitor-view__jvm-charts">
              <div class="monitor-view__jvm-chart">
                <MonitorRing
                  :used="jvm?.heapUsed"
                  :total="jvm?.heapMax ?? jvm?.heapCommitted"
                  label="堆内存"
                />
                <p class="monitor-view__disk-detail">
                  已用 {{ formatFileSize(jvm?.heapUsed) }} · 提交 {{ formatFileSize(jvm?.heapCommitted) }} ·
                  最大 {{ formatFileSize(jvm?.heapMax) }}
                </p>
              </div>
              <div v-if="jvm?.gcs && jvm.gcs.length > 0" class="monitor-view__jvm-chart">
                <strong class="monitor-view__jvm-subtitle">GC 统计</strong>
                <ul class="monitor-view__gc-list">
                  <li v-for="gc in jvm.gcs" :key="gc.name">
                    <span>{{ gc.name }}</span>
                    <em>{{ gc.count ?? '--' }} 次 · {{ gc.timeMs ?? '--' }} ms</em>
                  </li>
                </ul>
              </div>
            </div>
          </div>
        </BaseCard>
      </div>
    </template>

    <!-- 未登记展示组件的监控源：占位兜底（预留中间件扩展） -->
    <div v-if="unknownItems.length > 0" class="monitor-view__grid">
      <BaseCard v-for="item in unknownItems" :key="item.type" :title="item.title || item.type">
        <p class="monitor-view__empty">
          监控源「{{ item.type }}」数据已接入，但前端暂未登记对应展示组件，请在前端补充 type 映射。
        </p>
      </BaseCard>
    </div>
  </section>
</template>

<style scoped>
.monitor-view {
  display: grid;
  gap: 18px;
}

.monitor-view__toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
  padding: 16px 20px;
  border: 1px solid var(--rookie-border);
  border-radius: var(--rookie-radius-lg);
  background: var(--rookie-card-bg);
  box-shadow: var(--rookie-shadow);
}

.monitor-view__toolbar-copy {
  display: flex;
  align-items: baseline;
  gap: 12px;
}

.monitor-view__toolbar-copy strong {
  color: var(--rookie-text);
  font-size: var(--rookie-font-size-md);
}

.monitor-view__toolbar-copy span {
  color: var(--rookie-text-secondary);
  font-size: var(--rookie-font-size-sm);
}

.monitor-view__toolbar-actions {
  display: flex;
  align-items: center;
  gap: 10px;
}

.monitor-view__auto-btn {
  padding: 7px 14px;
  border: 1px solid var(--rookie-border);
  border-radius: var(--rookie-radius-md);
  background: var(--rookie-surface-weak);
  color: var(--rookie-text-secondary);
  font-size: var(--rookie-font-size-sm);
  cursor: pointer;
  transition:
    border-color 0.2s ease,
    color 0.2s ease;
}

.monitor-view__auto-btn.is-on {
  border-color: var(--rookie-primary-border);
  color: var(--rookie-primary-strong);
}

.monitor-view__auto-btn:hover {
  border-color: var(--rookie-primary-border);
}

.monitor-view__grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 18px;
}

.monitor-view__card-wide {
  grid-column: 1 / -1;
}

.monitor-view__desc-list {
  display: grid;
  gap: 8px;
  margin: 14px 0 0;
}

.monitor-view__desc-row {
  display: flex;
  gap: 10px;
  min-width: 0;
}

.monitor-view__desc-row dt {
  width: 84px;
  flex: none;
  color: var(--rookie-text-tertiary);
  font-size: var(--rookie-font-size-sm);
}

.monitor-view__desc-row dd {
  margin: 0;
  color: var(--rookie-text);
  font-size: var(--rookie-font-size-sm);
  word-break: break-all;
}

.monitor-view__desc-ellipsis {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.monitor-view__empty {
  margin: 0;
  color: var(--rookie-text-tertiary);
  font-size: var(--rookie-font-size-sm);
  line-height: 1.6;
}

.monitor-view__disk {
  display: grid;
  gap: 6px;
}

.monitor-view__disk + .monitor-view__disk {
  margin-top: 10px;
  padding-top: 10px;
  border-top: 1px dashed var(--rookie-border);
}

.monitor-view__disk-chart {
  display: grid;
  gap: 2px;
}

.monitor-view__disk-detail {
  margin: 0;
  color: var(--rookie-text-tertiary);
  font-size: var(--rookie-font-size-xs);
  text-align: center;
}

.monitor-view__jvm {
  display: grid;
  grid-template-columns: minmax(0, 1.2fr) minmax(0, 1fr);
  gap: 24px;
}

.monitor-view__jvm-charts {
  display: grid;
  gap: 14px;
  align-content: start;
}

.monitor-view__jvm-chart {
  display: grid;
  gap: 6px;
}

.monitor-view__jvm-subtitle {
  color: var(--rookie-text);
  font-size: var(--rookie-font-size-sm);
}

.monitor-view__gc-list {
  display: grid;
  gap: 6px;
  margin: 0;
  padding: 0;
  list-style: none;
}

.monitor-view__gc-list li {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  padding: 8px 10px;
  border: 1px solid var(--rookie-border);
  border-radius: var(--rookie-radius-md);
  background: var(--rookie-surface-weak);
}

.monitor-view__gc-list span {
  color: var(--rookie-text);
  font-size: var(--rookie-font-size-sm);
}

.monitor-view__gc-list em {
  color: var(--rookie-text-secondary);
  font-size: var(--rookie-font-size-xs);
  font-style: normal;
}

@media (max-width: 1024px) {
  .monitor-view__grid,
  .monitor-view__jvm {
    grid-template-columns: 1fr;
  }
}
</style>
