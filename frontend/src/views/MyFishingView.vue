<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  Calendar,
  Key,
  Location,
  Phone,
  RefreshRight,
  SwitchButton,
  Tickets,
} from '@element-plus/icons-vue'
import ResourceState from '@/components/ResourceState.vue'
import StatusTag from '@/components/StatusTag.vue'
import { authApi, userBookingApi } from '@/api'
import { errorMessage } from '@/api/http'
import { useAuthStore } from '@/stores/auth'
import type { Availability, Booking, Id, UserBookingPayload } from '@/types'
import { businessDateValue, calendarDateValue } from '@/utils/businessTime'
import { formatCurrency, formatDate, statusLabel, timeSlotLabel } from '@/utils/format'

type TimeSlot = UserBookingPayload['timeSlot']
type ZoneOption = { id: Id; name: string }

const router = useRouter()
const auth = useAuthStore()
const today = businessDateValue()
const fishingDate = ref(today)
const timeSlot = ref<TimeSlot>('MORNING')
const selectedZoneId = ref<Id | ''>('')
const selectedSpotId = ref<Id | ''>('')
const availability = ref<Availability[]>([])
const bookings = ref<Booking[]>([])
const availabilityLoading = ref(true)
const bookingsLoading = ref(true)
const availabilityError = ref('')
const bookingsError = ref('')
const saving = ref(false)
const cancellingId = ref<Id | null>(null)
const loggingOut = ref(false)
const successBooking = ref<Booking | null>(null)
const passwordDialogOpen = ref(false)
const changingPassword = ref(false)
const passwordForm = reactive({ currentPassword: '', newPassword: '', confirmPassword: '' })
const form = reactive({ guests: 1, contactPhone: '', notes: '' })
let availabilityRequest = 0

const slots: Array<{ value: TimeSlot; label: string; hint: string }> = [
  { value: 'MORNING', label: '上午', hint: '06:00—11:30' },
  { value: 'AFTERNOON', label: '下午', hint: '12:30—17:30' },
  { value: 'EVENING', label: '夜钓', hint: '18:30—22:00' },
]

const zones = computed<ZoneOption[]>(() => {
  const records = new Map<string, ZoneOption>()
  availability.value.forEach((spot) => {
    if (spot.zoneId === undefined || !spot.zoneName) return
    records.set(String(spot.zoneId), { id: spot.zoneId, name: spot.zoneName })
  })
  return [...records.values()]
})

const visibleSpots = computed(() =>
  availability.value.filter(
    (spot) => !selectedZoneId.value || String(spot.zoneId) === String(selectedZoneId.value),
  ),
)

const selectedSpot = computed(
  () => availability.value.find((spot) => String(spot.spotId) === String(selectedSpotId.value)),
)

const availableCount = computed(() => Number(selectedSpot.value?.availableCount ?? 0))
const estimatedAmount = computed(() => Number(selectedSpot.value?.price ?? 0) * form.guests)
const futureBookings = computed(() =>
  bookings.value.filter((booking) => booking.fishingDate >= today && booking.status === 'CONFIRMED'),
)
const historyBookings = computed(() =>
  bookings.value.filter((booking) => !futureBookings.value.some((item) => String(item.id) === String(booking.id))),
)

function disablePastDate(date: Date) {
  return calendarDateValue(date) < today
}

function spotCanBook(spot: Availability) {
  return spot.status === 'AVAILABLE' && Number(spot.availableCount ?? 0) > 0
}

function occupancyPercent(spot: Availability) {
  const capacity = Number(spot.capacity ?? 0)
  if (capacity <= 0) return 100
  return Math.min(100, Math.round((Number(spot.reservedCount ?? 0) / capacity) * 100))
}

function chooseZone(zoneId: Id) {
  selectedZoneId.value = zoneId
  if (selectedSpot.value && String(selectedSpot.value.zoneId) !== String(zoneId)) {
    selectedSpotId.value = ''
  }
}

function chooseSpot(spot: Availability) {
  if (!spotCanBook(spot)) return
  selectedSpotId.value = spot.spotId
  form.guests = Math.min(Math.max(form.guests, 1), Number(spot.availableCount ?? 1))
  successBooking.value = null
}

async function loadAvailability() {
  const requestId = ++availabilityRequest
  availabilityLoading.value = true
  availabilityError.value = ''
  try {
    const result = await userBookingApi.availability({
      date: fishingDate.value,
      timeSlot: timeSlot.value,
    })
    if (requestId !== availabilityRequest) return
    availability.value = result.records
    if (!zones.value.some((zone) => String(zone.id) === String(selectedZoneId.value))) {
      selectedZoneId.value = zones.value[0]?.id ?? ''
    }
    if (selectedSpotId.value && !selectedSpot.value) selectedSpotId.value = ''
    if (selectedSpot.value && !spotCanBook(selectedSpot.value)) selectedSpotId.value = ''
  } catch (reason) {
    if (requestId === availabilityRequest) availabilityError.value = errorMessage(reason)
  } finally {
    if (requestId === availabilityRequest) availabilityLoading.value = false
  }
}

async function loadBookings() {
  bookingsLoading.value = true
  bookingsError.value = ''
  try {
    bookings.value = (await userBookingApi.list()).records
  } catch (reason) {
    bookingsError.value = errorMessage(reason)
  } finally {
    bookingsLoading.value = false
  }
}

async function createBooking() {
  if (!selectedSpot.value || !spotCanBook(selectedSpot.value)) {
    ElMessage.warning('请先选择一个可预约钓位')
    return
  }
  if (!/^[0-9+\- ]{6,32}$/.test(form.contactPhone.trim())) {
    ElMessage.warning('请填写有效的联系电话')
    return
  }
  if (form.guests < 1 || form.guests > availableCount.value) {
    ElMessage.warning(`当前钓位最多还可预约 ${availableCount.value} 人`)
    return
  }

  try {
    await ElMessageBox.confirm(
      `${formatDate(fishingDate.value, { month: 'long', day: 'numeric' })} ${timeSlotLabel(timeSlot.value)}，预约 ${selectedSpot.value.spotName} ${form.guests} 人，预计 ${formatCurrency(estimatedAmount.value)}。`,
      '确认这张垂钓预约',
      { confirmButtonText: '确认预约', cancelButtonText: '再看一下', type: 'info' },
    )
  } catch (reason) {
    if (reason === 'cancel' || reason === 'close') return
    ElMessage.error(errorMessage(reason))
    return
  }

  saving.value = true
  try {
    const created = await userBookingApi.create({
      contactPhone: form.contactPhone.trim(),
      spotId: selectedSpot.value.spotId,
      fishingDate: fishingDate.value,
      timeSlot: timeSlot.value,
      guests: form.guests,
      notes: form.notes.trim() || undefined,
    })
    successBooking.value = created
    form.notes = ''
    ElMessage.success('预约已生成，现场工作人员可以在管理端看到')
    await Promise.all([loadAvailability(), loadBookings()])
  } catch (reason) {
    ElMessage.error(errorMessage(reason))
  } finally {
    saving.value = false
  }
}

function canCancel(booking: Booking) {
  return booking.status === 'CONFIRMED' && booking.fishingDate > today
}

async function cancelBooking(booking: Booking) {
  try {
    await ElMessageBox.confirm(
      `取消 ${formatDate(booking.fishingDate)} ${timeSlotLabel(booking.timeSlot)} 的 ${booking.spotName || '钓位'} 预约？`,
      '取消预约',
      { confirmButtonText: '确认取消', cancelButtonText: '保留预约', type: 'warning' },
    )
  } catch (reason) {
    if (reason === 'cancel' || reason === 'close') return
    ElMessage.error(errorMessage(reason))
    return
  }
  cancellingId.value = booking.id
  try {
    await userBookingApi.cancel(booking.id)
    ElMessage.success('预约已取消，钓位余量已释放')
    await Promise.all([loadAvailability(), loadBookings()])
  } catch (reason) {
    ElMessage.error(errorMessage(reason))
  } finally {
    cancellingId.value = null
  }
}

async function handleLogout() {
  try {
    await ElMessageBox.confirm('确认退出当前钓友账号吗？', '退出登录', {
      confirmButtonText: '确认退出',
      cancelButtonText: '继续选位',
      type: 'warning',
    })
  } catch (reason) {
    if (reason === 'cancel' || reason === 'close') return
    ElMessage.error(errorMessage(reason))
    return
  }
  loggingOut.value = true
  try {
    await auth.logout()
    await router.replace('/login')
  } catch (reason) {
    ElMessage.error(errorMessage(reason))
  } finally {
    loggingOut.value = false
  }
}

async function changePassword() {
  if (!passwordForm.currentPassword || !passwordForm.newPassword) {
    ElMessage.warning('请填写当前密码和新密码')
    return
  }
  if (!/^(?=.*[A-Za-z])(?=.*\d)\S{8,64}$/.test(passwordForm.newPassword)) {
    ElMessage.warning('新密码需为 8 至 64 位，至少包含字母和数字')
    return
  }
  if (passwordForm.newPassword !== passwordForm.confirmPassword) {
    ElMessage.warning('两次输入的新密码不一致')
    return
  }
  changingPassword.value = true
  try {
    await authApi.changePassword({
      currentPassword: passwordForm.currentPassword,
      newPassword: passwordForm.newPassword,
    })
    auth.clearSession()
    passwordDialogOpen.value = false
    ElMessage.success('密码已修改，请重新登录')
    await router.replace('/login')
  } catch (reason) {
    ElMessage.error(errorMessage(reason))
  } finally {
    changingPassword.value = false
  }
}

function clearPasswordForm() {
  Object.assign(passwordForm, { currentPassword: '', newPassword: '', confirmPassword: '' })
}

watch([fishingDate, timeSlot], () => {
  selectedSpotId.value = ''
  successBooking.value = null
  void loadAvailability()
})

onMounted(() => Promise.all([loadAvailability(), loadBookings()]))
</script>

<template>
  <div class="angler-page">
    <a class="angler-skip" href="#angler-main">跳到选位内容</a>
    <header class="angler-topbar">
      <RouterLink class="angler-brand" to="/my-fishing" aria-label="我的垂钓首页">
        <span class="angler-brand__mark" aria-hidden="true">水</span>
        <span><strong>湖畔预约</strong><small>淡水垂钓基地</small></span>
      </RouterLink>
      <div class="angler-account">
        <span><strong>{{ auth.user?.displayName || '钓友' }}</strong><small>钓友用户</small></span>
        <el-button :icon="Key" circle plain aria-label="修改密码" @click="passwordDialogOpen = true" />
        <el-button :icon="SwitchButton" :loading="loggingOut" circle plain aria-label="退出登录" @click="handleLogout" />
      </div>
    </header>

    <main id="angler-main" class="angler-main" tabindex="-1">
      <section class="angler-hero" aria-labelledby="angler-title">
        <div>
          <span class="angler-eyebrow">一席水岸 · 一段安静时间</span>
          <h1 id="angler-title">选好今天想坐的湖边</h1>
          <p>先定日期和时段，再从开放钓位中挑一席。余量与价格以现场配置为准。</p>
        </div>
        <div class="angler-waterline" aria-label="当前预约条件">
          <span><small>日期</small>{{ formatDate(fishingDate, { month: 'long', day: 'numeric' }) }}</span>
          <i aria-hidden="true" />
          <span><small>时段</small>{{ timeSlotLabel(timeSlot) }}</span>
          <i aria-hidden="true" />
          <span><small>钓位</small>{{ selectedSpot?.spotName || '待选择' }}</span>
        </div>
      </section>

      <section class="angler-workbench">
        <div class="angler-selection">
          <section class="booking-controls" aria-label="选择日期和时段">
            <div class="control-block">
              <span class="control-label"><el-icon><Calendar /></el-icon>垂钓日期</span>
              <el-date-picker
                v-model="fishingDate"
                type="date"
                value-format="YYYY-MM-DD"
                :clearable="false"
                :disabled-date="disablePastDate"
                aria-label="选择垂钓日期"
                style="width: 100%"
              />
            </div>
            <div class="control-block control-block--wide">
              <span class="control-label"><el-icon><Tickets /></el-icon>到场时段</span>
              <div class="slot-pills">
                <button
                  v-for="slot in slots"
                  :key="slot.value"
                  type="button"
                  :class="{ active: timeSlot === slot.value }"
                  :aria-pressed="timeSlot === slot.value"
                  @click="timeSlot = slot.value"
                >
                  <strong>{{ slot.label }}</strong><small>{{ slot.hint }}</small>
                </button>
              </div>
            </div>
          </section>

          <ResourceState :loading="availabilityLoading" :error="availabilityError" @retry="loadAvailability">
            <section class="shore-section" aria-labelledby="shore-title">
              <header class="shore-heading">
                <div><span>水岸分区</span><h2 id="shore-title">从一片湖岸开始</h2></div>
                <el-button :icon="RefreshRight" text @click="loadAvailability">刷新余量</el-button>
              </header>
              <div class="zone-tabs" role="tablist" aria-label="湖区分区">
                <button
                  v-for="zone in zones"
                  :key="zone.id"
                  type="button"
                  role="tab"
                  :aria-selected="String(selectedZoneId) === String(zone.id)"
                  :class="{ active: String(selectedZoneId) === String(zone.id) }"
                  @click="chooseZone(zone.id)"
                >
                  {{ zone.name }}
                </button>
              </div>

              <div v-if="visibleSpots.length" class="spot-grid">
                <button
                  v-for="spot in visibleSpots"
                  :key="spot.spotId"
                  type="button"
                  class="spot-ticket"
                  :class="{
                    selected: String(selectedSpotId) === String(spot.spotId),
                    unavailable: !spotCanBook(spot),
                  }"
                  :disabled="!spotCanBook(spot)"
                  :aria-pressed="String(selectedSpotId) === String(spot.spotId)"
                  @click="chooseSpot(spot)"
                >
                  <span class="spot-ticket__top"><small>{{ spot.spotCode }}</small><StatusTag :status="spot.status" /></span>
                  <strong>{{ spot.spotName || '未命名钓位' }}</strong>
                  <span class="spot-ticket__facts">
                    <span>剩余 <b>{{ spot.availableCount ?? 0 }}</b> / {{ spot.capacity ?? 0 }} 人</span>
                    <span>{{ formatCurrency(spot.price) }} / 人</span>
                  </span>
                  <span class="occupancy-track" aria-hidden="true"><i :style="{ width: `${occupancyPercent(spot)}%` }" /></span>
                  <small class="spot-ticket__hint">{{ spotCanBook(spot) ? '点击选中这处水岸' : statusLabel(spot.status) }}</small>
                </button>
              </div>
              <div v-else class="shore-empty">
                <strong>这个时段还没有可展示的钓位</strong>
                <p>换一个日期或时段，再看看水岸余量。</p>
              </div>
            </section>
          </ResourceState>
        </div>

        <aside class="booking-permit" aria-labelledby="permit-title">
          <header><span>预约凭单</span><small>BOOKING PERMIT</small></header>
          <div v-if="selectedSpot" class="permit-content">
            <div class="permit-location"><el-icon><Location /></el-icon><span><small>{{ selectedSpot.zoneName }}</small><strong id="permit-title">{{ selectedSpot.spotName }}</strong></span></div>
            <dl>
              <div><dt>日期</dt><dd>{{ formatDate(fishingDate, { year: 'numeric', month: 'long', day: 'numeric' }) }}</dd></div>
              <div><dt>时段</dt><dd>{{ timeSlotLabel(timeSlot) }}</dd></div>
              <div><dt>单价</dt><dd>{{ formatCurrency(selectedSpot.price) }}</dd></div>
            </dl>
            <label class="permit-field"><span>同行人数</span><el-input-number v-model="form.guests" :min="1" :max="Math.max(availableCount, 1)" /></label>
            <label class="permit-field"><span><el-icon><Phone /></el-icon>联系电话</span><el-input v-model.trim="form.contactPhone" maxlength="32" autocomplete="tel" placeholder="用于现场联系" /></label>
            <label class="permit-field"><span>给现场的备注</span><el-input v-model="form.notes" type="textarea" maxlength="500" show-word-limit :rows="3" placeholder="例如需要靠近护栏的钓位…" /></label>
            <div class="permit-total"><span>预计合计</span><strong>{{ formatCurrency(estimatedAmount) }}</strong></div>
            <el-button type="primary" size="large" :loading="saving" class="permit-submit" @click="createBooking">确认预约这处钓位</el-button>
            <p class="permit-note">提交后生成待收款记录，费用由现场工作人员核对。</p>
          </div>
          <div v-else class="permit-placeholder">
            <span aria-hidden="true">钓</span><strong id="permit-title">先从左侧选择钓位</strong><p>可预约钓位会显示剩余人数与当次价格。</p>
          </div>
        </aside>
      </section>

      <section v-if="successBooking" class="booking-receipt" aria-live="polite">
        <div><span>预约已入湖畔值守簿</span><strong>{{ successBooking.bookingNo }}</strong></div>
        <p>{{ formatDate(successBooking.fishingDate, { month: 'long', day: 'numeric' }) }} · {{ timeSlotLabel(successBooking.timeSlot) }} · {{ successBooking.zoneName }} {{ successBooking.spotName }}</p>
        <StatusTag :status="successBooking.paymentStatus" />
      </section>

      <section class="my-bookings" aria-labelledby="my-bookings-title">
        <header class="shore-heading">
          <div><span>我的预约</span><h2 id="my-bookings-title">接下来与曾经的湖边时间</h2></div>
          <el-button :icon="RefreshRight" text @click="loadBookings">刷新记录</el-button>
        </header>
        <ResourceState :loading="bookingsLoading" :error="bookingsError" @retry="loadBookings">
          <div v-if="bookings.length" class="booking-ledger">
            <article v-for="booking in [...futureBookings, ...historyBookings]" :key="booking.id" class="booking-entry" :class="{ muted: booking.status !== 'CONFIRMED' }">
              <div class="booking-entry__date"><strong>{{ formatDate(booking.fishingDate, { month: '2-digit', day: '2-digit' }) }}</strong><span>{{ timeSlotLabel(booking.timeSlot) }}</span></div>
              <div class="booking-entry__main"><span>{{ booking.bookingNo }}</span><strong>{{ booking.zoneName }} · {{ booking.spotName }}</strong><small>{{ booking.guests }} 人 · {{ formatCurrency(booking.amount) }}</small></div>
              <div class="booking-entry__status"><StatusTag :status="booking.status" /><small>收款：{{ statusLabel(booking.paymentStatus) }}</small></div>
              <el-button v-if="canCancel(booking)" type="danger" plain :loading="String(cancellingId) === String(booking.id)" @click="cancelBooking(booking)">取消预约</el-button>
            </article>
          </div>
          <div v-else class="shore-empty"><strong>还没有预约记录</strong><p>从上方选一个合适钓位，第一张预约会出现在这里。</p></div>
        </ResourceState>
      </section>
    </main>

    <el-dialog v-model="passwordDialogOpen" title="修改钓友账号密码" width="460px" destroy-on-close @closed="clearPasswordForm">
      <el-form label-position="top">
        <el-form-item label="当前密码" required><el-input v-model="passwordForm.currentPassword" type="password" show-password autocomplete="current-password" /></el-form-item>
        <el-form-item label="新密码" required><el-input v-model="passwordForm.newPassword" type="password" show-password autocomplete="new-password" /></el-form-item>
        <el-form-item label="再次输入新密码" required><el-input v-model="passwordForm.confirmPassword" type="password" show-password autocomplete="new-password" /></el-form-item>
      </el-form>
      <el-alert title="修改成功后，所有旧会话都会失效，需要重新登录。" type="info" :closable="false" show-icon />
      <template #footer><el-button @click="passwordDialogOpen = false">取消</el-button><el-button type="primary" :loading="changingPassword" @click="changePassword">确认修改</el-button></template>
    </el-dialog>
  </div>
</template>

<style scoped>
.angler-page { min-height: 100dvh; color: #183b35; background: #e8efe9; background-image: radial-gradient(ellipse at 12% 5%, rgb(255 255 255 / 76%) 0 10%, transparent 33%), repeating-radial-gradient(ellipse at 84% 18%, transparent 0 46px, rgb(31 89 76 / 6%) 47px 48px, transparent 49px 78px); }
.angler-skip { position: fixed; top: 10px; left: 12px; z-index: 100; padding: 9px 13px; border-radius: 7px; color: white; background: #12352d; text-decoration: none; transform: translateY(-150%); }
.angler-skip:focus { transform: translateY(0); }
.angler-topbar { display: flex; min-height: 76px; align-items: center; justify-content: space-between; padding: 0 clamp(20px, 5vw, 72px); border-bottom: 1px solid rgb(18 53 45 / 14%); background: rgb(239 245 240 / 86%); backdrop-filter: blur(18px); }
.angler-brand { display: flex; align-items: center; gap: 11px; color: inherit; text-decoration: none; }
.angler-brand__mark { display: grid; width: 42px; height: 42px; place-items: center; border: 1px solid rgb(255 255 255 / 44%); border-radius: 50% 46% 52% 48%; color: #edf4ef; background: #17463a; font-family: "Songti SC", "STSong", serif; font-size: 19px; box-shadow: 0 8px 24px rgb(23 70 58 / 18%); transform: rotate(-5deg); }
.angler-brand strong, .angler-brand small, .angler-account strong, .angler-account small { display: block; }
.angler-brand strong { font-family: "Songti SC", "STSong", serif; font-size: 18px; letter-spacing: .08em; }
.angler-brand small, .angler-account small { margin-top: 2px; color: #6e8179; font-size: 10px; letter-spacing: .08em; }
.angler-account { display: flex; align-items: center; gap: 9px; text-align: right; }
.angler-account > span { margin-right: 4px; }
.angler-account strong { font-size: 13px; }
.angler-main { width: min(1460px, 100%); margin: 0 auto; padding: 42px clamp(18px, 4vw, 58px) 68px; outline: none; }
.angler-hero { display: grid; grid-template-columns: minmax(0, 1fr) minmax(420px, .8fr); align-items: end; gap: 42px; margin-bottom: 30px; }
.angler-eyebrow { color: #a4543b; font-size: 10px; font-weight: 800; letter-spacing: .18em; }
.angler-hero h1 { max-width: 760px; margin: 8px 0 10px; color: #12352d; font-family: "Songti SC", "STSong", serif; font-size: clamp(34px, 5vw, 66px); font-weight: 700; letter-spacing: -.055em; line-height: 1.02; text-wrap: balance; }
.angler-hero p { max-width: 58ch; margin: 0; color: #61766d; font-size: 13px; line-height: 1.8; }
.angler-waterline { display: grid; grid-template-columns: 1fr auto 1fr auto 1fr; align-items: center; gap: 13px; padding: 16px 18px; border: 1px solid rgb(23 70 58 / 18%); border-radius: 16px; background: rgb(248 251 248 / 72%); box-shadow: 0 18px 52px rgb(23 70 58 / 8%); }
.angler-waterline span { min-width: 0; overflow: hidden; color: #264e45; font-family: "Songti SC", "STSong", serif; font-size: 15px; font-weight: 700; text-overflow: ellipsis; white-space: nowrap; }
.angler-waterline small { display: block; margin-bottom: 4px; color: #7d9088; font-family: "SF Pro Text", "PingFang SC", sans-serif; font-size: 9px; font-weight: 700; letter-spacing: .12em; }
.angler-waterline i { width: 5px; height: 5px; border-radius: 50%; background: #b46145; box-shadow: 0 0 0 5px rgb(180 97 69 / 10%); }
.angler-workbench { display: grid; grid-template-columns: minmax(0, 1fr) 350px; align-items: start; gap: 20px; }
.angler-selection { display: grid; gap: 18px; min-width: 0; }
.booking-controls { display: grid; grid-template-columns: minmax(190px, .55fr) minmax(420px, 1.45fr); gap: 14px; padding: 17px; border: 1px solid rgb(23 70 58 / 14%); border-radius: 18px; background: rgb(255 253 248 / 86%); }
.control-block { display: grid; align-content: start; gap: 9px; }
.control-label { display: flex; align-items: center; gap: 6px; color: #48655d; font-size: 11px; font-weight: 750; letter-spacing: .04em; }
.slot-pills { display: grid; grid-template-columns: repeat(3, 1fr); gap: 7px; }
.slot-pills button { display: grid; gap: 2px; min-height: 52px; padding: 8px 10px; border: 1px solid #d4dfd8; border-radius: 11px; color: #536d64; background: #f3f7f3; cursor: pointer; text-align: left; transition: border-color 150ms ease, color 150ms ease, transform 150ms ease, background-color 150ms ease; }
.slot-pills button:hover { border-color: #6b9486; transform: translateY(-1px); }
.slot-pills button.active { border-color: #205747; color: white; background: #205747; box-shadow: 0 10px 22px rgb(32 87 71 / 17%); }
.slot-pills strong { font-size: 13px; }
.slot-pills small { color: inherit; font-size: 9px; opacity: .76; }
.shore-section, .my-bookings { padding: 20px; border: 1px solid rgb(23 70 58 / 14%); border-radius: 20px; background: rgb(255 253 248 / 88%); }
.shore-heading { display: flex; align-items: flex-end; justify-content: space-between; gap: 18px; margin-bottom: 15px; }
.shore-heading span { color: #a4543b; font-size: 9px; font-weight: 800; letter-spacing: .14em; }
.shore-heading h2 { margin: 4px 0 0; color: #153d34; font-family: "Songti SC", "STSong", serif; font-size: clamp(21px, 2.8vw, 31px); letter-spacing: -.035em; }
.zone-tabs { display: flex; flex-wrap: wrap; gap: 7px; margin-bottom: 16px; }
.zone-tabs button { padding: 7px 12px; border: 1px solid #d2ddd6; border-radius: 999px; color: #567168; background: transparent; cursor: pointer; font-size: 11px; }
.zone-tabs button.active { border-color: #17463a; color: #f7fbf8; background: #17463a; }
.spot-grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 11px; }
.spot-ticket { position: relative; display: grid; gap: 11px; min-height: 178px; overflow: hidden; padding: 15px; border: 1px solid #d7dfd8; border-radius: 14px 14px 22px 14px; color: #244a41; background: #f6f7ef; cursor: pointer; text-align: left; transition: transform 160ms ease, border-color 160ms ease, box-shadow 160ms ease; }
.spot-ticket::after { position: absolute; right: -10px; bottom: -10px; width: 34px; height: 34px; border: 1px solid rgb(32 87 71 / 14%); border-radius: 50%; content: ""; }
.spot-ticket:hover:not(:disabled) { border-color: #6a9184; box-shadow: 0 13px 26px rgb(23 70 58 / 9%); transform: translateY(-2px); }
.spot-ticket.selected { border-color: #205747; background: #eff6f0; box-shadow: 0 0 0 3px rgb(32 87 71 / 9%), 0 16px 32px rgb(23 70 58 / 12%); }
.spot-ticket.selected::after { animation: ticket-ripple 720ms ease-out 1; }
.spot-ticket.unavailable { cursor: not-allowed; filter: saturate(.55); opacity: .62; }
.spot-ticket__top, .spot-ticket__facts { display: flex; align-items: center; justify-content: space-between; gap: 8px; }
.spot-ticket__top > small { color: #8a6e5f; font-family: "SFMono-Regular", Consolas, monospace; font-size: 9px; letter-spacing: .12em; }
.spot-ticket > strong { align-self: end; font-family: "Songti SC", "STSong", serif; font-size: 20px; }
.spot-ticket__facts { color: #5b7168; font-size: 10px; }
.spot-ticket__facts b { color: #a4543b; font-size: 14px; }
.occupancy-track { height: 3px; overflow: hidden; border-radius: 999px; background: #d9e2dc; }
.occupancy-track i { display: block; height: 100%; border-radius: inherit; background: #b46145; }
.spot-ticket__hint { color: #82918b; font-size: 9px; }
.booking-permit { position: sticky; top: 18px; overflow: hidden; border: 1px solid #c8d5cd; border-radius: 22px 22px 8px 22px; background: #fffdf8; box-shadow: 0 24px 58px rgb(23 70 58 / 13%); }
.booking-permit > header { display: flex; align-items: center; justify-content: space-between; padding: 14px 17px; color: #e9f0eb; background: #17463a; }
.booking-permit > header span { font-family: "Songti SC", "STSong", serif; font-size: 16px; font-weight: 700; }
.booking-permit > header small { font-family: "SFMono-Regular", Consolas, monospace; font-size: 8px; letter-spacing: .14em; opacity: .68; }
.permit-content { display: grid; gap: 15px; padding: 19px; }
.permit-location { display: flex; align-items: center; gap: 10px; padding-bottom: 14px; border-bottom: 1px dashed #d7ddd8; }
.permit-location > .el-icon { display: grid; width: 38px; height: 38px; place-items: center; border-radius: 50%; color: #f6f4ec; background: #b46145; }
.permit-location small, .permit-location strong { display: block; }
.permit-location small { margin-bottom: 2px; color: #7b8d85; font-size: 10px; }
.permit-location strong { color: #153d34; font-family: "Songti SC", "STSong", serif; font-size: 22px; }
.permit-content dl { display: grid; grid-template-columns: 1fr 1fr; gap: 8px; margin: 0; }
.permit-content dl div { padding: 9px 10px; border-radius: 9px; background: #f3f5ef; }
.permit-content dt { color: #84928c; font-size: 9px; }
.permit-content dd { margin: 4px 0 0; color: #34554c; font-size: 11px; font-weight: 700; }
.permit-field { display: grid; gap: 7px; }
.permit-field > span { display: flex; align-items: center; gap: 5px; color: #4e665e; font-size: 10px; font-weight: 750; }
.permit-total { display: flex; align-items: baseline; justify-content: space-between; padding-top: 14px; border-top: 1px dashed #d7ddd8; }
.permit-total span { color: #74877f; font-size: 11px; }
.permit-total strong { color: #a4543b; font-family: "Songti SC", "STSong", serif; font-size: 25px; }
.permit-submit { width: 100%; }
.permit-note { margin: -5px 0 0; color: #84918b; font-size: 9px; line-height: 1.6; text-align: center; }
.permit-placeholder { display: grid; min-height: 430px; place-items: center; align-content: center; padding: 30px; text-align: center; }
.permit-placeholder > span { display: grid; width: 58px; height: 58px; place-items: center; margin-bottom: 15px; border: 1px solid #bdd0c5; border-radius: 50%; color: #205747; font-family: "Songti SC", "STSong", serif; font-size: 23px; background: #edf4ef; }
.permit-placeholder strong { color: #244a41; font-family: "Songti SC", "STSong", serif; font-size: 18px; }
.permit-placeholder p { max-width: 24ch; margin: 8px 0 0; color: #7b8c85; font-size: 11px; line-height: 1.7; }
.booking-receipt { display: grid; grid-template-columns: minmax(0, 1fr) auto; align-items: center; gap: 6px 20px; margin-top: 18px; padding: 17px 20px; border: 1px solid #bcd2c5; border-left: 4px solid #205747; border-radius: 12px; background: #f5faf6; }
.booking-receipt div span, .booking-receipt div strong { display: block; }
.booking-receipt div span { color: #668078; font-size: 10px; }
.booking-receipt div strong { margin-top: 3px; color: #17463a; font-family: "SFMono-Regular", Consolas, monospace; font-size: 17px; }
.booking-receipt p { grid-column: 1; margin: 0; color: #49655d; font-size: 11px; }
.my-bookings { margin-top: 20px; }
.booking-ledger { display: grid; gap: 8px; }
.booking-entry { display: grid; grid-template-columns: 74px minmax(0, 1fr) 140px auto; align-items: center; gap: 14px; padding: 12px 14px; border: 1px solid #d9e1db; border-radius: 12px; background: #f8faf6; }
.booking-entry.muted { background: #f3f2ed; opacity: .78; }
.booking-entry__date { display: grid; gap: 2px; padding-right: 12px; border-right: 1px solid #d7dfd9; }
.booking-entry__date strong { color: #17463a; font-family: "Songti SC", "STSong", serif; font-size: 19px; }
.booking-entry__date span, .booking-entry__main span, .booking-entry__main small, .booking-entry__status small { color: #7a8b84; font-size: 9px; }
.booking-entry__main { display: grid; gap: 3px; }
.booking-entry__main span { font-family: "SFMono-Regular", Consolas, monospace; letter-spacing: .04em; }
.booking-entry__main strong { color: #35564d; font-size: 13px; }
.booking-entry__status { display: grid; justify-items: start; gap: 5px; }
.shore-empty { display: grid; min-height: 180px; place-items: center; align-content: center; padding: 28px; text-align: center; }
.shore-empty strong { color: #3d5a52; font-family: "Songti SC", "STSong", serif; font-size: 17px; }
.shore-empty p { margin: 7px 0 0; color: #82918b; font-size: 11px; }
@keyframes ticket-ripple { from { box-shadow: 0 0 0 0 rgb(32 87 71 / 24%); } to { box-shadow: 0 0 0 34px rgb(32 87 71 / 0%); } }
@media (max-width: 1060px) { .angler-hero { grid-template-columns: 1fr; gap: 22px; } .angler-workbench { grid-template-columns: 1fr; } .booking-permit { position: static; } .spot-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); } .permit-placeholder { min-height: 250px; } }
@media (max-width: 700px) { .angler-topbar { min-height: 66px; padding: 0 14px; } .angler-account > span { display: none; } .angler-main { padding: 28px 13px 48px; } .angler-hero h1 { font-size: clamp(34px, 11vw, 48px); } .angler-waterline { grid-template-columns: 1fr; gap: 8px; } .angler-waterline i { width: 100%; height: 1px; border-radius: 0; box-shadow: none; opacity: .28; } .booking-controls { grid-template-columns: 1fr; padding: 13px; } .slot-pills { grid-template-columns: 1fr 1fr 1fr; } .slot-pills button { min-height: 48px; padding: 7px; } .slot-pills small { display: none; } .shore-section, .my-bookings { padding: 15px; } .shore-heading { align-items: center; } .shore-heading h2 { font-size: 22px; } .spot-grid { grid-template-columns: 1fr; } .spot-ticket { min-height: 156px; } .booking-entry { grid-template-columns: 58px minmax(0, 1fr); gap: 10px; } .booking-entry__status { grid-column: 2; } .booking-entry > .el-button { grid-column: 1 / -1; width: 100%; } .booking-receipt { grid-template-columns: 1fr; } .booking-receipt p { grid-column: 1; } }
@media (prefers-reduced-motion: reduce) { .spot-ticket, .slot-pills button { transition: none; } .spot-ticket.selected::after { animation: none; } }
</style>
