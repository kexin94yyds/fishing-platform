import { request } from './http'
import { toCurrentUser, toDashboard, toPage, toTraffic } from './adapters'
import type {
  Availability,
  Account,
  AccountAudit,
  BookingAudit,
  Booking,
  CatchCreatePayload,
  CatchRecord,
  CatchUpdatePayload,
  DashboardSummary,
  Id,
  Member,
  MemberActivity,
  MemberAudit,
  PageResult,
  Payment,
  Product,
  RegistrationStatus,
  SaleCreatePayload,
  SaleCreateResult,
  SaleOrder,
  SaleOrderDetail,
  SlotInventory,
  SlotInventoryPayload,
  Spot,
  TrafficAnalytics,
  TrafficDailyEntry,
  TrafficDailyUpsertPayload,
  Zone,
} from '@/types'

type Query = Record<string, string | number | boolean | undefined>
type ProductUpdatePayload = Pick<
  Product,
  'sku' | 'name' | 'category' | 'price' | 'stockQuantity' | 'status' | 'version'
>

export const authApi = {
  csrf: () => request<unknown>({ url: '/auth/csrf', method: 'GET' }),
  registration: () =>
    request<RegistrationStatus>({ url: '/auth/registration', method: 'GET' }),
  login: async (payload: { username: string; password: string }) => {
    const user = await request<unknown>({ url: '/auth/login', method: 'POST', data: payload }).then(
      toCurrentUser,
    )
    await request<unknown>({ url: '/auth/csrf', method: 'GET' })
    return user
  },
  register: async (payload: { username: string; displayName: string; password: string }) => {
    const user = await request<unknown>({ url: '/auth/register', method: 'POST', data: payload }).then(
      toCurrentUser,
    )
    await request<unknown>({ url: '/auth/csrf', method: 'GET' })
    return user
  },
  me: () => request<unknown>({ url: '/auth/me', method: 'GET' }).then(toCurrentUser),
  logout: () => request<void>({ url: '/auth/logout', method: 'POST' }),
  changePassword: (data: { currentPassword: string; newPassword: string }) =>
    request<void>({ url: '/auth/change-password', method: 'POST', data }),
}

export const accountApi = {
  list: (): Promise<PageResult<Account>> =>
    request<unknown>({ url: '/admin/accounts', method: 'GET' }).then((data) =>
      toPage<Account>(data, ['accounts']),
    ),
  create: (data: { username: string; displayName: string; password: string; role: Account['role'] }) =>
    request<Account>({ url: '/admin/accounts', method: 'POST', data }),
  update: (id: Account['id'], data: Pick<Account, 'displayName' | 'role' | 'enabled'> & { expectedVersion: number }) =>
    request<Account>({ url: `/admin/accounts/${id}`, method: 'PUT', data }),
  resetPassword: (id: Account['id'], data: { newPassword: string; expectedVersion: number }) =>
    request<Account>({ url: `/admin/accounts/${id}/reset-password`, method: 'POST', data }),
  audits: (params?: { targetId?: Id; limit?: number }): Promise<PageResult<AccountAudit>> =>
    request<unknown>({ url: '/admin/account-audits', method: 'GET', params }).then((data) =>
      toPage<AccountAudit>(data, ['audits']),
    ),
}

export const dashboardApi = {
  summary: (): Promise<DashboardSummary> =>
    request<unknown>({ url: '/dashboard/summary', method: 'GET' }).then(toDashboard),
}

export const zoneApi = {
  list: (params?: Query): Promise<PageResult<Zone>> =>
    request<unknown>({ url: '/zones', method: 'GET', params }).then((data) => toPage<Zone>(data, ['zones'])),
  create: (data: Partial<Zone>) => request<Zone>({ url: '/zones', method: 'POST', data }),
  update: (id: Zone['id'], data: Partial<Zone>) =>
    request<Zone>({ url: `/zones/${id}`, method: 'PUT', data }),
}

export const spotApi = {
  list: (params?: Query): Promise<PageResult<Spot>> =>
    request<unknown>({ url: '/spots', method: 'GET', params }).then((data) => toPage<Spot>(data, ['spots'])),
  map: (): Promise<PageResult<Spot>> =>
    request<unknown>({ url: '/spots/map', method: 'GET' }).then((data) => toPage<Spot>(data, ['spots', 'map'])),
  create: (data: Partial<Spot>) => request<Spot>({ url: '/spots', method: 'POST', data }),
  update: (id: Spot['id'], data: Partial<Spot>) =>
    request<Spot>({ url: `/spots/${id}`, method: 'PUT', data }),
}

export const bookingApi = {
  list: (params?: { status?: string; date?: string; memberId?: Id }): Promise<PageResult<Booking>> =>
    request<unknown>({
      url: '/bookings',
      method: 'GET',
      params: params
        ? { status: params.status, date: params.date, memberId: params.memberId }
        : undefined,
    }).then((data) =>
      toPage<Booking>(data, ['bookings']),
    ),
  availability: (params: { date?: string; timeSlot?: string; spotId?: Id }): Promise<PageResult<Availability>> =>
    request<unknown>({
      url: '/bookings/availability',
      method: 'GET',
      params: { date: params.date, timeSlot: params.timeSlot, spotId: params.spotId },
    }).then((data) =>
      toPage<Availability>(data, ['spots', 'availability']),
    ),
  slots: (params: { date?: string; spotId?: Id }): Promise<PageResult<SlotInventory>> =>
    request<unknown>({
      url: '/bookings/slots',
      method: 'GET',
      params: { date: params.date, spotId: params.spotId },
    }).then((data) => toPage<SlotInventory>(data, ['slots', 'inventories'])),
  configureSlot: (spotId: Id, data: SlotInventoryPayload) =>
    request<SlotInventory>({ url: `/bookings/slots/${spotId}`, method: 'PUT', data }),
  create: (data: Partial<Booking>) => request<Booking>({ url: '/bookings', method: 'POST', data }),
  cancel: (id: Booking['id']) =>
    request<Booking>({ url: `/bookings/${id}/cancel`, method: 'POST' }),
  complete: (id: Booking['id']) =>
    request<Booking>({ url: `/bookings/${id}/complete`, method: 'POST' }),
  noShow: (id: Booking['id']) =>
    request<Booking>({ url: `/bookings/${id}/no-show`, method: 'POST' }),
  audits: (id: Booking['id'], limit = 20): Promise<PageResult<BookingAudit>> =>
    request<unknown>({ url: `/bookings/${id}/audits`, method: 'GET', params: { limit } }).then((data) =>
      toPage<BookingAudit>(data, ['audits']),
    ),
}

export const catchApi = {
  list: (params?: Query): Promise<PageResult<CatchRecord>> =>
    request<unknown>({ url: '/catches', method: 'GET', params }).then((data) =>
      toPage<CatchRecord>(data, ['catches']),
    ),
  create: (data: CatchCreatePayload) => request<CatchRecord>({ url: '/catches', method: 'POST', data }),
  update: (id: CatchRecord['id'], data: CatchUpdatePayload) =>
    request<CatchRecord>({ url: `/catches/${id}`, method: 'PUT', data }),
}

export const memberApi = {
  list: (params?: Query): Promise<PageResult<Member>> =>
    request<unknown>({ url: '/members', method: 'GET', params }).then((data) =>
      toPage<Member>(data, ['members']),
    ),
  create: (data: Partial<Member>) => request<Member>({ url: '/members', method: 'POST', data }),
  update: (id: Member['id'], data: Partial<Member>) =>
    request<Member>({ url: `/members/${id}`, method: 'PUT', data }),
  activity: (id: Member['id']) =>
    request<MemberActivity>({ url: `/members/${id}/activity`, method: 'GET' }),
  audits: (id: Member['id'], limit = 20): Promise<PageResult<MemberAudit>> =>
    request<unknown>({ url: `/members/${id}/audits`, method: 'GET', params: { limit } }).then((data) =>
      toPage<MemberAudit>(data, ['audits']),
    ),
}

export const productApi = {
  list: (params?: Query): Promise<PageResult<Product>> =>
    request<unknown>({ url: '/products', method: 'GET', params }).then((data) =>
      toPage<Product>(data, ['products']),
    ),
  create: (data: Partial<Product>) => request<Product>({ url: '/products', method: 'POST', data }),
  update: (id: Product['id'], data: ProductUpdatePayload) =>
    request<Product>({ url: `/products/${id}`, method: 'PUT', data }),
}

export const saleOrderApi = {
  list: (params?: Query): Promise<PageResult<SaleOrder>> =>
    request<unknown>({ url: '/sales-orders', method: 'GET', params }).then((data) =>
      toPage<SaleOrder>(data, ['orders']),
    ),
  create: (data: SaleCreatePayload) =>
    request<SaleCreateResult>({ url: '/sales-orders', method: 'POST', data }),
  detail: (id: SaleOrder['id']) =>
    request<SaleOrderDetail>({ url: `/sales-orders/${id}`, method: 'GET' }),
  cancel: (id: SaleOrder['id']) =>
    request<SaleOrder>({ url: `/sales-orders/${id}/cancel`, method: 'POST' }),
}

export const paymentApi = {
  list: (params?: Query): Promise<PageResult<Payment>> =>
    request<unknown>({ url: '/payments', method: 'GET', params }).then((data) =>
      toPage<Payment>(data, ['payments']),
    ),
  confirm: (id: Payment['id'], method?: string) =>
    request<Payment>({
      url: `/payments/${id}/confirm`,
      method: 'POST',
      data: method ? { method } : {},
    }),
}

export const analyticsApi = {
  traffic: (days = 7): Promise<TrafficAnalytics> =>
    request<unknown>({ url: '/analytics/traffic', method: 'GET', params: { days } }).then(toTraffic),
}

export const trafficDailyApi = {
  list: (days = 30): Promise<TrafficDailyEntry[]> =>
    request<TrafficDailyEntry[]>({ url: '/traffic-daily', method: 'GET', params: { days } }),
  upsert: (statDate: string, data: TrafficDailyUpsertPayload): Promise<TrafficDailyEntry> =>
    request<TrafficDailyEntry>({ url: `/traffic-daily/${statDate}`, method: 'PUT', data }),
}
