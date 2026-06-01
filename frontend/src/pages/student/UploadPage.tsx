import { useState, useEffect, useRef, useCallback } from 'react'
import { useNavigate } from 'react-router-dom'
import { uploadFile, listUploadTasks } from '@/api/student/upload'
import type { UploadTask, KbScope, TaskStatus } from '@/api/student/upload'
import styles from './UploadPage.module.css'

const STATUS_LABEL: Record<TaskStatus, string> = {
  PENDING: '排队中',
  PROCESSING: '处理中',
  COMPLETED: '已完成',
  FAILED: '失败',
}

function formatSize(bytes?: number): string {
  if (bytes == null) return '—'
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / (1024 * 1024)).toFixed(2)} MB`
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

const knowledgePoints = [
  { name: '导数定义', value: 92, tone: 'blue' },
  { name: '导数几何意义', value: 88, tone: 'blue' },
  { name: '链式法则', value: 81, tone: 'amber' },
  { name: '隐函数求导', value: 76, tone: 'amber' },
  { name: '典型例题应用', value: 69, tone: 'orange' },
] as const

const actions = [
  { title: '优先巩固链式法则', desc: '薄弱知识点', icon: '◎' },
  { title: '补充隐函数求导练习', desc: '强化应用能力', icon: '⌘' },
  { title: '进入路径查漏补缺', desc: '针对性学习', icon: '↗' },
] as const

const pathCards = [
  { title: '核心概念学习', desc: '系统复习导数的核心概念与性质，夯实基础。', icon: '🎓' },
  { title: '例题训练', desc: '精选典型例题精讲精练，提升解题能力。', icon: '▤' },
  { title: '查漏补缺', desc: '针对薄弱知识点专项练习，巩固提升。', icon: '✓' },
] as const

const suggestions = [
  { title: '建议学习顺序', desc: '链式法则 → 隐函数求导 → 例题训练', icon: '☰' },
  { title: '推荐资源', desc: '精选微课 / 题目 / 示范解题', icon: '▣' },
  { title: '画像联动', desc: '后续完成练习后可同步更新学习画像', icon: '●' },
] as const

export default function UploadPage() {
  const navigate = useNavigate()
  const fileInputRef = useRef<HTMLInputElement>(null)
  const dropRef = useRef<HTMLDivElement>(null)

  const [tasks, setTasks] = useState<UploadTask[]>([])
  const [uploading, setUploading] = useState(false)
  const [scope, setScope] = useState<KbScope>('PERSONAL')
  const [dragOver, setDragOver] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const hasCompleted = tasks.some((t) => t.status === 'COMPLETED')

  // 拉取任务列表
  const refreshTasks = useCallback(async () => {
    try {
      const data = await listUploadTasks()
      setTasks(data)
      setError(null)
    } catch {
      // 静默失败，轮询不打断用户
    }
  }, [])

  // 判断是否还有未完成的任务需要轮询
  const needPolling = tasks.some((t) => t.status === 'PENDING' || t.status === 'PROCESSING')

  // 初始加载 + 定时轮询（仅在有进行中任务时持续轮询）
  useEffect(() => {
    refreshTasks()
  }, [refreshTasks])

  useEffect(() => {
    if (!needPolling) return
    const timer = setInterval(refreshTasks, 5000)
    return () => clearInterval(timer)
  }, [refreshTasks, needPolling])

  // 触发文件选择
  const handleChooseFile = () => {
    fileInputRef.current?.click()
  }

  // 执行上传
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

  // 文件选择回调
  const handleFileChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0]
    if (!file) return
    doUpload(file)
    // 重置 input 以允许重复上传同名文件
    e.target.value = ''
  }

  // 拖拽事件
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
    if (file) doUpload(file)
  }

  // 删除任务（本地移除）
  const handleRemoveTask = (id: number) => {
    setTasks((prev) => prev.filter((t) => t.id !== id))
  }

  return (
    <section className={styles.page}>
      <div className={styles.glow} />

      <header className={styles.topbar}>
        <div>
          <h1 className={styles.title}>上传分析</h1>
          <p className={styles.subtitle}>上传学习资料并自动分析内容、生成摘要、知识点映射与后续学习建议</p>
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
            <span className={styles.btnIcon}>{uploading ? '◌' : '☁'}</span>
            {uploading ? '上传中...' : '继续上传'}
          </button>
        </div>
      </header>

      <section className={styles.infoPanel}>
        <div className={styles.infoGrid}>
          <div className={styles.infoBlock}>
            <div className={styles.infoIcon}>▯</div>
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
            <div className={styles.infoIcon}>◎</div>
            <div>
              <span className={styles.infoLabel}>分析目标：</span>
              <strong>识别薄弱知识点并生成学习建议</strong>
            </div>
          </div>
          <div className={styles.infoDivider} />
          <div className={styles.infoBlock}>
            <div className={styles.infoIcon}>∞</div>
            <div>
              <span className={styles.infoLabel}>知识点跳转：</span>
              <strong>分析完成后可跳转至学习路径面板</strong>
            </div>
          </div>
        </div>
      </section>

      <div className={styles.layout}>
        <div className={styles.leftColumn}>
          <section className={styles.panel}>
            <div className={styles.sectionHead}>
              <h2 className={styles.panelTitle}>上传学习资料</h2>
              <span className={styles.sectionMeta}>支持 PDF / Word / PPT / 图片</span>
            </div>

            {/* 隐藏的文件选择器 */}
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
              <div className={styles.uploadIcon}>⇪</div>
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

            {error && <p className={styles.errorTip}>⚠ {error}</p>}

            <div className={styles.taskSection}>
              <h3 className={styles.subTitle}>
                分析任务列表
                <span className={styles.taskCount}>{tasks.length > 0 ? ` (${tasks.length})` : ''}</span>
              </h3>
              {tasks.length === 0 ? (
                <div className={styles.emptyTaskList}>
                  <p className={styles.emptyTaskText}>暂无上传任务，拖拽文件或点击上方按钮开始上传</p>
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
                        {STATUS_LABEL[task.status]}
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
                        🗑
                      </button>
                    </article>
                  ))}
                </div>
              )}
            </div>
          </section>

          {hasCompleted && (
            <>
              <section className={styles.panel}>
                <div className={styles.sectionHead}>
                  <h2 className={styles.panelTitle}>分析摘要</h2>
                  <div className={styles.summaryMeta}>
                    <span>
                      完成时间：
                      {tasks.find((t) => t.status === 'COMPLETED')?.finishedAt
                        ? new Date(
                            tasks.find((t) => t.status === 'COMPLETED')!.finishedAt!,
                          ).toLocaleString('zh-CN')
                        : '—'}
                    </span>
                    <span className={styles.doneBadge}>分析完成</span>
                  </div>
                </div>

                <div className={styles.summaryCard}>
                  <div className={styles.summaryItem}>
                    <span className={styles.summaryIcon}>▣</span>
                    <div>
                      <h3>内容摘要</h3>
                      <p>
                        {tasks
                          .filter((t) => t.status === 'COMPLETED' && t.analysisResult)
                          .map((t) => {
                            try {
                              const r = JSON.parse(t.analysisResult || '{}')
                              return r.summary || ''
                            } catch {
                              return ''
                            }
                          })
                          .filter(Boolean)
                          .join('；') || '分析结果将在任务处理完成后展示'}
                      </p>
                    </div>
                  </div>
                  <div className={styles.summaryItem}>
                    <span className={`${styles.summaryIcon} ${styles.summaryIconWarn}`}>◔</span>
                    <div>
                      <h3>学习诊断</h3>
                      <p>基础概念掌握较好，但在链式法则和隐函数求导的理解与应用方面仍有提升空间。</p>
                    </div>
                  </div>
                </div>

                <div className={styles.actionCards}>
                  {actions.map((action) => (
                    <article key={action.title} className={styles.actionCard}>
                      <span className={styles.actionIcon}>{action.icon}</span>
                      <div>
                        <h3>{action.title}</h3>
                        <p>{action.desc}</p>
                      </div>
                    </article>
                  ))}
                </div>
              </section>
            </>
          )}
        </div>

        <div className={styles.rightColumn}>
          <section className={styles.panel}>
            <div className={styles.sectionHead}>
              <h2 className={styles.panelTitle}>映射知识点</h2>
              <span className={styles.sectionMeta}>点击知识点可联动到学习路径</span>
            </div>

            <div className={styles.knowledgeCard}>
              {knowledgePoints.map((point) => (
                <button
                  key={point.name}
                  type="button"
                  className={styles.knowledgeRow}
                  onClick={() => navigate('/workspace/learning-path')}
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
                      style={{ width: `${point.value}%` }}
                    />
                  </div>
                  <strong className={styles.knowledgeValue}>{point.value}%</strong>
                  <span className={styles.knowledgeArrow}>›</span>
                </button>
              ))}
            </div>

            <div className={styles.flowHint}>薄弱点驱动学习路径推荐</div>

            <div className={styles.pathPanel}>
              <div className={styles.pathHead}>
                <h2 className={styles.pathTitle}>跳转到学习路径</h2>
                <span className={styles.pathMeta}>基于薄弱点推荐的个性化学习路径</span>
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
