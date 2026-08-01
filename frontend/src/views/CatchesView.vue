<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { Edit, Plus, Refresh, Search } from '@element-plus/icons-vue'
import ResourceState from '@/components/ResourceState.vue'
import LakeEmptyState from '@/components/LakeEmptyState.vue'
import StatusTag from '@/components/StatusTag.vue'
import { bookingApi, catchApi, memberApi, spotApi } from '@/api'
import { errorMessage } from '@/api/http'
import { formatDate, formatNumber } from '@/utils/format'
import { focusFirstInvalid } from '@/utils/forms'
import type { Booking, CatchRecord, Id, Member, Spot } from '@/types'

const loading = ref(true)
const error = ref('')
const records = ref<CatchRecord[]>([])
const members = ref<Member[]>([])
const spots = ref<Spot[]>([])
const bookings = ref<Booking[]>([])
const dialogOpen = ref(false)
const saving = ref(false)
const formRef = ref<FormInstance>()
const keyword = ref('')
const statusFilter = ref('')
const speciesFilter = ref('')

type CatchForm = {
  id?: Id
  bookingId: Id | ''
  memberId: Id | ''
  spotId: Id | ''
  species: string
  weight: number
  quantity: number
  fishingDate: string
  status: string
  notes: string
}

const form = reactive<CatchForm>({
  bookingId: '',
  memberId: '',
  spotId: '',
  species: '',
  weight: 0,
  quantity: 1,
  fishingDate: '',
  status: 'RECORDED',
  notes: '',
})

const rules: FormRules = {
  spotId: [{ required: true, message: '请选择钓位', trigger: 'change' }],
  species: [{ required: true, message: '请输入渔获品种', trigger: 'blur' }],
  weight: [{ required: true, message: '请输入渔获重量', trigger: 'change' }],
  quantity: [{ required: true, message: '请输入渔获数量', trigger: 'change' }],
  fishingDate: [{ required: true, message: '请选择垂钓日期', trigger: 'change' }],
}

const visibleRecords = computed(() => {
  const query = keyword.value.trim().toLowerCase()
  return records.value.filter((record) => {
    const text =
      `${record.catchNo || ''} ${record.species} ${record.memberName || ''} ${record.spotName || ''}`.toLowerCase()
    return (
      (!query || text.includes(query)) &&
      (!statusFilter.value || record.status === statusFilter.value) &&
      (!speciesFilter.value || record.species === speciesFilter.value)
    )
  })
})

const speciesStats = computed(() => {
  const groups = new Map<string, { species: string; quantity: number; weight: number; records: number }>()
  records.value.filter((record) => record.status !== 'VOID').forEach((record) => {
    const current = groups.get(record.species) ?? {
      species: record.species,
      quantity: 0,
      weight: 0,
      records: 0,
    }
    current.quantity += Number(record.quantity || 0)
    current.weight += Number(record.weight || 0)
    current.records += 1
    groups.set(record.species, current)
  })
  return [...groups.values()].sort((a, b) => b.quantity - a.quantity)
})

const totalWeight = computed(() =>
  records.value
    .filter((record) => record.status !== 'VOID')
    .reduce((sum, record) => sum + Number(record.weight || 0), 0),
)

const bookingLinked = computed(() => form.bookingId !== '')

function toggleSpecies(species: string) {
  speciesFilter.value = speciesFilter.value === species ? '' : species
}

async function load() {
  loading.value = true
  error.value = ''
  try {
    const [catchResult, memberResult, spotResult, bookingResult] = await Promise.all([
      catchApi.list(),
      memberApi.list(),
      spotApi.list(),
      bookingApi.list(),
    ])
    records.value = catchResult.records
    members.value = memberResult.records
    spots.value = spotResult.records
    bookings.value = bookingResult.records
  } catch (reason) {
    error.value = errorMessage(reason)
  } finally {
    loading.value = false
  }
}

function openForm(record?: CatchRecord) {
  Object.assign(form, {
    id: record?.id,
    bookingId: record?.bookingId ?? '',
    memberId: record?.memberId ?? '',
    spotId: record?.spotId ?? '',
    species: record?.species ?? '',
    weight: record?.weight ?? 0,
    quantity: record?.quantity ?? 1,
    fishingDate: record?.fishingDate ?? '',
    status: record?.status ?? 'RECORDED',
    notes: record?.notes ?? '',
  })
  formRef.value?.clearValidate()
  dialogOpen.value = true
}

function applyBooking(bookingId: Id | '') {
  if (!bookingId) return
  const booking = bookings.value.find((item) => String(item.id) === String(bookingId))
  if (!booking) return
  form.spotId = booking.spotId
  form.memberId = booking.memberId ?? ''
  form.fishingDate = booking.fishingDate
}

async function save() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) {
    await focusFirstInvalid('.catch-record-form')
    return
  }

  const currentRecord = records.value.find((record) => String(record.id) === String(form.id))
  if (form.status === 'VOID' && currentRecord?.status !== 'VOID') {
    try {
      await ElMessageBox.confirm(
        `确认将档案 ${currentRecord?.catchNo || form.id || ''} 标记为已作废吗？作废后会保留记录，但不再作为有效渔获统计。`,
        '作废渔获档案',
        {
          confirmButtonText: '确认作废',
          cancelButtonText: '返回检查',
          type: 'warning',
        },
      )
    } catch {
      return
    }
  }
  saving.value = true
  try {
    const payload = {
      bookingId: form.bookingId || undefined,
      memberId: form.memberId || undefined,
      spotId: form.spotId,
      species: form.species,
      weight: form.weight,
      quantity: form.quantity,
      fishingDate: form.fishingDate,
      status: form.status,
      notes: form.notes,
    }
    if (form.id !== undefined) await catchApi.update(form.id, payload)
    else await catchApi.create(payload)
    ElMessage.success(form.id !== undefined ? '渔获档案已更新' : '渔获档案已创建')
    dialogOpen.value = false
    await load()
  } catch (reason) {
    ElMessage.error(errorMessage(reason))
  } finally {
    saving.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="catch-archive">
    <header class="archive-head">
      <div>
        <span class="station-kicker">渔获档案室</span>
        <h1>每一笔渔获都有出处</h1>
        <p>按鱼种查看累计数量与重量，档案卡保留会员、钓位和核验状态。</p>
      </div>
      <div class="archive-head__actions">
        <el-button :icon="Refresh" :loading="loading" @click="load">刷新档案</el-button>
        <el-button type="primary" :icon="Plus" @click="openForm()">新增登记</el-button>
      </div>
    </header>

    <ResourceState
      :loading="loading"
      :error="error"
      :empty="!records.length"
      empty-title="暂无渔获档案"
      empty-description="完成第一次现场登记后，档案卡会陈列在这里"
      @retry="load"
    >
      <template #empty-action>
        <el-button type="primary" @click="openForm()">新增登记</el-button>
      </template>

      <section class="archive-layout">
        <aside class="species-index">
          <header>
            <span>鱼种统计</span>
            <strong class="metric-value">{{ formatNumber(totalWeight, ' kg') }}</strong>
            <small>累计登记重量</small>
          </header>
          <div class="species-list">
            <button
              v-for="item in speciesStats"
              :key="item.species"
              type="button"
              :class="{ active: speciesFilter === item.species }"
              :aria-pressed="speciesFilter === item.species"
              :aria-label="`${speciesFilter === item.species ? '清除' : '筛选'}${item.species}档案，共${item.records}笔`"
              @click="toggleSpecies(item.species)"
            >
              <div>
                <strong>{{ item.species }}</strong>
                <span>{{ item.records }} 笔档案</span>
              </div>
              <div>
                <strong class="metric-value">{{ item.quantity }} 尾</strong>
                <span class="metric-value">{{ formatNumber(item.weight, ' kg') }}</span>
              </div>
            </button>
          </div>
        </aside>

        <div class="archive-main">
          <div class="archive-tools">
            <el-input
              v-model="keyword"
              :prefix-icon="Search"
              clearable
              placeholder="搜索编号、鱼种、会员或钓位"
              aria-label="搜索渔获档案"
              style="width: 260px"
            />
            <el-select
              v-model="statusFilter"
              clearable
              placeholder="全部状态"
              aria-label="按档案状态筛选"
              style="width: 130px"
            >
              <el-option label="已登记" value="RECORDED" />
              <el-option label="已核验" value="VERIFIED" />
              <el-option label="已作废" value="VOID" />
            </el-select>
            <span>
              <b v-if="speciesFilter">{{ speciesFilter }} · </b>
              筛选后 {{ visibleRecords.length }} 笔
            </span>
          </div>

          <LakeEmptyState
            v-if="!visibleRecords.length"
            title="当前筛选条件下没有档案"
            description="调整搜索条件，或新建一笔渔获登记"
          />
          <div v-else class="catch-wall">
            <article v-for="record in visibleRecords" :key="record.id" class="catch-file">
              <header>
                <div>
                  <span>{{ record.catchNo || record.id }}</span>
                  <h2>{{ record.species }}</h2>
                </div>
                <StatusTag :status="record.status" />
              </header>
              <div class="catch-file__weight">
                <strong class="metric-value">{{ formatNumber(record.weight, ' kg') }}</strong>
                <span>{{ record.quantity }} 尾</span>
              </div>
              <dl>
                <div>
                  <dt>日期</dt>
                  <dd>{{ formatDate(record.fishingDate) }}</dd>
                </div>
                <div>
                  <dt>钓位</dt>
                  <dd>{{ record.spotName || '暂无' }}</dd>
                </div>
                <div>
                  <dt>归属</dt>
                  <dd>{{ record.memberName || '散客' }}</dd>
                </div>
              </dl>
              <footer>
                <span>{{ record.notes || '无补充备注' }}</span>
                <el-button link type="primary" :icon="Edit" @click="openForm(record)">编辑</el-button>
              </footer>
            </article>
          </div>
        </div>
      </section>
    </ResourceState>

    <el-dialog
      v-model="dialogOpen"
      :title="form.id !== undefined ? '编辑渔获档案' : '新增渔获档案'"
      width="700px"
      destroy-on-close
    >
      <el-form
        ref="formRef"
        class="catch-record-form"
        :model="form"
        :rules="rules"
        label-position="top"
        aria-live="polite"
        @submit.prevent="save"
      >
        <div class="form-grid">
          <el-form-item label="关联预订" class="form-span-2">
            <el-select
              v-model="form.bookingId"
              clearable
              filterable
              aria-label="关联预订"
              placeholder="可选…"
              style="width: 100%"
              @change="applyBooking"
            >
              <el-option
                v-for="booking in bookings"
                :key="booking.id"
                :label="`${booking.bookingNo || booking.id} ${booking.spotName || ''}`"
                :value="booking.id"
              />
            </el-select>
          </el-form-item>
          <el-alert
            v-if="bookingLinked"
            class="form-span-2"
            title="会员、钓位和日期已按关联预订锁定"
            description="如需单独调整这些信息，请先清空关联预订。"
            type="info"
            :closable="false"
            show-icon
          />
          <el-form-item label="会员">
            <el-select
              v-model="form.memberId"
              clearable
              filterable
              :disabled="bookingLinked"
              aria-label="关联会员"
              placeholder="散客可不选…"
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
          <el-form-item label="钓位" prop="spotId">
            <el-select
              v-model="form.spotId"
              filterable
              :disabled="bookingLinked"
              aria-label="钓位"
              style="width: 100%"
            >
              <el-option
                v-for="spot in spots"
                :key="spot.id"
                :label="spot.name || spot.code"
                :value="spot.id"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="垂钓日期" prop="fishingDate">
            <el-date-picker
              v-model="form.fishingDate"
              type="date"
              value-format="YYYY-MM-DD"
              :disabled="bookingLinked"
              aria-label="垂钓日期"
              placeholder="请选择日期…"
              style="width: 100%"
            />
          </el-form-item>
          <el-form-item label="渔获品种" prop="species">
            <el-input v-model.trim="form.species" name="species" placeholder="例如 鲫鱼…" />
          </el-form-item>
          <el-form-item label="档案状态">
            <el-select v-model="form.status" aria-label="档案状态" style="width: 100%">
              <el-option label="已登记" value="RECORDED" />
              <el-option label="已核验" value="VERIFIED" />
              <el-option label="已作废" value="VOID" />
            </el-select>
          </el-form-item>
          <el-form-item label="重量（kg）" prop="weight">
            <el-input-number
              v-model="form.weight"
              :min="0"
              :precision="2"
              controls-position="right"
            />
          </el-form-item>
          <el-form-item label="数量（尾）" prop="quantity">
            <el-input-number v-model="form.quantity" :min="1" controls-position="right" />
          </el-form-item>
          <el-form-item label="备注" class="form-span-2">
            <el-input
              v-model="form.notes"
              type="textarea"
              :rows="3"
              placeholder="填写核验说明或其他备注"
            />
          </el-form-item>
        </div>
      </el-form>
      <template #footer>
        <el-button @click="dialogOpen = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存档案</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.archive-head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 22px;
  margin-bottom: 17px;
}

.archive-head h1 {
  margin: 0;
  color: var(--lake-900);
  font-size: clamp(25px, 2.3vw, 34px);
  letter-spacing: -0.04em;
}

.archive-head p {
  margin: 6px 0 0;
  color: var(--ink-500);
  font-size: 11px;
}

.archive-head__actions,
.archive-tools {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
}

.archive-layout {
  display: grid;
  grid-template-columns: 230px minmax(0, 1fr);
  gap: 15px;
}

.species-index {
  align-self: start;
  overflow: hidden;
  border: 1px solid #ded5c8;
  border-radius: 14px;
  background: var(--lake-900);
}

.species-index > header {
  padding: 19px;
  border-bottom: 1px solid rgb(255 255 255 / 12%);
}

.species-index > header span,
.species-index > header strong,
.species-index > header small {
  display: block;
}

.species-index > header span {
  color: #d58b6e;
  font-size: 10px;
  font-weight: 700;
}

.species-index > header strong {
  margin-top: 8px;
  color: var(--paper-100);
  font-size: 25px;
}

.species-index > header small {
  margin-top: 5px;
  color: #91ada1;
  font-size: 11px;
}

.species-list button {
  display: flex;
  width: 100%;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  padding: 13px 17px;
  border: 0;
  border-bottom: 1px solid rgb(255 255 255 / 9%);
  color: #e7efe9;
  background: transparent;
  font: inherit;
  text-align: left;
  cursor: pointer;
  transition: background-color 140ms ease;
}

.species-list button:hover,
.species-list button.active {
  background: rgb(255 255 255 / 10%);
}

.species-list button.active {
  box-shadow: 3px 0 0 var(--clay-600) inset;
}

.species-list button:focus-visible {
  outline: 3px solid rgb(213 139 110 / 65%);
  outline-offset: -4px;
}

.species-list button > div:last-child {
  text-align: right;
}

.species-list strong,
.species-list span {
  display: block;
}

.species-list strong {
  font-size: 11px;
}

.species-list span {
  margin-top: 4px;
  color: #94afa4;
  font-size: 11px;
}

.archive-tools {
  margin-bottom: 12px;
  padding-bottom: 12px;
  border-bottom: 1px solid #ddd5c8;
}

.archive-tools > span {
  margin-left: auto;
  color: var(--ink-500);
  font-size: 10px;
}

.archive-tools > span b {
  color: var(--clay-700);
}

.catch-wall {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(250px, 1fr));
  gap: 11px;
}

.catch-file {
  display: flex;
  min-height: 262px;
  flex-direction: column;
  padding: 15px;
  border: 1px solid #ded5c8;
  border-radius: 12px;
  background: var(--paper-50);
}

.catch-file > header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 10px;
}

.catch-file header span {
  color: var(--clay-700);
  font-size: 11px;
  font-weight: 700;
}

.catch-file h2 {
  margin: 4px 0 0;
  color: var(--lake-900);
  font-size: 18px;
}

.catch-file__weight {
  display: flex;
  align-items: baseline;
  gap: 8px;
  margin: 22px 0 15px;
}

.catch-file__weight strong {
  color: var(--ink-900);
  font-size: 25px;
}

.catch-file__weight span {
  color: var(--ink-500);
  font-size: 10px;
}

.catch-file dl {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 9px;
  margin: 0;
}

.catch-file dt {
  color: #8c948f;
  font-size: 11px;
}

.catch-file dd {
  margin: 4px 0 0;
  color: #44524c;
  font-size: 10px;
}

.catch-file footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  margin-top: auto;
  padding-top: 13px;
  border-top: 1px solid var(--paper-300);
}

.catch-file footer > span {
  overflow: hidden;
  color: #8c948f;
  font-size: 11px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

@media (max-width: 820px) {
  .archive-layout {
    grid-template-columns: 1fr;
  }

  .species-list {
    display: grid;
    grid-template-columns: repeat(auto-fit, minmax(150px, 1fr));
  }
}

@media (max-width: 620px) {
  .archive-head {
    display: block;
  }

  .archive-head__actions {
    margin-top: 13px;
  }

  .archive-tools > * {
    width: 100% !important;
  }

  .archive-tools > span {
    margin-left: 0;
  }

  .catch-wall {
    grid-template-columns: 1fr;
  }
}
</style>
