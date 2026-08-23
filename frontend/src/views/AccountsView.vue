<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { Plus, Refresh } from '@element-plus/icons-vue'
import ResourceState from '@/components/ResourceState.vue'
import LakeEmptyState from '@/components/LakeEmptyState.vue'
import StatusTag from '@/components/StatusTag.vue'
import { accountApi } from '@/api'
import { errorMessage } from '@/api/http'
import { useAuthStore } from '@/stores/auth'
import { formatDateTime } from '@/utils/format'
import { focusFirstInvalid } from '@/utils/forms'
import type { Account, AccountAudit } from '@/types'

const auth = useAuthStore()
const loading = ref(true)
const error = ref('')
const accounts = ref<Account[]>([])
const audits = ref<AccountAudit[]>([])
const dialogOpen = ref(false)
const saving = ref(false)
const formRef = ref<FormInstance>()
const mode = ref<'create' | 'edit'>('create')
const form = reactive({
  id: undefined as Account['id'] | undefined,
  username: '',
  displayName: '',
  password: '',
  role: 'OPERATOR' as Account['role'],
  enabled: true,
  expectedVersion: 0,
})

const rules: FormRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  displayName: [{ required: true, message: '请输入显示名称', trigger: 'blur' }],
  password: [{ required: true, message: '请输入初始密码', trigger: 'blur' }],
  role: [{ required: true, message: '请选择角色', trigger: 'change' }],
}
const enabledAdmins = computed(() => accounts.value.filter((item) => item.enabled && item.role === 'ADMIN').length)

async function load() {
  loading.value = true
  error.value = ''
  try {
    const [accountResult, auditResult] = await Promise.all([accountApi.list(), accountApi.audits({ limit: 20 })])
    accounts.value = accountResult.records
    audits.value = auditResult.records
  } catch (reason) {
    error.value = errorMessage(reason)
  } finally {
    loading.value = false
  }
}

function openCreate() {
  mode.value = 'create'
  Object.assign(form, { id: undefined, username: '', displayName: '', password: '', role: 'OPERATOR', enabled: true, expectedVersion: 0 })
  dialogOpen.value = true
}

function openEdit(account: Account) {
  mode.value = 'edit'
  Object.assign(form, { id: account.id, username: account.username, displayName: account.displayName, password: '', role: account.role, enabled: account.enabled, expectedVersion: account.version })
  dialogOpen.value = true
}

async function save() {
  if (!formRef.value) return
  try {
    await formRef.value.validate()
  } catch {
    focusFirstInvalid(formRef.value.$el)
    return
  }
  saving.value = true
  try {
    if (mode.value === 'create') {
      await accountApi.create({ username: form.username, displayName: form.displayName, password: form.password, role: form.role })
    } else if (form.id !== undefined) {
      await accountApi.update(form.id, { displayName: form.displayName, role: form.role, enabled: form.enabled, expectedVersion: form.expectedVersion })
    }
    ElMessage.success(mode.value === 'create' ? '账号已创建' : '账号已更新')
    dialogOpen.value = false
    await load()
  } catch (reason) {
    ElMessage.error(errorMessage(reason))
  } finally {
    saving.value = false
  }
}

async function resetPassword(account: Account) {
  try {
    const result = await ElMessageBox.prompt(`为 ${account.username} 设置新密码`, '重置账号密码', {
      inputType: 'password',
      inputPattern: /^(?=.*[A-Za-z])(?=.*\d)\S{8,64}$/,
      inputErrorMessage: '需为 8-64 位，至少包含字母和数字，且不能含空格',
      confirmButtonText: '确认重置',
      cancelButtonText: '取消',
    })
    await accountApi.resetPassword(account.id, { newPassword: result.value, expectedVersion: account.version })
    ElMessage.success('密码已重置，旧会话将在下次请求时失效')
    await load()
  } catch (reason) {
    if (reason !== 'cancel' && reason !== 'close') ElMessage.error(errorMessage(reason))
  }
}

function actionLabel(action: string) {
  return ({
    CREATE: '创建账号',
    UPDATE: '更新账号',
    RESET_PASSWORD: '重置密码',
    CHANGE_PASSWORD: '本人改密',
    PUBLIC_REGISTER: '钓友公开注册',
    BOOTSTRAP_CREATE: '初始化管理员',
    BOOTSTRAP_RESTORE: '恢复管理员',
  } as Record<string, string>)[action] || action
}

function roleLabel(role: Account['role']) {
  if (role === 'ADMIN') return '管理员'
  if (role === 'USER') return '钓友用户'
  return '运营人员'
}

function clearSensitiveFields() {
  form.password = ''
}

onMounted(load)
</script>

<template>
  <div class="account-office">
    <header class="account-head">
      <div>
        <span class="station-kicker">权限值守</span>
        <h1>账号、角色与会话状态</h1>
        <p>角色或启停变更会在下一次请求即时生效；密码变更会使全部旧会话失效。</p>
      </div>
      <div class="account-head__actions">
        <el-button :icon="Refresh" :loading="loading" @click="load">刷新</el-button>
        <el-button type="primary" :icon="Plus" @click="openCreate">创建账号</el-button>
      </div>
    </header>

    <ResourceState :loading="loading" :error="error" @retry="load">
      <section class="account-summary">
        <div><span>账号总数</span><strong>{{ accounts.length }}</strong></div>
        <div><span>启用管理员</span><strong>{{ enabledAdmins }}</strong></div>
        <div><span>审计记录</span><strong>{{ audits.length }}</strong></div>
      </section>

      <section class="account-panel">
        <header><h2>账号名册</h2><span>用户名创建后不可修改</span></header>
        <LakeEmptyState v-if="!accounts.length" title="暂无账号" description="创建首个账号后可在这里管理" compact />
        <el-table v-else :data="accounts" stripe>
          <el-table-column prop="username" label="用户名" min-width="150" />
          <el-table-column prop="displayName" label="显示名称" min-width="150" />
          <el-table-column label="角色" width="110"><template #default="scope">{{ roleLabel(scope.row.role) }}</template></el-table-column>
          <el-table-column label="状态" width="110"><template #default="scope"><StatusTag :status="scope.row.enabled ? 'ACTIVE' : 'INACTIVE'" /></template></el-table-column>
          <el-table-column label="更新时间" min-width="170"><template #default="scope">{{ formatDateTime(scope.row.updatedAt) }}</template></el-table-column>
          <el-table-column label="操作" width="180" align="right">
            <template #default="scope">
              <el-button link type="primary" @click="openEdit(scope.row)">编辑</el-button>
              <el-button v-if="String(scope.row.id) !== String(auth.user?.id)" link type="warning" @click="resetPassword(scope.row)">重置密码</el-button>
            </template>
          </el-table-column>
        </el-table>
      </section>

      <section class="account-panel">
        <header><h2>最近账号审计</h2><span>不记录密码或密码哈希</span></header>
        <LakeEmptyState v-if="!audits.length" title="暂无审计记录" description="账号变更后将在这里留下操作轨迹" compact />
        <el-table v-else :data="audits" stripe>
          <el-table-column prop="actorUsername" label="操作者" min-width="140" />
          <el-table-column prop="targetUsername" label="目标账号" min-width="140" />
          <el-table-column label="动作" min-width="120"><template #default="scope">{{ actionLabel(scope.row.action) }}</template></el-table-column>
          <el-table-column label="时间" min-width="170"><template #default="scope">{{ formatDateTime(scope.row.createdAt) }}</template></el-table-column>
        </el-table>
      </section>
    </ResourceState>

    <el-dialog v-model="dialogOpen" :title="mode === 'create' ? '创建账号' : '编辑账号'" width="520px" destroy-on-close @closed="clearSensitiveFields">
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
        <el-form-item label="用户名" prop="username"><el-input v-model.trim="form.username" :disabled="mode === 'edit'" autocomplete="username" /></el-form-item>
        <el-form-item label="显示名称" prop="displayName"><el-input v-model.trim="form.displayName" /></el-form-item>
        <el-form-item v-if="mode === 'create'" label="初始密码" prop="password"><el-input v-model="form.password" type="password" show-password autocomplete="new-password" /></el-form-item>
        <el-form-item label="角色" prop="role"><el-select v-model="form.role" :disabled="mode === 'edit' && (String(form.id) === String(auth.user?.id) || form.role === 'USER')" style="width:100%"><el-option label="运营人员" value="OPERATOR" /><el-option label="管理员" value="ADMIN" /><el-option label="钓友用户" value="USER" /></el-select></el-form-item>
        <el-form-item v-if="mode === 'edit'" label="账号状态"><el-switch v-model="form.enabled" :disabled="String(form.id) === String(auth.user?.id)" active-text="启用" inactive-text="停用" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="dialogOpen = false">取消</el-button><el-button type="primary" :loading="saving" @click="save">保存</el-button></template>
    </el-dialog>
  </div>
</template>

<style scoped>
.account-office { display: grid; gap: 16px; }
.account-head { display: flex; align-items: flex-end; justify-content: space-between; gap: 18px; }
.account-head h1 { margin: 5px 0 6px; color: var(--lake-950); font-family: var(--font-display); font-size: clamp(24px, 3vw, 38px); }
.account-head p { margin: 0; color: #738079; font-size: 13px; }
.account-head__actions { display: flex; gap: 8px; }
.account-summary { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 10px; margin-bottom: 14px; }
.account-summary div { display: grid; gap: 5px; padding: 15px; border: 1px solid #ded5c8; border-radius: 12px; background: #f8f2e7; }
.account-summary span, .account-panel > header span { color: #7d8881; font-size: 11px; }
.account-summary strong { color: var(--lake-900); font-size: 24px; }
.account-panel { margin-bottom: 14px; padding: 17px 19px; border: 1px solid #ded5c8; border-radius: 14px; background: var(--paper-50); }
.account-panel > header { display: flex; align-items: baseline; justify-content: space-between; gap: 12px; margin-bottom: 13px; }
.account-panel h2 { margin: 0; color: var(--lake-950); font-family: var(--font-display); font-size: 19px; }
@media (max-width: 720px) { .account-head { align-items: stretch; flex-direction: column; } .account-head__actions { justify-content: flex-end; } .account-summary { grid-template-columns: 1fr; } }
</style>
