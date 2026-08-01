import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import AppShell from '@/layouts/AppShell.vue'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/login',
      name: 'login',
      component: () => import('@/views/LoginView.vue'),
      meta: { public: true, title: '登录' },
    },
    {
      path: '/register',
      name: 'register',
      component: () => import('@/views/RegisterView.vue'),
      meta: { public: true, title: '创建账号' },
    },
    {
      path: '/',
      component: AppShell,
      children: [
        {
          path: '',
          name: 'dashboard',
          component: () => import('@/views/DashboardView.vue'),
          meta: { title: '总体看板', subtitle: '经营数据与今日运行概况' },
        },
        {
          path: 'zones-spots',
          name: 'zones-spots',
          component: () => import('@/views/ZonesSpotsView.vue'),
          meta: { title: '钓位分区', subtitle: '维护分区、钓位和现场状态' },
        },
        {
          path: 'bookings',
          name: 'bookings',
          component: () => import('@/views/BookingsView.vue'),
          meta: { title: '时段预订', subtitle: '查询空闲时段并处理客户预订' },
        },
        {
          path: 'catches',
          name: 'catches',
          component: () => import('@/views/CatchesView.vue'),
          meta: { title: '渔获登记', subtitle: '记录渔获品种、重量和归属钓位' },
        },
        {
          path: 'products',
          name: 'products',
          component: () => import('@/views/ProductsView.vue'),
          meta: { title: '渔具售卖', subtitle: '管理商品库存与现场销售' },
        },
        {
          path: 'members',
          name: 'members',
          component: () => import('@/views/MembersView.vue'),
          meta: { title: '会员管理', subtitle: '维护会员资料、等级和账户状态' },
        },
        {
          path: 'payments',
          name: 'payments',
          component: () => import('@/views/PaymentsView.vue'),
          meta: { title: '收费订单', subtitle: '核对收费记录并确认收款' },
        },
        {
          path: 'analytics',
          name: 'analytics',
          component: () => import('@/views/TrafficView.vue'),
          meta: { title: '客流分析', subtitle: '查看客流趋势、时段与来源构成' },
        },
      ],
    },
    {
      path: '/:pathMatch(.*)*',
      redirect: '/',
    },
  ],
})

router.beforeEach(async (to) => {
  const auth = useAuthStore()
  await auth.hydrate()

  if (to.meta.public) {
    if (['login', 'register'].includes(String(to.name)) && auth.isAuthenticated) {
      return { name: 'dashboard' }
    }
    if (to.name === 'register') {
      await auth.hydrateRegistration()
      if (!auth.registrationEnabled) return { name: 'login' }
    }
    return true
  }

  if (!auth.isAuthenticated) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }
  return true
})

router.afterEach((to) => {
  document.title = `${String(to.meta.title || '运营管理')} | 湖畔运营所`
})

export default router
