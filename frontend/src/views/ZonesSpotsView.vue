<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { Edit, Plus, Refresh, Search } from '@element-plus/icons-vue'
import ResourceState from '@/components/ResourceState.vue'
import LakeEmptyState from '@/components/LakeEmptyState.vue'
import StatusTag from '@/components/StatusTag.vue'
import { spotApi, zoneApi } from '@/api'
import { errorMessage } from '@/api/http'
import { statusLabel } from '@/utils/format'
import { focusFirstInvalid } from '@/utils/forms'
import { useAuthStore } from '@/stores/auth'
import type { Id, Spot, Zone } from '@/types'

const auth = useAuthStore()
const canManageConfiguration = computed(() => auth.isAdmin)
const viewMode = ref('map')
const loading = ref(true)
const error = ref('')
const keyword = ref('')
const statusFilter = ref('')
const zones = ref<Zone[]>([])
const spots = ref<Spot[]>([])
const mapSpots = ref<Spot[]>([])
const selectedZoneId = ref<Id | ''>('')
const selectedSpotId = ref<Id | ''>('')
const zoneDialogOpen = ref(false)
const spotDialogOpen = ref(false)
const saving = ref(false)
const zoneFormRef = ref<FormInstance>()
const spotFormRef = ref<FormInstance>()

type ZoneForm = {
  id?: Id
  name: string
  code: string
  description: string
  status: string
}

type SpotForm = {
  id?: Id
  zoneId: Id | ''
  code: string
  name: string
  status: string
  capacity: number
  mapX: number | null
  mapY: number | null
  note: string
}

const zoneForm = reactive<ZoneForm>({
  name: '',
  code: '',
  description: '',
  status: 'ACTIVE',
})

const spotForm = reactive<SpotForm>({
  zoneId: '',
  code: '',
  name: '',
  status: 'OPEN',
  capacity: 1,
  mapX: 50,
  mapY: 50,
  note: '',
})

const zoneRules: FormRules = {
  name: [{ required: true, message: '请输入分区名称', trigger: 'blur' }],
  code: [{ required: true, message: '请输入分区编码', trigger: 'blur' }],
}

const spotRules: FormRules = {
  zoneId: [{ required: true, message: '请选择所属分区', trigger: 'change' }],
  code: [{ required: true, message: '请输入钓位编码', trigger: 'blur' }],
  name: [{ required: true, message: '请输入钓位名称', trigger: 'blur' }],
  status: [{ required: true, message: '请选择钓位状态', trigger: 'change' }],
}

const filteredSpots = computed(() => {
  const query = keyword.value.trim().toLowerCase()
  return spots.value.filter((spot) => {
    const text = `${spot.code} ${spot.name || ''} ${spot.zoneName || ''}`.toLowerCase()
    return (!query || text.includes(query)) && (!statusFilter.value || spot.status === statusFilter.value)
  })
})

const selectedZone = computed(() =>
  zones.value.find((zone) => String(zone.id) === String(selectedZoneId.value)),
)

const selectedZoneSpots = computed(() =>
  mapSpots.value.filter((spot) => String(spot.zoneId) === String(selectedZoneId.value)),
)

const positionedSpots = computed(() =>
  selectedZoneSpots.value.filter(
    (spot) => spot.mapX !== null && spot.mapX !== undefined && spot.mapY !== null && spot.mapY !== undefined,
  ),
)

const unpositionedSpots = computed(() =>
  selectedZoneSpots.value.filter(
    (spot) => spot.mapX === null || spot.mapX === undefined || spot.mapY === null || spot.mapY === undefined,
  ),
)

const selectedSpot = computed(() =>
  mapSpots.value.find((spot) => String(spot.id) === String(selectedSpotId.value)),
)

function zoneSpotCount(zoneId: Id) {
  return mapSpots.value.filter((spot) => String(spot.zoneId) === String(zoneId)).length
}

function chooseZone(zoneId: Id) {
  selectedZoneId.value = zoneId
  selectedSpotId.value =
    mapSpots.value.find((spot) => String(spot.zoneId) === String(zoneId))?.id ?? ''
}

function chooseSpot(spot: Spot) {
  selectedSpotId.value = spot.id
}

function pointStyle(spot: Spot) {
  const x = Math.min(100, Math.max(0, Number(spot.mapX ?? 0)))
  const y = Math.min(100, Math.max(0, Number(spot.mapY ?? 0)))
  return { left: `${x}%`, top: `${y}%` }
}

function pointClass(status: string) {
  if (status === 'OPEN') return 'lake-point--open'
  if (status === 'MAINTENANCE') return 'lake-point--maintenance'
  return 'lake-point--closed'
}

function resetZoneForm(zone?: Zone) {
  Object.assign(zoneForm, {
    id: zone?.id,
    name: zone?.name ?? '',
    code: zone?.code ?? '',
    description: zone?.description ?? '',
    status: zone?.status ?? 'ACTIVE',
  })
  zoneFormRef.value?.clearValidate()
}

function resetSpotForm(spot?: Spot) {
  Object.assign(spotForm, {
    id: spot?.id,
    zoneId: spot?.zoneId ?? selectedZoneId.value ?? zones.value[0]?.id ?? '',
    code: spot?.code ?? '',
    name: spot?.name ?? '',
    status: spot?.status ?? 'OPEN',
    capacity: spot?.capacity ?? 1,
    mapX: spot ? (spot.mapX ?? null) : 50,
    mapY: spot ? (spot.mapY ?? null) : 50,
    note: spot?.note ?? '',
  })
  spotFormRef.value?.clearValidate()
}

function openZone(zone?: Zone) {
  resetZoneForm(zone)
  zoneDialogOpen.value = true
}

function openSpot(spot?: Spot) {
  resetSpotForm(spot)
  spotDialogOpen.value = true
}

function clearSpotCoordinates() {
  spotForm.mapX = null
  spotForm.mapY = null
}

async function load() {
  loading.value = true
  error.value = ''
  try {
    const [zoneResult, spotResult, mapResult] = await Promise.all([
      zoneApi.list(),
      spotApi.list(),
      spotApi.map(),
    ])
    const mapById = new Map(
      mapResult.records.map((spot) => [String(spot.id), spot]),
    )
    const mergedSpots = spotResult.records.map((spot) => ({
      ...spot,
      ...mapById.get(String(spot.id)),
    }))
    const listedIds = new Set(mergedSpots.map((spot) => String(spot.id)))
    const mapOnlySpots = mapResult.records.filter((spot) => !listedIds.has(String(spot.id)))
    zones.value = zoneResult.records
    spots.value = [...mergedSpots, ...mapOnlySpots]
    mapSpots.value = spots.value
    if (!zones.value.some((zone) => String(zone.id) === String(selectedZoneId.value))) {
      selectedZoneId.value = zones.value[0]?.id ?? ''
    }
    if (!mapSpots.value.some((spot) => String(spot.id) === String(selectedSpotId.value))) {
      selectedSpotId.value =
        mapSpots.value.find((spot) => String(spot.zoneId) === String(selectedZoneId.value))?.id ?? ''
    }
  } catch (reason) {
    error.value = errorMessage(reason)
  } finally {
    loading.value = false
  }
}

async function saveZone() {
  const valid = await zoneFormRef.value?.validate().catch(() => false)
  if (!valid) {
    await focusFirstInvalid('.zone-record-form')
    return
  }
  saving.value = true
  try {
    const payload = {
      name: zoneForm.name,
      code: zoneForm.code,
      description: zoneForm.description,
      status: zoneForm.status,
    }
    if (zoneForm.id !== undefined) await zoneApi.update(zoneForm.id, payload)
    else await zoneApi.create(payload)
    ElMessage.success(zoneForm.id !== undefined ? '分区已更新' : '分区已创建')
    zoneDialogOpen.value = false
    await load()
  } catch (reason) {
    ElMessage.error(errorMessage(reason))
  } finally {
    saving.value = false
  }
}

async function saveSpot() {
  const valid = await spotFormRef.value?.validate().catch(() => false)
  if (!valid) {
    await focusFirstInvalid('.spot-record-form')
    return
  }
  saving.value = true
  try {
    const payload = {
      zoneId: spotForm.zoneId,
      code: spotForm.code,
      name: spotForm.name,
      status: spotForm.status,
      capacity: spotForm.capacity,
      mapX: spotForm.mapX,
      mapY: spotForm.mapY,
      note: spotForm.note,
    }
    if (spotForm.id !== undefined) await spotApi.update(spotForm.id, payload)
    else await spotApi.create(payload)
    ElMessage.success(spotForm.id !== undefined ? '钓位已更新' : '钓位已创建')
    spotDialogOpen.value = false
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
  <div class="zones-workbench">
    <header class="station-intro">
      <div>
        <span class="station-kicker">湖区平面图</span>
        <h1>钓位分区与现场状态</h1>
        <p>地图严格使用钓位的 mapX 和 mapY 坐标，未定位钓位单独列出。</p>
      </div>
      <div class="station-actions">
        <el-radio-group v-model="viewMode" aria-label="钓位视图">
          <el-radio-button value="map">平面图</el-radio-button>
          <el-radio-button value="list">钓位册</el-radio-button>
          <el-radio-button value="zones">分区册</el-radio-button>
        </el-radio-group>
        <el-button v-if="canManageConfiguration" :icon="Plus" @click="openZone()">新增分区</el-button>
        <el-button
          v-if="canManageConfiguration"
          type="primary"
          :icon="Plus"
          :disabled="!zones.length"
          @click="openSpot()"
        >
          新增钓位
        </el-button>
      </div>
    </header>

    <ResourceState :loading="loading" :error="error" @retry="load">
      <section v-if="viewMode === 'map'" class="lake-plan-layout">
        <aside class="zone-rail">
          <header>
            <span>湖区</span>
            <el-button :icon="Refresh" text aria-label="刷新湖区状态" @click="load" />
          </header>
          <button
            v-for="zone in zones"
            :key="zone.id"
            type="button"
            :class="{ active: String(zone.id) === String(selectedZoneId) }"
            :aria-pressed="String(zone.id) === String(selectedZoneId)"
            @click="chooseZone(zone.id)"
          >
            <span>{{ zone.name }}</span>
            <strong>{{ zoneSpotCount(zone.id) }}</strong>
            <small>{{ zone.code || '未编码' }}</small>
          </button>
        </aside>

        <div class="lake-plan paper-panel">
          <header>
            <div>
              <h2>{{ selectedZone?.name || '暂无湖区' }}</h2>
              <p>{{ selectedZone?.description || '该湖区暂无说明' }}</p>
            </div>
            <StatusTag :status="selectedZone?.status" />
          </header>
          <LakeEmptyState
            v-if="!selectedZoneSpots.length"
            title="该湖区暂无钓位"
            description="新增钓位后，可在平面图中安排坐标"
            compact
          />
          <template v-else>
            <div class="lake-plan-canvas" aria-label="钓位平面位置">
              <span class="lake-bank lake-bank--north">北岸</span>
              <span class="lake-bank lake-bank--south">南岸</span>
              <button
                v-for="spot in positionedSpots"
                :key="spot.id"
                type="button"
                class="lake-point"
                :class="[
                  pointClass(spot.status),
                  { selected: String(spot.id) === String(selectedSpotId) },
                ]"
                :style="pointStyle(spot)"
                :aria-label="`${spot.name || spot.code}，${statusLabel(spot.status)}`"
                :aria-pressed="String(spot.id) === String(selectedSpotId)"
                @click="chooseSpot(spot)"
              >
                {{ spot.code }}
              </button>
            </div>
            <div v-if="unpositionedSpots.length" class="unpositioned-spots">
              <span>未定位</span>
              <button
                v-for="spot in unpositionedSpots"
                :key="spot.id"
                type="button"
                @click="chooseSpot(spot)"
              >
                {{ spot.code }}
              </button>
            </div>
          </template>
        </div>

        <aside class="spot-inspector">
          <template v-if="selectedSpot">
            <div class="spot-inspector__title">
              <span>{{ selectedSpot.code }}</span>
              <h2>{{ selectedSpot.name || '未命名钓位' }}</h2>
              <StatusTag :status="selectedSpot.status" />
            </div>
            <dl>
              <div>
                <dt>所属湖区</dt>
                <dd>{{ selectedSpot.zoneName || selectedZone?.name || '暂无' }}</dd>
              </div>
              <div>
                <dt>可容纳</dt>
                <dd>{{ selectedSpot.capacity ?? '暂无' }} 人</dd>
              </div>
              <div>
                <dt>平面坐标</dt>
                <dd>
                  {{
                    selectedSpot.mapX !== undefined && selectedSpot.mapY !== undefined
                      ? `${selectedSpot.mapX}, ${selectedSpot.mapY}`
                      : '未定位'
                  }}
                </dd>
              </div>
              <div>
                <dt>现场备注</dt>
                <dd>{{ selectedSpot.note || '暂无备注' }}</dd>
              </div>
            </dl>
            <el-button
              v-if="canManageConfiguration"
              type="primary"
              :icon="Edit"
              @click="openSpot(selectedSpot)"
            >
              编辑钓位
            </el-button>
          </template>
          <LakeEmptyState
            v-else
            title="选择钓位查看详情"
            description="从湖区平面图或未定位列表中选择一处钓位"
            compact
          />
        </aside>
      </section>

      <section v-else-if="viewMode === 'list'" class="spot-ledger paper-panel">
        <div class="toolbar">
          <el-input
            v-model="keyword"
            :prefix-icon="Search"
            clearable
            placeholder="搜索钓位或分区"
            aria-label="搜索钓位或分区"
            style="width: 230px"
          />
          <el-select
            v-model="statusFilter"
            clearable
            placeholder="全部状态"
            aria-label="按钓位状态筛选"
            style="width: 140px"
          >
            <el-option label="开放" value="OPEN" />
            <el-option label="维护中" value="MAINTENANCE" />
            <el-option label="关闭" value="CLOSED" />
          </el-select>
          <div class="toolbar__spacer" />
          <span class="list-count">共 {{ filteredSpots.length }} 个钓位</span>
        </div>
        <LakeEmptyState
          v-if="!filteredSpots.length"
          title="暂无匹配钓位"
          description="可调整关键词或状态筛选条件"
        />
        <el-table v-else :data="filteredSpots" class="ledger-table" stripe>
          <el-table-column prop="code" label="钓位编码" min-width="110" />
          <el-table-column prop="name" label="钓位名称" min-width="130" />
          <el-table-column prop="zoneName" label="所属湖区" min-width="120" />
          <el-table-column label="坐标" min-width="110">
            <template #default="{ row }">
              {{ row.mapX ?? '未定' }}, {{ row.mapY ?? '未定' }}
            </template>
          </el-table-column>
          <el-table-column label="容量" width="90">
            <template #default="{ row }">{{ row.capacity ?? '暂无' }}</template>
          </el-table-column>
          <el-table-column label="状态" width="105">
            <template #default="{ row }"><StatusTag :status="row.status" /></template>
          </el-table-column>
          <el-table-column v-if="canManageConfiguration" label="操作" width="90" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" @click="openSpot(row)">编辑</el-button>
            </template>
          </el-table-column>
        </el-table>
      </section>

      <section v-else class="zone-ledger paper-panel">
        <LakeEmptyState
          v-if="!zones.length"
          title="暂无分区"
          description="先创建湖区分区，再添加对应钓位"
        >
          <el-button v-if="canManageConfiguration" type="primary" @click="openZone()">新增分区</el-button>
        </LakeEmptyState>
        <el-table v-else :data="zones" class="ledger-table" stripe>
          <el-table-column prop="name" label="分区名称" min-width="130" />
          <el-table-column prop="code" label="编码" min-width="100" />
          <el-table-column prop="description" label="说明" min-width="240">
            <template #default="{ row }">{{ row.description || '暂无说明' }}</template>
          </el-table-column>
          <el-table-column label="钓位数" width="95">
            <template #default="{ row }">{{ zoneSpotCount(row.id) }}</template>
          </el-table-column>
          <el-table-column label="状态" width="100">
            <template #default="{ row }"><StatusTag :status="row.status" /></template>
          </el-table-column>
          <el-table-column v-if="canManageConfiguration" label="操作" width="90">
            <template #default="{ row }">
              <el-button link type="primary" @click="openZone(row)">编辑</el-button>
            </template>
          </el-table-column>
        </el-table>
      </section>
    </ResourceState>

    <el-dialog
      v-if="canManageConfiguration"
      v-model="zoneDialogOpen"
      :title="zoneForm.id !== undefined ? '编辑分区' : '新增分区'"
      width="560px"
      destroy-on-close
    >
      <el-form
        ref="zoneFormRef"
        class="zone-record-form"
        :model="zoneForm"
        :rules="zoneRules"
        label-position="top"
        aria-live="polite"
        @submit.prevent="saveZone"
      >
        <div class="form-grid">
          <el-form-item label="分区名称" prop="name">
            <el-input v-model.trim="zoneForm.name" name="zoneName" placeholder="例如 东岸区…" />
          </el-form-item>
          <el-form-item label="分区编码" prop="code">
            <el-input v-model.trim="zoneForm.code" name="zoneCode" placeholder="请输入唯一编码…" />
          </el-form-item>
          <el-form-item label="状态">
            <el-select v-model="zoneForm.status" aria-label="分区状态" style="width: 100%">
              <el-option label="启用" value="ACTIVE" />
              <el-option label="停用" value="INACTIVE" />
            </el-select>
          </el-form-item>
          <el-form-item label="分区说明" class="form-span-2">
            <el-input
              v-model="zoneForm.description"
              type="textarea"
              :rows="3"
              placeholder="填写分区位置或管理提示…"
            />
          </el-form-item>
        </div>
      </el-form>
      <template #footer>
        <el-button @click="zoneDialogOpen = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveZone">保存分区</el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-if="canManageConfiguration"
      v-model="spotDialogOpen"
      :title="spotForm.id !== undefined ? '编辑钓位' : '新增钓位'"
      width="660px"
      destroy-on-close
    >
      <el-form
        ref="spotFormRef"
        class="spot-record-form"
        :model="spotForm"
        :rules="spotRules"
        label-position="top"
        aria-live="polite"
        @submit.prevent="saveSpot"
      >
        <div class="form-grid">
          <el-form-item label="所属分区" prop="zoneId">
            <el-select v-model="spotForm.zoneId" aria-label="所属分区" style="width: 100%">
              <el-option v-for="zone in zones" :key="zone.id" :label="zone.name" :value="zone.id" />
            </el-select>
          </el-form-item>
          <el-form-item label="钓位编码" prop="code">
            <el-input v-model.trim="spotForm.code" name="spotCode" placeholder="请输入唯一编码…" />
          </el-form-item>
          <el-form-item label="钓位名称" prop="name">
            <el-input v-model.trim="spotForm.name" name="spotName" placeholder="请输入钓位名称…" />
          </el-form-item>
          <el-form-item label="状态" prop="status">
            <el-select v-model="spotForm.status" aria-label="钓位状态" style="width: 100%">
              <el-option label="开放" value="OPEN" />
              <el-option label="维护中" value="MAINTENANCE" />
              <el-option label="关闭" value="CLOSED" />
            </el-select>
          </el-form-item>
          <el-form-item label="容量">
            <el-input-number
              v-model="spotForm.capacity"
              :min="1"
              aria-label="钓位容量"
              controls-position="right"
            />
          </el-form-item>
          <el-form-item label="地图横坐标">
            <el-input-number
              v-model="spotForm.mapX"
              :min="0"
              :max="100"
              aria-label="地图横坐标"
              controls-position="right"
            />
          </el-form-item>
          <el-form-item label="地图纵坐标">
            <el-input-number
              v-model="spotForm.mapY"
              :min="0"
              :max="100"
              aria-label="地图纵坐标"
              controls-position="right"
            />
          </el-form-item>
          <div class="coordinate-note form-span-2">
            <span>坐标为空时，钓位会保留在“未定位”列表中。</span>
            <el-button link type="primary" @click="clearSpotCoordinates">清除坐标</el-button>
          </div>
          <el-form-item label="备注" class="form-span-2">
            <el-input
              v-model="spotForm.note"
              type="textarea"
              :rows="3"
              placeholder="填写现场提示…"
            />
          </el-form-item>
        </div>
      </el-form>
      <template #footer>
        <el-button @click="spotDialogOpen = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveSpot">保存钓位</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.lake-plan-layout {
  display: grid;
  grid-template-columns: 164px minmax(0, 1fr) 240px;
  gap: 13px;
  min-height: 610px;
}

.coordinate-note {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-top: -9px;
  color: #858e88;
  font-size: 10px;
}

.zone-rail,
.spot-inspector {
  overflow: hidden;
  border: 1px solid #e2dacd;
  border-radius: 14px;
  background: var(--paper-100);
}

.zone-rail > header {
  display: flex;
  height: 49px;
  align-items: center;
  justify-content: space-between;
  padding: 0 12px;
  border-bottom: 1px solid #e2dacd;
  color: var(--lake-900);
  font-size: 11px;
  font-weight: 750;
}

.zone-rail > button {
  display: grid;
  width: calc(100% - 12px);
  grid-template-columns: 1fr auto;
  gap: 3px 8px;
  margin: 6px;
  padding: 11px;
  border: 0;
  border-radius: 9px;
  color: var(--ink-700);
  background: transparent;
  text-align: left;
  cursor: pointer;
}

.zone-rail > button:hover {
  background: #efe9de;
}

.zone-rail > button.active {
  color: #f7f2e9;
  background: var(--lake-900);
}

.zone-rail button span {
  font-size: 12px;
  font-weight: 700;
}

.zone-rail button strong {
  font-size: 13px;
}

.zone-rail button small {
  grid-column: 1 / -1;
  opacity: 0.65;
  font-size: 11px;
}

.lake-plan {
  min-width: 0;
  padding: 17px;
}

.lake-plan > header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 13px;
}

.lake-plan h2 {
  margin: 0;
  color: var(--lake-900);
  font-size: 16px;
}

.lake-plan header p {
  margin: 4px 0 0;
  color: var(--ink-500);
  font-size: 10px;
}

.lake-plan-canvas {
  position: relative;
  min-height: 490px;
  overflow: hidden;
  border: 8px solid var(--paper-300);
  border-radius: 46% 36% 42% 34% / 26% 42% 31% 46%;
  background: #dce8df;
}

.lake-plan-canvas::before {
  position: absolute;
  inset: 11%;
  border: 1px solid rgb(32 87 71 / 15%);
  border-radius: inherit;
  content: "";
}

.lake-bank {
  position: absolute;
  z-index: 1;
  color: #657b72;
  font-size: 11px;
}

.lake-bank--north {
  top: 13px;
  left: 50%;
}

.lake-bank--south {
  right: 50%;
  bottom: 13px;
}

.lake-point {
  position: absolute;
  z-index: 2;
  min-width: 42px;
  min-height: 30px;
  padding: 4px 7px;
  border: 2px solid var(--paper-50);
  border-radius: 8px;
  box-shadow: 0 2px 0 rgb(23 70 58 / 14%);
  font-size: 11px;
  font-weight: 800;
  transform: translate(-50%, -50%);
  cursor: pointer;
}

.lake-point--open {
  color: #f7f2e9;
  background: var(--lake-800);
}

.lake-point--maintenance {
  color: #fff8f2;
  background: var(--clay-600);
}

.lake-point--closed {
  color: #6c756f;
  background: #d7d4cc;
}

.lake-point.selected {
  outline: 3px solid var(--clay-600);
  outline-offset: 2px;
}

.unpositioned-spots {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
  margin-top: 12px;
}

.unpositioned-spots > span {
  margin-right: 4px;
  color: var(--clay-700);
  font-size: 10px;
  font-weight: 700;
}

.unpositioned-spots button {
  padding: 5px 8px;
  border: 1px solid var(--paper-400);
  border-radius: 7px;
  color: var(--ink-700);
  background: var(--paper-100);
  font-size: 11px;
  cursor: pointer;
}

.spot-inspector {
  align-self: start;
  padding: 17px;
}

.spot-inspector__title {
  padding-bottom: 16px;
  border-bottom: 1px solid #e2dacd;
}

.spot-inspector__title > span {
  color: var(--clay-700);
  font-size: 10px;
  font-weight: 750;
}

.spot-inspector__title h2 {
  margin: 5px 0 10px;
  color: var(--lake-900);
  font-size: 18px;
}

.spot-inspector dl {
  margin: 9px 0 17px;
}

.spot-inspector dl > div {
  padding: 10px 0;
  border-bottom: 1px solid #e8e0d4;
}

.spot-inspector dt {
  color: #8c948f;
  font-size: 11px;
}

.spot-inspector dd {
  margin: 4px 0 0;
  color: #3d4a44;
  font-size: 11px;
  line-height: 1.5;
}

.spot-ledger,
.zone-ledger {
  padding: 17px;
}

@media (max-width: 1120px) {
  .lake-plan-layout {
    grid-template-columns: 145px minmax(0, 1fr);
  }

  .spot-inspector {
    grid-column: 1 / -1;
    display: grid;
    grid-template-columns: 1fr 2fr auto;
    gap: 15px;
    align-items: end;
  }

  .spot-inspector dl {
    display: grid;
    grid-template-columns: repeat(4, 1fr);
    margin: 0;
  }
}

@media (max-width: 760px) {
  .lake-plan-layout {
    grid-template-columns: 1fr;
  }

  .zone-rail {
    display: flex;
    overflow-x: auto;
    padding: 6px;
  }

  .zone-rail > header {
    display: none;
  }

  .zone-rail > button {
    width: 130px;
    flex: 0 0 130px;
    margin: 0 5px 0 0;
  }

  .lake-plan-canvas {
    min-height: 420px;
  }

  .spot-inspector {
    grid-column: auto;
    display: block;
  }

  .spot-inspector dl {
    display: block;
  }
}

@media (max-width: 420px) {
  .lake-plan {
    padding: 11px;
  }

  .lake-plan-canvas {
    min-height: 360px;
    border-width: 5px;
  }

  .lake-point {
    min-width: 36px;
    min-height: 27px;
    padding: 3px 5px;
    font-size: 11px;
  }
}

@media (pointer: coarse) {
  .lake-point::after {
    position: absolute;
    inset: -8px;
    content: "";
  }
}
</style>
