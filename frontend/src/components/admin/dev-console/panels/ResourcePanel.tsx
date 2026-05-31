/**
 * 学习资源面板 — 按科目筛选 + 类型统计 + 卡片展示
 */
import { useEffect, useState } from 'react'
import { listAdminResources, type AdminResourceItem } from '@/api/admin/admin'
import { Card, CardContent, CardHeader, CardTitle, ConsoleBadge } from '@/components/admin/dev-console/ui'
import { panel } from './panelStyles'

const TYPE_CONFIG: Record<string, { label: string; color: string; icon: string }> = {
  LESSON: { label: '讲义', color: 'text-console-blue', icon: '📖' },
  QUIZ: { label: '练习题', color: 'text-yellow-400', icon: '📝' },
  CODE_CASE: { label: '代码', color: 'text-green-400', icon: '💻' },
  ILLUSTRATION: { label: '示意图', color: 'text-purple-400', icon: '🖼️' },
  EXTENSION_READING: { label: '拓展', color: 'text-cyan-400', icon: '📚' },
}

export default function ResourcePanel() {
  const [resources, setResources] = useState<AdminResourceItem[]>([])
  const [subjects, setSubjects] = useState<string[]>([])
  const [loading, setLoading] = useState(true)
  const [selectedSubject, setSelectedSubject] = useState('')
  const [filterType, setFilterType] = useState('')
  const [expandedId, setExpandedId] = useState<number | null>(null)

  async function fetchData(subject?: string, type?: string) {
    setLoading(true)
    try {
      const res = await listAdminResources(type || undefined, undefined, subject || undefined)
      setResources(res.items || [])
      if (res.subjects) setSubjects(res.subjects)
    } catch {
      setResources([])
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => { void fetchData() }, [])

  function handleSubjectChange(subject: string) {
    setSelectedSubject(subject)
    void fetchData(subject || undefined, filterType || undefined)
  }

  function handleTypeChange(type: string) {
    setFilterType(type)
    void fetchData(selectedSubject || undefined, type || undefined)
  }

  // 按类型统计
  const typeCounts = (resources || []).reduce((acc, r) => {
    acc[r.resourceType] = (acc[r.resourceType] || 0) + 1
    return acc
  }, {} as Record<string, number>)

  return (
    <div className="space-y-4">
      {/* 科目标签栏 */}
      {subjects.length > 0 && (
        <Card>
          <CardContent className="flex flex-wrap gap-2 py-3">
            <button
              onClick={() => handleSubjectChange('')}
              className={`rounded-full px-3 py-1 text-xs font-medium transition-colors ${
                !selectedSubject
                  ? 'bg-console-blue text-white'
                  : 'bg-console-border/30 text-console-muted hover:bg-console-border/50'
              }`}
            >
              全部科目
            </button>
            {subjects.map(s => (
              <button
                key={s}
                onClick={() => handleSubjectChange(s)}
                className={`rounded-full px-3 py-1 text-xs font-medium transition-colors ${
                  selectedSubject === s
                    ? 'bg-console-blue text-white'
                    : 'bg-console-border/30 text-console-muted hover:bg-console-border/50'
                }`}
              >
                {s}
              </button>
            ))}
          </CardContent>
        </Card>
      )}

      {/* 类型统计 */}
      <div className="grid grid-cols-5 gap-3">
        {Object.entries(TYPE_CONFIG).map(([type, config]) => (
          <Card key={type}
            className={`cursor-pointer transition-colors hover:border-console-blue/30 ${filterType === type ? 'border-console-blue/50 bg-console-blue/5' : ''}`}
            onClick={() => handleTypeChange(filterType === type ? '' : type)}>
            <CardContent className="py-3 text-center">
              <p className="text-lg">{config.icon}</p>
              <p className={`text-xl font-bold ${config.color}`}>{typeCounts[type] || 0}</p>
              <p className="text-xs text-console-muted">{config.label}</p>
            </CardContent>
          </Card>
        ))}
      </div>

      {/* 资源列表 */}
      {loading ? (
        <p className={panel.hint}>加载中...</p>
      ) : resources.length === 0 ? (
        <Card><CardContent className="py-8 text-center text-sm text-console-muted">
          {selectedSubject ? `"${selectedSubject}" 下暂无资源` : '暂无资源'}
        </CardContent></Card>
      ) : (
        <div className="space-y-3">
          {resources.map(r => {
            const config = TYPE_CONFIG[r.resourceType] ?? { label: r.resourceType, color: 'text-console-muted', icon: '📄' }
            const isExpanded = expandedId === r.id
            const preview = r.content.replace(/<[^>]*>/g, '').replace(/#{1,6}\s/g, '').trim()
            return (
              <Card key={r.id} className="hover:border-console-blue/30 transition-colors">
                <CardContent className="py-3">
                  <div className="flex items-start gap-3">
                    <span className="text-lg mt-0.5">{config.icon}</span>
                    <div className="flex-1 min-w-0">
                      <div className="flex items-center gap-2 flex-wrap">
                        <p className="text-sm font-medium text-console-text truncate">{r.title}</p>
                        <ConsoleBadge variant="muted">{config.label}</ConsoleBadge>
                        <span className="font-mono text-[11px] text-console-muted">{r.kpId}</span>
                      </div>
                      <p className={`mt-1.5 text-xs text-console-muted ${isExpanded ? '' : 'line-clamp-2'}`}>
                        {preview.slice(0, isExpanded ? 500 : 150)}{preview.length > (isExpanded ? 500 : 150) ? '...' : ''}
                      </p>
                      {preview.length > 150 && (
                        <button className="mt-1 text-[11px] text-console-blue hover:underline"
                          onClick={() => setExpandedId(isExpanded ? null : r.id)}>
                          {isExpanded ? '收起' : '展开全部'}
                        </button>
                      )}
                    </div>
                  </div>
                </CardContent>
              </Card>
            )
          })}
        </div>
      )}
    </div>
  )
}
