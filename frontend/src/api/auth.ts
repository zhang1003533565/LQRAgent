import http from './http'
import type { AuthUser } from '@/store/authStore'

export interface LoginRequest {
  username: string
  password: string
}

export interface RegisterRequest {
  username: string
  password: string
  displayName?: string
}

export interface LoginResponse {
  token: string
  userId: number
  username: string
  role: 'student' | 'teacher' | 'admin'
  redirectPath: string
}

function mapResponse(d: LoginResponse): AuthUser {
  return {
    userId: d.userId,
    username: d.username,
    role: d.role,
    token: d.token,
    redirectPath: d.redirectPath,
  }
}

export async function login(data: LoginRequest): Promise<AuthUser> {
  const res = await http.post<{ data: LoginResponse }>('/auth/login', data)
  return mapResponse(res.data.data)
}

export async function register(data: RegisterRequest): Promise<AuthUser> {
  const res = await http.post<{ data: LoginResponse }>('/auth/register', data)
  return mapResponse(res.data.data)
}

export async function logout(): Promise<void> {
  await http.post('/auth/logout')
}
