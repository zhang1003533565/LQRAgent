import axios from 'axios'

function stringifyResponseData(data: unknown): string {
  if (data == null) return ''
  if (typeof data === 'string') return data
  if (typeof data === 'object' && 'message' in data) {
    const msg = (data as { message?: unknown }).message
    if (typeof msg === 'string') return msg
  }
  try {
    return JSON.stringify(data)
  } catch {
    return ''
  }
}

function isBackendUnavailable(error: axios.AxiosError): boolean {
  const code = error.code ?? ''
  const message = `${error.message ?? ''} ${stringifyResponseData(error.response?.data)}`.toLowerCase()
  const hints = [
    'econnrefused',
    'connect econnrefused',
    'socket hang up',
    'network error',
    'err_network',
    'failed to fetch',
    'proxy error',
    'error occurred while trying to proxy',
    'connect refused',
  ]
  if (hints.some((hint) => message.includes(hint) || code.toLowerCase().includes(hint))) {
    return true
  }
  const status = error.response?.status
  return status === 502 || status === 503 || status === 504
}

/** 将 axios / 网络错误转为用户可读文案 */
export function parseApiError(error: unknown, fallback = '请求失败'): string {
  if (axios.isAxiosError(error)) {
    if (!error.response || isBackendUnavailable(error)) {
      return '无法连接后端服务，请确认 MySQL 与 Spring Boot 后端（端口 8080）已启动'
    }

    const status = error.response.status
    const body = error.response.data as { message?: string; code?: number } | undefined
    const serverMsg = body?.message?.trim() || stringifyResponseData(error.response.data).trim()

    if (status === 502 || status === 503 || status === 504) {
      return '后端服务不可用，请启动 Spring Boot 后重试'
    }

    if (status === 500) {
      if (isBackendUnavailable(error)) {
        return '无法连接后端服务，请确认 MySQL 与 Spring Boot 后端（端口 8080）已启动'
      }
      return serverMsg ? `服务异常：${serverMsg}` : '服务内部错误，请检查后端日志后重试'
    }

    if (status === 403) {
      return '无访问权限，请重新登录后再试'
    }

    if (status === 401) {
      return '登录已过期，请重新登录'
    }

    if (serverMsg) return serverMsg
    return error.message || fallback
  }

  if (error instanceof Error) return error.message || fallback
  return fallback
}
