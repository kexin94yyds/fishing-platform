import type {
  CurrentUser,
  DashboardSummary,
  PageResult,
  RecentBooking,
  TrafficAnalytics,
  TrafficPoint,
  TrendPoint,
} from '@/types'

type Dict = Record<string, unknown>

function isDict(value: unknown): value is Dict {
  return typeof value === 'object' && value !== null && !Array.isArray(value)
}

function asNumber(value: unknown): number | null {
  if (value === null || value === undefined || value === '') return null
  const parsed = Number(value)
  return Number.isFinite(parsed) ? parsed : null
}

function first(record: Dict, keys: string[]): unknown {
  for (const key of keys) {
    if (record[key] !== undefined && record[key] !== null) return record[key]
  }
  return undefined
}

export function toPage<T>(payload: unknown, aliases: string[] = []): PageResult<T> {
  if (Array.isArray(payload)) return { records: payload as T[], total: payload.length }
  if (!isDict(payload)) return { records: [], total: 0 }

  const keys = ['records', 'items', 'list', 'content', ...aliases]
  const collection = first(payload, keys)
  const records = Array.isArray(collection) ? (collection as T[]) : []
  const total = asNumber(first(payload, ['total', 'totalElements', 'count'])) ?? records.length
  return { records, total }
}

export function toCurrentUser(payload: unknown): CurrentUser {
  const value = isDict(payload) ? payload : {}
  return {
    id: (first(value, ['id', 'userId']) as string | number) ?? '',
    username: String(first(value, ['username', 'account']) ?? ''),
    displayName: String(first(value, ['displayName', 'realName', 'name', 'username']) ?? '运营人员'),
    role: String(first(value, ['roleName', 'role', 'authority']) ?? '运营管理'),
  }
}

function toTrend(payload: unknown): TrendPoint[] {
  if (!Array.isArray(payload)) return []
  return payload
    .filter(isDict)
    .map((item) => ({
      label: String(first(item, ['label', 'date', 'day', 'time', 'hour']) ?? ''),
      value: asNumber(first(item, ['value', 'count', 'visitors', 'revenue', 'bookings'])) ?? 0,
      secondary: asNumber(first(item, ['secondary', 'amount', 'orders'])) ?? undefined,
    }))
}

function toNamedValues(payload: unknown): Array<{ name: string; value: number }> {
  if (!Array.isArray(payload)) return []
  return payload
    .filter(isDict)
    .map((item) => ({
      name: String(first(item, ['name', 'label', 'status', 'source']) ?? '未分类'),
      value: asNumber(first(item, ['value', 'count', 'amount'])) ?? 0,
    }))
}

export function toDashboard(payload: unknown): DashboardSummary {
  const value = isDict(payload) ? payload : {}
  const metrics = isDict(value.metrics) ? value.metrics : value
  const recent = first(value, ['recentBookings', 'bookings'])

  return {
    revenueToday: asNumber(first(metrics, ['revenueToday', 'todayRevenue', 'revenue'])),
    visitorsToday: asNumber(first(metrics, ['visitorsToday', 'todayVisitors', 'visitorCount'])),
    bookingsToday: asNumber(first(metrics, ['bookingsToday', 'todayBookings', 'bookingCount'])),
    availableSpots: asNumber(first(metrics, ['availableSpots', 'openSpots', 'freeSpots', 'spotAvailableCount'])),
    pendingPayments: asNumber(first(metrics, ['pendingPayments', 'unpaidCount'])),
    memberTotal: asNumber(first(metrics, ['memberTotal', 'activeMembers', 'members', 'memberCount'])),
    lowStockProducts: asNumber(first(metrics, ['lowStockProducts', 'lowStockCount'])),
    todayCatchCount: asNumber(first(metrics, ['todayCatchCount', 'catchesToday'])),
    todaySalesOrders: asNumber(first(metrics, ['todaySalesOrders', 'salesOrdersToday'])),
    todayProductQuantity: asNumber(first(metrics, ['todayProductQuantity', 'productsSoldToday'])),
    trafficTrend: toTrend(first(value, ['trafficTrend', 'trend', 'visitorTrend'])),
    bookingMix: toNamedValues(first(value, ['bookingMix', 'bookingStatus', 'statusDistribution'])),
    recentBookings: Array.isArray(recent) ? (recent as RecentBooking[]) : [],
  }
}

export function toTraffic(payload: unknown): TrafficAnalytics {
  const value = isDict(payload) ? payload : {}
  const rawSeries = Array.isArray(value.series) ? value.series : []
  const series: TrafficPoint[] = rawSeries.filter(isDict).map((item) => ({
    statDate: String(first(item, ['statDate', 'date']) ?? ''),
    visits: asNumber(first(item, ['visits', 'value'])) ?? 0,
    uniqueVisitors: asNumber(first(item, ['uniqueVisitors'])) ?? 0,
    newMembers: asNumber(first(item, ['newMembers'])) ?? 0,
    bookingCount: asNumber(first(item, ['bookingCount', 'bookings'])) ?? 0,
    salesOrderCount: asNumber(first(item, ['salesOrderCount', 'salesOrders'])) ?? 0,
    productQuantity: asNumber(first(item, ['productQuantity', 'productsSold'])) ?? 0,
    revenue: asNumber(first(item, ['revenue', 'amount'])) ?? 0,
  }))
  const sum = (selector: (point: TrafficPoint) => number) =>
    series.reduce((total, point) => total + selector(point), 0)
  return {
    days: asNumber(value.days) ?? series.length,
    series,
    totalVisitors: series.length ? sum((point) => point.visits) : null,
    peakDailyUniqueVisitors: series.length
      ? Math.max(...series.map((point) => point.uniqueVisitors))
      : null,
    newMembers: series.length ? sum((point) => point.newMembers) : null,
    bookingCount: series.length ? sum((point) => point.bookingCount) : null,
    salesOrderCount: series.length ? sum((point) => point.salesOrderCount) : null,
    productQuantity: series.length ? sum((point) => point.productQuantity) : null,
    revenue: series.length ? sum((point) => point.revenue) : null,
  }
}
