import { useEffect, useState } from 'react'
import { listAdminUsers, type AdminUser } from '@/api/admin'
import styles from './UserListPanel.module.css'

export default function UserListPanel() {
  const [users, setUsers] = useState<AdminUser[]>([])
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    void (async () => {
      try {
        setUsers(await listAdminUsers())
      } finally {
        setLoading(false)
      }
    })()
  }, [])

  if (loading) return <p className={styles.hint}>加载中...</p>
  if (users.length === 0) return <p className={styles.hint}>暂无用户</p>

  return (
    <>
      <p className={styles.note}>测试账号 admin / student1 默认密码均为 123456</p>
      <table className={styles.table}>
        <thead>
          <tr>
            <th>ID</th>
            <th>用户名</th>
            <th>显示名</th>
            <th>角色</th>
            <th>状态</th>
          </tr>
        </thead>
        <tbody>
          {users.map((u) => (
            <tr key={u.id}>
              <td>{u.id}</td>
              <td>{u.username}</td>
              <td>{u.displayName}</td>
              <td>{u.role}</td>
              <td>{u.enabled ? '启用' : '禁用'}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </>
  )
}
