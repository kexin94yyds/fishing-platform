<script setup lang="ts">
import { computed, nextTick, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { Plus, Refresh, Search } from '@element-plus/icons-vue'
import ResourceState from '@/components/ResourceState.vue'
import StatusTag from '@/components/StatusTag.vue'
import { bookingApi, memberApi, spotApi } from '@/api'
import { errorMessage } from '@/api/http'
import { useAuthStore } from '@/stores/auth'
import { formatCurrency, formatDate, timeSlotLabel } from '@/utils/format'
import {
  businessDateValue,
  calendarDateValue,
  utcDateFromValue,
  utcDateValue,
} from '@/utils/businessTime'
import type { Availability, Booking, Id, Member, Spot } from '@/types'

const auth = useAuthStore()
const canSettleBookings = computed(() => auth.isAdmin)
const loading = ref(true)
const error = ref('')
const bookings = ref<Booking[]>([])
const members = ref<Member[]>([])
const spots = ref<Spot[]>([])
const availability = ref<Availability[]>([])
const availabilityChecked = ref(false)
const dialogOpen = ref(false)
const saving = ref(false)
const checkingAvailability = ref(false)
const settlingId = ref<Id | null>(null)
const settlingAction = ref<'complete' | 'noShow' | null>(null)
const formRef = ref<FormInstance>()
const formShellRef = ref<HTMLElement>()

const todayValue = ref(businessDateValue())
const weekAnchor = ref(todayValue.value)
const mobileSelectedDate = ref(todayValue.value)
const filters = reactive({
  status: '',
  keyword: '',
})

const slotOptions = [
  { value: 'MORNING', label: '上午', note: '早场' },
  { value: 'AFTERNOON', label: '下午', note: '午后' },
  { value: 'EVENING', label: '夜钓', note: '夜场' },
]

type BookingForm = {
  memberId: Id | ''
  spotId: Id | ''
  fishingDate: string
  timeSlot: string
  guests: number
  amount: number
  notes: string
}

const form = reactive<BookingForm>({
  memberId: '',
  spotId: '',
  fishingDate: '',
  timeSlot: 'MORNING',
  guests: 1,
  amount: 0,
  notes: '',
})

const rules: FormRules = {
  spotId: [{ required: true, message: '请选择钓位', trigger: 'change' }],
  fishingDate: [{ required: true, message: '请选择垂钓日期', trigger: 'change' }],
  timeSlot: [{ required: true, message: '请选择时段', trigger: 'change' }],
  guests: [{ required: true, message: '请填写到场人数', trigger: 'change' }],
  amount: [
    { required: true, message: '请填写预约金额', trigger: 'change' },
    { type: 'number', min: 0, message: '预约金额不能小于 0', trigger: 'change' },
  ],
}

const weekDates = computed(() => {
  const anchor = utcDateFromValue(weekAnchor.value)
  const weekday = anchor.getUTCDay() || 7
  const monday = new Date(anchor)
  monday.setUTCDate(anchor.getUTCDate() - weekday + 1)
  const weekdayLabels = ['周一', '周二', '周三', '周四', '周五', '周六', '周日']
  return Array.from({ length: 7 }, (_, index) => {
    const date = new Date(monday)
    date.setUTCDate(monday.getUTCDate() + index)
    const value = utcDateValue(date)
    return {
      value,
      label: `${date.getUTCMonth() + 1}/${date.getUTCDate()}`,
      weekday: weekdayLabels[index],
      isToday: value === todayValue.value,
      isPast: value < todayValue.value,
    }
  })
})

const firstSchedulableDate = computed(
  () => weekDates.value.find((day) => !day.isPast)?.value ?? todayValue.value,
)

const selectedMobileDay = computed(
  () =>
    weekDates.value.find((day) => day.value === mobileSelectedDate.value) ??
    weekDates.value[0],
)

const visibleBookings = computed(() => {
  const query = filters.keyword.trim().toLowerCase()
  return bookings.value.filter((booking) => {
    const text = `${booking.bookingNo || ''} ${booking.memberName || ''} ${booking.spotName || ''}`.toLowerCase()
    return (
      (!query || text.includes(query)) &&
      (!filters.status || booking.status === filters.status)
    )
  })
})

const availableSpots = computed(() => {
  if (!availabilityChecked.value) return []
  const allowed = new Set(
    availability.value
      .filter(
        (item) =>
          ['AVAILABLE', 'OPEN'].includes(item.status.toUpperCase()) &&
          (item.availableCount ?? 0) >= form.guests,
      )
      .map((item) => String(item.spotId)),
  )
  return spots.value.filter((spot) => allowed.has(String(spot.id)))
})

function disablePastDate(date: Date) {
  return calendarDateValue(date) < todayValue.value
}

function bookingsFor(date: string, slot: string) {
  return visibleBookings.value.filter(
    (booking) => booking.fishingDate === date && booking.timeSlot === slot,
  )
}

function dayTotal(date: string) {
  return visibleBookings.value.filter((booking) => booking.fishingDate === date).length
}

async function load() {
  todayValue.value = businessDateValue()
  loading.value = true
  error.value = ''
  if (!weekDates.value.some((day) => day.value === mobileSelectedDate.value)) {
    mobileSelectedDate.value = weekDates.value[0]?.value ?? weekAnchor.value
  }
  try {
    const [weekResults, memberResult, spotResult] = await Promise.all([
      Promise.all(weekDates.value.map((day) => bookingApi.list({ date: day.value }))),
      memberApi.list(),
      spotApi.list(),
    ])
    const unique = new Map<string, Booking>()
    weekResults.flatMap((result) => result.records).forEach((booking) => {
      unique.set(String(booking.id), booking)
    })
    bookings.value = [...unique.values()]
    members.value = memberResult.records.filter((member) => member.status === 'ACTIVE')
    spots.value = spotResult.records.filter((spot) => spot.status === 'OPEN')
  } catch (reason) {
    error.value = errorMessage(reason)
  } finally {
    loading.value = false
  }
}

function handleWeekChange(value: string | null) {
  if (value) mobileSelectedDate.value = value
  void load()
}

function openCreate(fishingDate = '', timeSlot = 'MORNING') {
  todayValue.value = businessDateValue()
  if (fishingDate && fishingDate < todayValue.value) {
    ElMessage.warning('过去日期不能再安排预订')
    return
  }
  Object.assign(form, {
    memberId: '',
    spotId: '',
    fishingDate,
    timeSlot,
    guests: 1,
    amount: 0,
    notes: '',
  })
  availability.value = []
  availabilityChecked.value = false
  formRef.value?.clearValidate()
  dialogOpen.value = true
  if (fishingDate && timeSlot) void checkAvailability()
}

async function checkAvailability() {
  form.spotId = ''
  if (!form.fishingDate || !form.timeSlot) {
    availability.value = []
    availabilityChecked.value = false
    return
  }
  if (form.fishingDate < businessDateValue()) {
    availability.value = []
    availabilityChecked.value = false
    ElMessage.warning('垂钓日期不能早于今天')
    return
  }
  checkingAvailability.value = true
  availabilityChecked.value = false
  try {
    const result = await bookingApi.availability({
      date: form.fishingDate,
      timeSlot: form.timeSlot,
    })
    availability.value = result.records
    availabilityChecked.value = true
  } catch (reason) {
    ElMessage.error(errorMessage(reason))
  } finally {
    checkingAvailability.value = false
  }
}

function handleGuestsChange() {
  if (
    form.spotId &&
    !availableSpots.value.some((spot) => String(spot.id) === String(form.spotId))
  ) {
    form.spotId = ''
    ElMessage.warning('到场人数已超过原钓位余量，请重新选择钓位')
  }
}

async function focusFirstInvalidField() {
  await nextTick()
  const invalidItem = formShellRef.value?.querySelector<HTMLElement>('.el-form-item.is-error')
  const control = invalidItem?.querySelector<HTMLElement>(
    'input:not([disabled]), textarea:not([disabled]), [role="combobox"]:not([aria-disabled="true"]), [tabindex]:not([tabindex="-1"])',
  )
  control?.focus({ preventScroll: true })
  invalidItem?.scrollIntoView({ block: 'center' })
}

async function save() {
  if (!formRef.value || saving.value) return
  try {
    await formRef.value.validate()
  } catch {
    await focusFirstInvalidField()
    return
  }
  if (form.fishingDate < businessDateValue()) {
    ElMessage.warning('垂钓日期不能早于今天')
    return
  }
  if (!availableSpots.value.some((spot) => String(spot.id) === String(form.spotId))) {
    form.spotId = ''
    ElMessage.warning('所选钓位已不满足当前人数，请重新选择')
    return
  }
  saving.value = true
  try {
    await bookingApi.create({
      memberId: form.memberId || undefined,
      spotId: form.spotId,
      fishingDate: form.fishingDate,
      timeSlot: form.timeSlot,
      guests: form.guests,
      amount: form.amount,
      notes: form.notes,
    })
    ElMessage.success('预订已创建')
    dialogOpen.value = false
    await load()
  } catch (reason) {
    ElMessage.error(errorMessage(reason))
  } finally {
    saving.value = false
  }
}

async function cancelBooking(booking: Booking) {
  if (!canCancelBooking(booking)) {
    ElMessage.warning('仅可取消垂钓日前的已确认预约')
    return
  }
  try {
    await ElMessageBox.confirm(
      `确认取消预订 ${booking.bookingNo || booking.id} 吗？`,
      '取消预订',
      {
        confirmButtonText: '确认取消',
        cancelButtonText: '返回',
        type: 'warning',
      },
    )
    await bookingApi.cancel(booking.id)
    ElMessage.success('预订已取消')
    await load()
  } catch (reason) {
    if (reason !== 'cancel' && reason !== 'close') ElMessage.error(errorMessage(reason))
  }
}

function canCancelBooking(booking: Booking) {
  return booking.status === 'CONFIRMED' && booking.fishingDate > todayValue.value
}

function canSettleBooking(booking: Booking) {
  return booking.status === 'CONFIRMED' && booking.fishingDate <= todayValue.value
}

async function settleBooking(booking: Booking, action: 'complete' | 'noShow') {
  if (!canSettleBookings.value || !canSettleBooking(booking)) return
  const noShow = action === 'noShow'
  try {
    await ElMessageBox.confirm(
      noShow
        ? `确认将预订 ${booking.bookingNo || booking.id} 标记为爽约吗？`
        : `确认完成预订 ${booking.bookingNo || booking.id} 的结单吗？`,
      noShow ? '标记爽约' : '完成结单',
      {
        confirmButtonText: noShow ? '确认爽约' : '确认完成',
        cancelButtonText: '返回',
        type: noShow ? 'warning' : 'success',
      },
    )
    settlingId.value = booking.id
    settlingAction.value = action
    if (noShow) await bookingApi.noShow(booking.id)
    else await bookingApi.complete(booking.id)
    ElMessage.success(noShow ? '预订已标记为爽约' : '预订已完成结单')
    await load()
  } catch (reason) {
    if (reason !== 'cancel' && reason !== 'close') ElMessage.error(errorMessage(reason))
  } finally {
    settlingId.value = null
    settlingAction.value = null
  }
}

onMounted(load)
</script>

<template>
  <div class="booking-roster">
    <header class="roster-head">
      <div>
        <span class="station-kicker">本周排班</span>
        <h1>日期与时段预约板</h1>
        <p>按日期与固定时段查看安排，点击空档可直接创建预订。</p>
      </div>
      <div class="roster-head__tools">
        <el-date-picker
          v-model="weekAnchor"
          type="date"
          value-format="YYYY-MM-DD"
          placeholder="选择所在周"
          aria-label="选择预约所在周"
          style="width: 150px"
          @change="handleWeekChange"
        />
        <el-button :icon="Refresh" :loading="loading" @click="load">刷新本周</el-button>
        <el-button type="primary" :icon="Plus" @click="openCreate()">新增预订</el-button>
      </div>
    </header>

    <div class="roster-filters">
      <el-input
        v-model="filters.keyword"
        :prefix-icon="Search"
        clearable
        placeholder="查订单、会员或钓位"
        aria-label="搜索订单、会员或钓位"
        style="width: 230px"
      />
      <el-select
        v-model="filters.status"
        clearable
        placeholder="全部状态"
        aria-label="按预约状态筛选"
        style="width: 130px"
      >
        <el-option label="已确认" value="CONFIRMED" />
        <el-option label="已完成" value="COMPLETED" />
        <el-option label="爽约" value="NO_SHOW" />
        <el-option label="已取消" value="CANCELLED" />
      </el-select>
      <span>本周 {{ visibleBookings.length }} 笔预约</span>
    </div>

    <ResourceState
      :loading="loading"
      :error="error"
      @retry="load"
    >
      <div v-if="!bookings.length" class="roster-empty-note">
        <div>
          <strong>本周暂无预约</strong>
          <span>下方 21 个时段空档均可直接安排。</span>
        </div>
        <el-button type="primary" @click="openCreate(firstSchedulableDate)">
          安排第一笔
        </el-button>
      </div>

      <div class="roster-scroll">
        <section class="week-board" aria-label="本周预约排班">
          <div class="week-board__corner">时段</div>
          <header
            v-for="day in weekDates"
            :key="day.value"
            :class="{ today: day.isToday }"
            :aria-current="day.isToday ? 'date' : undefined"
          >
            <span>
              {{ day.weekday }}
              <em v-if="day.isToday">今天</em>
            </span>
            <strong>{{ day.label }}</strong>
            <small>{{ dayTotal(day.value) }} 单</small>
          </header>

          <template v-for="slot in slotOptions" :key="slot.value">
            <aside class="slot-label">
              <strong>{{ slot.label }}</strong>
              <span>{{ slot.note }}</span>
            </aside>
            <div
              v-for="day in weekDates"
              :key="`${day.value}-${slot.value}`"
              class="schedule-cell"
              :class="{ today: day.isToday }"
            >
              <div v-if="bookingsFor(day.value, slot.value).length" class="booking-stack">
                <el-popover
                  v-for="booking in bookingsFor(day.value, slot.value)"
                  :key="booking.id"
                  placement="top"
                  width="230"
                  trigger="click"
                >
                  <template #reference>
                    <button
                      type="button"
                      class="booking-ticket"
                      :class="{ cancelled: booking.status === 'CANCELLED' }"
                    >
                      <span>{{ booking.spotName || '待安排钓位' }}</span>
                      <strong>{{ booking.memberName || '散客' }}</strong>
                      <small>{{ booking.guests }} 人</small>
                    </button>
                  </template>
                  <div class="booking-popover">
                    <strong>{{ booking.bookingNo || booking.id }}</strong>
                    <p>{{ formatDate(booking.fishingDate) }} {{ timeSlotLabel(booking.timeSlot) }}</p>
                    <p>{{ formatCurrency(booking.amount) }}</p>
                    <StatusTag :status="booking.status" />
                    <el-button
                      link
                      type="danger"
                      :disabled="!canCancelBooking(booking)"
                      :title="canCancelBooking(booking) ? undefined : '仅可取消垂钓日前的已确认预约'"
                      @click="cancelBooking(booking)"
                    >
                      取消预订
                    </el-button>
                    <template v-if="canSettleBookings && booking.status === 'CONFIRMED'">
                      <el-button
                        link
                        type="primary"
                        :disabled="!canSettleBooking(booking)"
                        :loading="settlingId === booking.id && settlingAction === 'complete'"
                        :title="canSettleBooking(booking) ? undefined : '未来预约不能结单'"
                        @click="settleBooking(booking, 'complete')"
                      >
                        完成结单
                      </el-button>
                      <el-button
                        link
                        type="warning"
                        :disabled="!canSettleBooking(booking)"
                        :loading="settlingId === booking.id && settlingAction === 'noShow'"
                        :title="canSettleBooking(booking) ? undefined : '未来预约不能结单'"
                        @click="settleBooking(booking, 'noShow')"
                      >
                        标记爽约
                      </el-button>
                    </template>
                  </div>
                </el-popover>
              </div>
              <button
                type="button"
                class="empty-slot"
                :class="{
                  compact: bookingsFor(day.value, slot.value).length,
                  priority: day.isToday,
                }"
                :aria-label="`安排${day.weekday}${day.label}${slot.label}预订`"
                :disabled="day.isPast"
                @click="openCreate(day.value, slot.value)"
              >
                <span class="empty-slot__plus" aria-hidden="true">＋</span>
                <span class="empty-slot__label">
                  {{ bookingsFor(day.value, slot.value).length ? '继续安排' : '安排' }}
                </span>
              </button>
            </div>
          </template>
        </section>
      </div>

      <section class="mobile-board" aria-label="本周预约排班">
        <div class="mobile-date-strip" role="group" aria-label="选择查看日期">
          <button
            v-for="day in weekDates"
            :key="day.value"
            type="button"
            class="mobile-date"
            :class="{ selected: day.value === mobileSelectedDate, today: day.isToday }"
            :aria-pressed="day.value === mobileSelectedDate"
            :aria-current="day.isToday ? 'date' : undefined"
            :aria-label="`${day.weekday} ${day.label}，${dayTotal(day.value)} 笔预约`"
            @click="mobileSelectedDate = day.value"
          >
            <span>{{ day.weekday }}</span>
            <strong>{{ day.label }}</strong>
            <small>{{ dayTotal(day.value) }} 单</small>
          </button>
        </div>

        <div class="mobile-day-summary">
          <div>
            <span>{{ selectedMobileDay?.weekday }}</span>
            <strong>{{ selectedMobileDay?.label }}</strong>
          </div>
          <small>{{ dayTotal(mobileSelectedDate) }} 笔预约</small>
        </div>

        <div class="mobile-slot-list">
          <article v-for="slot in slotOptions" :key="slot.value" class="mobile-slot-card">
            <header>
              <div>
                <strong>{{ slot.label }}</strong>
                <span>{{ slot.note }}</span>
              </div>
              <small>{{ bookingsFor(mobileSelectedDate, slot.value).length }} 单</small>
            </header>

            <div
              v-if="bookingsFor(mobileSelectedDate, slot.value).length"
              class="booking-stack"
            >
              <el-popover
                v-for="booking in bookingsFor(mobileSelectedDate, slot.value)"
                :key="booking.id"
                placement="top"
                width="230"
                trigger="click"
              >
                <template #reference>
                  <button
                    type="button"
                    class="booking-ticket"
                    :class="{ cancelled: booking.status === 'CANCELLED' }"
                  >
                    <span>{{ booking.spotName || '待安排钓位' }}</span>
                    <strong>{{ booking.memberName || '散客' }}</strong>
                    <small>{{ booking.guests }} 人</small>
                  </button>
                </template>
                <div class="booking-popover">
                  <strong>{{ booking.bookingNo || booking.id }}</strong>
                  <p>{{ formatDate(booking.fishingDate) }} {{ timeSlotLabel(booking.timeSlot) }}</p>
                  <p>{{ formatCurrency(booking.amount) }}</p>
                  <StatusTag :status="booking.status" />
                  <el-button
                    link
                    type="danger"
                    :disabled="!canCancelBooking(booking)"
                    :title="canCancelBooking(booking) ? undefined : '仅可取消垂钓日前的已确认预约'"
                    @click="cancelBooking(booking)"
                  >
                    取消预订
                  </el-button>
                  <template v-if="canSettleBookings && booking.status === 'CONFIRMED'">
                    <el-button
                      link
                      type="primary"
                      :disabled="!canSettleBooking(booking)"
                      :loading="settlingId === booking.id && settlingAction === 'complete'"
                      :title="canSettleBooking(booking) ? undefined : '未来预约不能结单'"
                      @click="settleBooking(booking, 'complete')"
                    >
                      完成结单
                    </el-button>
                    <el-button
                      link
                      type="warning"
                      :disabled="!canSettleBooking(booking)"
                      :loading="settlingId === booking.id && settlingAction === 'noShow'"
                      :title="canSettleBooking(booking) ? undefined : '未来预约不能结单'"
                      @click="settleBooking(booking, 'noShow')"
                    >
                      标记爽约
                    </el-button>
                  </template>
                </div>
              </el-popover>
            </div>

            <button
              type="button"
              class="empty-slot"
              :class="{ compact: bookingsFor(mobileSelectedDate, slot.value).length }"
              :aria-label="`安排${selectedMobileDay?.weekday ?? ''}${selectedMobileDay?.label ?? ''}${slot.label}预订`"
              :disabled="mobileSelectedDate < todayValue"
              @click="openCreate(mobileSelectedDate, slot.value)"
            >
              {{
                bookingsFor(mobileSelectedDate, slot.value).length
                  ? '+ 继续安排'
                  : `安排${slot.label}`
              }}
            </button>
          </article>
        </div>
      </section>
    </ResourceState>

    <el-dialog v-model="dialogOpen" title="新增时段预订" width="680px" destroy-on-close>
      <div ref="formShellRef">
        <el-form
          ref="formRef"
          :model="form"
          :rules="rules"
          label-position="top"
          aria-live="polite"
          @submit.prevent="save"
        >
          <div class="form-grid">
            <el-form-item label="会员">
              <el-select
                v-model="form.memberId"
                clearable
                filterable
                placeholder="不选择则按散客登记"
                style="width: 100%"
              >
                <el-option
                  v-for="member in members"
                  :key="member.id"
                  :label="`${member.name} ${member.phone}`"
                  :value="member.id"
                />
              </el-select>
            </el-form-item>
            <el-form-item label="到场人数" prop="guests">
              <el-input-number
                v-model="form.guests"
                :min="1"
                controls-position="right"
                @change="handleGuestsChange"
              />
            </el-form-item>
            <el-form-item label="垂钓日期" prop="fishingDate">
              <el-date-picker
                v-model="form.fishingDate"
                type="date"
                value-format="YYYY-MM-DD"
                :disabled-date="disablePastDate"
                placeholder="请选择日期"
                aria-label="选择垂钓日期"
                style="width: 100%"
                @change="checkAvailability"
              />
            </el-form-item>
            <el-form-item label="时段" prop="timeSlot">
              <el-select v-model="form.timeSlot" style="width: 100%" @change="checkAvailability">
                <el-option
                  v-for="slot in slotOptions"
                  :key="slot.value"
                  :label="slot.label"
                  :value="slot.value"
                />
              </el-select>
            </el-form-item>
            <el-form-item label="可用钓位" prop="spotId">
              <el-select
                v-model="form.spotId"
                filterable
                :loading="checkingAvailability"
                :disabled="!form.fishingDate || !form.timeSlot"
                :placeholder="
                  !availabilityChecked
                    ? '请先选择日期和时段'
                    : availableSpots.length
                      ? '请选择可用钓位'
                      : '当前时段暂无满足人数的钓位'
                "
                style="width: 100%"
              >
                <el-option
                  v-for="spot in availableSpots"
                  :key="spot.id"
                  :label="spot.name || spot.code"
                  :value="spot.id"
                />
              </el-select>
            </el-form-item>
            <el-form-item label="预约金额（元）" prop="amount">
              <el-input-number
                v-model="form.amount"
                :min="0"
                :precision="2"
                :step="10"
                controls-position="right"
              />
            </el-form-item>
            <el-form-item label="备注" class="form-span-2">
              <el-input
                v-model="form.notes"
                type="textarea"
                :rows="3"
                placeholder="填写特殊安排或客户需求"
              />
            </el-form-item>
          </div>
          <div class="dialog-actions">
            <el-button native-type="button" @click="dialogOpen = false">取消</el-button>
            <el-button type="primary" native-type="submit" :loading="saving">
              提交预订
            </el-button>
          </div>
        </el-form>
      </div>
    </el-dialog>
  </div>
</template>

<style scoped>
.roster-head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 22px;
  margin-bottom: 14px;
}

.roster-head h1 {
  margin: 0;
  color: var(--lake-900);
  font-size: clamp(24px, 2.2vw, 32px);
  letter-spacing: -0.04em;
}

.roster-head p {
  margin: 6px 0 0;
  color: var(--ink-500);
  font-size: 11px;
}

.roster-head__tools,
.roster-filters {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
}

.roster-filters {
  margin-bottom: 12px;
  padding: 10px 12px;
  border: 1px solid #e2dacd;
  border-radius: 11px;
  background: var(--paper-100);
}

.roster-filters > span {
  margin-left: auto;
  color: var(--ink-500);
  font-size: 10px;
}

.roster-empty-note {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  margin-bottom: 10px;
  padding: 11px 13px;
  border-left: 3px solid var(--clay-600);
  color: #5d6d65;
  background: #f6ecdf;
}

.roster-empty-note > div {
  display: grid;
  gap: 2px;
}

.roster-empty-note strong {
  color: #30463c;
  font-size: 11px;
}

.roster-empty-note span {
  font-size: 11px;
}

.roster-scroll {
  overflow-x: auto;
  border: 1px solid #ded5c8;
  border-radius: 14px;
  background: var(--paper-50);
}

.mobile-board {
  display: none;
}

.week-board {
  display: grid;
  min-width: 1030px;
  grid-template-columns: 105px repeat(7, minmax(128px, 1fr));
}

.week-board__corner,
.week-board > header,
.slot-label,
.schedule-cell {
  border-right: 1px solid var(--paper-300);
  border-bottom: 1px solid var(--paper-300);
}

.week-board__corner {
  display: grid;
  place-items: center;
  color: #8b938e;
  background: #f3eee4;
  font-size: 10px;
}

.week-board > header {
  padding: 12px 10px;
  text-align: center;
  background: var(--paper-100);
}

.week-board > header.today {
  color: #f7f2e9;
  background: var(--lake-900);
}

.week-board > header span,
.week-board > header strong,
.week-board > header small {
  display: block;
}

.week-board > header span,
.week-board > header small {
  color: inherit;
  opacity: 0.65;
  font-size: 11px;
}

.week-board > header span em {
  margin-left: 4px;
  font-size: 9px;
  font-style: normal;
  font-weight: 750;
  letter-spacing: 0.08em;
}

.week-board > header strong {
  margin: 4px 0;
  font-size: 15px;
}

.slot-label {
  display: flex;
  min-height: 150px;
  flex-direction: column;
  justify-content: center;
  padding: 12px;
  background: var(--paper-100);
}

.slot-label strong {
  color: var(--lake-900);
  font-size: 13px;
}

.slot-label span {
  margin-top: 5px;
  color: #9a8d7d;
  font-size: 11px;
}

.schedule-cell {
  min-height: 150px;
  padding: 8px;
  background: var(--paper-50);
}

.schedule-cell.today {
  background: #fbf6ec;
}

.booking-stack {
  display: grid;
  gap: 6px;
}

.booking-ticket {
  display: block;
  width: 100%;
  padding: 9px;
  border: 0;
  border-left: 3px solid var(--clay-600);
  border-radius: 0 8px 8px 0;
  color: #34443d;
  background: var(--lake-50);
  text-align: left;
  cursor: pointer;
}

.booking-ticket span,
.booking-ticket strong,
.booking-ticket small {
  display: block;
}

.booking-ticket span {
  color: var(--lake-800);
  font-size: 11px;
  font-weight: 700;
}

.booking-ticket strong {
  margin-top: 4px;
  overflow: hidden;
  font-size: 11px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.booking-ticket small {
  margin-top: 4px;
  color: var(--ink-500);
  font-size: 11px;
}

.booking-ticket.cancelled {
  border-left-color: #aaaca8;
  opacity: 0.55;
  background: #f2f0eb;
}

.empty-slot {
  display: flex;
  width: 100%;
  min-height: 132px;
  align-items: center;
  justify-content: center;
  gap: 5px;
  border: 1px dashed transparent;
  border-radius: 8px;
  color: #9a8d7d;
  background: transparent;
  font-size: 10px;
  cursor: pointer;
}

.empty-slot.compact {
  min-height: 30px;
  margin-top: 6px;
  border-color: var(--paper-400);
}

.empty-slot__plus {
  color: var(--lake-700);
  font-size: 18px;
  line-height: 1;
  opacity: 0.62;
}

.empty-slot__label {
  max-width: 0;
  overflow: hidden;
  opacity: 0;
  transition:
    max-width 140ms ease,
    opacity 140ms ease;
  white-space: nowrap;
}

.empty-slot.compact .empty-slot__label,
.empty-slot.priority .empty-slot__label,
.schedule-cell:hover .empty-slot__label,
.empty-slot:focus-visible .empty-slot__label {
  max-width: 64px;
  opacity: 1;
}

.empty-slot.priority,
.schedule-cell:hover .empty-slot,
.empty-slot:focus-visible {
  border-color: var(--paper-400);
}

.empty-slot:hover {
  border-color: var(--lake-800);
  color: var(--lake-800);
  background: var(--lake-50);
}

.booking-ticket,
.empty-slot,
.mobile-date {
  touch-action: manipulation;
}

.booking-ticket:focus-visible,
.empty-slot:focus-visible,
.mobile-date:focus-visible {
  outline: 3px solid rgb(180 97 69 / 35%);
  outline-offset: 2px;
}

.booking-popover p {
  margin: 7px 0;
  color: var(--ink-500);
  font-size: 11px;
}

.booking-popover .el-button {
  margin-left: 8px;
}

.dialog-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 4px;
  padding-top: 18px;
  border-top: 1px solid var(--paper-300);
}

.dialog-actions .el-button + .el-button {
  margin-left: 0;
}

@media (pointer: coarse) {
  .booking-ticket,
  .empty-slot.compact,
  .mobile-date {
    min-height: 44px;
  }

  .empty-slot {
    border-color: var(--paper-400);
  }

  .empty-slot__label {
    max-width: 80px;
    opacity: 1;
  }
}

@media (max-width: 840px) {
  .roster-head {
    display: block;
  }

  .roster-head__tools {
    margin-top: 13px;
  }
}

@media (max-width: 759px) {
  .roster-scroll {
    display: none;
  }

  .mobile-board {
    display: block;
  }

  .mobile-date-strip {
    display: flex;
    max-width: 100%;
    gap: 7px;
    overflow-x: auto;
    padding: 3px 3px 10px;
    overscroll-behavior-inline: contain;
    scroll-snap-type: x proximity;
    scrollbar-width: none;
  }

  .mobile-date-strip::-webkit-scrollbar {
    display: none;
  }

  .mobile-date {
    flex: 0 0 70px;
    min-height: 68px;
    padding: 8px 6px;
    border: 1px solid #ded5c8;
    border-radius: 11px;
    color: #64736b;
    background: var(--paper-50);
    scroll-snap-align: start;
    cursor: pointer;
  }

  .mobile-date span,
  .mobile-date strong,
  .mobile-date small {
    display: block;
  }

  .mobile-date span,
  .mobile-date small {
    font-size: 11px;
  }

  .mobile-date strong {
    margin: 4px 0;
    color: var(--lake-900);
    font-size: 14px;
  }

  .mobile-date small {
    color: #8b938e;
  }

  .mobile-date.today:not(.selected) {
    border-color: var(--clay-600);
  }

  .mobile-date.selected {
    border-color: var(--lake-900);
    color: #f7f2e9;
    background: var(--lake-900);
  }

  .mobile-date.selected strong,
  .mobile-date.selected small {
    color: inherit;
  }

  .mobile-date.selected small {
    opacity: 0.72;
  }

  .mobile-day-summary {
    display: flex;
    align-items: baseline;
    justify-content: space-between;
    margin: 5px 0 10px;
    padding: 0 2px;
  }

  .mobile-day-summary > div {
    display: flex;
    align-items: baseline;
    gap: 7px;
  }

  .mobile-day-summary span {
    color: var(--lake-900);
    font-size: 13px;
    font-weight: 700;
  }

  .mobile-day-summary strong {
    color: #52635a;
    font-size: 11px;
  }

  .mobile-day-summary small {
    color: var(--ink-500);
    font-size: 10px;
  }

  .mobile-slot-list {
    display: grid;
    gap: 10px;
  }

  .mobile-slot-card {
    padding: 12px;
    border: 1px solid #ded5c8;
    border-radius: 13px;
    background: var(--paper-50);
  }

  .mobile-slot-card > header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-bottom: 9px;
  }

  .mobile-slot-card > header > div {
    display: flex;
    align-items: baseline;
    gap: 7px;
  }

  .mobile-slot-card > header strong {
    color: var(--lake-900);
    font-size: 14px;
  }

  .mobile-slot-card > header span,
  .mobile-slot-card > header small {
    color: #8b938e;
    font-size: 11px;
  }

  .mobile-slot-card .booking-ticket {
    padding: 11px 12px;
  }

  .mobile-slot-card .empty-slot {
    min-height: 58px;
    border-color: var(--paper-400);
  }

  .mobile-slot-card .empty-slot__label {
    max-width: 90px;
    opacity: 1;
  }

  .mobile-slot-card .empty-slot.compact {
    min-height: 44px;
    margin-top: 8px;
  }
}

@media (max-width: 480px) {
  .roster-head__tools > * {
    flex: 1 1 46%;
  }

  .roster-filters > * {
    width: 100% !important;
  }

  .roster-filters > span {
    margin-left: 0;
  }

  .roster-empty-note {
    align-items: stretch;
    flex-direction: column;
  }
}
</style>
