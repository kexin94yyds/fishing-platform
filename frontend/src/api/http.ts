import axios, { AxiosError } from 'axios'
import type { ApiResponse } from '@/types'

export class ApiError extends Error {
  status?: number

  constructor(message: string, status?: number) {
    super(message)
    this.name = 'ApiError'
    this.status = status
  }
}

export const http = axios.create({
  baseURL: '/api',
  timeout: 15000,
  withCredentials: true,
  xsrfCookieName: 'XSRF-TOKEN',
  xsrfHeaderName: 'X-XSRF-TOKEN',
  headers: {
    Accept: 'application/json',
  },
})

http.interceptors.response.use(
  (response) => response,
  (error: AxiosError<ApiResponse<unknown>>) => {
    const requestUrl = String(error.config?.url || '')
    if (
      error.response?.status === 401 &&
      !requestUrl.endsWith('/auth/login') &&
      !requestUrl.endsWith('/auth/register') &&
      !requestUrl.endsWith('/auth/me')
    ) {
      window.dispatchEvent(new Event('fishing-auth-expired'))
    }
    const message =
      error.response?.data?.message ||
      (error.code === 'ECONNABORTED' ? '请求超时，请稍后重试' : '网络连接失败，请检查服务状态')
    return Promise.reject(new ApiError(message, error.response?.status))
  },
)

export async function request<T>(config: Parameters<typeof http.request>[0]): Promise<T> {
  const response = await http.request<ApiResponse<T>>(config)
  const envelope = response.data

  if (!envelope || typeof envelope.success !== 'boolean') {
    throw new ApiError('服务返回格式不正确', response.status)
  }
  if (!envelope.success) {
    throw new ApiError(envelope.message || '请求未成功', response.status)
  }
  return envelope.data
}

export function errorMessage(error: unknown): string {
  return error instanceof Error ? error.message : '操作失败，请稍后重试'
}
