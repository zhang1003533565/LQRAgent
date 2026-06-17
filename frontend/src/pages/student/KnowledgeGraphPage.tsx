import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import {
  BookOpen,
  ChevronRight,
  Crosshair,
  Eye,
  GitBranch,
  Maximize2,
  Minus,
  Network,
  Plus,
  RefreshCw,
  Route,
  Search,
  Sparkles,
  Target,
  X,
  ZoomIn,
} from 'lucide-react'
import {
  getKnowledgeGraph,
  type KnowledgeGraphData,
  type KnowledgeGraphEdge,
  type KnowledgeGraphNode,
} from '@/api/student/knowledge'
import { usePathStore } from '@/utils/store/pathStore'
import styles from './KnowledgeGraphPage.module.css'

type GraphStatus = 'mastered' | 'learning' | 'weak' | 'unlearned'
type ViewMode = 'chapter' | 'subject' | 'path'

interface GraphNode extends KnowledgeGraphNode {
  x: number
  y: number
  vx: number
  vy: number
  radius: number
  status: GraphStatus
}

const STATUS_META: Record<GraphStatus, { label: string; color: string; bg: string }> = {
  mastered: { label: '已掌握', color: '#22c55e', bg: '#ecfdf3' },
  learning: { label: '学习中', color: '#2563eb', bg: '#eff6ff' },
  weak: { label: '薄弱', color: '#f97316', bg: '#fff7ed' },
  unlearned: { label: '未学习', color: '#cbd5e1', bg: '#f8fafc' },
}

const EDGE_COLOR = '#b9c7dc'
const PATH_COLOR = '#2563eb'
const SELECTED_COLOR = '#f97316'

function getCurveControlPoint(from: GraphNode, to: GraphNode) {
  const midX = (from.x + to.x) / 2
  const midY = (from.y + to.y) / 2
  const dx = to.x - from.x
  const dy = to.y - from.y
  const distance = Math.max(Math.sqrt(dx * dx + dy * dy), 1)
  const curve = Math.min(58, Math.max(20, distance * 0.18))
  return {
    x: midX - (dy / distance) * curve,
    y: midY + (dx / distance) * curve,
  }
}

function drawSoftArrow(ctx: CanvasRenderingContext2D, from: GraphNode, to: GraphNode) {
  const control = getCurveControlPoint(from, to)
  const t = 0.84
  const endX = (1 - t) * (1 - t) * from.x + 2 * (1 - t) * t * control.x + t * t * to.x
  const endY = (1 - t) * (1 - t) * from.y + 2 * (1 - t) * t * control.y + t * t * to.y
  const tangentX = 2 * (1 - t) * (control.x - from.x) + 2 * t * (to.x - control.x)
  const tangentY = 2 * (1 - t) * (control.y - from.y) + 2 * t * (to.y - control.y)
  const angle = Math.atan2(tangentY, tangentX)
  const arrowSize = 9

  ctx.beginPath()
  ctx.moveTo(endX, endY)
  ctx.lineTo(endX - arrowSize * Math.cos(angle - 0.44), endY - arrowSize * Math.sin(angle - 0.44))
  ctx.lineTo(endX - arrowSize * Math.cos(angle + 0.44), endY - arrowSize * Math.sin(angle + 0.44))
  ctx.closePath()
  ctx.fill()
}

function getStatus(node: KnowledgeGraphNode, pathSet: Set<string>): GraphStatus {
  if (pathSet.has(node.kpId)) return 'learning'
  if ((node.difficulty ?? 0) >= 4) return 'weak'
  if ((node.difficulty ?? 0) <= 2 && node.difficulty !== undefined) return 'mastered'
  return 'unlearned'
}

function getNodeDegree(kpId: string, edges: KnowledgeGraphEdge[]) {
  return edges.reduce((count, edge) => count + (edge.fromKpId === kpId || edge.toKpId === kpId ? 1 : 0), 0)
}

function getNodeRadius(node: KnowledgeGraphNode, edges: KnowledgeGraphEdge[], pathSet: Set<string>) {
  const degree = getNodeDegree(node.kpId, edges)
  const base = 20 + Math.min(10, degree * 1.8)
  const pathBoost = pathSet.has(node.kpId) ? 5 : 0
  const difficultyBoost = (node.difficulty ?? 0) >= 4 ? 2 : 0
  return Math.max(20, Math.min(36, base + pathBoost + difficultyBoost))
}

function isFunctionDefinitionNode(node: KnowledgeGraphNode) {
  const text = `${node.title} ${node.kpId}`.toLowerCase()
  return (text.includes('函数') && text.includes('调用')) || text.includes('function')
}

function getDefaultFocusNode(nodes: GraphNode[]) {
  return nodes.find(isFunctionDefinitionNode) || nodes[0] || null
}

function getDifficultyText(value?: number) {
  if (!value) return '★★★☆☆'
  return `${'★'.repeat(Math.min(value, 5))}${'☆'.repeat(Math.max(0, 5 - value))}`
}

function shortLabel(title: string) {
  return title.length > 6 ? `${title.slice(0, 6)}…` : title
}

function getCanvasCssSize(canvas: HTMLCanvasElement) {
  const rect = canvas.getBoundingClientRect()
  return {
    width: rect.width || canvas.clientWidth || canvas.width,
    height: rect.height || canvas.clientHeight || canvas.height,
  }
}

function scaleCanvasForDpr(canvas: HTMLCanvasElement) {
  const { width, height } = getCanvasCssSize(canvas)
  const dpr = Math.max(2, window.devicePixelRatio || 1)
  const nextWidth = Math.max(1, Math.round(width * dpr))
  const nextHeight = Math.max(1, Math.round(height * dpr))
  if (canvas.width !== nextWidth) canvas.width = nextWidth
  if (canvas.height !== nextHeight) canvas.height = nextHeight
  const ctx = canvas.getContext('2d')
  ctx?.setTransform(dpr, 0, 0, dpr, 0, 0)
  return { width, height, dpr }
}

export default function KnowledgeGraphPage() {
  const navigate = useNavigate()
  const canvasRef = useRef<HTMLCanvasElement>(null)
  const miniMapRef = useRef<HTMLCanvasElement>(null)
  const nodesRef = useRef<GraphNode[]>([])
  const edgesRef = useRef<KnowledgeGraphEdge[]>([])
  const drawGraphRef = useRef<() => void>(() => undefined)
  const dragRef = useRef<{ node: GraphNode | null; offsetX: number; offsetY: number }>({ node: null, offsetX: 0, offsetY: 0 })

  const [data, setData] = useState<KnowledgeGraphData | null>(null)
  const [loading, setLoading] = useState(true)
  const [search, setSearch] = useState('')
  const [subject, setSubject] = useState('')
  const [statusFilter, setStatusFilter] = useState<GraphStatus | 'all'>('all')
  const [viewMode, setViewMode] = useState<ViewMode>('chapter')
  const [selectedNode, setSelectedNode] = useState<GraphNode | null>(null)
  const [zoom, setZoom] = useState(1)

  const pathNodes = usePathStore((state) => state.nodes)
  const selectPathNode = usePathStore((state) => state.selectNode)
  const pathSet = useMemo(() => new Set(pathNodes.map((node) => node.kpId)), [pathNodes])
  const pathIndexMap = useMemo(() => {
    const map = new Map<string, number>()
    pathNodes.forEach((node, index) => map.set(node.kpId, index))
    return map
  }, [pathNodes])

  const getVisibleNodes = useCallback(() => {
    const keyword = search.trim().toLowerCase()
    return nodesRef.current.filter((node) => {
      const matchSearch = !keyword || node.title.toLowerCase().includes(keyword) || node.kpId.toLowerCase().includes(keyword)
      const matchStatus = statusFilter === 'all' || node.status === statusFilter
      return matchSearch && matchStatus
    })
  }, [search, statusFilter])

  const selectedIncoming = useMemo(() => {
    if (!selectedNode || !data) return []
    return data.edges
      .filter((edge) => edge.toKpId === selectedNode.kpId)
      .map((edge) => data.nodes.find((node) => node.kpId === edge.fromKpId)?.title)
      .filter((title): title is string => Boolean(title))
      .slice(0, 4)
  }, [data, selectedNode])

  const selectedOutgoing = useMemo(() => {
    if (!selectedNode || !data) return []
    return data.edges
      .filter((edge) => edge.fromKpId === selectedNode.kpId)
      .map((edge) => data.nodes.find((node) => node.kpId === edge.toKpId)?.title)
      .filter((title): title is string => Boolean(title))
      .slice(0, 4)
  }, [data, selectedNode])

  const drawMiniMap = useCallback(() => {
    const canvas = miniMapRef.current
    if (!canvas) return
    const ctx = canvas.getContext('2d')
    if (!ctx) return
    const { width: w, height: h } = scaleCanvasForDpr(canvas)
    ctx.clearRect(0, 0, w, h)
    ctx.fillStyle = '#f8fbff'
    ctx.fillRect(0, 0, w, h)

    const nodes = nodesRef.current
    if (nodes.length === 0) return
    const source = canvasRef.current
    const sourceSize = source ? getCanvasCssSize(source) : null
    const sx = sourceSize ? w / sourceSize.width : 0.18
    const sy = sourceSize ? h / sourceSize.height : 0.18

    for (const edge of edgesRef.current) {
      const from = nodes.find((node) => node.kpId === edge.fromKpId)
      const to = nodes.find((node) => node.kpId === edge.toKpId)
      if (!from || !to) continue
      const control = getCurveControlPoint(from, to)
      ctx.beginPath()
      ctx.moveTo(from.x * sx, from.y * sy)
      ctx.quadraticCurveTo(control.x * sx, control.y * sy, to.x * sx, to.y * sy)
      ctx.strokeStyle = 'rgba(126, 145, 174, 0.58)'
      ctx.lineWidth = 1.15
      ctx.stroke()
    }

    for (const node of nodes) {
      ctx.beginPath()
      ctx.arc(node.x * sx, node.y * sy, node.status === 'learning' ? 3.5 : 2.6, 0, Math.PI * 2)
      ctx.fillStyle = STATUS_META[node.status].color
      ctx.fill()
    }

    ctx.strokeStyle = '#74a7ff'
    ctx.lineWidth = 2
    ctx.strokeRect(w * 0.2, h * 0.22, w * 0.58, h * 0.46)
  }, [])

  const drawGraph = useCallback(() => {
    const canvas = canvasRef.current
    if (!canvas) return
    const ctx = canvas.getContext('2d')
    if (!ctx) return
    const { width: w, height: h } = scaleCanvasForDpr(canvas)
    const centerX = w / 2
    const centerY = h / 2

    ctx.clearRect(0, 0, w, h)
    ctx.save()
    ctx.translate(centerX, centerY)
    ctx.scale(zoom, zoom)
    ctx.translate(-centerX, -centerY)

    ctx.strokeStyle = '#edf2fa'
    ctx.lineWidth = 1
    for (let r = 120; r < Math.min(w, h); r += 120) {
      ctx.beginPath()
      ctx.arc(centerX, centerY, r, 0, Math.PI * 2)
      ctx.stroke()
    }

    const visibleSet = new Set(getVisibleNodes().map((node) => node.kpId))
    const nodes = nodesRef.current
    const edges = edgesRef.current
    const focusedSet = new Set<string>()
    if (selectedNode) {
      focusedSet.add(selectedNode.kpId)
      for (const edge of edges) {
        if (edge.fromKpId === selectedNode.kpId) focusedSet.add(edge.toKpId)
        if (edge.toKpId === selectedNode.kpId) focusedSet.add(edge.fromKpId)
      }
    }
    for (const kpId of pathSet) focusedSet.add(kpId)
    const hasFocus = focusedSet.size > 0

    for (const edge of edges) {
      const from = nodes.find((node) => node.kpId === edge.fromKpId)
      const to = nodes.find((node) => node.kpId === edge.toKpId)
      if (!from || !to) continue
      if (!visibleSet.has(from.kpId) || !visibleSet.has(to.kpId)) continue

      const isPath = pathSet.has(from.kpId) && pathSet.has(to.kpId)
      const isRelated = selectedNode && (selectedNode.kpId === from.kpId || selectedNode.kpId === to.kpId)
      const fromPathIndex = pathIndexMap.get(from.kpId)
      const toPathIndex = pathIndexMap.get(to.kpId)
      const isPathAdjacent = fromPathIndex !== undefined && toPathIndex !== undefined && Math.abs(fromPathIndex - toPathIndex) === 1
      const isPrimaryPath = isPath && (isRelated || isPathAdjacent)
      const isMuted = hasFocus && !isPath && !isRelated
      const control = getCurveControlPoint(from, to)
      ctx.beginPath()
      ctx.moveTo(from.x, from.y)
      ctx.quadraticCurveTo(control.x, control.y, to.x, to.y)
      ctx.setLineDash(isPath ? [] : [4, 5])
      ctx.strokeStyle = isPrimaryPath
        ? 'rgba(29, 78, 216, 0.96)'
        : isPath
          ? 'rgba(147, 197, 253, 0.78)'
          : isRelated
            ? 'rgba(82, 132, 235, 0.78)'
            : isMuted
              ? 'rgba(128, 146, 174, 0.32)'
              : 'rgba(108, 126, 154, 0.5)'
      ctx.lineWidth = isPrimaryPath ? 2.8 : isPath ? 1.8 : isRelated ? 1.9 : isMuted ? 1.05 : 1.2
      ctx.stroke()
      ctx.setLineDash([])
      ctx.fillStyle = isPrimaryPath
        ? 'rgba(29, 78, 216, 1)'
        : isPath
          ? 'rgba(96, 165, 250, 0.82)'
          : isRelated
            ? 'rgba(82, 132, 235, 0.86)'
            : isMuted
              ? 'rgba(99, 116, 145, 0.36)'
              : 'rgba(90, 108, 136, 0.58)'
      drawSoftArrow(ctx, from, to)
    }

    for (const node of nodes) {
      if (!visibleSet.has(node.kpId)) continue
      const isSelected = selectedNode?.kpId === node.kpId
      const isPath = pathSet.has(node.kpId)
      const isFocused = focusedSet.has(node.kpId)
      const isMuted = hasFocus && !isFocused
      const meta = STATUS_META[node.status]
      const displayRadius = node.radius + (isSelected ? 9 : isPath ? 4 : isFocused ? 2 : -1)

      ctx.beginPath()
      ctx.arc(node.x, node.y, displayRadius + 5, 0, Math.PI * 2)
      ctx.fillStyle = isSelected ? 'rgba(249, 115, 22, 0.18)' : isPath ? 'rgba(37, 99, 235, 0.14)' : isMuted ? 'rgba(248, 250, 252, 0.92)' : meta.bg
      ctx.fill()

      ctx.beginPath()
      ctx.arc(node.x, node.y, displayRadius, 0, Math.PI * 2)
      ctx.fillStyle = isSelected ? SELECTED_COLOR : isMuted ? 'rgba(255, 255, 255, 0.92)' : '#ffffff'
      ctx.fill()
      ctx.strokeStyle = isSelected ? SELECTED_COLOR : isPath ? PATH_COLOR : isMuted ? 'rgba(148, 163, 184, 0.72)' : meta.color
      ctx.lineWidth = isSelected ? 3.4 : isPath ? 2.6 : isMuted ? 1.35 : 1.8
      ctx.stroke()

      ctx.fillStyle = isSelected ? '#fff' : isMuted ? 'rgba(86, 104, 130, 0.68)' : meta.color
      const fontSize = displayRadius >= 34 ? 11 : displayRadius >= 27 ? 10 : 9
      ctx.font = `700 ${fontSize}px sans-serif`
      ctx.textAlign = 'center'
      ctx.textBaseline = 'middle'
      ctx.fillText(shortLabel(node.title), node.x, node.y)
    }

    ctx.restore()
    drawMiniMap()
  }, [drawMiniMap, getVisibleNodes, pathIndexMap, pathSet, selectedNode, zoom])

  useEffect(() => {
    drawGraphRef.current = drawGraph
  }, [drawGraph])

  const runLayout = useCallback((graph: KnowledgeGraphData) => {
    const canvas = canvasRef.current
    if (!canvas) return
    const { width: w, height: h } = getCanvasCssSize(canvas)
    const centerX = w / 2
    const centerY = h / 2
    const radiusBase = Math.min(w, h) * 0.38
    const total = Math.max(graph.nodes.length, 1)

    const nodes: GraphNode[] = graph.nodes.map((node, index) => {
      const isPath = pathSet.has(node.kpId)
      const isDefaultFocus = isFunctionDefinitionNode(node)
      const chapterHash = [...(node.chapter || node.subject || node.title)].reduce((sum, char) => sum + char.charCodeAt(0), 0)
      const layer = viewMode === 'chapter' ? chapterHash % 4 : viewMode === 'path' && isPath ? 1 : index % 4
      const angle = (2 * Math.PI * index) / total + layer * 0.18
      const radius = isDefaultFocus ? 0 : viewMode === 'path' && isPath ? radiusBase * 0.72 : radiusBase * (0.68 + layer * 0.22)
      return {
        ...node,
        x: centerX + Math.cos(angle) * radius + (isDefaultFocus ? 0 : (Math.random() - 0.5) * 24),
        y: centerY + Math.sin(angle) * radius + (isDefaultFocus ? 0 : (Math.random() - 0.5) * 24),
        vx: 0,
        vy: 0,
        radius: getNodeRadius(node, graph.edges, pathSet),
        status: getStatus(node, pathSet),
      }
    })

    for (let iteration = 0; iteration < 220; iteration += 1) {
      for (const node of nodes) {
        node.vx = 0
        node.vy = 0
      }

      for (let i = 0; i < nodes.length; i += 1) {
        for (let j = i + 1; j < nodes.length; j += 1) {
          const a = nodes[i]
          const b = nodes[j]
          const dx = b.x - a.x
          const dy = b.y - a.y
          const dist = Math.max(Math.sqrt(dx * dx + dy * dy), 1)
          const force = 7600 / (dist * dist)
          a.vx -= (dx / dist) * force
          a.vy -= (dy / dist) * force
          b.vx += (dx / dist) * force
          b.vy += (dy / dist) * force
        }
      }

      for (const edge of graph.edges) {
        const from = nodes.find((node) => node.kpId === edge.fromKpId)
        const to = nodes.find((node) => node.kpId === edge.toKpId)
        if (!from || !to) continue
        const dx = to.x - from.x
        const dy = to.y - from.y
        const dist = Math.max(Math.sqrt(dx * dx + dy * dy), 1)
        const target = pathSet.has(from.kpId) && pathSet.has(to.kpId) ? 152 : 174
        const force = (dist - target) * 0.01
        from.vx += (dx / dist) * force
        from.vy += (dy / dist) * force
        to.vx -= (dx / dist) * force
        to.vy -= (dy / dist) * force
      }

      for (const node of nodes) {
        if (isFunctionDefinitionNode(node)) {
          node.x += (centerX - node.x) * 0.08
          node.y += (centerY - node.y) * 0.08
        }
        node.vx += (centerX - node.x) * 0.0008
        node.vy += (centerY - node.y) * 0.0008
        node.x = Math.max(56, Math.min(w - 56, node.x + node.vx * 0.46))
        node.y = Math.max(56, Math.min(h - 56, node.y + node.vy * 0.46))
      }
    }

    nodesRef.current = nodes
    edgesRef.current = graph.edges
    setSelectedNode((prev) => {
      const selectedStillExists = prev && nodes.find((node) => node.kpId === prev.kpId)
      return selectedStillExists || getDefaultFocusNode(nodes) || nodes.find((node) => pathSet.has(node.kpId)) || nodes[0] || null
    })
    requestAnimationFrame(() => drawGraphRef.current())
  }, [pathSet, viewMode])

  const resizeCanvas = useCallback(() => {
    const canvas = canvasRef.current
    const miniMap = miniMapRef.current
    if (!canvas) return
    const parent = canvas.parentElement
    if (!parent) return
    scaleCanvasForDpr(canvas)
    if (miniMap) {
      scaleCanvasForDpr(miniMap)
    }
    if (data) runLayout(data)
  }, [data, runLayout])

  useEffect(() => {
    setLoading(true)
    getKnowledgeGraph(subject || undefined)
      .then((graph) => {
        setData(graph)
        setTimeout(() => runLayout(graph), 0)
      })
      .catch(() => setData(null))
      .finally(() => setLoading(false))
  }, [subject])

  useEffect(() => {
    if (data) runLayout(data)
  }, [data, viewMode])

  useEffect(() => {
    resizeCanvas()
    window.addEventListener('resize', resizeCanvas)
    return () => window.removeEventListener('resize', resizeCanvas)
  }, [resizeCanvas])

  useEffect(() => {
    drawGraph()
  }, [drawGraph])

  const toCanvasPoint = (event: React.MouseEvent<HTMLCanvasElement>) => {
    const canvas = canvasRef.current
    if (!canvas) return null
    const rect = canvas.getBoundingClientRect()
    const rawX = event.clientX - rect.left
    const rawY = event.clientY - rect.top
    return {
      x: (rawX - rect.width / 2) / zoom + rect.width / 2,
      y: (rawY - rect.height / 2) / zoom + rect.height / 2,
    }
  }

  const pickNode = (x: number, y: number) => {
    const visibleSet = new Set(getVisibleNodes().map((node) => node.kpId))
    for (const node of [...nodesRef.current].reverse()) {
      if (!visibleSet.has(node.kpId)) continue
      const dx = x - node.x
      const dy = y - node.y
      if (dx * dx + dy * dy <= (node.radius + 6) * (node.radius + 6)) return node
    }
    return null
  }

  const handleCanvasClick = (event: React.MouseEvent<HTMLCanvasElement>) => {
    const point = toCanvasPoint(event)
    if (!point) return
    setSelectedNode(pickNode(point.x, point.y))
  }

  const handleMouseDown = (event: React.MouseEvent<HTMLCanvasElement>) => {
    const point = toCanvasPoint(event)
    if (!point) return
    const node = pickNode(point.x, point.y)
    if (node) {
      dragRef.current = { node, offsetX: point.x - node.x, offsetY: point.y - node.y }
    }
  }

  const handleMouseMove = (event: React.MouseEvent<HTMLCanvasElement>) => {
    const dragged = dragRef.current.node
    if (!dragged) return
    const point = toCanvasPoint(event)
    if (!point) return
    dragged.x = point.x - dragRef.current.offsetX
    dragged.y = point.y - dragRef.current.offsetY
    drawGraph()
  }

  const handleMouseUp = () => {
    dragRef.current.node = null
  }

  const chooseSearchResult = (node: KnowledgeGraphNode) => {
    const graphNode = nodesRef.current.find((item) => item.kpId === node.kpId)
    if (graphNode) setSelectedNode(graphNode)
    setSearch('')
  }

  const searchResults = search.trim()
    ? (data?.nodes.filter((node) => node.title.toLowerCase().includes(search.toLowerCase()) || node.kpId.toLowerCase().includes(search.toLowerCase())).slice(0, 8) ?? [])
    : []

  const subjects = data?.subjects ?? []
  const masteryCounts = nodesRef.current.reduce<Record<GraphStatus, number>>((acc, node) => {
    acc[node.status] += 1
    return acc
  }, { mastered: 0, learning: 0, weak: 0, unlearned: 0 })

  return (
    <section className={styles.page}>
      <header className={styles.header}>
        <div>
          <h1>知识图谱</h1>
          <p>可视化展示知识点之间的依赖关系</p>
        </div>
        <strong>{data ? `${data.nodeCount} 知识点 · ${data.edgeCount} 关系` : '加载中'}</strong>
      </header>

      <div className={styles.toolbar}>
        <div className={styles.searchBox}>
          <Search size={15} />
          <input value={search} onChange={(event) => setSearch(event.target.value)} placeholder="搜索知识点..." />
          {searchResults.length > 0 && (
            <div className={styles.searchResults}>
              {searchResults.map((node) => (
                <button key={node.kpId} type="button" onClick={() => chooseSearchResult(node)}>
                  {node.title}
                  <ChevronRight size={14} />
                </button>
              ))}
            </div>
          )}
        </div>

        <div className={styles.filterGroup}>
          <button type="button" className={statusFilter === 'all' ? styles.filterActive : styles.filterButton} onClick={() => setStatusFilter('all')}>全部</button>
          {(Object.keys(STATUS_META) as GraphStatus[]).map((status) => (
            <button key={status} type="button" className={statusFilter === status ? styles.filterActive : styles.filterButton} onClick={() => setStatusFilter(status)}>
              <span style={{ background: STATUS_META[status].color }} />
              {STATUS_META[status].label}
            </button>
          ))}
          <button type="button" className={styles.filterButton} onClick={() => setStatusFilter('learning')}>
            <Route size={14} />
            学习路径
          </button>
        </div>

        <div className={styles.subjectGroup}>
          {subjects.length > 0 && (
            <select value={subject} onChange={(event) => setSubject(event.target.value)} aria-label="选择主题">
              <option value="">全部主题</option>
              {subjects.map((item) => <option key={item} value={item}>{item}</option>)}
            </select>
          )}
          {(['chapter', 'subject', 'path'] as ViewMode[]).map((mode) => (
            <button key={mode} type="button" className={viewMode === mode ? styles.modeActive : styles.modeButton} onClick={() => setViewMode(mode)}>
              {mode === 'chapter' ? '按章节' : mode === 'subject' ? '按主题' : '路径模式'}
            </button>
          ))}
        </div>
      </div>

      <main className={styles.layout}>
        <section className={styles.graphPanel}>
          <div className={styles.legendCard}>
            {(Object.keys(STATUS_META) as GraphStatus[]).map((status) => (
              <span key={status}>
                <i style={{ background: STATUS_META[status].color }} />
                {STATUS_META[status].label}
                <em>{masteryCounts[status] || 0}</em>
              </span>
            ))}
            <span><b className={styles.solidLine} />主干路径</span>
            <span><b className={styles.softLine} />关联路径</span>
            <span><b className={styles.dashedLine} />依赖关系</span>
          </div>

          <div className={styles.canvasWrap}>
            {loading ? (
              <div className={styles.loadingState}>
                <RefreshCw className={styles.spin} size={22} />
                加载知识图谱...
              </div>
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

          <div className={styles.miniMap}>
            <canvas ref={miniMapRef} />
          </div>

          <div className={styles.graphHint}>拖拽画布节点 · 滚轮缩放由下方按钮控制 · 点击节点查看详情 · 双击聚焦路径</div>

          <div className={styles.zoomControls}>
            <button type="button" onClick={() => setZoom((value) => Math.min(1.8, value + 0.12))}><Plus size={16} /></button>
            <button type="button" onClick={() => setZoom((value) => Math.max(0.65, value - 0.12))}><Minus size={16} /></button>
            <button type="button" onClick={() => setZoom(1)}><Crosshair size={16} /></button>
          </div>

          <div className={styles.bottomControls}>
            <button type="button" onClick={() => setZoom(1)}><Target size={16} />适应屏幕</button>
            <button type="button" onClick={() => data && runLayout(data)}><RefreshCw size={16} />重置布局</button>
            <button type="button" onClick={() => setZoom(1.18)}><Maximize2 size={16} />全屏</button>
          </div>
        </section>

        <aside className={styles.detailPanel}>
          {selectedNode ? (
            <>
              <div className={styles.detailHead}>
                <div>
                  <h2>{selectedNode.title}</h2>
                  <p>章节：{selectedNode.chapter || selectedNode.subject || '未分组'}</p>
                </div>
                <button type="button" onClick={() => setSelectedNode(null)}><X size={18} /></button>
              </div>

              <div className={styles.detailInfo}>
                <span>难度：<strong>{getDifficultyText(selectedNode.difficulty)}</strong></span>
                <span>所属模块：<strong>{selectedNode.subject || '通用知识'}</strong></span>
                <span>学习进度：<strong>{STATUS_META[selectedNode.status].label}</strong></span>
              </div>

              <p className={styles.detailDesc}>{selectedNode.description || '该知识点用于串联核心概念、前置依赖与后续练习，是当前学习路径中的重要节点。'}</p>

              <div className={styles.tagList}>
                {[...selectedIncoming, ...selectedOutgoing].slice(0, 5).map((tag) => <span key={tag}>{tag}</span>)}
                {[...selectedIncoming, ...selectedOutgoing].length === 0 && <span>暂无关联知识点</span>}
              </div>

              <div className={styles.progressBox}>
                <span>学习进度：</span>
                <div><i style={{ width: `${selectedNode.status === 'mastered' ? 92 : selectedNode.status === 'learning' ? 68 : selectedNode.status === 'weak' ? 35 : 12}%` }} /></div>
                <strong>{selectedNode.status === 'mastered' ? '92%' : selectedNode.status === 'learning' ? '68%' : selectedNode.status === 'weak' ? '35%' : '12%'}</strong>
              </div>

              <div className={styles.primaryActions}>
                <button type="button" onClick={() => navigate('/workspace/resources')}><BookOpen size={16} />查看资源</button>
                <button type="button" onClick={() => navigate('/workspace/quiz')}><Sparkles size={16} />开始练习</button>
                <button
                  type="button"
                  onClick={() => {
                    selectPathNode(selectedNode.kpId)
                    navigate('/workspace/path')
                  }}
                >
                  <GitBranch size={16} />加入学习路径
                </button>
              </div>

              <div className={styles.tabs}>
                <button type="button" className={styles.tabActive}>概览</button>
                <button type="button">前置知识点</button>
                <button type="button">学习资源</button>
                <button type="button">练习建议</button>
              </div>

              <div className={styles.summaryList}>
                <article>
                  <Eye size={16} />
                  <div>
                    <h3>知识点说明</h3>
                    <p>{selectedNode.description || '掌握该知识点可帮助理解后续模块的结构、应用方式与调用场景。'}</p>
                  </div>
                </article>
                <article>
                  <Network size={16} />
                  <div>
                    <h3>理解要点</h3>
                    <p>前置：{selectedIncoming.join('、') || '无'}；后续：{selectedOutgoing.join('、') || '待扩展'}。</p>
                  </div>
                </article>
              </div>
            </>
          ) : (
            <div className={styles.emptyDetail}>
              <Network size={34} />
              <h2>选择一个知识点</h2>
              <p>点击图谱中的节点，查看章节、前置依赖、学习进度和资源入口。</p>
            </div>
          )}
        </aside>
      </main>
    </section>
  )
}
