/**
 * 文件作用：
 * 服务监控页专用的 ECharts 环形图组件：展示「已用 / 总量」占比（磁盘分区、堆内存）。
 * 中心文字显示使用率，颜色与文字色从主题 CSS 变量读取（刷新时跟随明暗主题）。
 */
<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import * as echarts from 'echarts'

const props = withDefaults(
  defineProps<{
    /** 已用量（字节，与 total 同单位） */
    used?: number
    /** 总量（字节） */
    total?: number
    /** 环形图标签 */
    label?: string
  }>(),
  {
    used: 0,
    total: 0,
    label: '',
  },
)

const chartEl = ref<HTMLDivElement>()
let chart: echarts.ECharts | null = null

const percent = computed(() => {
  const total = Number(props.total)
  if (!Number.isFinite(total) || total <= 0) {
    return 0
  }
  const used = Math.min(Math.max(Number(props.used) || 0, 0), total)
  return (used / total) * 100
})

/** 读取主题 CSS 变量（图表配色跟随明暗主题，刷新时重新读取） */
const readThemeColor = (name: string, fallback: string) => {
  const value = getComputedStyle(document.documentElement).getPropertyValue(name).trim()
  return value || fallback
}

const render = () => {
  if (!chart) {
    return
  }
  const primary = readThemeColor('--rookie-primary', '#0f766e')
  const danger = readThemeColor('--rookie-danger', '#d14343')
  const textColor = readThemeColor('--rookie-text-secondary', '#5b6780')
  const trackColor = readThemeColor('--rookie-surface-weak', '#eef2f7')
  const value = percent.value

  chart.setOption({
    series: [
      {
        type: 'pie',
        radius: ['68%', '86%'],
        avoidLabelOverlap: false,
        silent: true,
        label: {
          show: true,
          position: 'center',
          formatter: [`{name|${props.label}}`, `{value|${value.toFixed(1)}%}`].join('\n'),
          rich: {
            name: { color: textColor, fontSize: 12, lineHeight: 20 },
            value: { color: textColor, fontSize: 18, fontWeight: 700, lineHeight: 24 },
          },
        },
        data: [
          {
            value,
            itemStyle: { color: value >= 90 ? danger : primary },
          },
          { value: Math.max(100 - value, 0), itemStyle: { color: trackColor } },
        ],
      },
    ],
  })
}

const handleResize = () => {
  chart?.resize()
}

onMounted(() => {
  chart = echarts.init(chartEl.value as HTMLDivElement)
  render()
  window.addEventListener('resize', handleResize)
})

watch(() => [props.used, props.total, props.label], render, { flush: 'post' })

onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize)
  chart?.dispose()
  chart = null
})
</script>

<template>
  <div ref="chartEl" class="monitor-ring"></div>
</template>

<style scoped>
.monitor-ring {
  width: 100%;
  height: 150px;
}
</style>
