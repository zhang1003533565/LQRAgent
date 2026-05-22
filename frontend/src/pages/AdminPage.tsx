import { useEffect, useState } from 'react'
import {
  getAdminStatus,
  listAdminUploadTasks,
  processOneUpload,
  type AdminStatus,
} from '@/api/admin'
import type { UploadTask } from '@/api/upload'
import {
  ModelConfigPanel,
  SystemConfigPanel,
  UserListPanel,
} from '@/features/admin'
import { useAuthStore } from '@/store/authStore'
import styles from './AdminPage.module.css'

const STATUS_LABEL: Record<string, string> = {
  PENDING: '排队中',
  PROCESSING: '处理中',
  COMPLETED: '已完成',
  FAILED: '失败',
}

const STATUS_COLOR: Record<string, string> = {
  PENDING: '#f6ad55',
  PROCESSING: '#667eea',
  COMPLETED: '#48bb78',
  FAILED: '#fc8181',
}

type Tab = 'overview' | 'config' | 'upload' | 'users'

export default function AdminPage() {
  const logout = useAuthStore((s) => s.logout)
  const user = useAuthStore((s) => s.user)
  const [tab, setTab] = useState<Tab>('overview')
  const [status, setStatus] = useState<AdminStatus | null>(null)
  const [tasks, setTasks] = useState<UploadTask[]>([])
  const [loading, setLoading] = useState(true)
  const [processMsg, setProcessMsg] = useState('')

  async function fetchOverview() {
    const [st, list] = await Promise.all([getAdminStatus(), listAdminUploadTasks(50)])
    setStatus(st)
    setTasks(list)
  }

  useEffect(() => {
    void fetchOverview().finally(() => setLoading(false))
    const timer = setInterval(() => {
      if (tab === 'overview' || tab === 'upload') {
        void fetchOverview()
      }
    }, 8000)
    return () => clearInterval(timer)
  }, [tab])

  const counts = tasks.reduce(
    (acc, t) => {
      acc[t.status] = (acc[t.status] ?? 0) + 1
      return acc
    },
    {} as Record<string, number>,
  )

  async function handleProcessOne() {
    setProcessMsg('')
    try {
      const ok = await processOneUpload()
      setProcessMsg(ok ? '已触发处理一条待处理任务' : '当前无待处理任务')
      await fetchOverview()
    } catch {
      setProcessMsg('操作失败')
    }
  }

  return (
    <div className={styles.layout}>
      <header className={styles.header}>
        <h1 className={styles.logo}>LQRAgent 管理后台</h1>
        <div className={styles.headerRight}>
          <span className={styles.username}>{user?.username}</span>
          <button type="button" className={styles.logoutBtn} onClick={logout}>
            退出
          </button>
        </div>
      </header>

      <nav className={styles.tabs}>
        <button
          type="button"
          className={tab === 'overview' ? styles.tabActive : styles.tab}
          onClick={() => setTab('overview')}
        >
          概览
        </button>
        <button
          type="button"
          className={tab === 'config' ? styles.tabActive : styles.tab}
          onClick={() => setTab('config')}
        >
          系统配置
        </button>
        <button
          type="button"
          className={tab === 'upload' ? styles.tabActive : styles.tab}
          onClick={() => setTab('upload')}
        >
          上传队列
        </button>
        <button
          type="button"
          className={tab === 'users' ? styles.tabActive : styles.tab}
          onClick={() => setTab('users')}
        >
          用户管理
        </button>
      </nav>

      <div className={styles.content}>
        {tab === 'overview' && (
          <>
            <section className={styles.statsRow}>
              {(['PENDING', 'PROCESSING', 'COMPLETED', 'FAILED'] as const).map((s) => (
                <div key={s} className={styles.statCard}>
                  <p className={styles.statNum} style={{ color: STATUS_COLOR[s] }}>
                    {counts[s] ?? 0}
                  </p>
                  <p className={styles.statLabel}>{STATUS_LABEL[s]}</p>
                </div>
              ))}
            </section>

            <section className={styles.section}>
              <h2 className={styles.sectionTitle}>服务状态</h2>
              {loading || !status ? (
                <p className={styles.hint}>加载中...</p>
              ) : (
                <dl className={styles.statusGrid}>
                  <div>
                    <dt>后端端口</dt>
                    <dd>{status.serverPort}</dd>
                  </div>
                  <div>
                    <dt>AI HTTP</dt>
                    <dd>{status.aiServerBaseUrl}</dd>
                  </div>
                  <div>
                    <dt>AI WebSocket</dt>
                    <dd className={styles.mono}>{status.aiServerWsUrl}</dd>
                  </div>
                  <div>
                    <dt>AI 自动启动</dt>
                    <dd>{status.aiServerAutoStart ? '是' : '否'}</dd>
                  </div>
                  <div>
                    <dt>AI 连通性</dt>
                    <dd style={{ color: status.aiServerReachable ? '#48bb78' : '#fc8181' }}>
                      {status.aiServerReachable ? '可达' : '不可达'}
                    </dd>
                  </div>
                  <div>
                    <dt>用户数 / 任务数</dt>
                    <dd>
                      {status.userCount} / {status.uploadTaskCount}
                    </dd>
                  </div>
                </dl>
              )}
            </section>
          </>
        )}

        {tab === 'config' && (
          <section className={styles.section}>
            <h2 className={styles.sectionTitle}>API 与系统配置</h2>
            <ModelConfigPanel />
            <h3 className={styles.subTitle}>服务连接与其它参数</h3>
            <SystemConfigPanel />
          </section>
        )}

        {tab === 'upload' && (
          <section className={styles.section}>
            <div className={styles.sectionHead}>
              <h2 className={styles.sectionTitle}>上传任务队列</h2>
              <button type="button" className={styles.actionBtn} onClick={() => void handleProcessOne()}>
                处理一条待处理任务
              </button>
            </div>
            {processMsg && <p className={styles.processMsg}>{processMsg}</p>}
            {loading ? (
              <p className={styles.hint}>加载中...</p>
            ) : tasks.length === 0 ? (
              <p className={styles.hint}>暂无任务</p>
            ) : (
              <table className={styles.table}>
                <thead>
                  <tr>
                    <th>ID</th>
                    <th>用户ID</th>
                    <th>文件名</th>
                    <th>范围</th>
                    <th>状态</th>
                    <th>创建时间</th>
                    <th>完成时间</th>
                    <th>错误信息</th>
                  </tr>
                </thead>
                <tbody>
                  {tasks.map((t) => (
                    <tr key={t.id}>
                      <td>{t.id}</td>
                      <td>{t.userId}</td>
                      <td className={styles.fileName}>{t.fileName}</td>
                      <td>{t.kbScope === 'PERSONAL' ? '个人' : '公共'}</td>
                      <td>
                        <span style={{ color: STATUS_COLOR[t.status], fontWeight: 500 }}>
                          {STATUS_LABEL[t.status]}
                        </span>
                      </td>
                      <td>{new Date(t.createdAt).toLocaleString('zh-CN')}</td>
                      <td>{t.finishedAt ? new Date(t.finishedAt).toLocaleString('zh-CN') : '—'}</td>
                      <td className={styles.errCell}>{t.errorMessage ?? '—'}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            )}
          </section>
        )}

        {tab === 'users' && (
          <section className={styles.section}>
            <h2 className={styles.sectionTitle}>系统用户</h2>
            <UserListPanel />
          </section>
        )}
      </div>
    </div>
  )
}
