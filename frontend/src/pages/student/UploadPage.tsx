import { useState, useEffect, useRef, useCallback, useMemo } from 'react'
import { useNavigate } from 'react-router-dom'
import { uploadFile, listUploadTasks } from '@/api/student/upload'
import { listKnowledgePointsByIds } from '@/api/student/knowledge'
import type { UploadTask, KbScope, TaskStatus } from '@/api/student/upload'
import styles from './UploadPage.module.css'

const STATUS_LABEL: Record<TaskStatus, string> = {
  PENDING: '排队中',
  PROCESSING: '处理中',
  COMPLETED: '已完成',
  FAILED: '失败',
}

const pathCards = [
  { title: '核心概念学习', desc: '从本次映射到的知识点切入，建立完整理解。', icon: '◆' },
  { title: '例题训练', desc: '围绕当前文档内容生成针对性练习。', icon: '■' },
  { title: '查漏补缺', desc: '对还未掌握的相关知识点继续补强。', icon: '✓' },
] as const

const suggestions = [
  { title: '建议先看摘要', desc: '先快速了解本次文档的核心内容，再进入资源或路径。', icon: '☆' },
  { title: '基于映射继续生成资源', desc: '后续可直接围绕映射知识点生成讲义、题目和案例。', icon: '■' },
  { title: '知识画像联动', desc: '完成更多学习行为后可进一步沉淀到个人画像。', icon: '●' },
] as const

type ParsedAnalysis = {
  summary: string
  mappedKpIds: string[]
  matchedKnowledgePoints: {
    kpId: string
    score: number | null
  }[]
}

type DisplayKnowledgePoint = {
  id: string
  name: string
  value: number | null
  tone: 'blue' | 'amber' | 'orange'
}

function FileBadge({ fileName }: { fileName: string }) {
  const ext = (fileName || '').split('.').pop()?.toLowerCase() ?? ''
  const pdf = ext === 'pdf'
  const word = ['doc', 'docx'].includes(ext)
  const ppt = ['ppt', 'pptx'].includes(ext)
  return (
    <span className={`${styles.fileBadge} ${pdf ? styles.filePdf : word ? styles.fileWord : ppt ? styles.filePpt : styles.fileOther}`}>
      {pdf ? 'PDF' : word ? 'W' : ppt ? 'P' : ext.toUpperCase().slice(0, 3)}
    </span>
  )
}

function parseAnalysis(task: UploadTask): ParsedAnalysis {
  let summary = ''
  let mappedFromJson: string[] = []
  let matchedKnowledgePoints: ParsedAnalysis['matchedKnowledgePoints'] = []

  if (task.analysisResult) {
    try {
      const result = JSON.parse(task.analysisResult)
      if (typeof result?.summary === 'string') summary = result.summary
      if (Array.isArray(result?.mappedKpIds)) {
        mappedFromJson = result.mappedKpIds
          .map((item: unknown) => String(item || '').trim())
          .filter(Boolean)
      }
      if (Array.isArray(result?.matchedKnowledgePoints)) {
        matchedKnowledgePoints = result.matchedKnowledgePoints
          .map((item: unknown) => {
            if (!item || typeof item !== 'object') return null
            const point = item as {
              kpId?: unknown
              id?: unknown
              knowledgePointId?: unknown
              score?: unknown
              matchScore?: unknown
              matchingScore?: unknown
              similarity?: unknown
              relevance?: unknown
              confidence?: unknown
            }
            const kpId = String(point.kpId || point.id || point.knowledgePointId || '').trim()
            if (!kpId) return null
            const rawScore =
              point.score ??
              point.matchScore ??
              point.matchingScore ??
              point.similarity ??
              point.relevance ??
              point.confidence
            const score =
              typeof rawScore === 'number'
                ? Math.max(0, Math.min(100, Math.round(rawScore <= 1 ? rawScore * 100 : rawScore)))
                : typeof rawScore === 'string' && rawScore.trim() !== '' && !Number.isNaN(Number(rawScore))
                  ? Math.max(0, Math.min(100, Math.round(Number(rawScore) <= 1 ? Number(rawScore) * 100 : Number(rawScore))))
                  : null
            return { kpId, score }
          })
          .filter((item): item is NonNullable<typeof item> => Boolean(item))
      } else if (result?.matchedKnowledgePoints && typeof result.matchedKnowledgePoints === 'object') {
        matchedKnowledgePoints = Object.entries(result.matchedKnowledgePoints as Record<string, unknown>)
          .map(([kpId, rawScore]) => {
            const normalizedId = String(kpId || '').trim()
            if (!normalizedId) return null
            const score =
              typeof rawScore === 'number'
                ? Math.max(0, Math.min(100, Math.round(rawScore <= 1 ? rawScore * 100 : rawScore)))
                : typeof rawScore === 'string' && rawScore.trim() !== '' && !Number.isNaN(Number(rawScore))
                  ? Math.max(0, Math.min(100, Math.round(Number(rawScore) <= 1 ? Number(rawScore) * 100 : Number(rawScore))))
                  : null
            return { kpId: normalizedId, score }
          })
          .filter((item): item is NonNullable<typeof item> => Boolean(item))
      }
    } catch {
      // ignore malformed payload
    }
  }

  const mappedFromField = (task.mappedKpIds || '')
    .split(',')
    .map((item) => item.trim())
    .filter(Boolean)

  return {
    summary,
    mappedKpIds: Array.from(new Set([...mappedFromJson, ...mappedFromField])),
    matchedKnowledgePoints,
  }
}

export default function UploadPage() {
  const navigate = useNavigate()
  const fileInputRef = useRef<HTMLInputElement>(null)
  const dropRef = useRef<HTMLDivElement>(null)

  const [tasks, setTasks] = useState<UploadTask[]>([])
  const [uploading, setUploading] = useState(false)
  const [scope, setScope] = useState<KbScope>('PERSONAL')
  const [dragOver, setDragOver] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [knowledgePointTitles, setKnowledgePointTitles] = useState<Record<string, string>>({})
  const [animatedKnowledgeValues, setAnimatedKnowledgeValues] = useState<Record<string, number>>({})

  const completedTasks = useMemo(
    () => tasks.filter((t) => t.status === 'COMPLETED'),
    [tasks],
  )
  const latestCompletedTask = completedTasks[0] ?? null
  const latestAnalysis = useMemo(
    () => (latestCompletedTask ? parseAnalysis(latestCompletedTask) : null),
    [latestCompletedTask?.id, latestCompletedTask?.analysisResult, latestCompletedTask?.mappedKpIds],
  )
  const hasCompleted = completedTasks.length > 0
  const mappedKpIdsKey = useMemo(
    () => (latestAnalysis?.mappedKpIds ?? []).join('|'),
    [latestAnalysis],
  )

  useEffect(() => {
    const ids = latestAnalysis?.mappedKpIds ?? []
    if (ids.length === 0) {
      setKnowledgePointTitles((prev) => (Object.keys(prev).length === 0 ? prev : {}))
      return
    }

    let cancelled = false
    void listKnowledgePointsByIds(ids)
      .then((items) => {
        if (cancelled) return
        setKnowledgePointTitles(
          items.reduce<Record<string, string>>((acc, item) => {
            acc[item.kpId] = item.title
            return acc
          }, {}),
        )
      })
      .catch(() => {
        if (!cancelled) {
          setKnowledgePointTitles((prev) => (Object.keys(prev).length === 0 ? prev : {}))
        }
      })

    return () => {
      cancelled = true
    }
  }, [mappedKpIdsKey])

  const mappedKnowledgePoints = useMemo<DisplayKnowledgePoint[]>(() => {
    if (!latestAnalysis) return []
    const tones: DisplayKnowledgePoint['tone'][] = ['blue', 'amber', 'orange']
    const scoreMap = new Map(
      latestAnalysis.matchedKnowledgePoints.map((item) => [item.kpId, item.score] as const),
    )
    return latestAnalysis.mappedKpIds.map((kpId, index) => {
      const score = scoreMap.get(kpId)
      return {
        id: kpId,
        name: knowledgePointTitles[kpId] || kpId,
        value: typeof score === 'number' ? score : null,
        tone: tones[Math.min(index, tones.length - 1)],
      }
    })
  }, [knowledgePointTitles, latestAnalysis])
  const mappedKnowledgePointsAnimationKey = useMemo(
    () => mappedKnowledgePoints.map((point) => `${point.id}:${point.value ?? 'null'}`).join('|'),
    [mappedKnowledgePoints],
  )

  useEffect(() => {
    if (mappedKnowledgePoints.length === 0) {
      setAnimatedKnowledgeValues({})
      return
    }

    setAnimatedKnowledgeValues(
      mappedKnowledgePoints.reduce<Record<string, number>>((acc, point) => {
        acc[point.id] = 0
        return acc
      }, {}),
    )

    const frame = window.requestAnimationFrame(() => {
      setAnimatedKnowledgeValues(
        mappedKnowledgePoints.reduce<Record<string, number>>((acc, point) => {
          acc[point.id] = point.value ?? 0
          return acc
        }, {}),
      )
    })

    return () => window.cancelAnimationFrame(frame)
  }, [mappedKnowledgePointsAnimationKey])

  const combinedSummary = useMemo(() => {
    if (latestAnalysis?.summary) return latestAnalysis.summary
    return completedTasks
      .map((task) => parseAnalysis(task).summary)
      .filter(Boolean)
      .join('；')
  }, [completedTasks, latestAnalysis])

  const refreshTasks = useCallback(async () => {
    try {
      const data = await listUploadTasks()
      setTasks(data)
      setError(null)
    } catch {
      // keep current UI state on polling failure
    }
  }, [])

  const needPolling = tasks.some((t) => t.status === 'PENDING' || t.status === 'PROCESSING')

  useEffect(() => {
    refreshTasks()
  }, [refreshTasks])

  useEffect(() => {
    if (!needPolling) return
    const timer = setInterval(refreshTasks, 5000)
    return () => clearInterval(timer)
  }, [refreshTasks, needPolling])

  const handleChooseFile = () => {
    fileInputRef.current?.click()
  }

  const doUpload = useCallback(
    async (file: File) => {
      setUploading(true)
      setError(null)
      try {
        await uploadFile(file, scope)
        await refreshTasks()
      } catch (e: any) {
        setError(e?.message || '上传失败，请重试')
      } finally {
        setUploading(false)
      }
    },
    [scope, refreshTasks],
  )

  const handleFileChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0]
    if (!file) return
    void doUpload(file)
    e.target.value = ''
  }

  const handleDragOver = (e: React.DragEvent) => {
    e.preventDefault()
    e.stopPropagation()
    setDragOver(true)
  }

  const handleDragLeave = (e: React.DragEvent) => {
    e.preventDefault()
    e.stopPropagation()
    setDragOver(false)
  }

  const handleDrop = (e: React.DragEvent) => {
    e.preventDefault()
    e.stopPropagation()
    setDragOver(false)
    const file = e.dataTransfer.files?.[0]
    if (file) void doUpload(file)
  }

  const handleRemoveTask = (id: number) => {
    setTasks((prev) => prev.filter((t) => t.id !== id))
  }

  return (
    <section className={styles.page}>
      <div className={styles.glow} />

      <header className={styles.topbar}>
        <div>
          <h1 className={styles.title}>上传分析</h1>
          <p className={styles.subtitle}>上传学习资料后，系统会自动完成摘要生成与知识点映射。</p>
        </div>
        <div className={styles.topMeta}>M7 · 上传分析</div>
        <div className={styles.topActions}>
          <button type="button" className={styles.secondaryBtn} onClick={refreshTasks}>
            <span className={styles.btnIcon}>↻</span>
            刷新任务
          </button>
          <button
            type="button"
            className={styles.primaryBtn}
            onClick={handleChooseFile}
            disabled={uploading}
          >
            <span className={styles.btnIcon}>{uploading ? '●' : '☁'}</span>
            {uploading ? '上传中...' : '继续上传'}
          </button>
        </div>
      </header>

      <section className={styles.infoPanel}>
        <div className={styles.infoGrid}>
          <div className={styles.infoBlock}>
            <div className={styles.infoIcon}>■</div>
            <div>
              <span className={styles.infoLabel}>知识库范围：</span>
              <div className={styles.scopeToggle}>
                <button
                  type="button"
                  className={scope === 'PERSONAL' ? `${styles.scopeBtn} ${styles.scopeActive}` : styles.scopeBtn}
                  onClick={() => setScope('PERSONAL')}
                >
                  个人知识库
                </button>
                <button
                  type="button"
                  className={scope === 'PUBLIC' ? `${styles.scopeBtn} ${styles.scopeActive}` : styles.scopeBtn}
                  onClick={() => setScope('PUBLIC')}
                >
                  公共知识库
                </button>
              </div>
            </div>
          </div>
          <div className={styles.infoDivider} />
          <div className={styles.infoBlock}>
            <div className={styles.infoIcon}>●</div>
            <div>
              <span className={styles.infoLabel}>分析目标：</span>
              <strong>从上传结果中提炼摘要并回传真实知识点映射</strong>
            </div>
          </div>
          <div className={styles.infoDivider} />
          <div className={styles.infoBlock}>
            <div className={styles.infoIcon}>→</div>
            <div>
              <span className={styles.infoLabel}>后续联动：</span>
              <strong>可跳转到学习路径继续围绕映射知识点学习</strong>
            </div>
          </div>
        </div>
      </section>

      <div className={styles.layout}>
        <div className={styles.leftColumn}>
          <section className={styles.panel}>
            <div className={styles.sectionHead}>
              <h2 className={styles.panelTitle}>上传学习资料</h2>
              <span className={styles.sectionMeta}>支持 PDF / Word / PPT / 图片 / 文本</span>
            </div>

            <input
              ref={fileInputRef}
              type="file"
              accept=".pdf,.doc,.docx,.ppt,.pptx,.jpg,.jpeg,.png,.gif,.txt,.md"
              onChange={handleFileChange}
              style={{ display: 'none' }}
            />

            <div
              ref={dropRef}
              className={`${styles.uploadDropzone} ${dragOver ? styles.uploadDropzoneOver : ''}`}
              onDragOver={handleDragOver}
              onDragLeave={handleDragLeave}
              onDrop={handleDrop}
            >
              <div className={styles.uploadIcon}>⬆</div>
              <p className={styles.uploadText}>
                {dragOver ? '松开即可上传' : '拖拽文件到此处，或点击选择文件'}
              </p>
              <button
                type="button"
                className={styles.uploadBtn}
                onClick={handleChooseFile}
                disabled={uploading}
              >
                {uploading ? '上传中...' : '选择文件'}
              </button>
            </div>

            {error && <p className={styles.errorTip}>× {error}</p>}

            <div className={styles.taskSection}>
              <h3 className={styles.subTitle}>
                分析任务列表
                <span className={styles.taskCount}>{tasks.length > 0 ? ` (${tasks.length})` : ''}</span>
              </h3>
              {tasks.length === 0 ? (
                <div className={styles.emptyTaskList}>
                  <p className={styles.emptyTaskText}>暂无上传任务，拖拽文件或点击上方按钮开始上传。</p>
                </div>
              ) : (
                <div className={styles.fileList}>
                  {tasks.map((task) => (
                    <article key={task.id} className={styles.fileRow}>
                      <div className={styles.fileMain}>
                        <FileBadge fileName={task.fileName} />
                        <span className={styles.fileName}>{task.fileName}</span>
                      </div>
                      <span
                        className={`${styles.statusBadge} ${styles[`status${task.status[0].toUpperCase()}${task.status.slice(1).toLowerCase()}`] || styles.statusPending}`}
                      >
                        {task.status === 'PROCESSING' && task.progressPercent != null
                          ? `${STATUS_LABEL[task.status]} ${task.progressPercent}%`
                          : STATUS_LABEL[task.status]}
                      </span>
                      <span className={styles.fileTime}>
                        {task.createdAt
                          ? new Date(task.createdAt).toLocaleDateString('zh-CN', {
                              month: 'short',
                              day: 'numeric',
                              hour: '2-digit',
                              minute: '2-digit',
                            })
                          : '—'}
                      </span>
                      <button
                        type="button"
                        className={styles.fileDelete}
                        onClick={() => handleRemoveTask(task.id)}
                        title="移除记录"
                      >
                        ✕
                      </button>
                    </article>
                  ))}
                </div>
              )}
            </div>
          </section>

          {hasCompleted && (
            <section className={styles.panel}>
              <div className={styles.sectionHead}>
                <h2 className={styles.panelTitle}>分析摘要</h2>
                <div className={styles.summaryMeta}>
                  <span>
                    完成时间：
                    {latestCompletedTask?.finishedAt
                      ? new Date(latestCompletedTask.finishedAt).toLocaleString('zh-CN')
                      : '—'}
                  </span>
                  <span className={styles.doneBadge}>分析完成</span>
                </div>
              </div>

              <div className={styles.summaryCard}>
                <div className={styles.summaryItem}>
                  <span className={styles.summaryIcon}>■</span>
                  <div>
                    <h3>内容摘要</h3>
                    <p>{combinedSummary || '分析结果将在任务处理完成后展示。'}</p>
                  </div>
                </div>
                <div className={styles.summaryItem}>
                  <span className={`${styles.summaryIcon} ${styles.summaryIconWarn}`}>●</span>
                  <div>
                    <h3>知识点映射结果</h3>
                    <p>
                      {mappedKnowledgePoints.length > 0
                        ? `本次共映射到 ${mappedKnowledgePoints.length} 个知识点，可直接用于后续路径与资源联动。`
                        : '本次分析暂未返回知识点映射。'}
                    </p>
                  </div>
                </div>
              </div>

            </section>
          )}
        </div>

        <div className={styles.rightColumn}>
          <section className={styles.panel}>
            <div className={styles.sectionHead}>
              <h2 className={styles.panelTitle}>映射知识点</h2>
              <span className={styles.sectionMeta}>这里直接展示上传分析返回的真实 `mappedKpIds`</span>
            </div>

            <div className={styles.knowledgeCard}>
              {mappedKnowledgePoints.length > 0 ? (
                mappedKnowledgePoints.map((point) => (
                  <button
                    key={point.id}
                    type="button"
                    className={`${styles.knowledgeRow} ${point.value != null ? styles.knowledgeRowAnimated : ''}`}
                    onClick={() => navigate('/workspace/learning-path')}
                    title={point.id}
                  >
                    <div className={styles.knowledgeNameWrap}>
                      <span className={styles.knowledgeDot} />
                      <span className={styles.knowledgeName}>{point.name}</span>
                    </div>
                    <div className={styles.knowledgeTrack}>
                      <span
                        className={
                          point.tone === 'blue'
                            ? `${styles.knowledgeFill} ${styles.knowledgeBlue}`
                            : point.tone === 'amber'
                              ? `${styles.knowledgeFill} ${styles.knowledgeAmber}`
                              : `${styles.knowledgeFill} ${styles.knowledgeOrange}`
                        }
                        style={{ width: `${animatedKnowledgeValues[point.id] ?? 0}%` }}
                      />
                    </div>
                    <strong className={styles.knowledgeValue}>
                      {point.value == null ? '待分析' : `${Math.round(animatedKnowledgeValues[point.id] ?? 0)}%`}
                    </strong>
                    <span className={styles.knowledgeArrow}>→</span>
                  </button>
                ))
              ) : (
                <div className={styles.emptyTaskList}>
                  <p className={styles.emptyTaskText}>分析完成后，这里会展示后端返回的真实知识点映射。</p>
                </div>
              )}
            </div>

            <div className={styles.flowHint}>映射知识点可继续驱动学习路径与资源生成</div>

            <div className={styles.pathPanel}>
              <div className={styles.pathHead}>
                <h2 className={styles.pathTitle}>跳转到学习路径</h2>
                <span className={styles.pathMeta}>围绕本次映射结果继续学习</span>
              </div>

              <div className={styles.pathList}>
                {pathCards.map((card) => (
                  <article key={card.title} className={styles.pathCard}>
                    <span className={styles.pathIcon}>{card.icon}</span>
                    <div className={styles.pathContent}>
                      <h3>{card.title}</h3>
                      <p>{card.desc}</p>
                    </div>
                    <button
                      type="button"
                      className={styles.pathBtn}
                      onClick={() => navigate('/workspace/learning-path')}
                    >
                      前往路径
                    </button>
                  </article>
                ))}
              </div>
            </div>
          </section>
        </div>
      </div>

      <section className={styles.bottomPanel}>
        <h2 className={styles.panelTitle}>后续学习建议</h2>
        <div className={styles.suggestionGrid}>
          {suggestions.map((item) => (
            <article key={item.title} className={styles.suggestionCard}>
              <span className={styles.suggestionIcon}>{item.icon}</span>
              <div>
                <h3>{item.title}</h3>
                <p>{item.desc}</p>
              </div>
            </article>
          ))}
        </div>
      </section>
    </section>
  )
}
