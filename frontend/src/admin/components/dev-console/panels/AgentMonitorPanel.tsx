import { useEffect, useState } from 'react'
import { getAgentStats, getAgentRuns, type AgentStatItem, type AgentRunItem } from '@/shared/api/admin'
import { Card, CardContent, CardHeader, CardTitle, ConsoleBadge } from '@/admin/components/dev-console/ui'
import { panel } from './panelStyles'

const STATUS_VARIANT: Record<string, 'success' | 'danger' | 'warning'> = {
  SUCCESS: 'success',
  FAILED: 'danger',
  RUNNING: 'warning',
}

export default function AgentMonitorPanel() {
  const [stats, setStats] = useState<AgentStatItem[]>([])
  const [registeredAgents, setRegisteredAgents] = useState<string[]>([])
  const [runs, setRuns] = useState<AgentRunItem[]>([])
  const [loading, setLoading] = useState(true)
  const [page, setPage] = useState(1)

  async function load(p = 1) {
    const [s, r] = await Promise.all([getAgentStats(), getAgentRuns(p, 10)])
    setStats(s.stats)
    setRegisteredAgents(s.registeredAgents)
    setRuns(r.items)
  }

  useEffect(() => {
    void load().finally(() => setLoading(false))
  }, [])

  useEffect(() => { void load(page) }, [page])

  return (
    <div className="space-y-6">
      <Card>
        <CardHeader>
          <CardTitle>智能体调用统计</CardTitle>
          <p className={panel.desc}>GET /api/admin/agent-stats</p>
        </CardHeader>
        <CardContent>
          <div className="mb-3 flex flex-wrap gap-2">
            <ConsoleBadge variant="default" className="text-xs">已注册 {registeredAgents.length} 个智能体</ConsoleBadge>
            {registeredAgents.map(a => <ConsoleBadge key={a} variant="muted" className="text-xs">{a}</ConsoleBadge>)}
          </div>
          {loading ? (
            <p className={panel.hint}>加载中...</p>
          ) : stats.length === 0 ? (
            <p className={panel.hint}>暂无调用记录</p>
          ) : (
            <div className="overflow-x-auto rounded-md border border-console-border">
              <table className={panel.table}>
                <thead>
                  <tr>
                    <th className={panel.th}>智能体</th>
                    <th className={panel.th}>总调用</th>
                    <th className={panel.th}>成功</th>
                    <th className={panel.th}>失败</th>
                    <th className={panel.th}>成功率</th>
                    <th className={panel.th}>平均耗时(ms)</th>
                  </tr>
                </thead>
                <tbody>
                  {stats.map((s) => (
                    <tr key={s.agent}>
                      <td className={panel.td}>{s.agent}</td>
                      <td className={panel.td}>{s.total}</td>
                      <td className={panel.td}>{s.success}</td>
                      <td className={panel.td}>{s.failed}</td>
                      <td className={panel.td}>{s.successRate}</td>
                      <td className={panel.td}>{s.avgDurationMs}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </CardContent>
      </Card>

      <Card>
        <CardHeader className="flex-row items-center justify-between space-y-0">
          <div>
            <CardTitle>运行日志</CardTitle>
            <p className={panel.desc}>GET /api/admin/agent-runs</p>
          </div>
          <div className="flex gap-2">
            <button className={panel.secondaryBtn} disabled={page <= 1} onClick={() => setPage(p => p - 1)}>上一页</button>
            <button className={panel.secondaryBtn} onClick={() => setPage(p => p + 1)}>下一页</button>
          </div>
        </CardHeader>
        <CardContent>
          {loading ? (
            <p className={panel.hint}>加载中...</p>
          ) : runs.length === 0 ? (
            <p className={panel.hint}>暂无运行日志</p>
          ) : (
            <div className="overflow-x-auto rounded-md border border-console-border">
              <table className={panel.table}>
                <thead>
                  <tr>
                    <th className={panel.th}>ID</th>
                    <th className={panel.th}>智能体</th>
                    <th className={panel.th}>意图</th>
                    <th className={panel.th}>状态</th>
                    <th className={panel.th}>耗时(ms)</th>
                    <th className={panel.th}>错误</th>
                    <th className={panel.th}>时间</th>
                  </tr>
                </thead>
                <tbody>
                  {runs.map((r) => (
                    <tr key={r.id}>
                      <td className={panel.td}>{r.id}</td>
                      <td className={panel.td}>{r.agent}</td>
                      <td className={panel.td}>{r.intent ?? '—'}</td>
                      <td className={panel.td}>
                        <ConsoleBadge variant={STATUS_VARIANT[r.status] ?? 'warning'}>
                          {r.status}
                        </ConsoleBadge>
                      </td>
                      <td className={panel.td}>{r.durationMs ?? '—'}</td>
                      <td className={`${panel.td} max-w-[160px] truncate text-console-red`}>{r.errorMessage ?? '—'}</td>
                      <td className={`${panel.td} whitespace-nowrap text-xs`}>{new Date(r.createdAt).toLocaleString('zh-CN')}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </CardContent>
      </Card>
    </div>
  )
}
