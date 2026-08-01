<script setup lang="ts">
import {
  computed,
  nextTick,
  onBeforeUnmount,
  onMounted,
  ref,
  watch,
  type Component,
} from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Search } from '@element-plus/icons-vue'

interface NavigationItem {
  path: string
  label: string
  hint: string
  keywords: string
  icon: Component
}

const props = defineProps<{
  modelValue: boolean
  items: NavigationItem[]
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
}>()

const route = useRoute()
const router = useRouter()
const query = ref('')
const activeIndex = ref(0)
const inputRef = ref<HTMLInputElement | null>(null)
const panelRef = ref<HTMLElement | null>(null)
let returnFocus: HTMLElement | null = null

const filteredItems = computed(() => {
  const term = query.value.trim().toLocaleLowerCase('zh-CN')
  if (!term) return props.items

  return props.items.filter((item) =>
    `${item.label} ${item.hint} ${item.keywords}`.toLocaleLowerCase('zh-CN').includes(term),
  )
})

const activeOptionId = computed(() =>
  filteredItems.value.length ? `quick-navigation-option-${activeIndex.value}` : undefined,
)

function open() {
  if (!props.modelValue) emit('update:modelValue', true)
}

function close(restoreFocus = true) {
  emit('update:modelValue', false)
  if (restoreFocus) {
    const target = returnFocus
    void nextTick(() => target?.focus())
  }
}

function moveSelection(offset: number) {
  const count = filteredItems.value.length
  if (!count) return
  activeIndex.value = (activeIndex.value + offset + count) % count
  void nextTick(() => {
    const optionId = activeOptionId.value
    if (optionId) document.getElementById(optionId)?.scrollIntoView({ block: 'nearest' })
  })
}

async function navigate(item: NavigationItem) {
  await router.push(item.path)
  close(false)
  await nextTick()
  document.querySelector<HTMLElement>('#main-content')?.focus()
}

function handleSearchKeydown(event: KeyboardEvent) {
  if (event.key === 'ArrowDown') {
    event.preventDefault()
    moveSelection(1)
  } else if (event.key === 'ArrowUp') {
    event.preventDefault()
    moveSelection(-1)
  } else if (event.key === 'Enter') {
    const item = filteredItems.value[activeIndex.value]
    if (item) {
      event.preventDefault()
      void navigate(item)
    }
  }
}

function handlePanelKeydown(event: KeyboardEvent) {
  if (event.key === 'Escape') {
    event.preventDefault()
    close()
    return
  }

  if (event.key !== 'Tab' || !panelRef.value) return
  const focusable = Array.from(
    panelRef.value.querySelectorAll<HTMLElement>(
      'input:not([disabled]), button:not([disabled]), [href], [tabindex]:not([tabindex="-1"])',
    ),
  )
  const first = focusable[0]
  const last = focusable.at(-1)
  if (!first || !last) return

  if (event.shiftKey && document.activeElement === first) {
    event.preventDefault()
    last.focus()
  } else if (!event.shiftKey && document.activeElement === last) {
    event.preventDefault()
    first.focus()
  }
}

function handleGlobalKeydown(event: KeyboardEvent) {
  if ((event.metaKey || event.ctrlKey) && !event.altKey && event.key.toLowerCase() === 'k') {
    event.preventDefault()
    if (event.repeat) return
    if (props.modelValue) close()
    else open()
  }
}

watch(
  () => props.modelValue,
  (isOpen) => {
    if (!isOpen) return
    returnFocus = document.activeElement instanceof HTMLElement ? document.activeElement : null
    query.value = ''
    const currentIndex = props.items.findIndex((item) => item.path === route.path)
    activeIndex.value = currentIndex >= 0 ? currentIndex : 0
    void nextTick(() => inputRef.value?.focus())
  },
)

watch(filteredItems, (items) => {
  if (!items.length || activeIndex.value >= items.length) activeIndex.value = 0
})

onMounted(() => window.addEventListener('keydown', handleGlobalKeydown))
onBeforeUnmount(() => window.removeEventListener('keydown', handleGlobalKeydown))
</script>

<template>
  <Teleport to="body">
    <Transition name="quick-panel">
      <div v-if="modelValue" class="quick-overlay" @click.self="close()">
        <section
          ref="panelRef"
          class="quick-panel"
          role="dialog"
          aria-modal="true"
          aria-labelledby="quick-navigation-title"
          aria-describedby="quick-navigation-help"
          @keydown="handlePanelKeydown"
        >
          <header class="quick-panel__header">
            <div>
              <span>站内导航</span>
              <h2 id="quick-navigation-title">快速前往</h2>
            </div>
            <button type="button" class="quick-panel__close" aria-label="关闭快速前往" @click="close()">
              <span aria-hidden="true">×</span>
            </button>
          </header>

          <div
            class="quick-search"
            role="combobox"
            aria-haspopup="listbox"
            aria-expanded="true"
            aria-controls="quick-navigation-list"
            :aria-activedescendant="activeOptionId"
          >
            <el-icon aria-hidden="true"><Search /></el-icon>
            <input
              ref="inputRef"
              v-model="query"
              type="search"
              role="searchbox"
              autocomplete="off"
              spellcheck="false"
              placeholder="搜索看板、预订、订单…"
              aria-label="搜索八个运营模块"
              @keydown="handleSearchKeydown"
            >
            <kbd>Esc</kbd>
          </div>

          <p id="quick-navigation-help" class="sr-only">
            输入模块名称进行筛选，使用上下方向键选择，按回车前往。
          </p>

          <ul v-if="filteredItems.length" id="quick-navigation-list" class="quick-results" role="listbox">
            <li
              v-for="(item, index) in filteredItems"
              :id="`quick-navigation-option-${index}`"
              :key="item.path"
              class="quick-option"
              :class="{ 'is-active': index === activeIndex }"
              role="option"
              :aria-selected="index === activeIndex"
              @pointermove="activeIndex = index"
              @click="navigate(item)"
            >
              <span class="quick-option__icon" aria-hidden="true">
                <el-icon><component :is="item.icon" /></el-icon>
              </span>
              <span class="quick-option__copy">
                <strong>{{ item.label }}</strong>
                <small>{{ item.hint }}</small>
              </span>
              <span class="quick-option__enter" aria-hidden="true">↵</span>
            </li>
          </ul>

          <div v-else class="quick-empty" role="status">
            <strong>没有匹配的模块</strong>
            <span>试试“预订”“会员”或“订单”。</span>
          </div>

          <footer class="quick-panel__footer" aria-hidden="true">
            <span><kbd>↑</kbd><kbd>↓</kbd> 选择</span>
            <span><kbd>↵</kbd> 前往</span>
            <span>共 {{ items.length }} 个模块</span>
          </footer>
        </section>
      </div>
    </Transition>
  </Teleport>
</template>

<style scoped>
.sr-only {
  position: absolute;
  width: 1px;
  height: 1px;
  padding: 0;
  overflow: hidden;
  clip: rect(0, 0, 0, 0);
  white-space: nowrap;
  border: 0;
}

.quick-overlay {
  position: fixed;
  inset: 0;
  z-index: 2500;
  display: grid;
  align-items: start;
  justify-items: center;
  padding: max(12vh, 72px) 18px 24px;
  background: rgb(18 53 45 / 36%);
}

.quick-panel {
  width: min(580px, 100%);
  max-height: min(680px, calc(100dvh - max(12vh, 72px) - 24px));
  overflow: hidden;
  border: 1px solid var(--paper-400);
  border-radius: 16px;
  color: var(--ink-900);
  background: var(--paper-50);
  box-shadow: 0 24px 70px rgb(18 53 45 / 22%);
}

.quick-panel__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 17px 18px 13px;
}

.quick-panel__header span {
  color: var(--clay-700);
  font-size: 11px;
  font-weight: 760;
  letter-spacing: 0.12em;
}

.quick-panel__header h2 {
  margin: 3px 0 0;
  color: var(--lake-950);
  font-family: "Songti SC", "STSong", "Noto Serif CJK SC", serif;
  font-size: 21px;
  line-height: 1.2;
}

.quick-panel__close {
  display: grid;
  width: 34px;
  height: 34px;
  place-items: center;
  border: 1px solid transparent;
  border-radius: 9px;
  color: var(--ink-500);
  background: transparent;
  cursor: pointer;
  touch-action: manipulation;
}

.quick-panel__close span {
  color: inherit;
  font-size: 23px;
  font-weight: 400;
  letter-spacing: 0;
  line-height: 1;
}

.quick-panel__close:hover {
  color: var(--lake-900);
  background: var(--paper-100);
}

.quick-panel__close:focus-visible,
.quick-search:focus-within {
  outline: 3px solid color-mix(in srgb, var(--clay-600) 35%, transparent);
  outline-offset: 2px;
}

.quick-search {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  align-items: center;
  gap: 10px;
  margin: 0 18px 10px;
  padding: 0 11px;
  border: 1px solid var(--paper-400);
  border-radius: 10px;
  color: var(--ink-500);
  background: var(--paper-100);
}

.quick-search input {
  min-width: 0;
  height: 43px;
  padding: 0;
  border: 0;
  outline: 0;
  color: var(--ink-900);
  background: transparent;
  font-size: 13px;
}

.quick-search input::placeholder {
  color: var(--ink-500);
}

.quick-search input::-webkit-search-cancel-button {
  cursor: pointer;
}

.quick-search kbd,
.quick-panel__footer kbd {
  padding: 3px 5px;
  border: 1px solid var(--paper-400);
  border-radius: 5px;
  color: var(--ink-500);
  background: var(--paper-50);
  font-family: inherit;
  font-size: 11px;
  line-height: 1;
}

.quick-results {
  max-height: min(418px, calc(100dvh - 285px));
  margin: 0;
  padding: 3px 10px 9px;
  overflow-y: auto;
  list-style: none;
  overscroll-behavior: contain;
}

.quick-option {
  display: grid;
  grid-template-columns: 36px minmax(0, 1fr) auto;
  align-items: center;
  gap: 11px;
  min-height: 54px;
  padding: 7px 10px;
  border-radius: 10px;
  cursor: pointer;
  touch-action: manipulation;
}

.quick-option.is-active {
  background: var(--lake-50);
}

.quick-option__icon {
  display: grid;
  width: 34px;
  height: 34px;
  place-items: center;
  border: 1px solid var(--paper-300);
  border-radius: 9px;
  color: var(--lake-800);
  background: var(--paper-50);
}

.quick-option__copy,
.quick-option__copy strong,
.quick-option__copy small {
  display: block;
  min-width: 0;
}

.quick-option__copy strong {
  color: var(--ink-900);
  font-size: 13px;
}

.quick-option__copy small {
  margin-top: 3px;
  overflow: hidden;
  color: var(--ink-500);
  font-size: 10px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.quick-option__enter {
  color: transparent;
  font-size: 14px;
}

.quick-option.is-active .quick-option__enter {
  color: var(--lake-700);
}

.quick-empty {
  display: grid;
  min-height: 174px;
  align-content: center;
  justify-items: center;
  gap: 6px;
  padding: 24px;
  text-align: center;
}

.quick-empty strong {
  color: var(--ink-900);
  font-size: 13px;
}

.quick-empty span {
  color: var(--ink-500);
  font-size: 11px;
}

.quick-panel__footer {
  display: flex;
  align-items: center;
  gap: 17px;
  min-height: 39px;
  padding: 7px 18px;
  border-top: 1px solid var(--paper-300);
  color: var(--ink-500);
  background: var(--paper-100);
  font-size: 11px;
}

.quick-panel__footer span:last-child {
  margin-left: auto;
}

.quick-panel-enter-active,
.quick-panel-leave-active {
  transition: opacity 180ms ease;
}

.quick-panel-enter-active .quick-panel,
.quick-panel-leave-active .quick-panel {
  transition:
    opacity 180ms ease,
    transform 180ms cubic-bezier(0.16, 1, 0.3, 1);
}

.quick-panel-enter-from,
.quick-panel-leave-to {
  opacity: 0;
}

.quick-panel-enter-from .quick-panel,
.quick-panel-leave-to .quick-panel {
  opacity: 0;
  transform: translateY(-7px) scale(0.99);
}

@media (max-width: 600px) {
  .quick-overlay {
    align-items: end;
    padding: 0;
  }

  .quick-panel {
    width: 100%;
    max-height: min(78dvh, 650px);
    padding-bottom: env(safe-area-inset-bottom);
    border-width: 1px 0 0;
    border-radius: 18px 18px 0 0;
  }

  .quick-panel__header {
    padding-top: 14px;
  }

  .quick-panel__close {
    width: 44px;
    height: 44px;
  }

  .quick-results {
    max-height: calc(78dvh - 174px - env(safe-area-inset-bottom));
  }

  .quick-option {
    min-height: 58px;
  }

  .quick-panel__footer {
    display: none;
  }

  .quick-panel-enter-from .quick-panel,
  .quick-panel-leave-to .quick-panel {
    transform: translateY(14px);
  }
}

@media (prefers-reduced-motion: reduce) {
  .quick-panel-enter-active,
  .quick-panel-leave-active,
  .quick-panel-enter-active .quick-panel,
  .quick-panel-leave-active .quick-panel {
    transition: none;
  }

  .quick-panel-enter-from .quick-panel,
  .quick-panel-leave-to .quick-panel {
    transform: none;
  }
}
</style>
