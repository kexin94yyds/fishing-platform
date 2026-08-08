<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  Calendar,
  Coin,
  DataAnalysis,
  Expand,
  Goods,
  Grid,
  Menu as MenuIcon,
  Notebook,
  Operation,
  Search,
  SwitchButton,
  User,
} from '@element-plus/icons-vue'
import QuickNavigator from '@/components/QuickNavigator.vue'
import { useAuthStore } from '@/stores/auth'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const mobileMenuOpen = ref(false)
const quickNavigatorOpen = ref(false)
const loggingOut = ref(false)
const todayLabel = new Intl.DateTimeFormat('zh-CN', {
  month: 'long',
  day: 'numeric',
  weekday: 'short',
}).format(new Date())

const menuItems = [
  { path: '/', label: '总体看板', hint: '经营概况与今日运行', keywords: '首页 仪表盘 数据', icon: Operation },
  { path: '/zones-spots', label: '钓位分区', hint: '分区、钓位与现场状态', keywords: '区域 位置 状态', icon: Grid },
  { path: '/bookings', label: '时段预订', hint: '空闲时段与客户预订', keywords: '预约 时间 客户', icon: Calendar },
  { path: '/catches', label: '渔获登记', hint: '品种、重量与钓位归属', keywords: '鱼获 重量 记录', icon: Notebook },
  { path: '/products', label: '渔具售卖', hint: '商品库存与现场销售', keywords: '商品 库存 销售', icon: Goods },
  { path: '/members', label: '会员管理', hint: '会员资料与账户状态', keywords: '客户 用户 等级', icon: User },
  { path: '/payments', label: '收费订单', hint: '收费记录与收款确认', keywords: '支付 账单 收款', icon: Coin },
  { path: '/analytics', label: '客流分析', hint: '趋势、时段与来源构成', keywords: '统计 趋势 来源', icon: DataAnalysis },
]

const roleLabel = computed(() => {
  const role = String(auth.user?.role || '').toUpperCase()
  if (role === 'ADMIN') return '管理员'
  if (role === 'OPERATOR') return '运营人员'
  return auth.user?.role || '运营人员'
})

function closeMobileMenu() {
  mobileMenuOpen.value = false
}

function focusPageHeading() {
  const heading = document.querySelector<HTMLElement>('#main-content h1')
  if (!heading) return
  heading.tabIndex = -1
  heading.focus({ preventScroll: true })
}

async function handleLogout() {
  if (loggingOut.value) return
  try {
    await ElMessageBox.confirm('确认退出当前运营账号吗？', '退出登录', {
      confirmButtonText: '确认退出',
      cancelButtonText: '继续工作',
      type: 'warning',
    })
    loggingOut.value = true
    await auth.logout()
    await router.replace('/login')
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') {
      ElMessage.error(
        error instanceof Error
          ? `退出未确认，当前登录状态已保留：${error.message}`
          : '退出未确认，当前登录状态已保留，请重试',
      )
    }
  } finally {
    loggingOut.value = false
  }
}
</script>

<template>
  <div class="app-shell">
    <a class="skip-link" href="#main-content">跳到主要内容</a>

    <aside class="desktop-sidebar">
      <div class="brand-block">
        <div class="brand-mark" aria-hidden="true">
          <el-icon><MenuIcon /></el-icon>
        </div>
        <div>
          <strong>湖畔运营所</strong>
          <span>基地现场工作台</span>
        </div>
      </div>
      <p class="menu-section-label">基地运营</p>
      <nav class="main-menu" aria-label="主导航">
        <RouterLink
          v-for="item in menuItems"
          :key="item.path"
          :to="item.path"
          class="main-menu__link"
          :aria-current="route.path === item.path ? 'page' : undefined"
        >
          <el-icon><component :is="item.icon" /></el-icon>
          <span>{{ item.label }}</span>
        </RouterLink>
      </nav>
      <div class="sidebar-foot">
        <span>{{ todayLabel }}</span>
        <small>当班数据由业务接口提供</small>
      </div>
    </aside>

    <el-drawer
      v-model="mobileMenuOpen"
      direction="ltr"
      size="280px"
      :with-header="false"
      class="mobile-drawer"
    >
      <div class="brand-block brand-block--mobile">
        <div class="brand-mark" aria-hidden="true">
          <el-icon><MenuIcon /></el-icon>
        </div>
        <div>
          <strong>湖畔运营所</strong>
          <span>基地现场工作台</span>
        </div>
      </div>
      <p class="menu-section-label">基地运营</p>
      <nav class="main-menu" aria-label="移动端主导航">
        <RouterLink
          v-for="item in menuItems"
          :key="item.path"
          :to="item.path"
          class="main-menu__link"
          :aria-current="route.path === item.path ? 'page' : undefined"
          @click="closeMobileMenu"
        >
          <el-icon><component :is="item.icon" /></el-icon>
          <span>{{ item.label }}</span>
        </RouterLink>
      </nav>
    </el-drawer>

    <section class="shell-main">
      <header class="topbar">
        <div class="topbar__left">
          <el-button
            class="mobile-menu-button"
            text
            :icon="Expand"
            aria-label="打开导航"
            @click="mobileMenuOpen = true"
          />
          <div>
            <span class="topbar__context">{{ todayLabel }} · 现场值守</span>
            <strong>{{ route.meta.title }}</strong>
          </div>
        </div>
        <div class="account-area">
          <button
            class="quick-trigger"
            type="button"
            aria-label="快速前往，快捷键 Command 或 Control 加 K"
            aria-keyshortcuts="Meta+K Control+K"
            @click="quickNavigatorOpen = true"
          >
            <el-icon aria-hidden="true"><Search /></el-icon>
            <span>快速前往</span>
            <kbd>⌘ / Ctrl&nbsp;K</kbd>
          </button>
          <div class="account-copy">
            <strong>{{ auth.user?.displayName || '运营人员' }}</strong>
            <span>{{ roleLabel }}</span>
          </div>
          <el-tooltip content="退出登录" placement="bottom">
            <el-button
              :icon="SwitchButton"
              :loading="loggingOut"
              :disabled="loggingOut"
              circle
              plain
              aria-label="退出登录"
              @click="handleLogout"
            />
          </el-tooltip>
        </div>
      </header>
      <main id="main-content" class="page-container" tabindex="-1">
        <RouterView v-slot="{ Component, route: viewRoute }">
          <Transition name="page-shift" mode="out-in" @after-enter="focusPageHeading">
            <component :is="Component" :key="viewRoute.path" />
          </Transition>
        </RouterView>
      </main>
    </section>

    <QuickNavigator v-model="quickNavigatorOpen" :items="menuItems" />
  </div>
</template>

<style scoped>
.skip-link {
  position: fixed;
  top: 10px;
  left: 12px;
  z-index: 3000;
  padding: 9px 13px;
  border: 1px solid var(--lake-700);
  border-radius: 8px;
  color: var(--paper-50);
  background: var(--lake-950);
  font-size: 13px;
  font-weight: 700;
  text-decoration: none;
  transform: translateY(calc(-100% - 14px));
  transition: transform 140ms cubic-bezier(0.16, 1, 0.3, 1);
}

.skip-link:focus-visible {
  outline: 3px solid var(--clay-600);
  outline-offset: 2px;
  transform: translateY(0);
}

.main-menu {
  display: grid;
  gap: 2px;
  border-right: 0;
  background: transparent;
}

.main-menu__link {
  display: flex;
  height: 43px;
  align-items: center;
  gap: 10px;
  padding: 0 20px;
  border-radius: 9px;
  color: #617069;
  font-size: 13px;
  line-height: 43px;
  text-decoration: none;
  white-space: nowrap;
  transition:
    color 140ms ease,
    background-color 140ms ease;
}

.main-menu__link:hover {
  color: var(--lake-900);
  background: #eee8dc;
}

.main-menu__link[aria-current='page'] {
  color: #f9f6ee;
  background: var(--lake-900);
}

.main-menu__link:focus-visible {
  outline: 3px solid color-mix(in srgb, var(--clay-600) 75%, white);
  outline-offset: 2px;
}

.main-menu__link .el-icon {
  flex: 0 0 auto;
  color: inherit;
}

.quick-trigger {
  display: inline-flex;
  min-height: 34px;
  align-items: center;
  gap: 7px;
  padding: 0 7px 0 10px;
  border: 1px solid var(--paper-400);
  border-radius: 9px;
  color: var(--ink-700);
  background: var(--paper-100);
  cursor: pointer;
  font-size: 11px;
  font-weight: 650;
  touch-action: manipulation;
  transition:
    border-color 140ms ease,
    color 140ms ease,
    background-color 140ms ease;
}

.quick-trigger:hover {
  border-color: #b9aa94;
  color: var(--lake-900);
  background: var(--paper-50);
}

.quick-trigger:focus-visible {
  outline: 3px solid color-mix(in srgb, var(--clay-600) 40%, transparent);
  outline-offset: 2px;
}

.quick-trigger kbd {
  padding: 3px 6px;
  border: 1px solid var(--paper-400);
  border-radius: 5px;
  color: var(--ink-500);
  background: var(--paper-50);
  font-family: inherit;
  font-size: 11px;
  font-weight: 650;
  line-height: 1;
}

.page-shift-enter-active,
.page-shift-leave-active {
  transition:
    opacity 150ms ease,
    transform 150ms cubic-bezier(0.16, 1, 0.3, 1);
}

.page-shift-enter-from {
  opacity: 0;
  transform: translateY(4px);
}

.page-shift-leave-to {
  opacity: 0;
  transform: translateY(-2px);
}

@media (max-width: 700px) {
  .account-area {
    gap: 7px;
  }

  .quick-trigger {
    width: 44px;
    min-height: 44px;
    justify-content: center;
    padding: 0;
  }

  .quick-trigger span,
  .quick-trigger kbd {
    display: none;
  }
}

@media (prefers-reduced-motion: reduce) {
  .skip-link,
  .main-menu__link,
  .quick-trigger,
  .page-shift-enter-active,
  .page-shift-leave-active {
    transition: none;
  }

  .page-shift-enter-from,
  .page-shift-leave-to {
    transform: none;
  }
}
</style>
