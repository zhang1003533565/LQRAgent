import { useEffect, useState } from 'react'
import { getKnowledgeGraph, type KnowledgeGraphData } from '@/shared/api/admin'
import { Card, CardContent, CardHeader, CardTitle } from '@/admin/components/dev-console/ui'
import { panel } from './panelStyles'

export default function KnowledgeGraphPanel() {
  const [graph, setGraph] = useState<KnowledgeGraphData | null>(null)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    void (async () => {
      try {
        setGraph(await getKnowledgeGraph())
      } finally {
        setLoading(false)
      }
    })()
  }, [])

  return (
    <Card>
      <CardHeader>
        <CardTitle>知识图谱</CardTitle>
        <p className={panel.desc}>GET /api/admin/knowledge-graph</p>
      </CardHeader>
      <CardContent>
        {loading ? (
          <p className={panel.hint}>加载中...</p>
        ) : !graph ? (
          <p className={panel.hint}>暂无数据</p>
        ) : (
          <div className="space-y-6">
            <div className="flex gap-4">
              <span className={panel.msgOk}>节点: {graph.nodeCount}</span>
              <span className={panel.msg}>依赖边: {graph.edgeCount}</span>
            </div>

            <div>
              <h3 className={panel.subTitle}>知识点节点</h3>
              <div className="mt-2 overflow-x-auto rounded-md border border-console-border">
                <table className={panel.table}>
                  <thead>
                    <tr>
                      <th className={panel.th}>kpId</th>
                      <th className={panel.th}>标题</th>
                      <th className={panel.th}>章节</th>
                      <th className={panel.th}>描述</th>
                    </tr>
                  </thead>
                  <tbody>
                    {graph.nodes.map((n) => (
                      <tr key={n.id}>
                        <td className={panel.td}>{n.kpId}</td>
                        <td className={panel.td}>{n.title}</td>
                        <td className={panel.td}>{n.chapter}</td>
                        <td className={panel.td}>{n.description}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </div>
          </div>
        )}
      </CardContent>
    </Card>
  )
}
