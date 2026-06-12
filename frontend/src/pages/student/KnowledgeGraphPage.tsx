import { useState, useEffect, useRef, useCallback } from 'react'
import { useNavigate } from 'react-router-dom'
import { getKnowledgeGraph, type KnowledgeGraphData, type KnowledgeGraphNode, type KnowledgeGraphEdge } from '@/api/student/knowledge'
import { usePathStore } from '@/utils/store/pathStore'
import { useProfileStore } from '@/utils/store/profileStore'
import styles from './KnowledgeGraphPage.module.css'

interface GraphNode extends KnowledgeGraphNode {
  x: number
  y: number
  vx: number
  vy: number
  radius: number
  color: string
}

const COLORS = {
  mastered: '#22c55e',
  learning: '#3b82f6',
  weak: '#ef4444',
  default: '#94a3b8',
  edge: '#cbd5e1',
  edgeHighlight: '#3b82f6',
  selected: '#f59e0b',
}

function getNodeColor(kpId: string, masteryMap: Record<string, number>): string {
  const m = masteryMap[kpId]
  if (m === undefined) return COLORS.default
  if (m >= 80) return COLORS.mastered
  if (m >= 40) return COLORS.learning
  return COLORS.weak
}

export default function KnowledgeGraphPage() {
  const navigate = useNavigate()
  const canvasRef = useRef<HTMLCanvasElement>(null)
  const [data, setData] = useState<KnowledgeGraphData | null>(null)
  const [loading, setLoading] = useState(true)
  const [search, setSearch] = useState('')
  const [selectedNode, setSelectedNode] = useState<GraphNode | null>(null)
  const [subject, setSubject] = useState('')
  const nodesRef = useRef<GraphNode[]>([])
  const edgesRef = useRef<KnowledgeGraphEdge[]>([])
  const animRef = useRef<number>(0)
  const dragRef = useRef<{ node: GraphNode | null; offsetX: number; offsetY: number }>({ node: null, offsetX: 0, offsetY: 0 })

  const pathKpIds = usePathStore((s) => new Set(s.nodes.map((n) => n.kpId)))

  useEffect(() => {
    setLoading(true)
    getKnowledgeGraph(subject || undefined)
      .then((d) => {
        setData(d)
        initGraph(d)
      })
      .catch(() => {})
      .finally(() => setLoading(false))
  }, [subject])

  const initGraph = useCallback((d: KnowledgeGraphData) => {
    const canvas = canvasRef.current
    if (!canvas) return
    const w = canvas.width
    const h = canvas.height
    const cx = w / 2, cy = h / 2

    const nodes: GraphNode[] = d.nodes.map((n, i) => {
      const angle = (2 * Math.PI * i) / d.nodes.length
      const r = Math.min(w, h) * 0.3
      return {
        ...n,
        x: cx + r * Math.cos(angle) + (Math.random() - 0.5) * 40,
        y: cy + r * Math.sin(angle) + (Math.random() - 0.5) * 40,
        vx: 0,
        vy: 0,
        radius: 18,
        color: COLORS.default,
      }
    })

    nodesRef.current = nodes
    edgesRef.current = d.edges
    startSimulation()
  }, [])

  const startSimulation = useCallback(() => {
    const nodes = nodesRef.current
    const edges = edgesRef.current
    const canvas = canvasRef.current
    if (!canvas || nodes.length === 0) return

    const w = canvas.width
    const h = canvas.height
    const iterations = 200

    for (let iter = 0; iter < iterations; iter++) {
      for (let i = 0; i < nodes.length; i++) {
        nodes[i].vx = 0
        nodes[i].vy = 0
      }

      for (let i = 0; i < nodes.length; i++) {
        for (let j = i + 1; j < nodes.length; j++) {
          const dx = nodes[j].x - nodes[i].x
          const dy = nodes[j].y - nodes[i].y
          const dist = Math.max(Math.sqrt(dx * dx + dy * dy), 1)
          const force = 5000 / (dist * dist)
          nodes[i].vx -= (dx / dist) * force
          nodes[i].vy -= (dy / dist) * force
          nodes[j].vx += (dx / dist) * force
          nodes[j].vy += (dy / dist) * force
        }
      }

      for (const edge of edges) {
        const from = nodes.find((n) => n.kpId === edge.fromKpId)
        const to = nodes.find((n) => n.kpId === edge.toKpId)
        if (!from || !to) continue
        const dx = to.x - from.x
        const dy = to.y - from.y
        const dist = Math.max(Math.sqrt(dx * dx + dy * dy), 1)
        const force = (dist - 100) * 0.01
        from.vx += (dx / dist) * force
        from.vy += (dy / dist) * force
        to.vx -= (dx / dist) * force
        to.vy -= (dy / dist) * force
      }

      for (const node of nodes) {
        node.vx += (w / 2 - node.x) * 0.001
        node.vy += (h / 2 - node.y) * 0.001
        node.x += node.vx * 0.3
        node.y += node.vy * 0.3
        node.x = Math.max(30, Math.min(w - 30, node.x))
        node.y = Math.max(30, Math.min(h - 30, node.y))
      }
    }

    drawGraph()
  }, [])

  const drawGraph = useCallback(() => {
    const canvas = canvasRef.current
    if (!canvas) return
    const ctx = canvas.getContext('2d')
    if (!ctx) return

    const w = canvas.width
    const h = canvas.height
    ctx.clearRect(0, 0, w, h)

    const nodes = nodesRef.current
    const edges = edgesRef.current
    const masteryMap: Record<string, number> = {}

    for (const node of nodes) {
      node.color = getNodeColor(node.kpId, masteryMap)
    }

    for (const edge of edges) {
      const from = nodes.find((n) => n.kpId === edge.fromKpId)
      const to = nodes.find((n) => n.kpId === edge.toKpId)
      if (!from || !to) continue
      const isPath = pathKpIds.has(from.kpId) && pathKpIds.has(to.kpId)
      ctx.beginPath()
      ctx.moveTo(from.x, from.y)
      ctx.lineTo(to.x, to.y)
      ctx.strokeStyle = isPath ? COLORS.edgeHighlight : COLORS.edge
      ctx.lineWidth = isPath ? 2.5 : 1
      ctx.stroke()

      const angle = Math.atan2(to.y - from.y, to.x - from.x)
      const arrowX = to.x - Math.cos(angle) * (to.radius + 4)
      const arrowY = to.y - Math.sin(angle) * (to.radius + 4)
      ctx.beginPath()
      ctx.moveTo(arrowX, arrowY)
      ctx.lineTo(arrowX - 8 * Math.cos(angle - 0.4), arrowY - 8 * Math.sin(angle - 0.4))
      ctx.lineTo(arrowX - 8 * Math.cos(angle + 0.4), arrowY - 8 * Math.sin(angle + 0.4))
      ctx.closePath()
      ctx.fillStyle = isPath ? COLORS.edgeHighlight : COLORS.edge
      ctx.fill()
    }

    for (const node of nodes) {
      const isSelected = selectedNode?.kpId === node.kpId
      const isOnPath = pathKpIds.has(node.kpId)

      ctx.beginPath()
      ctx.arc(node.x, node.y, node.radius, 0, Math.PI * 2)
      ctx.fillStyle = isSelected ? COLORS.selected : node.color
      ctx.fill()
      if (isOnPath) {
        ctx.strokeStyle = COLORS.edgeHighlight
        ctx.lineWidth = 3
        ctx.stroke()
      }

      ctx.fillStyle = '#fff'
      ctx.font = 'bold 11px sans-serif'
      ctx.textAlign = 'center'
      ctx.textBaseline = 'middle'
      const label = node.title.length > 4 ? node.title.slice(0, 4) : node.title
      ctx.fillText(label, node.x, node.y)

      ctx.fillStyle = '#334155'
      ctx.font = '11px sans-serif'
      ctx.textAlign = 'center'
      ctx.fillText(node.title, node.x, node.y + node.radius + 14)
    }
  }, [selectedNode, pathKpIds])

  const handleCanvasClick = useCallback((e: React.MouseEvent<HTMLCanvasElement>) => {
    const canvas = canvasRef.current
    if (!canvas) return
    const rect = canvas.getBoundingClientRect()
    const x = e.clientX - rect.left
    const y = e.clientY - rect.top

    for (const node of nodesRef.current) {
      const dx = x - node.x
      const dy = y - node.y
      if (dx * dx + dy * dy <= node.radius * node.radius) {
        setSelectedNode(node)
        drawGraph()
        return
      }
    }
    setSelectedNode(null)
    drawGraph()
  }, [drawGraph])

  const handleMouseDown = useCallback((e: React.MouseEvent<HTMLCanvasElement>) => {
    const canvas = canvasRef.current
    if (!canvas) return
    const rect = canvas.getBoundingClientRect()
    const x = e.clientX - rect.left
    const y = e.clientY - rect.top

    for (const node of nodesRef.current) {
      const dx = x - node.x
      const dy = y - node.y
      if (dx * dx + dy * dy <= node.radius * node.radius) {
        dragRef.current = { node, offsetX: dx, offsetY: dy }
        return
      }
    }
  }, [])

  const handleMouseMove = useCallback((e: React.MouseEvent<HTMLCanvasElement>) => {
    if (!dragRef.current.node) return
    const canvas = canvasRef.current
    if (!canvas) return
    const rect = canvas.getBoundingClientRect()
    dragRef.current.node.x = e.clientX - rect.left - dragRef.current.offsetX
    dragRef.current.node.y = e.clientY - rect.top - dragRef.current.offsetY
    drawGraph()
  }, [drawGraph])

  const handleMouseUp = useCallback(() => {
    dragRef.current.node = null
  }, [])

  useEffect(() => {
    const canvas = canvasRef.current
    if (!canvas) return
    const resize = () => {
      const parent = canvas.parentElement
      if (!parent) return
      canvas.width = parent.clientWidth
      canvas.height = parent.clientHeight
      if (nodesRef.current.length > 0) drawGraph()
    }
    resize()
    window.addEventListener('resize', resize)
    return () => window.removeEventListener('resize', resize)
  }, [drawGraph])

  const filteredNodes = data?.nodes.filter((n) => {
    if (!search) return true
    const q = search.toLowerCase()
    return n.title.toLowerCase().includes(q) || n.kpId.toLowerCase().includes(q)
  }) ?? []

  const subjects = data?.subjects ?? []

  return (
    <section className={styles.page}>
      <div className={styles.glow} />

      <header className={styles.topbar}>
        <div>
          <h1 className={styles.title}>知识图谱</h1>
          <p className={styles.subtitle}>可视化展示知识点之间的依赖关系</p>
        </div>
        <div className={styles.topMeta}>
          {data ? `${data.nodeCount} 知识点 · ${data.edgeCount} 关系` : ''}
        </div>
      </header>

      <div className={styles.toolbar}>
        <input
          type="text"
          className={styles.searchInput}
          placeholder="搜索知识点..."
          value={search}
          onChange={(e) => setSearch(e.target.value)}
        />
        {subjects.length > 0 && (
          <div className={styles.subjectTabs}>
            <button
              type="button"
              className={`${styles.subjectTab} ${!subject ? styles.subjectTabActive : ''}`}
              onClick={() => setSubject('')}
            >
              全部
            </button>
            {subjects.map((s) => (
              <button
                key={s}
                type="button"
                className={`${styles.subjectTab} ${subject === s ? styles.subjectTabActive : ''}`}
                onClick={() => setSubject(s)}
              >
                {s}
              </button>
            ))}
          </div>
        )}
        <div className={styles.legend}>
          <span className={styles.legendItem}><span className={styles.legendDot} style={{ background: COLORS.mastered }} /> 已掌握</span>
          <span className={styles.legendItem}><span className={styles.legendDot} style={{ background: COLORS.learning }} /> 学习中</span>
          <span className={styles.legendItem}><span className={styles.legendDot} style={{ background: COLORS.weak }} /> 薄弱</span>
          <span className={styles.legendItem}><span className={styles.legendDot} style={{ background: COLORS.default }} /> 未学习</span>
          <span className={styles.legendItem}><span className={styles.legendLine} style={{ background: COLORS.edgeHighlight }} /> 学习路径</span>
        </div>
      </div>

      <div className={styles.graphArea}>
        {loading ? (
          <p className={styles.loadingText}>加载知识图谱...</p>
        ) : (
          <canvas
            ref={canvasRef}
            className={styles.canvas}
            onClick={handleCanvasClick}
            onMouseDown={handleMouseDown}
            onMouseMove={handleMouseMove}
            onMouseUp={handleMouseUp}
            onMouseLeave={handleMouseUp}
          />
        )}
      </div>

      {selectedNode && (
        <div className={styles.detailPanel}>
          <div className={styles.detailHeader}>
            <h3 className={styles.detailTitle}>{selectedNode.title}</h3>
            <button type="button" className={styles.detailClose} onClick={() => { setSelectedNode(null); drawGraph() }}>×</button>
          </div>
          <div className={styles.detailBody}>
            {selectedNode.subject && <p className={styles.detailMeta}>科目：{selectedNode.subject}</p>}
            {selectedNode.chapter && <p className={styles.detailMeta}>章节：{selectedNode.chapter}</p>}
            {selectedNode.difficulty && <p className={styles.detailMeta}>难度：{'⭐'.repeat(selectedNode.difficulty)}</p>}
            {selectedNode.description && <p className={styles.detailDesc}>{selectedNode.description}</p>}
            <div className={styles.detailDeps}>
              <p className={styles.detailDepTitle}>前置知识点：</p>
              {data?.edges.filter((e) => e.toKpId === selectedNode.kpId).map((e) => {
                const from = data.nodes.find((n) => n.kpId === e.fromKpId)
                return from ? <span key={e.fromKpId} className={styles.detailDepTag}>{from.title}</span> : null
              })}
              {data?.edges.filter((e) => e.toKpId === selectedNode.kpId).length === 0 && <span className={styles.detailDepNone}>无</span>}
            </div>
            <div className={styles.detailActions}>
              <button type="button" className={styles.detailBtn} onClick={() => navigate('/workspace/resources')}>
                查看资源
              </button>
              <button type="button" className={styles.detailBtn} onClick={() => navigate('/workspace/quiz')}>
                开始答题
              </button>
            </div>
          </div>
        </div>
      )}

      {search && filteredNodes.length > 0 && (
        <div className={styles.searchResults}>
          {filteredNodes.slice(0, 10).map((n) => (
            <button
              key={n.kpId}
              type="button"
              className={styles.searchItem}
              onClick={() => {
                const node = nodesRef.current.find((gn) => gn.kpId === n.kpId)
                if (node) {
                  setSelectedNode(node)
                  drawGraph()
                }
                setSearch('')
              }}
            >
              {n.title}
            </button>
          ))}
        </div>
      )}
    </section>
  )
}
