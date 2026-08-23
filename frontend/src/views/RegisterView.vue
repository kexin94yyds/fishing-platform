<script setup lang="ts">
import { nextTick, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import type { FormInstance, FormRules } from 'element-plus'
import { Lock, User, UserFilled } from '@element-plus/icons-vue'
import { useAuthStore } from '@/stores/auth'
import { errorMessage } from '@/api/http'

const router = useRouter()
const auth = useAuthStore()
const formRef = ref<FormInstance>()
const error = ref('')

const form = reactive({
  username: '',
  displayName: '',
  password: '',
  confirmPassword: '',
  accepted: false,
})

const validateConfirm = (_rule: unknown, value: string, callback: (error?: Error) => void) => {
  if (!value) callback(new Error('请再次输入密码'))
  else if (value !== form.password) callback(new Error('两次输入的密码不一致'))
  else callback()
}

const validateAccepted = (_rule: unknown, value: boolean, callback: (error?: Error) => void) => {
  if (!value) callback(new Error('请先同意钓友账号使用规范'))
  else callback()
}

const rules: FormRules = {
  username: [
    { required: true, message: '请输入登录账号', trigger: 'blur' },
    {
      pattern: /^[a-z][a-z0-9_]{3,31}$/,
      message: '账号需为 4 至 32 位，以小写字母开头，仅含小写字母、数字或下划线',
      trigger: 'blur',
    },
  ],
  displayName: [
    { required: true, message: '请输入显示名称', trigger: 'blur' },
    { min: 2, max: 100, message: '显示名称需为 2 至 100 位', trigger: 'blur' },
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    {
      pattern: /^(?=.*[A-Za-z])(?=.*\d)[^\s]{8,64}$/,
      message: '密码需为 8 至 64 位，至少包含一个英文字母和一个数字，且不能含空格',
      trigger: 'blur',
    },
  ],
  confirmPassword: [{ validator: validateConfirm, trigger: ['blur', 'change'] }],
  accepted: [{ validator: validateAccepted, trigger: 'change' }],
}

async function submit() {
  error.value = ''
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) {
    await nextTick()
    document
      .querySelector<HTMLInputElement>('.register-page .el-form-item.is-error input')
      ?.focus()
    return
  }
  try {
    await auth.register({
      username: form.username,
      displayName: form.displayName,
      password: form.password,
    })
    await router.replace({ name: 'my-fishing' })
  } catch (reason) {
    error.value = errorMessage(reason)
  }
}
</script>

<template>
  <main class="register-page">
    <div class="register-shoreline" aria-hidden="true">
      <span class="register-shoreline__ring register-shoreline__ring--outer" />
      <span class="register-shoreline__ring register-shoreline__ring--inner" />
      <span class="register-shoreline__legend">NEW ANGLER · REGISTRATION LEDGER</span>
    </div>

    <header class="register-brand">
      <div class="register-brand__mark">湖</div>
      <div>
        <strong translate="no">湖畔运营所</strong>
        <span>淡水垂钓基地 · 钓友账号登记</span>
      </div>
    </header>

    <section class="register-ledger" aria-labelledby="register-title">
      <div class="register-ledger__binding" aria-hidden="true" />
      <div class="register-ledger__folio">
        <span>新成员登记簿</span>
        <span translate="no">ANGLER ACCOUNT</span>
      </div>

      <div class="register-form-wrap">
        <header class="register-form-head">
          <span>新钓友登记</span>
          <h1 id="register-title">创建你的湖畔钓友账号</h1>
          <p>完成后将直接进入“我的垂钓”，账号权限固定为钓友用户</p>
        </header>

        <el-alert
          v-if="error"
          :title="error"
          type="error"
          :closable="false"
          show-icon
          class="register-error"
        />

        <el-form
          ref="formRef"
          :model="form"
          :rules="rules"
          label-position="top"
          aria-live="polite"
          @submit.prevent="submit"
        >
          <div class="register-form-grid">
            <el-form-item label="登录账号" prop="username">
              <el-input
                v-model.trim="form.username"
                :prefix-icon="User"
                :spellcheck="false"
                name="username"
                autocomplete="username"
                autocapitalize="none"
                placeholder="例如 angler_01…"
              />
            </el-form-item>
            <el-form-item label="显示名称" prop="displayName">
              <el-input
                v-model.trim="form.displayName"
                :prefix-icon="UserFilled"
                name="displayName"
                autocomplete="name"
                maxlength="100"
                placeholder="例如 小王…"
              />
            </el-form-item>
            <el-form-item label="登录密码" prop="password">
              <el-input
                v-model="form.password"
                :prefix-icon="Lock"
                type="password"
                name="password"
                autocomplete="new-password"
                placeholder="至少 8 位，包含字母和数字…"
                show-password
              />
            </el-form-item>
            <el-form-item label="确认密码" prop="confirmPassword">
              <el-input
                v-model="form.confirmPassword"
                :prefix-icon="Lock"
                type="password"
                name="confirmPassword"
                autocomplete="new-password"
                enterkeyhint="done"
                placeholder="再次输入登录密码…"
                show-password
              />
            </el-form-item>
          </div>
          <el-form-item prop="accepted" class="register-agreement">
            <el-checkbox v-model="form.accepted">
              我已阅读并同意钓友账号使用规范
            </el-checkbox>
          </el-form-item>
          <div class="register-actions">
            <RouterLink class="register-back-link" to="/login">返回登录</RouterLink>
            <el-button
              type="primary"
              native-type="submit"
              :loading="auth.authenticating"
              :disabled="auth.authenticating"
            >
              创建并进入
            </el-button>
          </div>
        </el-form>
      </div>

      <aside class="register-notes" aria-label="账号使用说明">
        <div class="register-notes__intro">
          <span>预约须知</span>
          <strong>一人一号，只看本人预约</strong>
        </div>
        <dl>
          <div>
            <dt>账号用途</dt>
            <dd>查看钓位余量，提交和管理本人预约</dd>
          </div>
          <div>
            <dt>信息使用</dt>
            <dd>使用常用名称，预约时填写可联系手机号</dd>
          </div>
          <div>
            <dt>会话安全</dt>
            <dd>不要与他人共享账号，离开设备前主动退出</dd>
          </div>
        </dl>
      </aside>
    </section>
  </main>
</template>

<style scoped>
.register-page {
  position: relative;
  display: grid;
  min-height: 100dvh;
  grid-template-rows: auto 1fr;
  justify-items: center;
  overflow: hidden auto;
  padding: 26px clamp(20px, 4vw, 60px) 20px;
  background: var(--paper-200);
}

.register-shoreline {
  position: fixed;
  inset: 0;
  overflow: hidden;
  pointer-events: none;
}

.register-shoreline__ring {
  position: absolute;
  top: 51%;
  left: 50%;
  display: block;
  border: 1px solid rgb(23 70 58 / 14%);
  border-radius: 50%;
  transform: translate(-50%, -50%) rotate(6deg);
}

.register-shoreline__ring--outer {
  width: min(1240px, 92vw);
  height: min(650px, 57vw);
}

.register-shoreline__ring--inner {
  width: min(1050px, 78vw);
  height: min(500px, 45vw);
  border-color: rgb(158 79 55 / 15%);
  transform: translate(-50%, -50%) rotate(-4deg);
}

.register-shoreline__legend {
  position: absolute;
  top: 50%;
  right: 24px;
  color: rgb(82 97 90 / 44%);
  font-size: 11px;
  letter-spacing: 0.2em;
  writing-mode: vertical-rl;
  transform: translateY(-50%);
}

.register-brand {
  position: relative;
  z-index: 1;
  display: flex;
  align-items: center;
  gap: 11px;
}

.register-brand__mark {
  display: grid;
  width: 38px;
  height: 38px;
  place-items: center;
  border-radius: 50%;
  color: var(--paper-100);
  background: var(--lake-900);
  box-shadow: 0 0 0 5px rgb(23 70 58 / 8%);
}

.register-brand strong,
.register-brand span {
  display: block;
}

.register-brand strong {
  color: var(--lake-900);
  font-size: 15px;
}

.register-brand span {
  margin-top: 2px;
  color: var(--ink-500);
  font-size: 10px;
}

.register-ledger {
  position: relative;
  z-index: 1;
  width: min(100%, 760px);
  align-self: center;
  margin: 34px 0 24px;
  padding: 28px 42px 0;
  border: 1px solid var(--paper-400);
  border-radius: 24px;
  background: var(--paper-50);
  box-shadow: 0 28px 70px rgb(18 53 45 / 8%);
}

.register-ledger__binding {
  position: absolute;
  top: -1px;
  left: 50%;
  width: 112px;
  height: 6px;
  border-radius: 0 0 8px 8px;
  background: var(--clay-700);
  transform: translateX(-50%);
}

.register-ledger__folio {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 28px;
  padding-bottom: 12px;
  border-bottom: 1px solid var(--paper-300);
  color: var(--ink-500);
  font-size: 11px;
  letter-spacing: 0.14em;
}

.register-ledger__folio span:first-child {
  color: var(--lake-900);
  font-size: 11px;
  font-weight: 750;
  letter-spacing: 0.12em;
}

.register-form-wrap {
  width: 100%;
}

.register-form-head {
  margin-bottom: 24px;
}

.register-form-head > span {
  color: var(--clay-700);
  font-size: 11px;
  font-weight: 750;
  letter-spacing: 0.1em;
}

.register-form-wrap h1 {
  max-width: 15em;
  margin: 8px 0;
  color: var(--lake-900);
  font-family: "Songti SC", "STSong", "Noto Serif CJK SC", serif;
  font-size: clamp(29px, 3vw, 38px);
  letter-spacing: -0.045em;
  line-height: 1.28;
  text-wrap: balance;
}

.register-form-head p {
  margin: 0;
  color: var(--ink-500);
  font-size: 13px;
}

.register-error {
  margin-bottom: 18px;
}

.register-form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0 18px;
}

:deep(.el-form-item__label) {
  padding-bottom: 7px;
  color: #405149;
  font-size: 12px;
  font-weight: 650;
}

:deep(.el-input__wrapper) {
  min-height: 44px;
  border-radius: 10px;
  background: var(--paper-100);
  box-shadow: 0 0 0 1px #ddd5c8 inset;
}

:deep(.el-input__wrapper:hover) {
  box-shadow: 0 0 0 1px #9fb2aa inset;
}

:deep(.el-input__wrapper.is-focus) {
  box-shadow: 0 0 0 1px var(--lake-800) inset;
}

.register-agreement {
  margin: 4px 0 18px;
}

.register-actions {
  display: flex;
  justify-content: flex-end;
  gap: 9px;
  padding-top: 16px;
  border-top: 1px solid #ddd5c8;
}

.register-actions .el-button {
  min-height: 42px;
  border-radius: 10px;
}

.register-back-link {
  display: inline-flex;
  min-height: 42px;
  align-items: center;
  justify-content: center;
  padding: 0 19px;
  border: 1px solid #ddd5c8;
  border-radius: 10px;
  color: var(--ink-700);
  background: var(--paper-50);
  text-decoration: none;
  touch-action: manipulation;
}

.register-back-link:hover {
  border-color: #83a097;
  color: var(--lake-800);
  background: var(--lake-50);
}

.register-back-link:focus-visible {
  outline: 2px solid var(--lake-800);
  outline-offset: 3px;
}

.register-page :deep(button) {
  touch-action: manipulation;
}

.register-notes {
  display: grid;
  grid-template-columns: 180px minmax(0, 1fr);
  gap: 24px;
  margin: 28px -42px 0;
  padding: 21px 42px 24px;
  border-radius: 0 0 23px 23px;
  color: #eef2ec;
  background: var(--lake-900);
}

.register-notes__intro span,
.register-notes__intro strong {
  display: block;
}

.register-notes__intro span {
  color: #d58b6e;
  font-size: 11px;
  font-weight: 750;
  letter-spacing: 0.1em;
}

.register-notes__intro strong {
  margin-top: 7px;
  font-size: 14px;
}

.register-notes dl {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  margin: 0;
}

.register-notes dl > div {
  padding: 0 17px;
  border-left: 1px solid rgb(255 255 255 / 12%);
}

.register-notes dt {
  color: #eef2ec;
  font-size: 10px;
  font-weight: 700;
}

.register-notes dd {
  margin: 5px 0 0;
  color: #9fb8ad;
  font-size: 10px;
  line-height: 1.55;
}

@media (max-width: 720px) {
  .register-page {
    padding:
      max(21px, env(safe-area-inset-top))
      max(14px, env(safe-area-inset-right))
      max(18px, env(safe-area-inset-bottom))
      max(14px, env(safe-area-inset-left));
  }

  .register-brand {
    justify-self: start;
    margin-left: 7px;
  }

  .register-shoreline__ring--outer {
    width: 900px;
    height: 620px;
  }

  .register-shoreline__ring--inner {
    width: 720px;
    height: 500px;
  }

  .register-shoreline__legend {
    display: none;
  }

  .register-ledger {
    margin: 30px 0 22px;
    padding: 26px 20px 0;
    border-radius: 18px;
  }

  .register-ledger__folio {
    margin-bottom: 25px;
  }

  .register-form-wrap h1 {
    font-size: 28px;
  }

  .register-form-grid {
    grid-template-columns: 1fr;
  }

  .register-actions {
    justify-content: stretch;
  }

  .register-actions > * {
    flex: 1;
  }

  .register-notes {
    grid-template-columns: 1fr;
    gap: 18px;
    margin: 26px -20px 0;
    padding: 20px;
    border-radius: 0 0 17px 17px;
  }

  .register-notes dl {
    grid-template-columns: 1fr;
  }

  .register-notes dl > div {
    padding: 12px 0;
    border-top: 1px solid rgb(255 255 255 / 12%);
    border-left: 0;
  }
}

@media (max-height: 790px) and (min-width: 721px) {
  .register-page {
    padding-block: 14px 10px;
  }

  .register-ledger {
    margin-block: 18px 12px;
    padding-top: 23px;
  }

  .register-ledger__folio {
    margin-bottom: 20px;
  }

  .register-form-head {
    margin-bottom: 18px;
  }

  .register-notes {
    margin-top: 20px;
    padding-block: 17px 19px;
  }
}
</style>
