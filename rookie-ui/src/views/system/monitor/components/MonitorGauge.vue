/**
 * 文件作用：
 * 服务监控页专用的 ECharts 仪表盘组件：展示百分比使用率（CPU / 内存）。
 * 颜色与文字色从主题 CSS 变量读取（刷新时跟随明暗主题），背景透明跟随卡片。
 */
<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import * as echarts from 'echarts'

const props = withDefaults(
  defineProps<{
    /** 使用率（0-100） */
    percent?: number
    /** 仪表盘标签（如 CPU / 内存） */
    label?: string
  }>(),
  {
    percent: 0,
    label: '',
  },
)

const chartEl = ref<HTMLDivElement>()
let chart: echarts.ECharts | null = null

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
  const percent = Math.min(Math.max(Number(props.percent) || 0, 0), 100)

  chart.setOption({
    series: [
      {
        type: 'gauge',
        min: 0,
        max: 100,
        startAngle: 210,
        endAngle: -30,
        progress: {
          show: true,
          width: 12,
          itemStyle: { color: percent >= 90 ? danger : primary },
        },
        axisLine: { lineStyle: { width: 12, color: [[1, trackColor]] } },
        axisTick: { show: false },
        splitLine: { show: false },
        axisLabel: { show: false },
        pointer: { show: false },
        anchor: { show: false },
        title: {
          show: true,
          offsetCenter: [0, '42%'],
          color: textColor,
          fontSize: 12,
        },
        detail: {
          valueAnimation: true,
          offsetCenter: [0, '0%'],
          formatter: '{value}%',
          color: textColor,
          fontSize: 22,
          fontWeight: 700,
        },
        data: [{ value: percent, name: props.label }],
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

watch(() => [props.percent, props.label], render, { flush: 'post' })

onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize)
  chart?.dispose()
  chart = null
})
</script>

<template>
  <div ref="chartEl" class="monitor-gauge"></div>
</template>

<style scoped>
.monitor-gauge {
  width: 100%;
  height: 180px;
}
</style>
