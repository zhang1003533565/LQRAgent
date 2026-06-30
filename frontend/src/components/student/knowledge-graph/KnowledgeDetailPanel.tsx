import { useMemo } from 'react'
import { useNavigate } from 'react-router-dom'
import {
  AlertCircle,
  BookOpen,
  Bot,
  CheckCircle2,
  Eye,
  GitBranch,
  Network,
  Sparkles,
  X,
} from 'lucide-react'
import type { GraphEdge, LayoutNode } from '@/types/knowledgeGraph'
import { generatePracticeFromPath } from '@/services/quizService'
import { usePathStore } from '@/utils/store/pathStore'
import { buildWorkspaceSearch, navigateToWorkspace } from '@/utils/navigation/workspaceNav'
import {
  getDependents,
  getPrerequisites,
  getNeighborIds,
} from '@/utils/knowledgeGraph/graphRelations'
import {
  getDifficultyText,
  IMPORTANCE_LABEL,
  STATUS_META,
} from '@/utils/knowledgeGraph/graphColors'
import { getNodeDisplayName } from '@/utils/knowledgeGraph/graphLabels'
import type { DetailTab } from '@/hooks/useKnowledgeDetail'
import type { LearningResource } from '@/utils/types/media-resource'
import styles from '@/pages/student/KnowledgeGraphPage.module.css'

interface KnowledgeDetailPanelProps {
  selectedNode: LayoutNode | null
  edges: GraphEdge[]
  allNodes: LayoutNode[]
  activeTab: DetailTab
  onTabChange: (tab: DetailTab) => void
  onClose: () => void
  onFocusNode: (kpId: string) => void
  resources: LearningResource[]
  quizResources: LearningResource[]
  detailLoading: boolean
  quizStarting: boolean
  onQuizStartingChange: (v: boolean) => void
  isCurrentLearning?: boolean
}

export default function KnowledgeDetailPanel({
  selectedNode,
  edges,
  allNodes,
  activeTab,
  onTabChange,
  onClose,
  onFocusNode,
  resources,
  quizResources,
  detailLoading,
  quizStarting,
  onQuizStartingChange,
  isCurrentLearning = false,
}: KnowledgeDetailPanelProps) {
  const navigate = useNavigate()
  const selectPathNode = usePathStore((s) => s.selectNode)

  const prereqIds = useMemo(
    () => (selectedNode ? getPrerequisites(selectedNode.id, edges) : []),
    [selectedNode, edges],
  )
  const dependentIds = useMemo(
    () => (selectedNode ? getDependents(selectedNode.id, edges) : []),
    [selectedNode, edges],
  )

  const prereqNodes = useMemo(
    () => prereqIds.map((id) => allNodes.find((n) => n.id === id)).filter(Boolean) as LayoutNode[],
    [prereqIds, allNodes],
  )
  const dependentNodes = useMemo(
    () => dependentIds.map((id) => allNodes.find((n) => n.id === id)).filter(Boolean) as LayoutNode[],
    [dependentIds, allNodes],
  )
  const relatedNodes = useMemo(() => [...prereqNodes, ...dependentNodes], [prereqNodes, dependentNodes])

  const sameModuleNodes = useMemo(() => {
    if (!selectedNode) return []
    const moduleKey = selectedNode.chapterId || selectedNode.moduleName
    if (!moduleKey) return []
    return allNodes.filter(
      (n) => n.id !== selectedNode.id
        && (n.chapterId === moduleKey || n.moduleName === selectedNode.moduleName)
        && !prereqIds.includes(n.id)
        && !dependentIds.includes(n.id),
    )
  }, [selectedNode, allNodes, prereqIds, dependentIds])

  const weakRelatedNodes = useMemo(() => {
    if (!selectedNode) return []
    const neighbors = getNeighborIds(selectedNode.id, edges)
    return allNodes.filter(
      (n) => neighbors.has(n.id) && n.status === 'weak' && n.id !== selectedNode.id,
    )
  }, [selectedNode, edges, allNodes])

  const renderRelationGroup = (
    title: string,
    items: LayoutNode[],
  ) => {
    if (items.length === 0) return null
    return (
      <div className={styles.relationGroup}>
        <h4 className={styles.subHeading}>{title}</h4>
        <div className={styles.linkList}>
          {items.map((node) => (
            <button key={node.id} type="button" className={styles.linkItem} onClick={() => onFocusNode(node.id)}>
              <span>{getNodeDisplayName(node)}</span>
              <span className={styles.linkMeta}>
                {STATUS_META[node.status].label} · {IMPORTANCE_LABEL[node.importanceLevel]}
              </span>
            </button>
          ))}
        </div>
      </div>
    )
  }

  if (!selectedNode) return null

  const meta = STATUS_META[selectedNode.status]

  const handleStartQuiz = async () => {
    if (quizStarting) return
    onQuizStartingChange(true)
    try {
      const session = await generatePracticeFromPath(selectedNode.id)
      navigate(`/workspace/quiz/session/${session.id}`)
    } catch (e) {
      window.alert(e instanceof Error ? e.message : '生成练习失败')
    } finally {
      onQuizStartingChange(false)
    }
  }

  const tabs: { id: DetailTab; label: string }[] = [
    { id: 'overview', label: '概览' },
    { id: 'prerequisites', label: '前置知识点' },
    { id: 'resources', label: '学习资源' },
    { id: 'practice', label: '练习建议' },
  ]

  return (
    <aside className={styles.detailPanel} key={selectedNode.id}>
      <div className={styles.detailHead}>
        <div className={styles.detailTitleRow}>
          <span className={styles.detailIcon}>
            {selectedNode.status === 'mastered' ? <CheckCircle2 size={20} /> : selectedNode.status === 'weak' ? <AlertCircle size={20} /> : <Network size={20} />}
          </span>
          <div>
            <h2>{getNodeDisplayName(selectedNode)}</h2>
            {(selectedNode.chapterName || selectedNode.moduleName) && (
              <p>
                {selectedNode.chapterName ? `章节：${selectedNode.chapterName}` : ''}
                {selectedNode.chapterName && selectedNode.moduleName ? ' · ' : ''}
                {selectedNode.moduleName ? `模块：${selectedNode.moduleName}` : ''}
              </p>
            )}
          </div>
        </div>
        <button type="button" className={styles.detailClose} onClick={onClose} aria-label="关闭详情">
          <X size={18} />
        </button>
      </div>

      <div className={styles.detailInfo}>
        {selectedNode.difficulty != null && (
          <span>难度：<strong>{getDifficultyText(selectedNode.difficulty)}</strong></span>
        )}
        {selectedNode.moduleName && (
          <span>所属模块：<strong>{selectedNode.moduleName}</strong></span>
        )}
        <span>掌握度：<strong>{selectedNode.masteryRate}%</strong></span>
        <span>状态：<strong>{meta.label}</strong></span>
        {selectedNode.pathOrder != null && (
          <span>路径序号：<strong>第 {selectedNode.pathOrder} 步</strong></span>
        )}
        <span>重要程度：<strong>{IMPORTANCE_LABEL[selectedNode.importanceLevel]}</strong></span>
        {resources.length > 0 && (
          <span>相关资源：<strong>{resources.length} 个</strong></span>
        )}
        {quizResources.length > 0 && (
          <span>相关练习：<strong>{quizResources.length} 个</strong></span>
        )}
      </div>

      <div className={styles.tagList}>
        <span className={styles.statusBadge} style={{ color: meta.color, background: meta.bg }}>
          {meta.label}
        </span>
        {selectedNode.isLearningPathNode && (
          <span className={styles.statusBadge}>学习路径节点</span>
        )}
        {isCurrentLearning && (
          <span className={styles.statusBadge} style={{ color: '#F97316', background: 'rgba(249,115,22,0.12)' }}>
            当前学习
          </span>
        )}
        <span className={styles.statusBadge}>{IMPORTANCE_LABEL[selectedNode.importanceLevel]}</span>
      </div>

      {relatedNodes.length > 0 && (
        <div className={styles.tagList}>
          {relatedNodes.slice(0, 6).map((node) => (
            <button key={node.id} type="button" className={styles.relatedTag} onClick={() => onFocusNode(node.id)}>
              {getNodeDisplayName(node)}
            </button>
          ))}
        </div>
      )}

      <div className={styles.relationGroups}>
        {renderRelationGroup('前置知识', prereqNodes)}
        {renderRelationGroup('后续知识', dependentNodes)}
        {renderRelationGroup('同模块相关', sameModuleNodes)}
        {renderRelationGroup('薄弱关联', weakRelatedNodes)}
        {resources.length > 0 ? (
          <div className={styles.relationGroup}>
            <h4 className={styles.subHeading}>学习资源</h4>
            <p className={styles.emptyHint}>{resources.length} 个资源可用，详见「学习资源」标签页</p>
          </div>
        ) : null}
        {quizResources.length > 0 ? (
          <div className={styles.relationGroup}>
            <h4 className={styles.subHeading}>练习建议</h4>
            <p className={styles.emptyHint}>{quizResources.length} 个练习可用，详见「练习建议」标签页</p>
          </div>
        ) : null}
      </div>

      <div className={styles.progressBox}>
        <span>掌握度</span>
        <div><i style={{ width: `${selectedNode.masteryRate}%` }} /></div>
        <strong>{selectedNode.masteryRate}%</strong>
      </div>

      <div className={styles.primaryActions}>
        <button type="button" onClick={() => navigateToWorkspace(navigate, '/workspace/resources', selectedNode.id)}>
          <BookOpen size={16} />查看资源
        </button>
        <button type="button" disabled={quizStarting} onClick={() => void handleStartQuiz()}>
          <Sparkles size={16} />{quizStarting ? '生成中…' : '开始练习'}
        </button>
        <button
          type="button"
          onClick={() => {
            selectPathNode(selectedNode.id)
            navigate('/workspace/learning-path')
          }}
        >
          <GitBranch size={16} />加入学习路径
        </button>
        <button
          type="button"
          onClick={() => {
            selectPathNode(selectedNode.id)
            navigate(`/workspace${buildWorkspaceSearch({ kpId: selectedNode.id })}`)
          }}
        >
          <Bot size={16} />AI 讲解
        </button>
        {prereqNodes.length > 0 && (
          <button type="button" onClick={() => { onTabChange('prerequisites'); onFocusNode(prereqNodes[0].id) }}>
            <Eye size={16} />查看前置知识
          </button>
        )}
      </div>

      <div className={styles.tabs}>
        {tabs.map((tab) => (
          <button
            key={tab.id}
            type="button"
            className={activeTab === tab.id ? styles.tabActive : styles.tabButton}
            onClick={() => onTabChange(tab.id)}
          >
            {tab.label}
          </button>
        ))}
      </div>

      <div className={styles.tabContent}>
        {activeTab === 'overview' && (
          <div className={styles.summaryList}>
            <article>
              <Eye size={16} />
              <div>
                <h3>知识点说明</h3>
                <p>{selectedNode.description || '暂无详细说明，可通过 AI 讲解或查看学习资源了解更多。'}</p>
              </div>
            </article>
            <article>
              <Network size={16} />
              <div>
                <h3>理解要点</h3>
                <p>
                  前置：{prereqNodes.map((n) => n.name).join('、') || '无'}；
                  后续：{dependentNodes.map((n) => n.name).join('、') || '无'}。
                </p>
              </div>
            </article>
          </div>
        )}

        {activeTab === 'prerequisites' && (
          <div className={styles.linkList}>
            {prereqNodes.length === 0 ? (
              <p className={styles.emptyHint}>该知识点无前置依赖，可直接学习。</p>
            ) : (
              prereqNodes.map((node) => (
                <button key={node.id} type="button" className={styles.linkItem} onClick={() => onFocusNode(node.id)}>
                  <span>{node.name}</span>
                  <span className={styles.linkMeta}>{STATUS_META[node.status].label} · {node.masteryRate}%</span>
                </button>
              ))
            )}
            {dependentNodes.length > 0 && (
              <>
                <h4 className={styles.subHeading}>后续知识点</h4>
                {dependentNodes.map((node) => (
                  <button key={node.id} type="button" className={styles.linkItem} onClick={() => onFocusNode(node.id)}>
                    <span>{node.name}</span>
                    <span className={styles.linkMeta}>{STATUS_META[node.status].label}</span>
                  </button>
                ))}
              </>
            )}
          </div>
        )}

        {activeTab === 'resources' && (
          <div className={styles.linkList}>
            {detailLoading ? (
              <p className={styles.emptyHint}>加载资源中…</p>
            ) : resources.length === 0 ? (
              <p className={styles.emptyHint}>暂无学习资源，可前往资源页生成。</p>
            ) : (
              resources.map((res) => (
                <button
                  key={res.id}
                  type="button"
                  className={styles.linkItem}
                  onClick={() => navigateToWorkspace(navigate, '/workspace/resources', { kpId: selectedNode.id, resourceId: String(res.id) })}
                >
                  <span>{res.title}</span>
                  <span className={styles.linkMeta}>{res.resourceType}</span>
                </button>
              ))
            )}
          </div>
        )}

        {activeTab === 'practice' && (
          <div className={styles.linkList}>
            {detailLoading ? (
              <p className={styles.emptyHint}>加载练习中…</p>
            ) : quizResources.length === 0 ? (
              <>
                <p className={styles.emptyHint}>暂无关联练习，可直接生成针对性练习。</p>
                <button type="button" className={styles.inlineAction} disabled={quizStarting} onClick={() => void handleStartQuiz()}>
                  {quizStarting ? '生成中…' : '开始练习'}
                </button>
              </>
            ) : (
              <>
                {quizResources.map((quiz) => (
                  <div key={quiz.id} className={styles.linkItemStatic}>
                    <span>{quiz.title}</span>
                    <span className={styles.linkMeta}>QUIZ</span>
                  </div>
                ))}
                <button type="button" className={styles.inlineAction} disabled={quizStarting} onClick={() => void handleStartQuiz()}>
                  {quizStarting ? '生成中…' : '开始练习'}
                </button>
              </>
            )}
          </div>
        )}
      </div>
    </aside>
  )
}
