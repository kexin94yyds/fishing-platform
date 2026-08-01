<script setup lang="ts">
import { computed } from 'vue'
import { statusLabel, statusType } from '@/utils/format'

const props = defineProps<{ status?: string }>()

const tone = computed(() => {
  const type = statusType(props.status)
  if (type === 'success') return 'positive'
  if (type === 'warning') return 'attention'
  if (type === 'danger') return 'critical'
  if (type === 'primary') return 'active'
  return 'neutral'
})
</script>

<template>
  <span class="status-stamp" :class="`status-stamp--${tone}`">
    <i aria-hidden="true" />
    {{ statusLabel(status) }}
  </span>
</template>

<style scoped>
.status-stamp {
  display: inline-flex;
  min-height: 24px;
  align-items: center;
  gap: 6px;
  padding: 3px 7px;
  border: 1px solid var(--stamp-border);
  border-radius: 6px;
  color: var(--stamp-text);
  background: var(--stamp-bg);
  font-size: 10px;
  font-weight: 700;
  line-height: 1;
  white-space: nowrap;
}

.status-stamp i {
  width: 5px;
  height: 5px;
  flex: 0 0 5px;
  border-radius: 50%;
  background: currentColor;
}

.status-stamp--positive {
  --stamp-text: var(--status-positive-text);
  --stamp-bg: var(--status-positive-bg);
  --stamp-border: var(--status-positive-border);
}

.status-stamp--attention {
  --stamp-text: var(--status-attention-text);
  --stamp-bg: var(--status-attention-bg);
  --stamp-border: var(--status-attention-border);
}

.status-stamp--critical {
  --stamp-text: var(--status-critical-text);
  --stamp-bg: var(--status-critical-bg);
  --stamp-border: var(--status-critical-border);
}

.status-stamp--active {
  --stamp-text: var(--lake-900);
  --stamp-bg: #e1ece6;
  --stamp-border: #bfd6ca;
}

.status-stamp--neutral {
  --stamp-text: var(--status-neutral-text);
  --stamp-bg: var(--status-neutral-bg);
  --stamp-border: var(--status-neutral-border);
}
</style>
