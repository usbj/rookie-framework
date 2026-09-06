/**
 * 文件作用：
 * 用户头像公共组件：有头像地址且加载成功时显示图片，
 * 无头像或图片加载失败（404/网络异常）时回退显示展示名首字母大写。
 * 尺寸由外层容器控制（组件铺满容器，圆形容器）。
 */
<script setup lang="ts">
import { computed, ref, watch } from 'vue'

const props = defineProps<{
  /** 展示名（取首字母大写作为兜底文字） */
  name: string
  /** 头像地址（如 blob objectURL），为空或加载失败时显示字母 */
  src?: string
}>()

/** 图片加载失败标志：src 变化时重置，@error 时置位 */
const loadFailed = ref(false)

watch(
  () => props.src,
  () => {
    loadFailed.value = false
  },
)

const initial = computed(() => (props.name || 'U').slice(0, 1).toUpperCase())
</script>

<template>
  <span class="user-avatar">
    <img
      v-if="props.src && !loadFailed"
      class="user-avatar__img"
      :src="props.src"
      alt="用户头像"
      @error="loadFailed = true"
    />
    <template v-else>{{ initial }}</template>
  </span>
</template>

<style scoped>
.user-avatar {
  width: 100%;
  height: 100%;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 999px;
  background: var(--rookie-avatar-bg);
  color: var(--rookie-primary-strong);
  font-weight: 700;
  overflow: hidden;
}

.user-avatar__img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}
</style>
