import { APIRequestContext, Page } from '@playwright/test'

export type E2EAuth = {
  token: string
  userId: number
  username: string
  role: string
  redirectPath: string
}

const API_BASE = process.env.E2E_API_URL || 'http://localhost:8080/api'

export function hasE2ECredentials(): boolean {
  return Boolean(process.env.E2E_USERNAME && process.env.E2E_PASSWORD)
}

export async function loginViaApi(request: APIRequestContext): Promise<E2EAuth | null> {
  const username = process.env.E2E_USERNAME
  const password = process.env.E2E_PASSWORD
  if (!username || !password) return null

  const res = await request.post(`${API_BASE}/auth/login`, {
    data: { username, password },
  })
  if (!res.ok()) {
    throw new Error(`Login failed: ${res.status()} ${await res.text()}`)
  }
  const body = (await res.json()) as { data: E2EAuth }
  return body.data
}

export async function seedAuthStorage(page: Page, auth: E2EAuth) {
  const redirectPath =
    auth.role === 'admin' && (auth.redirectPath === '/admin' || auth.redirectPath === '/admin/')
      ? '/admin/console'
      : auth.redirectPath

  await page.addInitScript(
    (payload) => {
      localStorage.setItem(
        'lqragent-auth',
        JSON.stringify({
          state: {
            user: {
              userId: payload.userId,
              username: payload.username,
              role: payload.role,
              token: payload.token,
              redirectPath: payload.redirectPath,
            },
          },
          version: 0,
        }),
      )
    },
    { ...auth, redirectPath },
  )
}

export async function apiGet<T>(
  request: APIRequestContext,
  path: string,
  token: string,
): Promise<T> {
  const res = await request.get(`${API_BASE}${path}`, {
    headers: { Authorization: `Bearer ${token}` },
  })
  if (!res.ok()) {
    throw new Error(`GET ${path} failed: ${res.status()}`)
  }
  const body = (await res.json()) as { data: T }
  return body.data
}
