<script setup lang="ts">
import { nextTick, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Lock, User } from '@element-plus/icons-vue'
import type { FormInstance, FormRules } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import { errorMessage } from '@/api/http'

const router = useRouter()
const route = useRoute()
const auth = useAuthStore()
const formRef = ref<FormInstance>()
const error = ref('')

const form = reactive({
  username: '',
  password: '',
})

const rules: FormRules = {
  username: [{ required: true, message: '请输入账号', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
}

async function submit() {
  error.value = ''
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) {
    await nextTick()
    document
      .querySelector<HTMLInputElement>('.login-page .el-form-item.is-error input')
      ?.focus()
    return
  }
  try {
    await auth.login(form)
    const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : '/'
    await router.replace(auth.isUser ? { name: 'my-fishing' } : redirect)
  } catch (reason) {
    error.value = errorMessage(reason)
  }
}

onMounted(auth.hydrateRegistration)
</script>

<template>
  <main class="login-page">
    <div class="login-shoreline" aria-hidden="true">
      <span class="login-shoreline__ring login-shoreline__ring--outer" />
      <span class="login-shoreline__ring login-shoreline__ring--inner" />
      <span class="login-shoreline__legend">FRESHWATER BASE · DUTY DESK</span>
    </div>

    <header class="login-brand">
      <div class="login-brand__mark">湖</div>
      <div>
        <strong translate="no">湖畔运营所</strong>
        <span>淡水垂钓基地 · 湖畔入口</span>
      </div>
    </header>

    <section class="login-desk" aria-labelledby="login-title">
      <div class="login-desk__binding" aria-hidden="true" />
      <div class="login-desk__folio">
        <span>值守簿</span>
        <span translate="no">OPERATION DESK</span>
      </div>

      <div class="login-form-wrap">
        <div class="login-form-head">
          <span>湖畔登录</span>
          <h1 id="login-title">回到湖边，开始今天的安排</h1>
          <p>钓友进入预约页，工作人员进入现场工作台</p>
        </div>
        <el-alert
          v-if="error"
          :title="error"
          type="error"
          :closable="false"
          show-icon
          class="login-error"
        />
        <el-form
          ref="formRef"
          :model="form"
          :rules="rules"
          label-position="top"
          aria-live="polite"
          @submit.prevent="submit"
        >
          <el-form-item label="账号" prop="username">
            <el-input
              v-model.trim="form.username"
              :prefix-icon="User"
              :spellcheck="false"
              name="username"
              autocomplete="username"
              autocapitalize="none"
              placeholder="请输入登录账号…"
              size="large"
            />
          </el-form-item>
          <el-form-item label="密码" prop="password">
            <el-input
              v-model="form.password"
              :prefix-icon="Lock"
              type="password"
              name="password"
              autocomplete="current-password"
              enterkeyhint="go"
              placeholder="请输入登录密码…"
              show-password
              size="large"
            />
          </el-form-item>
          <el-button
            type="primary"
            size="large"
            native-type="submit"
            :loading="auth.authenticating"
            class="login-submit"
          >
            进入工作台
          </el-button>
        </el-form>
        <p class="login-help">如账号无法使用，请联系系统管理员处理</p>
        <p v-if="auth.registrationEnabled" class="login-register">
          还没有运营账号？
          <RouterLink to="/register">创建账号</RouterLink>
        </p>
      </div>

      <div class="login-desk__scope" aria-label="工作台范围">
        <span>钓友预约</span>
        <span>现场运营</span>
        <span>经营分析</span>
      </div>
    </section>

    <footer class="login-foot">
      <span>湖区数据由基地服务实时提供</span>
      <span>钓友与基地工作人员统一入口</span>
    </footer>
  </main>
</template>

<style scoped>
.login-page {
  position: relative;
  display: grid;
  min-height: 100dvh;
  grid-template-rows: auto 1fr auto;
  justify-items: center;
  overflow: hidden;
  padding: 28px clamp(20px, 4vw, 60px) 22px;
  background: var(--paper-200);
}

.login-shoreline {
  position: absolute;
  inset: 0;
  overflow: hidden;
  pointer-events: none;
}

.login-shoreline__ring {
  position: absolute;
  top: 50%;
  left: 50%;
  display: block;
  border: 1px solid rgb(23 70 58 / 16%);
  border-radius: 50%;
  transform: translate(-50%, -50%) rotate(-5deg);
}

.login-shoreline__ring--outer {
  width: min(1040px, 84vw);
  height: min(520px, 52vw);
}

.login-shoreline__ring--inner {
  width: min(860px, 70vw);
  height: min(390px, 39vw);
  border-color: rgb(158 79 55 / 17%);
  transform: translate(-50%, -50%) rotate(7deg);
}

.login-shoreline__legend {
  position: absolute;
  top: 50%;
  left: 24px;
  color: rgb(82 97 90 / 46%);
  font-size: 11px;
  letter-spacing: 0.22em;
  writing-mode: vertical-rl;
  transform: translateY(-50%);
}

.login-brand {
  position: relative;
  z-index: 1;
  display: flex;
  align-items: center;
  gap: 11px;
  color: var(--lake-900);
}

.login-brand strong,
.login-brand span {
  display: block;
}

.login-brand strong {
  font-size: 15px;
  letter-spacing: 0.02em;
}

.login-brand span {
  margin-top: 3px;
  color: var(--ink-500);
  font-size: 10px;
}

.login-brand__mark {
  display: grid;
  width: 38px;
  height: 38px;
  place-items: center;
  border-radius: 50%;
  color: var(--paper-100);
  background: var(--lake-900);
  box-shadow: 0 0 0 5px rgb(23 70 58 / 8%);
}

.login-desk {
  position: relative;
  z-index: 1;
  width: min(100%, 520px);
  align-self: center;
  margin: 40px 0 30px;
  padding: 30px 44px 26px;
  border: 1px solid var(--paper-400);
  border-radius: 24px;
  background: var(--paper-50);
  box-shadow: 0 28px 70px rgb(18 53 45 / 8%);
}

.login-desk__binding {
  position: absolute;
  top: -1px;
  left: 50%;
  width: 92px;
  height: 6px;
  border-radius: 0 0 8px 8px;
  background: var(--clay-700);
  transform: translateX(-50%);
}

.login-desk__folio {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 34px;
  padding-bottom: 13px;
  border-bottom: 1px solid var(--paper-300);
  color: var(--ink-500);
  font-size: 11px;
  letter-spacing: 0.14em;
}

.login-desk__folio span:first-child {
  color: var(--lake-900);
  font-size: 11px;
  font-weight: 750;
  letter-spacing: 0.12em;
}

.login-form-wrap {
  width: 100%;
}

.login-form-head {
  margin-bottom: 27px;
}

.login-form-head span {
  color: var(--clay-700);
  font-size: 11px;
  font-weight: 750;
  letter-spacing: 0.1em;
}

.login-form-head h1 {
  max-width: 12em;
  margin: 9px 0;
  color: #18322b;
  font-family: "Songti SC", "STSong", "Noto Serif CJK SC", serif;
  font-size: clamp(29px, 3.2vw, 37px);
  font-weight: 700;
  letter-spacing: -0.045em;
  line-height: 1.28;
  text-wrap: balance;
}

.login-form-head p,
.login-help {
  margin: 0;
  color: #71817c;
  font-size: 13px;
}

.login-error {
  margin-bottom: 18px;
}

:deep(.el-form-item__label) {
  padding-bottom: 7px;
  color: #405149;
  font-size: 12px;
  font-weight: 650;
}

:deep(.el-input__wrapper) {
  min-height: 46px;
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

.login-submit {
  width: 100%;
  min-height: 46px;
  margin-top: 7px;
  border-radius: 10px;
  font-weight: 700;
}

.login-help {
  margin-top: 18px;
  text-align: center;
}

.login-register {
  margin: 10px 0 0;
  color: var(--ink-500);
  font-size: 12px;
  text-align: center;
}

.login-register a {
  color: var(--lake-900);
  font-weight: 700;
  text-decoration: none;
}

.login-register a:hover {
  color: var(--clay-700);
}

.login-register a:focus-visible {
  border-radius: 3px;
  outline: 2px solid var(--lake-800);
  outline-offset: 3px;
}

.login-page :deep(button),
.login-page a {
  touch-action: manipulation;
}

.login-desk__scope {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  margin-top: 26px;
  padding-top: 18px;
  border-top: 1px solid var(--paper-300);
}

.login-desk__scope span {
  color: var(--ink-500);
  font-size: 10px;
  text-align: center;
}

.login-desk__scope span + span {
  border-left: 1px solid var(--paper-300);
}

.login-foot {
  position: relative;
  z-index: 1;
  display: flex;
  width: min(100%, 620px);
  justify-content: space-between;
  color: var(--ink-500);
  font-size: 10px;
}

@media (max-width: 640px) {
  .login-page {
    padding:
      max(22px, env(safe-area-inset-top))
      max(14px, env(safe-area-inset-right))
      max(18px, env(safe-area-inset-bottom))
      max(14px, env(safe-area-inset-left));
  }

  .login-brand {
    justify-self: start;
    margin-left: 7px;
  }

  .login-shoreline__ring--outer {
    width: 780px;
    height: 390px;
  }

  .login-shoreline__ring--inner {
    width: 620px;
    height: 300px;
  }

  .login-shoreline__legend {
    display: none;
  }

  .login-desk {
    margin: 32px 0 24px;
    padding: 27px 20px 22px;
    border-radius: 18px;
  }

  .login-desk__folio {
    margin-bottom: 28px;
  }

  .login-form-head h1 {
    font-size: 29px;
  }

  .login-foot {
    display: block;
    text-align: center;
  }

  .login-foot span {
    display: block;
  }

  .login-foot span + span {
    margin-top: 4px;
  }
}

@media (max-height: 720px) and (min-width: 641px) {
  .login-page {
    padding-block: 20px 16px;
  }

  .login-desk {
    margin-block: 24px 18px;
    padding-block: 24px 20px;
  }

  .login-desk__folio {
    margin-bottom: 22px;
  }

  .login-form-head {
    margin-bottom: 20px;
  }
}
</style>
