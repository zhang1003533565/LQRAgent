import { useEffect, useState } from 'react'
import { listAdminResources, type AdminResourceItem } from '@/api/admin/admin'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/admin/dev-console/ui'
import { panel } from './panelStyles'

const TYPE_LABEL: Record<string, string> = {
  LESSON: '讲义',
  QUIZ: '练习题',
  CODE_CASE: '代码示例',
  ILLUSTRATION: '示意图',
}

export default function ResourcePanel() {
  const [resources, setResources] = useState<AdminResourceItem[]>([])
  const [loading, setLoading] = useState(true)
  const [filterType, setFilterType] = useState('')
  const [filterKpId, setFilterKpId] = useState('')

  const fetchData = async (type?: string, kpId?: string) => {
    setLoading(true)
    try {
      setResources(await listAdminResources(type || undefined, kpId || undefined))
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    void fetchData()
  }, [])

  const handleFilter = () => {
    void fetchData(filterType || undefined, filterKpId || undefined)
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle>资源管理</CardTitle>
        <p className={panel.desc}>GET /api/admin/resources</p>
      </CardHeader>
      <CardContent>
        <div className="mb-4 flex flex-wrap gap-3">
          <label className={panel.label}>
            类型
            <select className={panel.select} value={filterType} onChange={(e) => setFilterType(e.target.value)}>
              <option value="">全部</option>
              <option value="LESSON">讲义</option>
              <option value="QUIZ">练习题</option>
              <option value="CODE_CASE">代码示例</option>
              <option value="ILLUSTRATION">示意图</option>
            </select>
          </label>
          <label className={panel.label}>
            知识点
            <input className={panel.input} value={filterKpId} onChange={(e) => setFilterKpId(e.target.value)} placeholder="如 kp_function" />
          </label>
          <div className="flex items-end">
            <button className={panel.primaryBtn} onClick={handleFilter}>查询</button>
          </div>
        </div>

        {loading ? (
          <p className={panel.hint}>加载中...</p>
        ) : resources.length === 0 ? (
          <p className={panel.hint}>暂无资源</p>
        ) : (
          <div className="overflow-x-auto rounded-md border border-console-border">
            <table className={panel.table}>
              <thead>
                <tr>
                  <th className={panel.th}>ID</th>
                  <th className={panel.th}>类型</th>
                  <th className={panel.th}>标题</th>
                  <th className={panel.th}>知识点</th>
                  <th className={panel.th}>内容预览</th>
                </tr>
              </thead>
              <tbody>
                {resources.map((r) => (
                  <tr key={r.id}>
                    <td className={panel.td}>{r.id}</td>
                    <td className={panel.td}>{TYPE_LABEL[r.resourceType] ?? r.resourceType}</td>
                    <td className={panel.td}>{r.title}</td>
                    <td className={panel.td}>{r.kpId}</td>
                    <td className={panel.td + ' max-w-xs truncate'}>{r.content.replace(/<[^>]*>/g, '').slice(0, 120)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </CardContent>
    </Card>
  )
}
