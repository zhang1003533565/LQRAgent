import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { MoreHorizontal, RefreshCw } from 'lucide-react'
import {
  GraphControls,
  GraphLegend,
  KnowledgeDetailPanel,
  KnowledgeGraphCanvas,
  KnowledgeGraphToolbar,
} from '@/components/student/knowledge-graph'
import { useGraphFocusMode } from '@/hooks/useGraphFocusMode'
import { useGraphFocusTransition } from '@/hooks/useGraphFocusTransition'
import { useGraphLabels } from '@/hooks/useGraphLabels'
import { useGraphLayout } from '@/hooks/useGraphLayout'
import { useGraphViewport } from '@/hooks/useGraphViewport'
import { useKnowledgeDetail } from '@/hooks/useKnowledgeDetail'
import { useKnowledgeGraphData } from '@/hooks/useKnowledgeGraphData'
import { useKnowledgeGraphFilters } from '@/hooks/useKnowledgeGraphFilters'
import type { GraphEdge, GraphHighlightState, GraphNode, LayoutNode } from '@/types/knowledgeGraph'
import { IMPORTANCE_LABEL, STATUS_META } from '@/utils/knowledgeGraph/graphColors'
import { getNodeDisplayName } from '@/utils/knowledgeGraph/graphLabels'
import { pickLayoutCenterNode, type LayoutBoundsOptions, DETAIL_PANEL_GAP, DETAIL_PANEL_WIDTH } from '@/utils/knowledgeGraph/graphLayout'
import { syncWorkspaceFromSearchParams } from '@/utils/navigation/workspaceNav'
import styles from './KnowledgeGraphPage.module.css'

export default function KnowledgeGraphPage() {
  const navigate = useNavigate()
  const [searchParams, setSearchParams] = useSearchParams()
  const graphAreaRef = useRef<HTMLDivElement>(null)
  const graphPanelRef = useRef<HTMLElement>(null)
  const searchInputRef = useRef<HTMLInputElement>(null)
  const pendingKpIdRef = useRef<string | null>(null)
  const initialFocusDoneRef = useRef(false)
  const defaultSelectionDoneRef = useRef(false)

  const [subject, setSubject] = useState('')
  const [selectedNode, setSelectedNode] = useState<LayoutNode | null>(null)
  const [detailOpen, setDetailOpen] = useState(false)
  const [hoveredNode, setHoveredNode] = useState<LayoutNode | null>(null)
  const [hoveredEdge, setHoveredEdge] = useState<GraphEdge | null>(null)
  const [quizStarting, setQuizStarting] = useState(false)
  const [mousePos, setMousePos] = useState({ x: 0, y: 0 })
  const [fitRequestId, setFitRequestId] = useState(0)

  const {
    nodes,
    edges,
    stats,
    loading,
    error,
    refetch,
    pathSet,
    currentLearningId,
  } = useKnowledgeGraphData(subject)

  const {
    search,
    setSearch,
    statusFilter,
    setStatusFilter,
    edgeFilter,
    setEdgeFilter,
    viewMode,
    setViewMode,
    filtersExpanded,
    setFiltersExpanded,
    visibleIds,
    visibleEdges,
    searchHighlightIds,
    searchResults,
    graphVisibility,
  } = useKnowledgeGraphFilters(nodes, edges, pathSet, {
    selectedNodeId: selectedNode?.id ?? null,
    currentLearningId,
  })

  const focusView = useGraphFocusMode(nodes, graphVisibility, {
    viewMode,
    selectedId: selectedNode?.id ?? null,
    currentLearningId,
  })

  const animatedFocusView = useGraphFocusTransition(
    focusView,
    visibleEdges.length > 0 ? visibleEdges : edges,
    selectedNode?.id ?? null,
    hoveredNode?.id ?? null,
  )

  const focusBoundsOptions = useMemo<LayoutBoundsOptions>(
    () => ({
      detailPanelOpen: false,
      focusPanelOffset: detailOpen ? (DETAIL_PANEL_WIDTH + DETAIL_PANEL_GAP) / 2 : 0,
    }),
    [detailOpen],
  )

  const layoutBoundsOptions = useMemo<LayoutBoundsOptions>(
    () => ({ detailPanelOpen: false }),
    [],
  )

  const { viewport, setViewport, zoomIn, zoomOut, resetZoom, fitToNodes, focusNode, panBy, zoomAtPoint } =
    useGraphViewport()

  const handleLayoutComplete = useCallback(
    (layoutResult: LayoutNode[], size: { width: number; height: number }) => {
      if (layoutResult.length === 0 || size.width <= 0 || size.height <= 0) return

      const urlKpId = pendingKpIdRef.current ?? searchParams.get('kpId')
      const shouldFocusKp = !initialFocusDoneRef.current && urlKpId

      if (shouldFocusKp) {
        const node = layoutResult.find((n) => n.id === urlKpId)
        if (node) {
          focusNode(node, size.width, size.height, 1.08, focusBoundsOptions, false)
          setSelectedNode(node)
          setDetailOpen(true)
          pendingKpIdRef.current = null
          initialFocusDoneRef.current = true
          return
        }
      }

      const nodesToFit = viewMode === 'full'
        ? layoutResult
        : layoutResult.filter((n) => visibleIds.has(n.id))

      fitToNodes(
        nodesToFit.length > 0 ? nodesToFit : layoutResult,
        size.width,
        size.height,
        layoutBoundsOptions,
        true,
      )
      setFitRequestId((id) => id + 1)
      initialFocusDoneRef.current = true
    },
    [fitToNodes, focusNode, searchParams, viewMode, visibleIds, focusBoundsOptions, layoutBoundsOptions],
  )

  const { layoutNodes, zoneRects, runLayout } = useGraphLayout(
    nodes,
    edges,
    viewMode,
    graphAreaRef,
    handleLayoutComplete,
    {
      currentLearningId,
      selectedNodeId: selectedNode?.id ?? null,
      pathSet,
    },
  )

  const layoutFitNodes = useMemo(() => {
    const focused = layoutNodes.filter((n) => visibleIds.has(n.id))
    return focused.length > 0 ? focused : layoutNodes
  }, [layoutNodes, visibleIds])

  const visibleIdKey = useMemo(
    () => (visibleIds.size > 0 ? [...visibleIds].sort().join('|') : '*'),
    [visibleIds],
  )

  const { labelByNodeId } = useGraphLabels(
    layoutNodes,
    viewport.zoom,
    selectedNode?.id ?? null,
    hoveredNode?.id ?? null,
    currentLearningId,
    visibleIds,
    visibleIdKey,
  )

  const {
    activeTab,
    setActiveTab,
    resources,
    quizResources,
    detailLoading,
  } = useKnowledgeDetail(detailOpen ? selectedNode : null)

  useEffect(() => {
    syncWorkspaceFromSearchParams(searchParams)
    const kpId = searchParams.get('kpId')
    if (kpId) pendingKpIdRef.current = kpId
  }, [searchParams])

  useEffect(() => {
    const onKeyDown = (e: KeyboardEvent) => {
      if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === 'k') {
        e.preventDefault()
        searchInputRef.current?.focus()
      }
    }
    window.addEventListener('keydown', onKeyDown)
    return () => window.removeEventListener('keydown', onKeyDown)
  }, [])

  useEffect(() => {
    initialFocusDoneRef.current = false
    defaultSelectionDoneRef.current = false
  }, [viewMode, nodes.length])

  useEffect(() => {
    if (layoutNodes.length === 0 || defaultSelectionDoneRef.current) return

    const urlKpId = searchParams.get('kpId') ?? pendingKpIdRef.current
    if (urlKpId) {
      const fromUrl = layoutNodes.find((n) => n.id === urlKpId)
      if (fromUrl) {
        setSelectedNode(fromUrl)
        defaultSelectionDoneRef.current = true
        return
      }
    }

    if (currentLearningId) {
      const current = layoutNodes.find((n) => n.id === currentLearningId)
      if (current) {
        setSelectedNode(current)
        defaultSelectionDoneRef.current = true
        return
      }
    }

    const center = pickLayoutCenterNode(layoutNodes, { currentLearningId })
    if (center) {
      const layoutCenter = layoutNodes.find((n) => n.id === center.id)
      if (layoutCenter) {
        setSelectedNode(layoutCenter)
        defaultSelectionDoneRef.current = true
      }
    }
  }, [layoutNodes, currentLearningId, searchParams])

  const focusKpNode = useCallback(
    (kpId: string, openPanel = true) => {
      const node = layoutNodes.find((n) => n.id === kpId)
      if (!node) return
      setSelectedNode(node)
      if (openPanel) setDetailOpen(true)
      const container = graphAreaRef.current
      if (container) {
        const rect = container.getBoundingClientRect()
        focusNode(node, rect.width, rect.height, 1.08, focusBoundsOptions)
      }
      setSearchParams((prev) => {
        const next = new URLSearchParams(prev)
        next.set('kpId', kpId)
        return next
      }, { replace: true })
    },
    [layoutNodes, focusNode, setSearchParams, focusBoundsOptions],
  )

  useEffect(() => {
    if (!selectedNode) return
    const updated = layoutNodes.find((n) => n.id === selectedNode.id)
    if (updated) setSelectedNode(updated)
  }, [layoutNodes, selectedNode?.id])

  useEffect(() => {
    if (layoutNodes.length === 0) return
    setFitRequestId((id) => id + 1)
  }, [viewMode, layoutNodes.length])

  const handleSelectNode = useCallback(
    (node: LayoutNode | null) => {
      if (node) {
        setSelectedNode(node)
        setDetailOpen(true)
        const container = graphAreaRef.current
        if (container) {
          const rect = container.getBoundingClientRect()
          focusNode(node, rect.width, rect.height, 1.08, focusBoundsOptions)
        }
        setSearchParams((prev) => {
          const next = new URLSearchParams(prev)
          next.set('kpId', node.id)
          return next
        }, { replace: true })
      } else {
        setSelectedNode(null)
        setDetailOpen(false)
        setSearchParams((prev) => {
          const next = new URLSearchParams(prev)
          next.delete('kpId')
          return next
        }, { replace: true })
      }
    },
    [setSearchParams, focusNode, focusBoundsOptions],
  )

  const handleDoubleClickNode = useCallback(
    (node: LayoutNode) => {
      setSelectedNode(node)
      setDetailOpen(true)
      const container = graphAreaRef.current
      if (container) {
        const rect = container.getBoundingClientRect()
        focusNode(node, rect.width, rect.height, 1.12, focusBoundsOptions)
      }
    },
    [focusNode, focusBoundsOptions],
  )

  const handleCloseDetail = useCallback(() => {
    setDetailOpen(false)
    setSelectedNode(null)
    setSearchParams((prev) => {
      const next = new URLSearchParams(prev)
      next.delete('kpId')
      return next
    }, { replace: true })
  }, [setSearchParams])

  const handleSearchSelect = useCallback(
    (node: GraphNode) => {
      focusKpNode(node.id)
      setSearch('')
    },
    [focusKpNode, setSearch],
  )

  const handleFitView = useCallback(() => {
    if (layoutNodes.length === 0) return
    const container = graphAreaRef.current
    if (container) {
      const rect = container.getBoundingClientRect()
      fitToNodes(layoutFitNodes, rect.width, rect.height, layoutBoundsOptions)
    }
    setFitRequestId((id) => id + 1)
  }, [fitToNodes, layoutNodes, layoutFitNodes, layoutBoundsOptions])

  const handleResetLayout = useCallback(() => {
    initialFocusDoneRef.current = false
    runLayout()
  }, [runLayout])

  const handleToggleFullscreen = useCallback(() => {
    const el = graphPanelRef.current
    if (!el) return
    if (document.fullscreenElement) {
      void document.exitFullscreen()
    } else {
      void el.requestFullscreen()
    }
  }, [])

  const highlight: GraphHighlightState = useMemo(
    () => ({
      selectedId: selectedNode?.id ?? null,
      hoveredId: hoveredNode?.id ?? null,
      hoveredEdgeId: hoveredEdge?.id ?? null,
      searchHighlightIds,
      currentLearningId,
      displayMode: viewMode,
    }),
    [selectedNode, hoveredNode, hoveredEdge, searchHighlightIds, currentLearningId, viewMode],
  )

  const nodeTooltip = useMemo(() => {
    if (!hoveredNode) return null
    const meta = STATUS_META[hoveredNode.status]
    const isCurrent = hoveredNode.id === currentLearningId
    const container = graphAreaRef.current
    const canvasW = container?.clientWidth ?? 960
    const canvasH = container?.clientHeight ?? 640
    const tooltipW = 232
    const tooltipH = 92
    let x = mousePos.x + 14
    let y = mousePos.y + 14
    if (x + tooltipW > canvasW - 8) x = Math.max(8, mousePos.x - tooltipW - 12)
    if (y + tooltipH > canvasH - 8) y = Math.max(8, mousePos.y - tooltipH - 12)
    if (x < 8) x = 8
    if (y < 8) y = 8

    return {
      x,
      y,
      title: getNodeDisplayName(hoveredNode),
      rows: [
        { icon: 'status' as const, text: `${meta.label} · 掌握度 ${hoveredNode.masteryRate}%` },
        { icon: 'link' as const, text: `${IMPORTANCE_LABEL[hoveredNode.importanceLevel]} · 连接 ${hoveredNode.degree}` },
        ...(isCurrent ? [{ icon: 'route' as const, text: '当前学习节点' }] : []),
        ...(!isCurrent && hoveredNode.isLearningPathNode
          ? [{ icon: 'route' as const, text: '学习路径节点' }]
          : []),
      ],
    }
  }, [hoveredNode, mousePos, currentLearningId])

  const edgeTooltip = useMemo(() => {
    if (!hoveredEdge || hoveredNode) return null
    const from = layoutNodes.find((n) => n.id === hoveredEdge.source)
    const to = layoutNodes.find((n) => n.id === hoveredEdge.target)
    if (!from || !to) return null
    const container = graphAreaRef.current
    const canvasW = container?.clientWidth ?? 960
    const canvasH = container?.clientHeight ?? 640
    const tooltipW = 260
    const tooltipH = 88
    let x = mousePos.x + 14
    let y = mousePos.y + 14
    if (x + tooltipW > canvasW - 8) x = Math.max(8, mousePos.x - tooltipW - 12)
    if (y + tooltipH > canvasH - 8) y = Math.max(8, mousePos.y - tooltipH - 12)

    const relationLabel =
      hoveredEdge.displayCategory === 'path_main' ? '主路径'
        : hoveredEdge.displayCategory === 'prerequisite' ? '前置依赖'
          : hoveredEdge.displayCategory === 'path_segment' ? '路径关联'
            : '普通关联'

    return {
      x,
      y,
      title: `${getNodeDisplayName(from)} → ${getNodeDisplayName(to)}`,
      rows: [
        { icon: 'link' as const, text: `关系 · ${relationLabel}` },
        ...(hoveredEdge.label ? [{ icon: 'info' as const, text: hoveredEdge.label }] : []),
      ],
    }
  }, [hoveredEdge, hoveredNode, layoutNodes, mousePos])

  const activeTooltip = edgeTooltip ?? nodeTooltip

  const trackMouse = useCallback((e: React.MouseEvent) => {
    const rect = graphAreaRef.current?.getBoundingClientRect()
    if (!rect) return
    setMousePos({ x: e.clientX - rect.left, y: e.clientY - rect.top })
  }, [])

  const subjects = stats?.subjects ?? []

  if (error && !loading && nodes.length === 0) {
    return (
      <section className={styles.page}>
        <div className={styles.errorState}>
          <h2>加载失败</h2>
          <p>{error}</p>
          <p className={styles.errorHint}>
            请先在项目目录启动后端：<code>mvn spring-boot:run</code>（端口 8080），并确认 MySQL 已运行，然后点击重试。
          </p>
          <button type="button" onClick={() => void refetch()}>重试</button>
          <button type="button" onClick={() => navigate('/workspace/learning-path')}>生成学习路径</button>
        </div>
      </section>
    )
  }

  if (!loading && nodes.length === 0) {
    return (
      <section className={styles.page}>
        <div className={styles.errorState}>
          <h2>暂无知识图谱数据</h2>
          <p>请先创建学习路径或上传学习资料。</p>
          <button type="button" onClick={() => navigate('/workspace/learning-path')}>生成学习路径</button>
          <button type="button" onClick={() => navigate('/workspace/upload')}>上传资料</button>
          <button type="button" onClick={() => navigate('/workspace')}>继续学习</button>
        </div>
      </section>
    )
  }

  return (
    <section className={styles.page}>
      <header className={styles.header}>
        <div>
          <h1>知识图谱</h1>
          <p>探索知识点依赖网络，聚焦当前学习</p>
        </div>
        <div className={styles.headerRight}>
          <strong>
            {stats ? `${stats.nodeCount} 知识点 · ${stats.edgeCount} 关系` : loading ? '加载中…' : ''}
          </strong>
          <button type="button" className={styles.headerIconBtn} onClick={() => void refetch()} aria-label="刷新">
            <RefreshCw size={16} />
          </button>
          <button type="button" className={styles.headerIconBtn} aria-label="更多">
            <MoreHorizontal size={16} />
          </button>
        </div>
      </header>

      <main className={styles.layout}>
        <section ref={graphPanelRef} className={styles.graphPanel}>
          <KnowledgeGraphToolbar
            searchInputRef={searchInputRef}
            search={search}
            onSearchChange={setSearch}
            searchResults={searchResults}
            onSelectSearchResult={handleSearchSelect}
            statusFilter={statusFilter}
            onStatusFilterChange={setStatusFilter}
            edgeFilter={edgeFilter}
            onEdgeFilterChange={setEdgeFilter}
            viewMode={viewMode}
            onViewModeChange={setViewMode}
            filtersExpanded={filtersExpanded}
            onFiltersExpandedChange={setFiltersExpanded}
            subjects={subjects}
            subject={subject}
            onSubjectChange={setSubject}
          />

          <GraphLegend />

          <div ref={graphAreaRef} onMouseMove={trackMouse} className={styles.graphStage}>
            <KnowledgeGraphCanvas
              layoutNodes={layoutNodes}
              edges={visibleEdges.length > 0 ? visibleEdges : edges}
              viewport={viewport}
              highlight={highlight}
              focusView={animatedFocusView}
              visibleIds={visibleIds}
              loading={loading}
              fitRequestId={fitRequestId}
              fitNodes={layoutFitNodes}
              layoutBoundsOptions={layoutBoundsOptions}
              zoneRects={zoneRects}
              hiddenByZone={graphVisibility.hiddenByZone}
              onViewportChange={setViewport}
              onSelectNode={handleSelectNode}
              onHoverNode={setHoveredNode}
              onHoverEdge={setHoveredEdge}
              onPanBy={panBy}
              onZoomAtPoint={zoomAtPoint}
              onDoubleClickNode={handleDoubleClickNode}
              labelByNodeId={labelByNodeId}
              tooltip={activeTooltip}
            />
          </div>

          <GraphControls
            onFitView={handleFitView}
            onResetLayout={handleResetLayout}
            onToggleFullscreen={handleToggleFullscreen}
            onZoomIn={zoomIn}
            onZoomOut={zoomOut}
            onResetZoom={resetZoom}
          />

          <p className={styles.graphHint}>
            关系分区知识网络 · 拖拽平移 · 滚轮缩放 · 点击节点查看详情
          </p>

          {selectedNode && detailOpen ? (
            <KnowledgeDetailPanel
              selectedNode={selectedNode}
              edges={edges}
              allNodes={layoutNodes}
              activeTab={activeTab}
              onTabChange={setActiveTab}
              onClose={handleCloseDetail}
              onFocusNode={(kpId) => focusKpNode(kpId, true)}
              resources={resources}
              quizResources={quizResources}
              detailLoading={detailLoading}
              quizStarting={quizStarting}
              onQuizStartingChange={setQuizStarting}
              isCurrentLearning={selectedNode.id === currentLearningId}
            />
          ) : null}
        </section>
      </main>
    </section>
  )
}
