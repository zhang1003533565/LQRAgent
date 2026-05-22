import http from './http'
import type { AuthUser } from '@/store/authStore'

export interface LoginRequest {
  username: string
  password: string
}

export interface LoginResponse {
  token: string
  userId: number
  username: string
  role: 'student' | 'teacher' | 'admin'
  redirectPath: string
}

export async function login(data: LoginRequest): Promise<AuthUser> {
  const res = await http.post<{ data: LoginResponse }>('/auth/login', data)
  const d = res.data.data
  return {
    userId: d.userId,
    username: d.username,
    role: d.role,
    token: d.token,
    redirectPath: d.redirectPath,
  }
}

export async function logout(): Promise<void> {
  await http.post('/auth/logout')
}
