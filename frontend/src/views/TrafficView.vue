<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import type { EChartsOption } from 'echarts'
import { Refresh } from '@element-plus/icons-vue'
import ResourceState from '@/components/ResourceState.vue'
import EChartPanel from '@/components/EChartPanel.vue'
import LakeEmptyState from '@/components/LakeEmptyState.vue'
import { analyticsApi } from '@/api'
import { errorMessage } from '@/api/http'
import { formatCurrency, formatDate, formatNumber } from '@/utils/format'
import type { TrafficAnalytics } from '@/types'

const days = ref(7)
const loading = ref(true)
const error = ref('')
const data = ref<TrafficAnalytics | null>(null)
const hasTrafficData = computed(() =>
  (data.value?.series ?? []).some((point) => point.visits > 0 || point.uniqueVisitors > 0),
)
const hasOperationData = computed(() =>
  (data.value?.series ?? []).some((point) => point.bookingCount > 0 || point.newMembers > 0),
)
const hasRevenueData = computed(() =>
  (data.value?.series ?? []).some((point) => point.revenue > 0),
)

const busiestPoint = computed(() => {
  const series = data.value?.series ?? []
  if (!series.some((point) => point.visits > 0)) return null
  return series.reduce((best, point) => (point.visits > best.visits ? point : best))
})

const dailyAverage = computed(() => {
  const count = data.value?.series.length ?? 0
  if (!count || data.value?.totalVisitors === null || data.value?.totalVisitors === undefined) {
    return null
  }
  return data.value.totalVisitors / count
})

const trendOption = computed<EChartsOption>(() => ({
  aria: { enabled: true },
  color: ['#17463a', '#b46145'],
  tooltip: { trigger: 'axis', renderMode: 'richText' },
  grid: {
    left: 12,
    right: 18,
    top: 28,
    bottom: 8,
    outerBoundsMode: 'same',
    outerBoundsContain: 'axisLabel',
  },
  xAxis: {
    type: 'category',
    data: data.value?.series.map((item) => item.statDate) ?? [],
    axisTick: { show: false },
    axisLine: { lineStyle: { color: '#d9d0c3' } },
    axisLabel: { color: '#78827c', fontSize: 10 },
  },
  yAxis: {
    type: 'value',
    minInterval: 1,
    splitLine: { lineStyle: { color: '#ebe4d9' } },
    axisLabel: { color: '#78827c', fontSize: 10 },
  },
  series: [
    {
      name: '到访人数',
      type: 'line',
      smooth: true,
      symbolSize: 7,
      data: data.value?.series.map((item) => item.visits) ?? [],
      lineStyle: { width: 3 },
      areaStyle: { color: 'rgba(23, 70, 58, 0.09)' },
    },
    {
      name: '独立访客',
      type: 'bar',
      barMaxWidth: 18,
      data: data.value?.series.map((item) => item.uniqueVisitors) ?? [],
      itemStyle: { borderRadius: [3, 3, 0, 0] },
    },
  ],
}))

const operationOption = computed<EChartsOption>(() => ({
  aria: { enabled: true },
  color: ['#17463a', '#b46145'],
  tooltip: { trigger: 'axis', renderMode: 'richText', axisPointer: { type: 'shadow' } },
  grid: {
    left: 12,
    right: 18,
    top: 24,
    bottom: 8,
    outerBoundsMode: 'same',
    outerBoundsContain: 'axisLabel',
  },
  xAxis: {
    type: 'category',
    data: data.value?.series.map((item) => item.statDate) ?? [],
    axisTick: { show: false },
    axisLine: { lineStyle: { color: '#d9d0c3' } },
    axisLabel: { color: '#78827c', fontSize: 10 },
  },
  yAxis: {
    type: 'value',
    minInterval: 1,
    splitLine: { lineStyle: { color: '#ebe4d9' } },
    axisLabel: { color: '#78827c', fontSize: 10 },
  },
  series: [
    {
      name: '预订量',
      type: 'bar',
      barMaxWidth: 23,
      data: data.value?.series.map((item) => item.bookingCount) ?? [],
      itemStyle: { borderRadius: [3, 3, 0, 0] },
    },
    {
      name: '新增会员',
      type: 'line',
      smooth: true,
      symbolSize: 6,
      data: data.value?.series.map((item) => item.newMembers) ?? [],
    },
  ],
}))

function dateLabel(value: string) {
  return formatDate(value, { month: 'long', day: 'numeric' })
}

async function load() {
  loading.value = true
  error.value = ''
  try {
    data.value = await analyticsApi.traffic(days.value)
  } catch (reason) {
    error.value = errorMessage(reason)
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="traffic-canvas">
    <header class="traffic-head">
      <div>
        <span class="station-kicker">客流研判</span>
        <h1>把人流变化摊开来看</h1>
        <p>到访、预订、会员与营收均来自同一统计周期。</p>
      </div>
      <div class="traffic-head__actions">
        <el-radio-group v-model="days" aria-label="统计周期" @change="load">
          <el-radio-button :value="7">7 天</el-radio-button>
          <el-radio-button :value="14">14 天</el-radio-button>
          <el-radio-button :value="30">30 天</el-radio-button>
        </el-radio-group>
        <el-button :icon="Refresh" :loading="loading" @click="load">刷新</el-button>
      </div>
    </header>

    <ResourceState :loading="loading" :error="error" @retry="load">
      <section class="analysis-board">
        <aside class="reading-rail">
          <div class="reading-rail__lead">
            <span>周期累计到访</span>
            <strong class="metric-value">{{ formatNumber(data?.totalVisitors, ' 人') }}</strong>
            <small>统计范围共 {{ data?.days ?? days }} 天</small>
          </div>

          <dl>
            <div>
              <dt>单日独立访客峰值</dt>
              <dd class="metric-value">{{ formatNumber(data?.peakDailyUniqueVisitors, ' 人') }}</dd>
            </div>
            <div>
              <dt>日均到访</dt>
              <dd class="metric-value">{{ formatNumber(dailyAverage, ' 人') }}</dd>
            </div>
            <div>
              <dt>新增会员</dt>
              <dd class="metric-value">{{ formatNumber(data?.newMembers, ' 人') }}</dd>
            </div>
            <div>
              <dt>关联预订</dt>
              <dd class="metric-value">{{ formatNumber(data?.bookingCount, ' 单') }}</dd>
            </div>
            <div>
              <dt>关联营收</dt>
              <dd class="metric-value">{{ formatCurrency(data?.revenue) }}</dd>
            </div>
          </dl>

          <div v-if="busiestPoint" class="reading-note">
            <span>高峰观察</span>
            <p>
              {{ dateLabel(busiestPoint.statDate) }}到访最多，
              共 {{ busiestPoint.visits }} 人。
            </p>
          </div>
        </aside>

        <section class="trend-sheet">
          <header>
            <div>
              <span class="chart-index">图一</span>
              <h2>到访与独立访客趋势</h2>
            </div>
            <div class="chart-legend" aria-label="图例">
              <span><i class="legend-line" />到访人数</span>
              <span><i class="legend-bar" />独立访客</span>
            </div>
          </header>
          <LakeEmptyState
            v-if="!hasTrafficData"
            title="暂无客流趋势"
            description="选择其他统计周期，或等待新的到访数据"
            compact
          />
          <EChartPanel
            v-else
            :option="trendOption"
            height="380px"
            label="到访与独立访客趋势图"
          />
          <details v-if="hasTrafficData" class="data-table-details">
            <summary>查看到访趋势数据表</summary>
            <table>
              <thead><tr><th>日期</th><th>到访人数</th><th>独立访客</th></tr></thead>
              <tbody>
                <tr v-for="point in data?.series" :key="`traffic-${point.statDate}`">
                  <th>{{ dateLabel(point.statDate) }}</th>
                  <td>{{ point.visits }}</td>
                  <td>{{ point.uniqueVisitors }}</td>
                </tr>
              </tbody>
            </table>
          </details>
          <footer>
            <span>横轴按统计接口返回日期排列</span>
            <span>当前周期 {{ data?.series.length ?? 0 }} 个数据点</span>
          </footer>
        </section>
      </section>

      <section class="analysis-lower">
        <article class="operation-sheet">
          <header>
            <div>
              <span class="chart-index">图二</span>
              <h2>预订与新增会员联动</h2>
            </div>
            <p>观察到访后形成预订和会员沉淀的日变化。</p>
          </header>
          <LakeEmptyState
            v-if="!hasOperationData"
            title="暂无运营数据"
            description="当前周期还没有预订或新增会员记录"
            compact
          />
          <EChartPanel
            v-else
            :option="operationOption"
            height="290px"
            label="每日预订与新增会员图"
          />
          <details v-if="hasOperationData" class="data-table-details">
            <summary>查看运营趋势数据表</summary>
            <table>
              <thead><tr><th>日期</th><th>预订量</th><th>新增会员</th></tr></thead>
              <tbody>
                <tr v-for="point in data?.series" :key="`operation-${point.statDate}`">
                  <th>{{ dateLabel(point.statDate) }}</th>
                  <td>{{ point.bookingCount }}</td>
                  <td>{{ point.newMembers }}</td>
                </tr>
              </tbody>
            </table>
          </details>
        </article>

        <aside class="revenue-diary">
          <header>
            <div>
              <span class="chart-index">附录</span>
              <h2>每日营收记录</h2>
            </div>
            <strong class="metric-value">{{ formatCurrency(data?.revenue) }}</strong>
          </header>
          <LakeEmptyState
            v-if="!hasRevenueData"
            title="暂无营收数据"
            description="确认入账后，每日营收会显示在这里"
            compact
          />
          <div v-else class="revenue-list">
            <div v-for="point in data?.series ?? []" :key="point.statDate">
              <span>{{ dateLabel(point.statDate) }}</span>
              <i aria-hidden="true" />
              <strong class="metric-value">{{ formatCurrency(point.revenue) }}</strong>
            </div>
          </div>
        </aside>
      </section>
    </ResourceState>
  </div>
</template>

<style scoped>
.traffic-head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 22px;
  margin-bottom: 15px;
}

.traffic-head h1 {
  margin: 0;
  color: var(--lake-900);
  font-size: clamp(25px, 2.3vw, 34px);
  letter-spacing: -0.04em;
}

.traffic-head p {
  margin: 6px 0 0;
  color: var(--ink-500);
  font-size: 11px;
}

.traffic-head__actions {
  display: flex;
  gap: 8px;
}

.analysis-board {
  display: grid;
  grid-template-columns: minmax(245px, 0.58fr) minmax(600px, 1.7fr);
  overflow: hidden;
  min-height: 500px;
  border: 1px solid #ded5c8;
  border-radius: 14px;
  background: var(--paper-50);
}

.reading-rail {
  display: flex;
  flex-direction: column;
  min-width: 0;
  padding: 24px 21px;
  color: #f9f1e7;
  background: var(--lake-900);
}

.reading-rail__lead {
  padding-bottom: 18px;
  border-bottom: 1px solid rgb(255 255 255 / 15%);
}

.reading-rail__lead span,
.reading-rail__lead strong,
.reading-rail__lead small {
  display: block;
}

.reading-rail__lead span {
  color: #becfc7;
  font-size: 10px;
  letter-spacing: 0.08em;
}

.reading-rail__lead strong {
  margin-top: 7px;
  font-size: 32px;
}

.reading-rail__lead small {
  margin-top: 5px;
  color: #aebfb7;
  font-size: 11px;
}

.reading-rail dl {
  display: grid;
  margin: 0;
}

.reading-rail dl > div {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
  padding: 13px 0;
  border-bottom: 1px solid rgb(255 255 255 / 11%);
}

.reading-rail dt {
  color: #b5c6be;
  font-size: 10px;
}

.reading-rail dd {
  margin: 0;
  color: #fff8ee;
  font-size: 14px;
  text-align: right;
}

.reading-note {
  margin-top: auto;
  padding: 14px;
  border-left: 3px solid #d08a6e;
  color: #3d473f;
  background: #f4e8d9;
}

.reading-note span {
  color: #a1563d;
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.1em;
}

.reading-note p {
  margin: 6px 0 0;
  font-size: 11px;
  line-height: 1.7;
}

.trend-sheet,
.operation-sheet,
.revenue-diary {
  min-width: 0;
  background: var(--paper-50);
}

.trend-sheet {
  padding: 23px 25px 15px;
}

.trend-sheet > header,
.operation-sheet > header,
.revenue-diary > header {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 16px;
}

.chart-index {
  color: var(--clay-600);
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.12em;
}

.trend-sheet h2,
.operation-sheet h2,
.revenue-diary h2 {
  margin: 3px 0 0;
  color: #263c33;
  font-size: 19px;
}

.chart-legend {
  display: flex;
  gap: 15px;
  color: #727e78;
  font-size: 11px;
}

.chart-legend span {
  display: flex;
  align-items: center;
  gap: 5px;
}

.chart-legend i {
  display: inline-block;
  width: 16px;
  height: 3px;
  background: var(--lake-900);
}

.chart-legend .legend-bar {
  height: 8px;
  background: var(--clay-600);
}

.trend-sheet > footer {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  padding-top: 10px;
  border-top: 1px solid #e6ded2;
  color: #949a95;
  font-size: 11px;
}

.analysis-lower {
  display: grid;
  grid-template-columns: minmax(520px, 1.5fr) minmax(270px, 0.72fr);
  gap: 14px;
  margin-top: 14px;
}

.operation-sheet {
  padding: 20px 23px 12px;
  border: 1px solid #ded5c8;
  border-radius: 14px;
}

.operation-sheet > header p {
  max-width: 230px;
  margin: 0;
  color: #818a84;
  font-size: 11px;
  line-height: 1.6;
  text-align: right;
}

.revenue-diary {
  overflow: hidden;
  padding: 20px;
  border: 1px solid #d9c5b6;
  border-radius: 14px;
  background: #f6ecdf;
}

.revenue-diary > header {
  padding-bottom: 14px;
  border-bottom: 2px solid var(--clay-600);
}

.revenue-diary > header > strong {
  color: var(--lake-900);
  font-size: 15px;
}

.revenue-list {
  overflow-y: auto;
  max-height: 270px;
}

.revenue-list > div {
  display: grid;
  grid-template-columns: auto 1fr auto;
  align-items: center;
  gap: 8px;
  min-height: 39px;
  border-bottom: 1px solid #dfd1c2;
  color: #667169;
  font-size: 10px;
}

.revenue-list i {
  height: 1px;
  border-top: 1px dotted #c7b7a8;
}

.revenue-list strong {
  color: #263f35;
  font-size: 11px;
}

.data-table-details {
  margin-top: 10px;
  color: #56665f;
  font-size: 11px;
}

.data-table-details summary {
  cursor: pointer;
  color: var(--lake-700);
  font-weight: 700;
}

.data-table-details table {
  width: 100%;
  margin-top: 8px;
  border-collapse: collapse;
}

.data-table-details th,
.data-table-details td {
  padding: 6px 8px;
  border-bottom: 1px solid #e7dfd4;
  text-align: left;
}

@media (max-width: 1040px) {
  .analysis-board {
    grid-template-columns: 245px minmax(0, 1fr);
  }

  .analysis-lower {
    grid-template-columns: minmax(0, 1.45fr) 270px;
  }
}

@media (max-width: 820px) {
  .analysis-board,
  .analysis-lower {
    grid-template-columns: 1fr;
  }

  .reading-rail {
    display: grid;
    grid-template-columns: 1fr 1.4fr;
    gap: 18px;
  }

  .reading-rail dl {
    grid-template-columns: repeat(2, 1fr);
  }

  .reading-rail dl > div {
    padding: 8px;
  }

  .reading-note {
    grid-column: 1 / -1;
    margin-top: 0;
  }
}

@media (max-width: 640px) {
  .traffic-head,
  .trend-sheet > header,
  .operation-sheet > header,
  .revenue-diary > header {
    align-items: stretch;
    flex-direction: column;
  }

  .traffic-head__actions {
    justify-content: space-between;
  }

  .traffic-head__actions .el-radio-group {
    flex: 1;
  }

  .traffic-head__actions :deep(.el-radio-button) {
    flex: 1;
  }

  .traffic-head__actions :deep(.el-radio-button__inner) {
    width: 100%;
  }

  .reading-rail {
    display: block;
  }

  .reading-rail dl {
    grid-template-columns: 1fr;
  }

  .reading-note {
    margin-top: 14px;
  }

  .trend-sheet,
  .operation-sheet,
  .revenue-diary {
    padding-right: 13px;
    padding-left: 13px;
  }

  .chart-legend {
    margin-top: 8px;
  }

  .operation-sheet > header p {
    max-width: none;
    text-align: left;
  }

  .trend-sheet > footer {
    flex-direction: column;
  }
}

@media (max-width: 390px) {
  .traffic-head h1 {
    font-size: 25px;
  }

  .traffic-head__actions {
    flex-direction: column;
  }

  .traffic-head__actions > .el-button {
    width: 100%;
  }
}
</style>
