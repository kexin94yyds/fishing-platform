<script setup lang="ts">
import { RefreshRight, WarningFilled } from '@element-plus/icons-vue'
import LakeEmptyState from '@/components/LakeEmptyState.vue'

withDefaults(
  defineProps<{
    loading?: boolean
    error?: string
    empty?: boolean
    emptyTitle?: string
    emptyDescription?: string
    skeletonRows?: number
  }>(),
  {
    loading: false,
    error: '',
    empty: false,
    emptyTitle: '暂无数据',
    emptyDescription: '当前筛选条件下没有可展示的记录',
    skeletonRows: 6,
  },
)

defineEmits<{
  retry: []
}>()
</script>

<template>
  <div
    v-if="loading"
    class="resource-skeleton"
    aria-label="数据加载中"
    aria-live="polite"
    aria-busy="true"
  >
    <span class="resource-skeleton__lead" />
    <div class="resource-skeleton__tiles" aria-hidden="true">
      <span v-for="index in 3" :key="index" />
    </div>
    <div class="resource-skeleton__lines" aria-hidden="true">
      <span v-for="index in Math.min(skeletonRows, 6)" :key="index" />
    </div>
  </div>
  <section
    v-else-if="error"
    class="resource-error"
    role="alert"
    aria-live="assertive"
  >
    <div class="resource-error__mark" aria-hidden="true">
      <el-icon><WarningFilled /></el-icon>
    </div>
    <strong>数据加载失败</strong>
    <p>{{ error }}</p>
    <el-button :icon="RefreshRight" @click="$emit('retry')">重新加载</el-button>
  </section>
  <LakeEmptyState
    v-else-if="empty"
    :title="emptyTitle"
    :description="emptyDescription"
  >
    <slot name="empty-action" />
  </LakeEmptyState>
  <slot v-else />
</template>

<style scoped>
.resource-skeleton {
  min-height: 270px;
  padding: 14px 4px;
}

.resource-skeleton__lead,
.resource-skeleton__tiles span,
.resource-skeleton__lines span {
  display: block;
  border-radius: 10px;
  background: var(--paper-300);
  animation: skeleton-pulse 1.35s ease-in-out infinite;
}

.resource-skeleton__lead {
  width: min(340px, 46%);
  height: 30px;
  margin-bottom: 14px;
}

.resource-skeleton__tiles {
  display: grid;
  grid-template-columns: 1.15fr 1fr 1fr;
  gap: 12px;
  margin-bottom: 14px;
}

.resource-skeleton__tiles span {
  min-height: 92px;
}

.resource-skeleton__lines {
  display: grid;
  gap: 9px;
}

.resource-skeleton__lines span {
  height: 14px;
  border-radius: 5px;
}

.resource-skeleton__lines span:nth-child(2n) {
  width: 86%;
}

.resource-skeleton__lines span:nth-child(3n) {
  width: 68%;
}

.resource-error {
  display: grid;
  min-height: 270px;
  place-items: center;
  align-content: center;
  padding: 28px 18px;
  text-align: center;
}

.resource-error__mark {
  display: grid;
  width: 44px;
  height: 44px;
  margin-bottom: 13px;
  place-items: center;
  border: 1px solid var(--clay-100);
  border-radius: 12px;
  color: var(--clay-700);
  background: var(--clay-50);
  font-size: 20px;
}

.resource-error > strong {
  color: var(--ink-900);
  font-size: 14px;
}

.resource-error > p {
  max-width: 46ch;
  margin: 8px 0 15px;
  color: var(--ink-500);
  font-size: 11px;
  line-height: 1.65;
}

@keyframes skeleton-pulse {
  0%,
  100% {
    opacity: 0.48;
  }

  50% {
    opacity: 0.84;
  }
}

@media (max-width: 640px) {
  .resource-skeleton__tiles {
    grid-template-columns: 1fr;
  }

  .resource-skeleton__tiles span:not(:first-child) {
    display: none;
  }
}

@media (prefers-reduced-motion: reduce) {
  .resource-skeleton__lead,
  .resource-skeleton__tiles span,
  .resource-skeleton__lines span {
    animation: none;
  }
}
</style>
