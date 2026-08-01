import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { authApi } from '@/api'
import type { CurrentUser } from '@/types'

export const useAuthStore = defineStore('auth', () => {
  const user = ref<CurrentUser | null>(null)
  const initialized = ref(false)
  const authenticating = ref(false)
  const registrationEnabled = ref(false)
  const registrationInitialized = ref(false)
  const isAuthenticated = computed(() => Boolean(user.value))
  const isAdmin = computed(() => String(user.value?.role || '').toUpperCase() === 'ADMIN')

  async function hydrate() {
    if (initialized.value) return
    try {
      user.value = await authApi.me()
    } catch {
      user.value = null
    } finally {
      initialized.value = true
    }
  }

  async function login(credentials: { username: string; password: string }) {
    authenticating.value = true
    try {
      await authApi.csrf()
      user.value = await authApi.login(credentials)
      initialized.value = true
    } finally {
      authenticating.value = false
    }
  }

  async function hydrateRegistration() {
    if (registrationInitialized.value) return
    try {
      registrationEnabled.value = (await authApi.registration()).enabled
    } catch {
      registrationEnabled.value = false
    } finally {
      registrationInitialized.value = true
    }
  }

  async function register(payload: { username: string; displayName: string; password: string }) {
    authenticating.value = true
    try {
      await authApi.csrf()
      user.value = await authApi.register(payload)
      initialized.value = true
    } finally {
      authenticating.value = false
    }
  }

  async function logout() {
    try {
      await authApi.logout()
    } finally {
      clearSession()
    }
  }

  function clearSession() {
    user.value = null
    initialized.value = true
  }

  return {
    user,
    initialized,
    authenticating,
    isAuthenticated,
    isAdmin,
    registrationEnabled,
    registrationInitialized,
    hydrate,
    hydrateRegistration,
    login,
    register,
    logout,
    clearSession,
  }
})
