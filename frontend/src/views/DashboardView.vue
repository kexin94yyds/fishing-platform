<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import type { EChartsOption } from 'echarts'
import { Calendar, Coin, Refresh, Tickets, Trophy, User, View } from '@element-plus/icons-vue'
import ResourceState from '@/components/ResourceState.vue'
import EChartPanel from '@/components/EChartPanel.vue'
import LakeEmptyState from '@/components/LakeEmptyState.vue'
import StatusTag from '@/components/StatusTag.vue'
import { dashboardApi } from '@/api'
import { errorMessage } from '@/api/http'
import { formatCurrency, formatDate, formatNumber, statusLabel, timeSlotLabel } from '@/utils/format'
import type { DashboardSummary } from '@/types'

const loading = ref(true)
const error = ref('')
const summary = ref<DashboardSummary | null>(null)

const dutyStats = computed(() => [
  {
    label: '到访',
    value: formatNumber(summary.value?.visitorsToday, ' 人'),
    icon: User,
  },
  {
    label: '预约',
    value: formatNumber(summary.value?.bookingsToday, ' 单'),
    icon: Calendar,
  },
  {
    label: '渔获',
    value: formatNumber(summary.value?.todayCatchCount, ' 尾'),
    icon: Trophy,
  },
])

const alerts = computed(() => [
  {
    label: '待确认收款',
    value: formatNumber(summary.value?.pendingPayments, ' 笔'),
    icon: Tickets,
    route: '/payments',
  },
  {
    label: '低库存商品',
    value: formatNumber(summary.value?.lowStockProducts, ' 件'),
    icon: Coin,
    route: '/products',
  },
])

const trafficOption = computed<EChartsOption>(() => ({
  color: ['#205747'],
  tooltip: { trigger: 'axis', renderMode: 'richText' },
  grid: { left: 8, right: 12, top: 20, bottom: 6, containLabel: true },
  xAxis: {
    type: 'category',
    boundaryGap: false,
    data: summary.value?.trafficTrend.map((item) => item.label) ?? [],
    axisLine: { lineStyle: { color: '#d8cebd' } },
    axisTick: { show: false },
    axisLabel: { color: '#7b867f', fontSize: 10 },
  },
  yAxis: {
    type: 'value',
    minInterval: 1,
    splitLine: { lineStyle: { color: '#eee7dc' } },
    axisLabel: { color: '#7b867f', fontSize: 10 },
  },
  series: [
    {
      type: 'line',
      name: '到访',
      data: summary.value?.trafficTrend.map((item) => item.value) ?? [],
      smooth: true,
      showSymbol: false,
      lineStyle: { width: 3 },
      areaStyle: { color: 'rgba(32, 87, 71, 0.09)' },
    },
  ],
}))

async function load() {
  loading.value = true
  error.value = ''
  try {
    summary.value = await dashboardApi.summary()
  } catch (reason) {
    error.value = errorMessage(reason)
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="dashboard-station">
    <header class="duty-brief">
      <div class="duty-brief__copy">
        <span class="station-kicker">今日值守</span>
        <h1>湖区经营概况</h1>
        <p>收入、客流、预约和渔获均来自当前值守数据。</p>
      </div>
      <el-button :icon="Refresh" :loading="loading" @click="load">更新值守数据</el-button>
    </header>

    <ResourceState :loading="loading" :error="error" @retry="load">
      <section class="duty-ledger" aria-label="今日值守数据">
        <div class="duty-revenue">
          <span>今日营收</span>
          <strong class="metric-value">{{ formatCurrency(summary?.revenueToday) }}</strong>
          <small>以服务端已入账口径为准</small>
        </div>
        <div class="duty-stat-list">
          <article v-for="item in dutyStats" :key="item.label">
            <el-icon><component :is="item.icon" /></el-icon>
            <div>
              <span>{{ item.label }}</span>
              <strong class="metric-value">{{ item.value }}</strong>
            </div>
          </article>
        </div>
      </section>

      <section class="dashboard-field">
        <div class="lake-situation paper-panel">
          <header class="lake-situation__head">
            <div>
              <h2>湖区态势</h2>
              <p>可安排钓位与近期客流走势</p>
            </div>
            <div class="lake-open-count">
              <el-icon><View /></el-icon>
              <span>开放钓位</span>
              <strong class="metric-value">{{ formatNumber(summary?.availableSpots, ' 个') }}</strong>
            </div>
          </header>
          <div class="lake-situation__body">
            <div class="lake-water">
              <div>
                <span>当前活跃会员</span>
                <strong class="metric-value">{{ formatNumber(summary?.memberTotal, ' 人') }}</strong>
              </div>
              <p>从钓位分区进入，可查看每个湖区的实际平面位置。</p>
              <RouterLink to="/zones-spots">查看湖区平面图</RouterLink>
            </div>
            <div class="lake-traffic">
              <LakeEmptyState
                v-if="!summary?.trafficTrend.length"
                title="暂无客流趋势"
                description="有新的到访数据后，趋势会显示在这里"
                compact
              />
              <EChartPanel
                v-else
                :option="trafficOption"
                height="220px"
                label="近期湖区客流趋势"
              />
            </div>
          </div>
        </div>

        <aside class="exception-board">
          <header>
            <span>异常与待办</span>
            <strong>需要当班确认</strong>
          </header>
          <RouterLink
            v-for="alert in alerts"
            :key="alert.label"
            :to="alert.route"
            class="exception-item"
          >
            <el-icon><component :is="alert.icon" /></el-icon>
            <div>
              <span>{{ alert.label }}</span>
              <strong class="metric-value">{{ alert.value }}</strong>
            </div>
            <small>去处理</small>
          </RouterLink>
          <div class="exception-foot">
            <span>预订状态构成</span>
            <div v-if="summary?.bookingMix.length" class="mix-list">
              <span v-for="item in summary.bookingMix" :key="item.name">
                {{ statusLabel(item.name) }} {{ item.value }}
              </span>
            </div>
            <small v-else>暂无状态汇总</small>
          </div>
        </aside>
      </section>

      <section class="booking-timeline">
        <header>
          <div>
            <h2>预约时间轴</h2>
            <p>最近预约按接口返回顺序排列</p>
          </div>
          <RouterLink to="/bookings">进入排班看板</RouterLink>
        </header>
        <LakeEmptyState
          v-if="!summary?.recentBookings.length"
          title="暂无最近预约"
          description="新增预订后，会按时段显示在值守时间轴中"
          compact
        />
        <div v-else class="timeline-track">
          <article
            v-for="booking in summary.recentBookings"
            :key="booking.bookingNo || `${booking.fishingDate}-${booking.timeSlot}-${booking.spotName}`"
          >
            <div class="timeline-time">
              <strong>{{ timeSlotLabel(booking.timeSlot) }}</strong>
              <span>{{ formatDate(booking.fishingDate) }}</span>
            </div>
            <div class="timeline-booking">
              <span>{{ booking.memberName || '散客' }}</span>
              <strong>{{ booking.spotName || '待安排钓位' }}</strong>
              <small>{{ booking.bookingNo || '未生成单号' }}</small>
            </div>
            <StatusTag :status="booking.status" />
          </article>
        </div>
      </section>
    </ResourceState>
  </div>
</template>

<style scoped>
.duty-brief {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 20px;
  margin-bottom: 17px;
}

.duty-brief h1 {
  margin: 0;
  color: var(--lake-900);
  font-size: clamp(25px, 2.3vw, 34px);
  letter-spacing: -0.04em;
}

.duty-brief p {
  margin: 6px 0 0;
  color: var(--ink-500);
  font-size: 12px;
}

.duty-ledger {
  display: grid;
  grid-template-columns: minmax(260px, 0.8fr) minmax(0, 1.5fr);
  overflow: hidden;
  margin-bottom: 15px;
  border: 1px solid var(--lake-900);
  border-radius: 14px;
  background: var(--lake-900);
}

.duty-revenue {
  padding: 24px;
  color: #eef2ec;
}

.duty-revenue span,
.duty-revenue strong,
.duty-revenue small {
  display: block;
}

.duty-revenue span {
  color: #a9c0b7;
  font-size: 11px;
}

.duty-revenue strong {
  margin-top: 9px;
  font-size: clamp(30px, 3vw, 44px);
  letter-spacing: -0.04em;
}

.duty-revenue small {
  margin-top: 9px;
  color: #8ca99d;
  font-size: 10px;
}

.duty-stat-list {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  padding: 10px;
  border-radius: 13px 0 0 13px;
  background: var(--paper-50);
}

.duty-stat-list article {
  display: flex;
  align-items: center;
  gap: 11px;
  padding: 15px;
  border-right: 1px solid var(--paper-300);
}

.duty-stat-list article:last-child {
  border-right: 0;
}

.duty-stat-list .el-icon {
  color: var(--clay-700);
  font-size: 18px;
}

.duty-stat-list span,
.duty-stat-list strong {
  display: block;
}

.duty-stat-list span {
  color: var(--ink-500);
  font-size: 10px;
}

.duty-stat-list strong {
  margin-top: 5px;
  color: var(--ink-900);
  font-size: 18px;
}

.dashboard-field {
  display: grid;
  grid-template-columns: minmax(0, 1.7fr) minmax(270px, 0.62fr);
  gap: 15px;
}

.lake-situation {
  padding: 18px;
}

.lake-situation__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 18px;
  padding-bottom: 15px;
  border-bottom: 1px solid var(--paper-300);
}

.lake-situation h2,
.booking-timeline h2 {
  margin: 0;
  color: var(--lake-900);
  font-size: 16px;
}

.lake-situation p,
.booking-timeline p {
  margin: 4px 0 0;
  color: var(--ink-500);
  font-size: 10px;
}

.lake-open-count {
  display: grid;
  grid-template-columns: auto auto;
  column-gap: 7px;
  align-items: center;
  color: var(--lake-800);
}

.lake-open-count .el-icon {
  grid-row: span 2;
  font-size: 22px;
}

.lake-open-count span {
  color: var(--ink-500);
  font-size: 11px;
}

.lake-open-count strong {
  font-size: 17px;
}

.lake-situation__body {
  display: grid;
  grid-template-columns: minmax(210px, 0.55fr) minmax(0, 1.45fr);
  gap: 15px;
  padding-top: 15px;
}

.lake-water {
  display: flex;
  min-height: 220px;
  flex-direction: column;
  justify-content: flex-end;
  padding: 18px;
  border-radius: 12px;
  color: #eef2ec;
  background: var(--lake-800);
}

.lake-water span,
.lake-water strong {
  display: block;
}

.lake-water span {
  color: #b4c9c0;
  font-size: 10px;
}

.lake-water strong {
  margin-top: 5px;
  font-size: 25px;
}

.lake-water p {
  margin-top: 24px;
  color: #acc2b8;
  line-height: 1.6;
}

.lake-water a {
  width: fit-content;
  margin-top: 10px;
  color: #f3d4c7;
  font-size: 11px;
  font-weight: 700;
  text-decoration: none;
}

.exception-board {
  overflow: hidden;
  border: 1px solid #e1cfc5;
  border-radius: 14px;
  background: var(--clay-50);
}

.exception-board > header {
  padding: 17px;
  border-bottom: 1px solid #e7cfc4;
}

.exception-board > header span,
.exception-board > header strong {
  display: block;
}

.exception-board > header span {
  color: var(--clay-700);
  font-size: 10px;
}

.exception-board > header strong {
  margin-top: 4px;
  color: #62392c;
  font-size: 14px;
}

.exception-item {
  display: grid;
  grid-template-columns: 28px 1fr auto;
  align-items: center;
  gap: 10px;
  padding: 14px 17px;
  border-bottom: 1px solid #ead7ce;
  color: #62392c;
  text-decoration: none;
}

.exception-item .el-icon {
  font-size: 18px;
}

.exception-item span,
.exception-item strong {
  display: block;
}

.exception-item span,
.exception-item small {
  font-size: 10px;
}

.exception-item strong {
  margin-top: 3px;
  font-size: 17px;
}

.exception-item small {
  color: var(--clay-700);
  font-weight: 700;
}

.exception-foot {
  padding: 15px 17px;
}

.exception-foot > span {
  color: #7a5043;
  font-size: 10px;
}

.mix-list {
  display: flex;
  flex-wrap: wrap;
  gap: 5px 10px;
  margin-top: 8px;
}

.mix-list span,
.exception-foot small {
  color: #7d5a4f;
  font-size: 10px;
}

.booking-timeline {
  margin-top: 15px;
  padding: 18px;
  border: 1px solid var(--paper-300);
  border-radius: 14px;
  background: var(--paper-50);
}

.booking-timeline > header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 15px;
  margin-bottom: 13px;
}

.booking-timeline > header a {
  color: var(--lake-800);
  font-size: 11px;
  font-weight: 700;
  text-decoration: none;
}

.timeline-track {
  display: grid;
  grid-auto-flow: column;
  grid-auto-columns: minmax(230px, 1fr);
  overflow-x: auto;
  gap: 9px;
  padding-bottom: 4px;
  scroll-snap-type: x proximity;
}

.timeline-track article {
  display: grid;
  grid-template-columns: 74px 1fr auto;
  align-items: center;
  gap: 11px;
  min-height: 86px;
  padding: 12px;
  border-left: 3px solid #d58b6e;
  border-radius: 0 10px 10px 0;
  background: #f7f2e9;
  scroll-snap-align: start;
}

.timeline-time strong,
.timeline-time span,
.timeline-booking span,
.timeline-booking strong,
.timeline-booking small {
  display: block;
}

.timeline-time strong {
  color: var(--lake-900);
  font-size: 11px;
}

.timeline-time span,
.timeline-booking small {
  margin-top: 4px;
  color: #89918c;
  font-size: 11px;
}

.timeline-booking span {
  color: #6e7a74;
  font-size: 10px;
}

.timeline-booking strong {
  margin-top: 4px;
  color: var(--ink-900);
  font-size: 12px;
}

@media (max-width: 980px) {
  .dashboard-field {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 720px) {
  .duty-ledger,
  .lake-situation__body {
    grid-template-columns: 1fr;
  }

  .duty-stat-list {
    border-radius: 0;
  }

  .lake-water {
    min-height: 170px;
  }
}

@media (max-width: 520px) {
  .duty-brief {
    display: block;
  }

  .duty-brief .el-button {
    margin-top: 13px;
  }

  .duty-stat-list {
    grid-template-columns: 1fr;
  }

  .duty-stat-list article {
    border-right: 0;
    border-bottom: 1px solid var(--paper-300);
  }

  .duty-stat-list article:last-child {
    border-bottom: 0;
  }

  .timeline-track {
    grid-auto-columns: 88%;
  }
}
</style>
