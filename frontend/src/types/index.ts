export type Id = string | number

export interface ApiResponse<T> {
  success: boolean
  message: string
  data: T
  timestamp: string | number
}

export interface PageResult<T> {
  records: T[]
  total: number
}

export interface CurrentUser {
  id: Id
  username: string
  displayName: string
  role: string
}

export interface RegistrationStatus {
  enabled: boolean
}

export interface Zone {
  id: Id
  name: string
  code: string
  description?: string
  status: string
  spotCount?: number
  createdAt?: string
  updatedAt?: string
}

export interface Spot {
  id: Id
  zoneId: Id
  zoneName?: string
  code: string
  name: string
  status: string
  capacity: number
  mapX?: number | null
  mapY?: number | null
  note?: string
  createdAt?: string
  updatedAt?: string
}

export interface Booking {
  id: Id
  bookingNo?: string
  memberId?: Id
  memberName?: string
  spotId: Id
  spotName?: string
  zoneName?: string
  fishingDate: string
  timeSlot: string
  guests: number
  status: string
  amount?: number
  notes?: string
  cancelledAt?: string
  createdAt?: string
  updatedAt?: string
}

export interface RecentBooking {
  bookingNo: string
  memberName?: string
  spotName: string
  fishingDate: string
  timeSlot: string
  status: string
  amount: number
}

export interface CatchRecord {
  id: Id
  catchNo?: string
  bookingId?: Id
  bookingNo?: string
  memberId?: Id
  memberName?: string
  spotId: Id
  spotName?: string
  species: string
  weight: number
  quantity: number
  fishingDate: string
  status: string
  notes?: string
  createdAt?: string
  updatedAt?: string
}

export interface Member {
  id: Id
  memberNo?: string
  name: string
  phone: string
  level?: string
  points?: number
  status: string
  createdAt?: string
  updatedAt?: string
}

export interface Product {
  id: Id
  sku: string
  name: string
  category: string
  price: number
  stockQuantity: number
  status: string
  version: number
  createdAt?: string
  updatedAt?: string
}

export interface SaleOrderItem {
  id?: Id
  orderId?: Id
  productId: Id
  productName?: string
  quantity: number
  unitPrice?: number
  lineAmount?: number
}

export interface SaleOrder {
  id: Id
  orderNo?: string
  memberId?: Id
  memberName?: string
  items?: SaleOrderItem[]
  totalAmount: number
  status: string
  paymentStatus?: string
  createdAt?: string
  updatedAt?: string
}

export interface SaleCreatePayload {
  memberId?: Id
  items: Array<Pick<SaleOrderItem, 'productId' | 'quantity'>>
  paymentMethod?: string
}

export interface SaleCreateResult {
  order: SaleOrder
  payment?: Payment
  items: SaleOrderItem[]
}

export interface Payment {
  id: Id
  paymentNo?: string
  businessType?: string
  businessId?: Id
  businessNo?: string
  amount: number
  method?: string
  status: string
  confirmedAt?: string
  createdAt?: string
  updatedAt?: string
}

export interface TrendPoint {
  label: string
  value: number
  secondary?: number
}

export interface DashboardSummary {
  revenueToday: number | null
  visitorsToday: number | null
  bookingsToday: number | null
  availableSpots: number | null
  pendingPayments: number | null
  memberTotal: number | null
  lowStockProducts: number | null
  todayCatchCount: number | null
  trafficTrend: TrendPoint[]
  bookingMix: Array<{ name: string; value: number }>
  recentBookings: RecentBooking[]
}

export interface TrafficPoint {
  statDate: string
  visits: number
  uniqueVisitors: number
  newMembers: number
  bookingCount: number
  revenue: number
}

export interface TrafficAnalytics {
  days: number
  series: TrafficPoint[]
  totalVisitors: number | null
  peakDailyUniqueVisitors: number | null
  newMembers: number | null
  bookingCount: number | null
  revenue: number | null
}

export interface Availability {
  spotId: Id
  spotCode: string
  spotName?: string
  zoneId?: Id
  zoneName?: string
  fishingDate?: string
  timeSlot?: string
  capacity?: number
  reservedCount?: number
  availableCount?: number
  status: string
}
