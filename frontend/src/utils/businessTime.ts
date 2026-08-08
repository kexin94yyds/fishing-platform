export const BUSINESS_TIME_ZONE = 'Asia/Shanghai'

export function businessDateValue(date = new Date()): string {
  const parts = new Intl.DateTimeFormat('zh-CN', {
    timeZone: BUSINESS_TIME_ZONE,
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  }).formatToParts(date)
  const values = Object.fromEntries(parts.map((part) => [part.type, part.value]))
  return `${values.year}-${values.month}-${values.day}`
}

export function calendarDateValue(date: Date): string {
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

export function utcDateFromValue(value: string): Date {
  return new Date(`${value}T00:00:00Z`)
}

export function utcDateValue(date: Date): string {
  return date.toISOString().slice(0, 10)
}
