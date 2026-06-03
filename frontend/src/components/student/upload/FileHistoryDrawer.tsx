import { useMemo, useState } from 'react'
import type { UploadTask } from '@/api/student/upload'
import summaryBg from '@/assets/student/img.png'
import styles from './FileHistoryDrawer.module.css'

export type DrawerKnowledgePoint = {
  id: string
  name: string
  value: number | null
}

type FileHistoryDrawerProps = {
  open: boolean
  task: UploadTask | null
  summary: string
  knowledgePoints: DrawerKnowledgePoint[]
  onClose: () => void
}

type DrawerTab = 'summary' | 'mapping'

function getFileExt(fileName: string) {
  return fileName.split('.').pop()?.toLowerCase() ?? ''
}

function getFileIcon(fileName: string) {
  const ext = getFileExt(fileName)
  if (['doc', 'docx'].includes(ext)) return 'W'
  if (ext === 'pdf') return 'P'
  if (['ppt', 'pptx'].includes(ext)) return 'P'
  return 'F'
}

function getFileFormat(fileName: string) {
  const ext = getFileExt(fileName)
  return ext ? `.${ext}` : '未知'
}

function getMatchStats(knowledgePoints: DrawerKnowledgePoint[]) {
  let high = 0
  let medium = 0
  let low = 0
  knowledgePoints.forEach((point) => {
    const value = point.value ?? 0
    if (value >= 70) high += 1
    else if (value >= 40) medium += 1
    else low += 1
  })
  const total = knowledgePoints.length || 1
  return {
    total: knowledgePoints.length,
    high,
    medium,
    low,
    highPct: Math.round((high / total) * 100),
    mediumPct: Math.round((medium / total) * 100),
    lowPct: Math.round((low / total) * 100),
  }
}

export default function FileHistoryDrawer({
  open,
  task,
  summary,
  knowledgePoints,
  onClose,
}: FileHistoryDrawerProps) {
  const [activeTab, setActiveTab] = useState<DrawerTab>('summary')

  const stats = useMemo(() => getMatchStats(knowledgePoints), [knowledgePoints])

  if (!open || !task) return null

  const createdAt = task.createdAt
    ? new Date(task.createdAt).toLocaleString('zh-CN', {
        year: 'numeric',
        month: 'numeric',
        day: 'numeric',
        hour: '2-digit',
        minute: '2-digit',
      })
    : '待生成'

  return (
    <div className={styles.overlay} onClick={onClose}>
      <aside className={styles.drawer} onClick={(e) => e.stopPropagation()}>
        <div className={styles.header}>
          <h2 className={styles.title}>文件详情</h2>
          <button type="button" className={styles.closeBtn} onClick={onClose} aria-label="关闭">
            ×
          </button>
        </div>

        <section className={styles.fileCard}>
          <div className={styles.fileIcon}>{getFileIcon(task.fileName)}</div>
          <div>
            <h3 className={styles.fileName}>{task.fileName}</h3>
            <div className={styles.fileMeta}>
              <span>上传时间：{createdAt}</span>
              <span>大小：2.4MB</span>
              <span>格式：{getFileFormat(task.fileName)}</span>
            </div>
          </div>
          <span className={styles.doneTag}>{task.status === 'COMPLETED' ? '已完成' : '处理中'}</span>
        </section>

        <div className={styles.tabs}>
          <button
            type="button"
            className={activeTab === 'summary' ? styles.tabActive : styles.tab}
            onClick={() => setActiveTab('summary')}
          >
            内容摘要
          </button>
          <button
            type="button"
            className={activeTab === 'mapping' ? styles.tabActive : styles.tab}
            onClick={() => setActiveTab('mapping')}
          >
            知识点映射
          </button>
        </div>

        {activeTab === 'summary' ? (
          <section
            className={`${styles.summaryCard} ${styles.summaryCardWithBg}`}
            style={{ backgroundImage: `url(${summaryBg})` }}
          >
            <div className={styles.sectionHead}>
              <h3 className={styles.sectionTitle}>内容摘要</h3>
              <span className={styles.aiTag}>AI 生成</span>
            </div>

            <div className={styles.summaryGrid}>
              <p className={styles.summaryText}>
                {summary || '本文档系统介绍了上传资料中的核心知识与实践技巧，覆盖关键概念、应用方式与典型案例，便于后续学习路径与资源联动。'}
              </p>
              <div className={styles.summaryVisualPlaceholder} aria-hidden="true" />
            </div>

            <div className={styles.metrics}>
              <div className={styles.metric}>
                <span className={styles.metricLabel}>全文页数</span>
                <span className={styles.metricValue}>48页</span>
              </div>
              <div className={styles.metric}>
                <span className={styles.metricLabel}>字数统计</span>
                <span className={styles.metricValue}>2.1万字</span>
              </div>
              <div className={styles.metric}>
                <span className={styles.metricLabel}>代码示例</span>
                <span className={styles.metricValue}>128个</span>
              </div>
              <div className={styles.metric}>
                <span className={styles.metricLabel}>图片/图表</span>
                <span className={styles.metricValue}>16个</span>
              </div>
            </div>

            <button type="button" className={styles.expandBtn}>展开全部摘要</button>
          </section>
        ) : (
          <section className={styles.mappingCard}>
            <div className={styles.mappingHead}>
              <h3 className={styles.sectionTitle}>知识点映射概览</h3>
              <span className={styles.mappingSub}>共映射 {stats.total} 个知识点</span>
            </div>

            <div className={styles.mappingBody}>
              <div className={styles.ringWrap}>
                <div className={styles.ringInner}>
                  <span className={styles.ringCount}>{stats.total}</span>
                  <span className={styles.ringLabel}>总数</span>
                </div>
              </div>

              <div className={styles.legend}>
                <div className={styles.legendItem}>
                  <span className={styles.legendDotGreen} />
                  <span>高匹配</span>
                  <strong>{stats.high}（{stats.highPct}%）</strong>
                </div>
                <div className={styles.legendItem}>
                  <span className={styles.legendDotOrange} />
                  <span>中匹配</span>
                  <strong>{stats.medium}（{stats.mediumPct}%）</strong>
                </div>
                <div className={styles.legendItem}>
                  <span className={styles.legendDotYellow} />
                  <span>低匹配</span>
                  <strong>{stats.low}（{stats.lowPct}%）</strong>
                </div>
              </div>

              <div className={styles.network}>
                <span className={styles.networkLineA} />
                <span className={styles.networkLineB} />
                <span className={styles.networkLineC} />
                <span className={styles.networkLineD} />
                <span className={styles.nodeBlue}>○</span>
                <span className={styles.nodeOrange}>◍</span>
                <span className={styles.nodeGreen}>◔</span>
                <span className={`${styles.nodeSoft} ${styles.nodeSoftA}`}>•</span>
                <span className={`${styles.nodeSoft} ${styles.nodeSoftB}`}>•</span>
                <span className={`${styles.nodeSoft} ${styles.nodeSoftC}`}>•</span>
              </div>
            </div>

            <button type="button" className={styles.mappingBtn}>查看完整映射详情</button>
          </section>
        )}
      </aside>
    </div>
  )
}
