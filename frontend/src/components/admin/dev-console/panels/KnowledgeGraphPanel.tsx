/**
 * 知识图谱面板 — 按科目筛选 + 卡片展示 + 学科交叉
 */
import { useEffect, useState } from 'react'
import { getKnowledgeGraph, type KnowledgeGraphData } from '@/api/admin/admin'
import { Card, CardContent, CardHeader, CardTitle, ConsoleBadge } from '@/components/admin/dev-console/ui'
import { panel } from './panelStyles'

export default function KnowledgeGraphPanel() {
  const [data, setData] = useState<KnowledgeGraphData & { subjects?: string[] } | null>(null)
  const [loading, setLoading] = useState(true)
  const [search, setSearch] = useState('')
  const [selectedSubject, setSelectedSubject] = useState('')
  const [selectedChapter, setSelectedChapter] = useState('')

  async function fetchData(subject?: string) {
    setLoading(true)
    try {
      const d = await getKnowledgeGraph(subject || undefined)
      setData(d)
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => { void fetchData() }, [])

  function handleSubjectChange(subject: string) {
    setSelectedSubject(subject)
    setSelectedChapter('')
    void fetchData(subject || undefined)
  }

  if (loading && !data) return <p className={panel.hint}>加载知识图谱...</p>
  if (!data) return <p className={panel.hint}>暂无数据</p>

  const chapters = [...new Set(data.nodes.map(n => n.chapter))].filter(Boolean).sort()
  const filtered = data.nodes.filter(n => {
    if (selectedChapter && n.chapter !== selectedChapter) return false
    if (search) {
      const q = search.toLowerCase()
      return n.title.toLowerCase().includes(q) || n.kpId.toLowerCase().includes(q) ||
        (n.description || '').toLowerCase().includes(q)
    }
    return true
  })

  // 依赖关系 map
  const depMap = new Map<string, string[]>()
  const crossMap = new Map<string, string[]>()
  data.edges.forEach(e => {
    if (e.relationType === 'CROSS_DISCIPLINE') {
      if (!crossMap.has(e.toKpId)) crossMap.set(e.toKpId, [])
      crossMap.get(e.toKpId)!.push(e.fromKpId)
    } else {
      if (!depMap.has(e.toKpId)) depMap.set(e.toKpId, [])
      depMap.get(e.toKpId)!.push(e.fromKpId)
    }
  })

  const subjects = data.subjects || []

  return (
    <div className="space-y-4">
      {/* 统计概览 */}
      <div className="grid grid-cols-4 gap-3">
        <Card>
          <CardContent className="py-3 text-center">
            <p className="text-2xl font-bold text-console-blue">{data.nodeCount}</p>
            <p className="text-xs text-console-muted">知识点</p>
          </CardContent>
        </Card>
        <Card>
          <CardContent className="py-3 text-center">
            <p className="text-2xl font-bold text-console-green">{data.edgeCount}</p>
            <p className="text-xs text-console-muted">依赖关系</p>
          </CardContent>
        </Card>
        <Card>
          <CardContent className="py-3 text-center">
            <p className="text-2xl font-bold text-yellow-400">{chapters.length}</p>
            <p className="text-xs text-console-muted">章节</p>
          </CardContent>
        </Card>
        <Card>
          <CardContent className="py-3 text-center">
            <p className="text-2xl font-bold text-purple-400">{subjects.length}</p>
            <p className="text-xs text-console-muted">科目</p>
          </CardContent>
        </Card>
      </div>

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
              全部 ({data.nodeCount})
            </button>
            {subjects.map(s => {
              const count = data.nodes.filter(n => n.subject === s).length
              return (
                <button
                  key={s}
                  onClick={() => handleSubjectChange(s)}
                  className={`rounded-full px-3 py-1 text-xs font-medium transition-colors ${
                    selectedSubject === s
                      ? 'bg-console-blue text-white'
                      : 'bg-console-border/30 text-console-muted hover:bg-console-border/50'
                  }`}
                >
                  {s} ({count})
                </button>
              )
            })}
          </CardContent>
        </Card>
      )}

      {/* 搜索 + 章节筛选 */}
      <Card>
        <CardContent className="flex flex-wrap items-end gap-3 py-3">
          <label className={panel.label}>
            搜索
            <input className={panel.input} value={search} onChange={e => setSearch(e.target.value)}
              placeholder="搜索名称或 ID..." />
          </label>
          <label className={panel.label}>
            章节
            <select className={panel.select} value={selectedChapter} onChange={e => setSelectedChapter(e.target.value)}>
              <option value="">全部</option>
              {chapters.map(c => <option key={c} value={c}>{c}</option>)}
            </select>
          </label>
          <span className="text-xs text-console-muted pb-0.5">
            {filtered.length} / {data.nodeCount} 个
          </span>
        </CardContent>
      </Card>

      {/* 知识点卡片 */}
      <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
        {filtered.map(node => {
          const deps = depMap.get(node.kpId) || []
          const crossDeps = crossMap.get(node.kpId) || []
          return (
            <Card key={node.id} className="hover:border-console-blue/30 transition-colors">
              <CardContent className="py-3">
                <div className="flex items-start justify-between gap-2">
                  <div className="min-w-0">
                    <p className="text-sm font-medium text-console-text truncate">{node.title}</p>
                    <p className="mt-0.5 font-mono text-[11px] text-console-muted">{node.kpId}</p>
                  </div>
                  <div className="flex flex-col items-end gap-1">
                    {node.subject && <ConsoleBadge variant="muted">{node.subject}</ConsoleBadge>}
                    {node.chapter && <ConsoleBadge variant="muted">{node.chapter}</ConsoleBadge>}
                  </div>
                </div>
                {node.description && (
                  <p className="mt-2 text-xs text-console-muted line-clamp-2">{node.description}</p>
                )}
                {deps.length > 0 && (
                  <div className="mt-2 flex flex-wrap gap-1">
                    <span className="text-[10px] text-console-muted">前置：</span>
                    {deps.map(d => (
                      <span key={d} className="rounded bg-console-border/30 px-1.5 py-0.5 text-[10px] text-console-muted font-mono">
                        {d}
                      </span>
                    ))}
                  </div>
                )}
                {crossDeps.length > 0 && (
                  <div className="mt-1 flex flex-wrap gap-1">
                    <span className="text-[10px] text-purple-400">🔗 交叉：</span>
                    {crossDeps.map(d => (
                      <span key={d} className="rounded bg-purple-500/10 px-1.5 py-0.5 text-[10px] text-purple-400 font-mono">
                        {d}
                      </span>
                    ))}
                  </div>
                )}
              </CardContent>
            </Card>
          )
        })}
      </div>
      {filtered.length === 0 && (
        <p className="text-center text-sm text-console-muted py-8">无匹配的知识点</p>
      )}
    </div>
  )
}
