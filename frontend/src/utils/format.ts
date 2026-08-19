export function formatCurrency(value: number | null | undefined): string {
  if (value === null || value === undefined || !Number.isFinite(Number(value))) return '暂无'
  return new Intl.NumberFormat('zh-CN', {
    style: 'currency',
    currency: 'CNY',
    minimumFractionDigits: 2,
  }).format(Number(value))
}

export function formatNumber(value: number | null | undefined, suffix = ''): string {
  if (value === null || value === undefined || !Number.isFinite(Number(value))) return '暂无'
  return `${new Intl.NumberFormat('zh-CN', { maximumFractionDigits: 2 }).format(Number(value))}${suffix}`
}

export function formatDateTime(value?: string): string {
  if (!value) return '暂无'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  return new Intl.DateTimeFormat('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  }).format(date)
}

export function formatDate(
  value?: string,
  options: Intl.DateTimeFormatOptions = { month: '2-digit', day: '2-digit' },
): string {
  if (!value) return '暂无'
  const date = new Date(value.includes('T') ? value : `${value}T00:00:00`)
  if (Number.isNaN(date.getTime())) return value
  return new Intl.DateTimeFormat('zh-CN', options).format(date)
}

export function paymentMethodLabel(value?: string): string {
  const key = String(value || '').toUpperCase()
  const labels: Record<string, string> = {
    CASH: '现金',
    WECHAT: '微信',
    ALIPAY: '支付宝',
    CARD: '银行卡',
  }
  return labels[key] || value || '待确认'
}

export function businessTypeLabel(value?: string): string {
  const key = String(value || '').toUpperCase()
  const labels: Record<string, string> = {
    SALES_ORDER: '销售订单',
  }
  return labels[key] || value || '未关联业务'
}

export function statusLabel(status?: string): string {
  const key = String(status || '').toUpperCase()
  const labels: Record<string, string> = {
    AVAILABLE: '空闲',
    FREE: '空闲',
    IDLE: '空闲',
    OCCUPIED: '使用中',
    IN_USE: '使用中',
    RESERVED: '已预订',
    BOOKED: '已预订',
    MAINTENANCE: '维护中',
    ACTIVE: '启用',
    ENABLED: '启用',
    INACTIVE: '停用',
    DISABLED: '停用',
    PENDING: '待处理',
    UNPAID: '待支付',
    PAID: '已支付',
    CONFIRMED: '已确认',
    COMPLETED: '已完成',
    NO_SHOW: '爽约',
    CANCELLED: '已取消',
    CANCELED: '已取消',
    REFUNDED: '已退款',
    OUT_OF_STOCK: '缺货',
    ON_SALE: '在售',
    OPEN: '开放',
    CLOSED: '关闭',
    PENDING_PAYMENT: '待支付',
    FAILED: '失败',
    FULL: '已满',
    RECORDED: '已登记',
    VERIFIED: '已核验',
    VOID: '已作废',
  }
  return labels[key] || status || '未知'
}

export function timeSlotLabel(slot?: string): string {
  const labels: Record<string, string> = {
    MORNING: '上午',
    AFTERNOON: '下午',
    EVENING: '夜钓',
    FULL_DAY: '全天',
  }
  return labels[String(slot || '').toUpperCase()] || slot || '未定时段'
}

export function statusType(
  status?: string,
): 'success' | 'warning' | 'danger' | 'info' | 'primary' {
  const key = String(status || '').toUpperCase()
  if (['AVAILABLE', 'FREE', 'IDLE', 'OPEN', 'ACTIVE', 'ENABLED', 'PAID', 'CONFIRMED', 'COMPLETED', 'ON_SALE', 'RECORDED', 'VERIFIED'].includes(key)) {
    return 'success'
  }
  if (['PENDING', 'UNPAID', 'PENDING_PAYMENT', 'RESERVED', 'BOOKED', 'MAINTENANCE', 'FULL', 'NO_SHOW'].includes(key)) return 'warning'
  if (['CANCELLED', 'CANCELED', 'DISABLED', 'INACTIVE', 'OUT_OF_STOCK', 'CLOSED', 'FAILED', 'VOID'].includes(key)) return 'danger'
  if (['OCCUPIED', 'IN_USE'].includes(key)) return 'primary'
  return 'info'
}
