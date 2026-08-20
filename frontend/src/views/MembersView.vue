<script setup lang="ts">
import { computed, nextTick, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { Edit, Plus, Refresh, Search } from '@element-plus/icons-vue'
import ResourceState from '@/components/ResourceState.vue'
import LakeEmptyState from '@/components/LakeEmptyState.vue'
import StatusTag from '@/components/StatusTag.vue'
import { memberApi } from '@/api'
import { errorMessage } from '@/api/http'
import { formatCurrency, formatDate, formatDateTime, timeSlotLabel } from '@/utils/format'
import { focusFirstInvalid } from '@/utils/forms'
import { useAuthStore } from '@/stores/auth'
import type { Id, Member, MemberActivity, MemberAudit } from '@/types'

const auth = useAuthStore()
const loading = ref(true)
const error = ref('')
const members = ref<Member[]>([])
const selectedMemberId = ref<Id | ''>('')
const keyword = ref('')
const levelFilter = ref('')
const statusFilter = ref('')
const dialogOpen = ref(false)
const saving = ref(false)
const formRef = ref<FormInstance>()
const memberDetailRef = ref<HTMLElement>()
const activityLoading = ref(false)
const activity = ref<MemberActivity | null>(null)
const audits = ref<MemberAudit[]>([])

type MemberForm = {
  id?: Id
  memberNo: string
  name: string
  phone: string
  level: string
  points: number
  status: string
}

const form = reactive<MemberForm>({
  memberNo: '',
  name: '',
  phone: '',
  level: 'NORMAL',
  points: 0,
  status: 'ACTIVE',
})

const rules: FormRules = {
  name: [{ required: true, message: '请输入会员姓名', trigger: 'blur' }],
  phone: [
    { required: true, message: '请输入联系电话', trigger: 'blur' },
    { pattern: /^[0-9+\- ]{6,32}$/, message: '联系电话格式不正确', trigger: 'blur' },
  ],
}

const levelLabels: Record<string, string> = {
  NORMAL: '普通会员',
  SILVER: '银卡会员',
  GOLD: '金卡会员',
  VIP: '贵宾会员',
}

const visibleMembers = computed(() => {
  const query = keyword.value.trim().toLowerCase()
  return members.value.filter((member) => {
    const text = `${member.memberNo || ''} ${member.name} ${member.phone}`.toLowerCase()
    return (
      (!query || text.includes(query)) &&
      (!levelFilter.value || member.level === levelFilter.value) &&
      (!statusFilter.value || member.status === statusFilter.value)
    )
  })
})

const selectedMember = computed(() =>
  members.value.find((member) => String(member.id) === String(selectedMemberId.value)),
)

const levelCounts = computed(() =>
  Object.keys(levelLabels).map((level) => ({
    level,
    label: levelLabels[level],
    count: members.value.filter((member) => member.level === level).length,
  })),
)

watch(visibleMembers, (items) => {
  const selectionVisible = items.some(
    (member) => String(member.id) === String(selectedMemberId.value),
  )
  if (!selectionVisible) selectedMemberId.value = items[0]?.id ?? ''
})

async function load() {
  loading.value = true
  error.value = ''
  try {
    const result = await memberApi.list()
    members.value = result.records
    if (!members.value.some((member) => String(member.id) === String(selectedMemberId.value))) {
      selectedMemberId.value = members.value[0]?.id ?? ''
    }
    await loadSelectedMemberActivity()
  } catch (reason) {
    error.value = errorMessage(reason)
  } finally {
    loading.value = false
  }
}

function openForm(member?: Member) {
  Object.assign(form, {
    id: member?.id,
    memberNo: member?.memberNo ?? '',
    name: member?.name ?? '',
    phone: member?.phone ?? '',
    level: member?.level ?? 'NORMAL',
    points: member?.points ?? 0,
    status: member?.status ?? 'ACTIVE',
  })
  formRef.value?.clearValidate()
  dialogOpen.value = true
}

async function selectMember(id: Id) {
  selectedMemberId.value = id
  await loadSelectedMemberActivity()
  if (!window.matchMedia('(max-width: 760px)').matches) return
  await nextTick()
  const reduceMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches
  memberDetailRef.value?.scrollIntoView({
    behavior: reduceMotion ? 'auto' : 'smooth',
    block: 'start',
  })
}

async function loadSelectedMemberActivity() {
  if (!selectedMemberId.value) {
    activity.value = null
    audits.value = []
    return
  }
  activityLoading.value = true
  try {
    const [activityResult, auditResult] = await Promise.all([
      memberApi.activity(selectedMemberId.value),
      auth.isAdmin ? memberApi.audits(selectedMemberId.value, 20) : Promise.resolve({ records: [], total: 0 }),
    ])
    activity.value = activityResult
    audits.value = auditResult.records
  } catch (reason) {
    ElMessage.error(errorMessage(reason))
  } finally {
    activityLoading.value = false
  }
}

function memberAuditLabel(action: string) {
  return action === 'CREATE' ? '创建会员' : action === 'UPDATE' ? '更新会员' : action
}

async function save() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) {
    await focusFirstInvalid('.member-record-form')
    return
  }
  saving.value = true
  try {
    const payload = {
      memberNo: form.memberNo || undefined,
      name: form.name,
      phone: form.phone,
      level: form.level,
      points: form.points,
      status: form.status,
    }
    const saved =
      form.id !== undefined
        ? await memberApi.update(form.id, payload)
        : await memberApi.create(payload)
    selectedMemberId.value = saved.id
    ElMessage.success(form.id !== undefined ? '会员资料已更新' : '会员已创建')
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
  <div class="member-room">
    <header class="member-head">
      <div>
        <span class="station-kicker">会员名册</span>
        <h1>左侧找人，右侧看档案</h1>
        <p>会员等级、积分和账户状态均由会员接口返回。</p>
      </div>
      <div class="member-head__actions">
        <el-button :icon="Refresh" :loading="loading" @click="load">刷新名册</el-button>
        <el-button type="primary" :icon="Plus" @click="openForm()">新增会员</el-button>
      </div>
    </header>

    <ResourceState
      :loading="loading"
      :error="error"
      :empty="!members.length"
      empty-title="名册中暂无会员"
      empty-description="新增会员后，可在预订和销售中关联身份"
      @retry="load"
    >
      <template #empty-action>
        <el-button type="primary" @click="openForm()">新增会员</el-button>
      </template>

      <section class="member-layout">
        <div class="member-roster">
          <div class="member-roster__filters">
            <el-input
              v-model="keyword"
              :prefix-icon="Search"
              clearable
              placeholder="姓名、电话或会员号"
              aria-label="搜索会员"
            />
            <div>
              <el-select
                v-model="levelFilter"
                clearable
                placeholder="全部等级"
                aria-label="按会员等级筛选"
              >
                <el-option
                  v-for="item in levelCounts"
                  :key="item.level"
                  :label="`${item.label} ${item.count}`"
                  :value="item.level"
                />
              </el-select>
              <el-select
                v-model="statusFilter"
                clearable
                placeholder="全部状态"
                aria-label="按会员状态筛选"
              >
                <el-option label="启用" value="ACTIVE" />
                <el-option label="停用" value="INACTIVE" />
              </el-select>
            </div>
          </div>

          <div class="member-roster__summary" aria-live="polite" aria-atomic="true">
            <span>筛选后 {{ visibleMembers.length }} 人</span>
            <span v-if="selectedMember">
              当前查看 <strong>{{ selectedMember.name }}</strong>
            </span>
          </div>

          <LakeEmptyState
            v-if="!visibleMembers.length"
            title="没有匹配的会员"
            description="调整搜索或筛选条件后再试"
            compact
          />
          <div v-else class="member-list">
            <button
              v-for="member in visibleMembers"
              :key="member.id"
              type="button"
              :class="{ active: String(member.id) === String(selectedMemberId) }"
              :aria-pressed="String(member.id) === String(selectedMemberId)"
              @click="selectMember(member.id)"
            >
              <span class="member-initial">{{ member.name.slice(0, 1) }}</span>
              <div>
                <strong>{{ member.name }}</strong>
                <span>{{ member.phone }}</span>
                <small>{{ member.memberNo || member.id }}</small>
              </div>
              <StatusTag :status="member.status" />
            </button>
          </div>
        </div>

        <aside ref="memberDetailRef" class="member-detail" tabindex="-1">
          <template v-if="selectedMember">
            <header>
              <div class="member-detail__identity">
                <span>{{ selectedMember.name.slice(0, 1) }}</span>
                <div>
                  <small>{{ selectedMember.memberNo || selectedMember.id }}</small>
                  <h2>{{ selectedMember.name }}</h2>
                  <p>{{ levelLabels[selectedMember.level || 'NORMAL'] || selectedMember.level }}</p>
                </div>
              </div>
              <StatusTag :status="selectedMember.status" />
            </header>

            <section class="member-points">
              <span>当前积分</span>
              <strong class="metric-value">{{ selectedMember.points ?? 0 }}</strong>
              <small>积分值来自会员账户</small>
            </section>

            <dl>
              <div>
                <dt>联系电话</dt>
                <dd>{{ selectedMember.phone }}</dd>
              </div>
              <div>
                <dt>会员等级</dt>
                <dd>{{ levelLabels[selectedMember.level || 'NORMAL'] || selectedMember.level }}</dd>
              </div>
              <div>
                <dt>账户状态</dt>
                <dd><StatusTag :status="selectedMember.status" /></dd>
              </div>
              <div>
                <dt>入会时间</dt>
                <dd>{{ formatDateTime(selectedMember.createdAt) }}</dd>
              </div>
            </dl>

            <section class="member-activity" v-loading="activityLoading">
              <header>
                <div>
                  <span>关联业务档案</span>
                  <strong>
                    {{ activity?.bookings.length || 0 }} 次预约 ·
                    {{ activity?.catches.length || 0 }} 笔渔获 ·
                    {{ activity?.salesOrders.length || 0 }} 笔消费
                  </strong>
                </div>
              </header>
              <el-tabs>
                <el-tab-pane label="预约">
                  <LakeEmptyState
                    v-if="!activity?.bookings.length"
                    title="暂无预约记录"
                    description="会员关联预约后会显示在这里"
                    compact
                  />
                  <div v-else class="activity-list">
                    <article v-for="booking in activity.bookings.slice(0, 8)" :key="booking.id">
                      <div><strong>{{ booking.bookingNo || booking.id }}</strong><span>{{ booking.spotName }}</span></div>
                      <div><span>{{ formatDate(booking.fishingDate) }} {{ timeSlotLabel(booking.timeSlot) }}</span><b>{{ formatCurrency(booking.amount) }}</b></div>
                    </article>
                  </div>
                </el-tab-pane>
                <el-tab-pane label="渔获">
                  <LakeEmptyState
                    v-if="!activity?.catches.length"
                    title="暂无渔获记录"
                    description="会员关联渔获后会显示在这里"
                    compact
                  />
                  <div v-else class="activity-list">
                    <article v-for="record in activity.catches.slice(0, 8)" :key="record.id">
                      <div><strong>{{ record.species }}</strong><span>{{ record.zoneName }} · {{ record.spotName }}</span></div>
                      <div><span>{{ formatDate(record.fishingDate) }} {{ timeSlotLabel(record.timeSlot) }}</span><b>{{ record.weight }} kg</b></div>
                    </article>
                  </div>
                </el-tab-pane>
                <el-tab-pane label="消费">
                  <LakeEmptyState
                    v-if="!activity?.salesOrders.length"
                    title="暂无消费记录"
                    description="会员关联现场销售后会显示在这里"
                    compact
                  />
                  <div v-else class="activity-list">
                    <article v-for="order in activity.salesOrders.slice(0, 8)" :key="order.id">
                      <div><strong>{{ order.orderNo || order.id }}</strong><StatusTag :status="order.status" /></div>
                      <div><span>{{ formatDateTime(order.createdAt) }}</span><b>{{ formatCurrency(order.totalAmount) }}</b></div>
                    </article>
                  </div>
                </el-tab-pane>
                <el-tab-pane v-if="auth.isAdmin" label="审计">
                  <LakeEmptyState
                    v-if="!audits.length"
                    title="暂无会员操作记录"
                    description="创建或修改会员后会留下审计轨迹"
                    compact
                  />
                  <div v-else class="activity-list">
                    <article v-for="audit in audits" :key="audit.id">
                      <div><strong>{{ memberAuditLabel(audit.action) }}</strong><span>{{ audit.actorUsername }}</span></div>
                      <div><span>{{ formatDateTime(audit.createdAt) }}</span><b>{{ audit.beforeStatus || '—' }} → {{ audit.afterStatus || '—' }}</b></div>
                    </article>
                  </div>
                </el-tab-pane>
              </el-tabs>
            </section>

            <el-button type="primary" :icon="Edit" @click="openForm(selectedMember)">
              编辑会员资料
            </el-button>
          </template>
          <LakeEmptyState
            v-else
            title="从名册中选择会员"
            description="会员资料、积分和状态会显示在这里"
            compact
          />
        </aside>
      </section>
    </ResourceState>

    <el-dialog
      v-model="dialogOpen"
      :title="form.id !== undefined ? '编辑会员' : '新增会员'"
      width="620px"
      destroy-on-close
    >
      <el-form
        ref="formRef"
        class="member-record-form"
        :model="form"
        :rules="rules"
        label-position="top"
        aria-live="polite"
        @submit.prevent="save"
      >
        <div class="form-grid">
          <el-form-item label="会员姓名" prop="name">
            <el-input v-model.trim="form.name" name="name" autocomplete="off" placeholder="请输入姓名…" />
          </el-form-item>
          <el-form-item label="联系电话" prop="phone">
            <el-input
              v-model.trim="form.phone"
              name="phone"
              inputmode="tel"
              autocomplete="off"
              placeholder="请输入联系电话…"
            />
          </el-form-item>
          <el-form-item label="会员编号">
            <el-input
              v-model.trim="form.memberNo"
              name="memberNo"
              autocomplete="off"
              placeholder="留空时由服务端生成…"
            />
          </el-form-item>
          <el-form-item label="会员等级">
            <el-select v-model="form.level" aria-label="会员等级" style="width: 100%">
              <el-option label="普通会员" value="NORMAL" />
              <el-option label="银卡会员" value="SILVER" />
              <el-option label="金卡会员" value="GOLD" />
              <el-option label="贵宾会员" value="VIP" />
            </el-select>
          </el-form-item>
          <el-form-item label="积分">
            <el-input-number
              v-model="form.points"
              :min="0"
              aria-label="会员积分"
              controls-position="right"
            />
          </el-form-item>
          <el-form-item label="账户状态">
            <el-select v-model="form.status" aria-label="账户状态" style="width: 100%">
              <el-option label="启用" value="ACTIVE" />
              <el-option label="停用" value="INACTIVE" />
            </el-select>
          </el-form-item>
        </div>
      </el-form>
      <template #footer>
        <el-button @click="dialogOpen = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存会员</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.member-head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 22px;
  margin-bottom: 15px;
}

.member-head h1 {
  margin: 0;
  color: var(--lake-900);
  font-size: clamp(25px, 2.3vw, 34px);
  letter-spacing: -0.04em;
}

.member-head p {
  margin: 6px 0 0;
  color: var(--ink-500);
  font-size: 11px;
}

.member-head__actions {
  display: flex;
  gap: 8px;
}

.member-layout {
  display: grid;
  grid-template-columns: minmax(320px, 0.78fr) minmax(390px, 1.22fr);
  overflow: hidden;
  min-height: 620px;
  border: 1px solid #ded5c8;
  border-radius: 14px;
  background: var(--paper-50);
}

.member-roster {
  min-width: 0;
  border-right: 1px solid #ded5c8;
  background: var(--paper-100);
}

.member-roster__filters {
  display: grid;
  gap: 8px;
  padding: 13px;
  border-bottom: 1px solid #ded5c8;
}

.member-roster__filters > div {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 7px;
}

.member-roster__summary {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 8px 13px;
  border-bottom: 1px solid #ded5c8;
  color: var(--ink-500);
  background: #f5f0e6;
  font-size: 10px;
}

.member-roster__summary strong {
  color: var(--lake-800);
}

.member-list {
  max-height: 535px;
  overflow-y: auto;
  padding: 7px;
}

.member-list > button {
  display: grid;
  width: 100%;
  grid-template-columns: 38px minmax(0, 1fr) auto;
  align-items: center;
  gap: 10px;
  padding: 11px;
  border: 0;
  border-radius: 10px;
  color: #44524c;
  background: transparent;
  text-align: left;
  cursor: pointer;
}

.member-list > button:hover {
  background: #efe9de;
}

.member-list > button.active {
  background: #e4ece7;
}

.member-initial {
  display: grid;
  width: 38px;
  height: 38px;
  place-items: center;
  border-radius: 10px;
  color: var(--paper-100);
  background: var(--lake-800);
  font-size: 14px;
  font-weight: 750;
}

.member-list strong,
.member-list div > span,
.member-list small {
  display: block;
}

.member-list strong {
  color: var(--ink-900);
  font-size: 12px;
}

.member-list div > span {
  margin-top: 3px;
  color: #68756f;
  font-size: 11px;
}

.member-list small {
  margin-top: 3px;
  color: #9a8d7d;
  font-size: 11px;
}

.member-detail {
  padding: clamp(22px, 4vw, 46px);
}

.member-detail > header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 18px;
  padding-bottom: 22px;
  border-bottom: 1px solid #e2dacd;
}

.member-detail__identity {
  display: flex;
  align-items: center;
  gap: 14px;
}

.member-detail__identity > span {
  display: grid;
  width: 62px;
  height: 62px;
  place-items: center;
  border-radius: 14px;
  color: var(--paper-100);
  background: var(--lake-900);
  font-size: 23px;
  font-weight: 760;
}

.member-detail small {
  color: var(--clay-700);
  font-size: 11px;
}

.member-detail h2 {
  margin: 4px 0 0;
  color: var(--lake-900);
  font-size: 22px;
}

.member-detail__identity p {
  margin: 4px 0 0;
  color: var(--ink-500);
  font-size: 10px;
}

.member-points {
  margin: 24px 0;
  padding: 18px;
  border-left: 4px solid var(--clay-600);
  background: var(--clay-50);
}

.member-points span,
.member-points strong,
.member-points small {
  display: block;
}

.member-points span,
.member-points small {
  color: #8d6255;
  font-size: 11px;
}

.member-points strong {
  margin: 6px 0;
  color: var(--clay-700);
  font-size: 30px;
}

.member-detail dl {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 15px;
  margin: 0 0 26px;
}

.member-detail dl > div {
  padding-bottom: 13px;
  border-bottom: 1px solid var(--paper-300);
}

.member-detail dt {
  color: #8c948f;
  font-size: 11px;
}

.member-detail dd {
  margin: 6px 0 0;
  color: #34443d;
  font-size: 12px;
}

.member-activity {
  margin: 0 0 24px;
  padding: 16px;
  border: 1px solid #ded5c8;
  border-radius: 10px;
  background: #fbf8f1;
}

.member-activity > header {
  margin-bottom: 8px;
}

.member-activity > header span,
.member-activity > header strong {
  display: block;
}

.member-activity > header span {
  color: var(--clay-700);
  font-size: 11px;
}

.member-activity > header strong {
  margin-top: 4px;
  color: var(--lake-900);
  font-size: 12px;
}

.activity-list {
  display: grid;
  gap: 8px;
}

.activity-list article {
  display: grid;
  gap: 5px;
  padding: 10px 0;
  border-bottom: 1px solid #e5ddd0;
}

.activity-list article:last-child {
  border-bottom: 0;
}

.activity-list article > div {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.activity-list strong,
.activity-list b {
  color: var(--lake-900);
  font-size: 11px;
}

.activity-list span {
  color: var(--ink-500);
  font-size: 10px;
}

@media (max-width: 1020px) {
  .member-layout {
    grid-template-columns: 1fr;
  }

  .member-roster {
    border-right: 0;
    border-bottom: 1px solid #ded5c8;
  }

  .member-list {
    max-height: 340px;
  }
}

@media (max-width: 600px) {
  .member-head {
    display: block;
  }

  .member-head__actions {
    margin-top: 13px;
  }

  .member-detail {
    padding: 18px;
  }

  .member-detail dl {
    grid-template-columns: 1fr;
  }
}
</style>
