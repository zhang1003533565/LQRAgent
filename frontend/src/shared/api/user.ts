import http from './http'

export interface UserProfileDto {
  id: number
  username: string
  displayName: string
  role: string
}

export async function getMe(): Promise<UserProfileDto> {
  const res = await http.get<{ data: UserProfileDto }>('/users/me')
  return res.data.data
}
