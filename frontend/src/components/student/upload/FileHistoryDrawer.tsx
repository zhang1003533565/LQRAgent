import { useEffect, useMemo, useState } from 'react'
import ReactECharts from 'echarts-for-react'
import type { UploadTask } from '@/api/student/upload'
import { listKnowledgePointsByIds } from '@/api/student/knowledge'
import summaryBg from '@/assets/student/img.png'
import knowledgeBg from '@/assets/student/knowledge.png'
import { createDonutChartOption } from '@/utils/echarts'
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

const KNOWLEDGE_POINT_LABELS: Record<string, string> = {
  kp_class: '类',
  kp_inheritance: '继承',
  kp_encapsulation: '封装',
  kp_special_methods: '特殊方法',
  kp_generator: '生成器',
  kp_context_manager: '上下文管理器',
  kp_decorator: '装饰器',
  kp_lambda: 'Lambda 表达式',
  kp_variables: '变量',
  kp_if: '条件判断',
  kp_for: 'for 循环',
  kp_function: '函数',
  kp_exception: '异常处理',
  kp_while: 'while 循环',
  kp_list: '列表',
  kp_dict: '字典',
  kp_set: '集合',
  kp_tuple: '元组',
  kp_string: '字符串',
  kp_module: '模块',
  kp_package: '包',
  kp_object: '对象',
  kp_iterable: '可迭代对象',
  kp_iterator: '迭代器',
}

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

function getPointProgressValue(value: number | null) {
  return Math.max(0, Math.min(100, Math.round(value ?? 0)))
}

function getKnowledgePointDistribution(knowledgePoints: DrawerKnowledgePoint[]) {
  const normalized = knowledgePoints.map((point) => ({
    name: point.name,
    value: getPointProgressValue(point.value),
  }))
  const totalValue = normalized.reduce((sum, point) => sum + point.value, 0)

  if (totalValue > 0) {
    return normalized.filter((point) => point.value > 0)
  }

  return normalized.map((point) => ({
    ...point,
    value: 1,
  }))
}

function getKnowledgePointLabel(name: string) {
  if (KNOWLEDGE_POINT_LABELS[name]) return KNOWLEDGE_POINT_LABELS[name]

  if (name.startsWith('kp_')) {
    return name
      .slice(3)
      .split('_')
      .filter(Boolean)
      .map((part) => {
        const normalized = part.toLowerCase()
        if (normalized === 'if') return '条件判断'
        if (normalized === 'for') return 'for 循环'
        if (normalized === 'while') return 'while 循环'
        return normalized.charAt(0).toUpperCase() + normalized.slice(1)
      })
      .join(' ')
  }

  return name
}

export default function FileHistoryDrawer({
  open,
  task,
  summary,
  knowledgePoints,
  onClose,
}: FileHistoryDrawerProps) {
  const [activeTab, setActiveTab] = useState<DrawerTab>('summary')
  const [animatedProgress, setAnimatedProgress] = useState<Record<string, number>>({})
  const [knowledgePointTitles, setKnowledgePointTitles] = useState<Record<string, string>>({})

  const stats = useMemo(() => getMatchStats(knowledgePoints), [knowledgePoints])
  const pointIdsKey = useMemo(
    () => knowledgePoints.map((point) => point.id).join('|'),
    [knowledgePoints],
  )
  const displayKnowledgePoints = useMemo(
    () =>
      knowledgePoints.map((point) => ({
        ...point,
        name: knowledgePointTitles[point.id] || getKnowledgePointLabel(point.name),
      })),
    [knowledgePointTitles, knowledgePoints],
  )
  const distribution = useMemo(
    () => getKnowledgePointDistribution(displayKnowledgePoints),
    [displayKnowledgePoints],
  )
  const distributionTotal = useMemo(
    () => distribution.reduce((sum, point) => sum + point.value, 0),
    [distribution],
  )

  const donutOption = useMemo(
    () =>
      createDonutChartOption({
        color: [
          '#3f7cff',
          '#ff8a3d',
          '#ffd25f',
          '#5fcf8b',
          '#8b5cf6',
          '#06b6d4',
          '#f97316',
          '#ef4444',
        ],
        tooltip: {
          trigger: 'item',
          formatter: '{b}: {c} ({d}%)',
        },
        legend: {
          top: 'middle',
          left: '58%',
          right: 'auto',
          orient: 'vertical',
          itemGap: 14,
          formatter: (name) => {
            const item = distribution.find((entry) => entry.name === name)
            const value = item?.value ?? 0
            const percent = distributionTotal > 0 ? Math.round((value / distributionTotal) * 100) : 0
            return `${name}  ${percent}%`
          },
          textStyle: {
            color: '#355281',
            fontSize: 14,
            fontWeight: 700,
          },
        },
        series: [
          {
            name: '知识点映射',
            radius: ['60%', '88%'],
            center: ['34%', '56%'],
            emphasis: {
              label: {
                show: true,
                formatter: '{b}\n{c}',
                fontSize: 20,
                fontWeight: 'bold',
                lineHeight: 26,
              },
            },
            data: distribution,
          },
        ],
      }),
    [distribution, distributionTotal],
  )

  useEffect(() => {
    if (open) {
      setActiveTab('summary')
    }
  }, [open, task?.id])

  useEffect(() => {
    if (!open) return

    const ids = knowledgePoints.map((point) => point.id).filter(Boolean)
    if (ids.length === 0) {
      setKnowledgePointTitles({})
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
          setKnowledgePointTitles({})
        }
      })

    return () => {
      cancelled = true
    }
  }, [knowledgePoints, open, pointIdsKey])

  useEffect(() => {
    if (!open || !task || activeTab !== 'mapping') return

    const targets = Object.fromEntries(
      displayKnowledgePoints.map((point) => [point.id, getPointProgressValue(point.value)]),
    )
    const duration = 900
    const start = performance.now()

    setAnimatedProgress(Object.fromEntries(displayKnowledgePoints.map((point) => [point.id, 0])))

    let frameId = 0

    const tick = (now: number) => {
      const progress = Math.min((now - start) / duration, 1)
      const eased = 1 - (1 - progress) * (1 - progress)

      setAnimatedProgress(
        Object.fromEntries(
          displayKnowledgePoints.map((point) => [
            point.id,
            Math.round((targets[point.id] ?? 0) * eased),
          ]),
        ),
      )

      if (progress < 1) {
        frameId = requestAnimationFrame(tick)
      }
    }

    frameId = requestAnimationFrame(tick)

    return () => cancelAnimationFrame(frameId)
  }, [activeTab, displayKnowledgePoints, open, task])

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
          <span className={styles.doneTag}>
            {task.status === 'COMPLETED' ? '已完成' : '处理中'}
          </span>
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
                {summary ||
                  '本文档系统介绍了上传资料中的核心知识与实践技巧，覆盖关键概念、应用方式与典型案例，便于后续学习路径与资源联动。'}
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

            <button type="button" className={styles.expandBtn}>
              展开全部摘要
            </button>
          </section>
        ) : (
          <section
            className={`${styles.mappingCard} ${styles.mappingCardWithBg}`}
            style={{ backgroundImage: `url(${knowledgeBg})` }}
          >
            <div className={styles.mappingHead}>
              <h3 className={styles.sectionTitle}>知识点映射概览</h3>
              <span className={styles.mappingSub}>共映射 {stats.total} 个知识点</span>
            </div>

            <div className={styles.mappingBody}>
              <div className={styles.chartWrap}>
                <ReactECharts
                  option={donutOption}
                  notMerge
                  lazyUpdate
                  className={styles.chart}
                />
              </div>
            </div>

            <button type="button" className={styles.mappingBtn}>
              查看完整映射详情
            </button>

            <div className={styles.mappingProgress}>
              <div className={styles.mappingProgressHead}>
                <h4 className={styles.mappingProgressTitle}>知识点匹配进度</h4>
                <span className={styles.mappingProgressSub}>按知识点查看当前匹配度</span>
              </div>

              <div className={styles.mappingProgressList}>
                {displayKnowledgePoints.map((point) => {
                  const progress = animatedProgress[point.id] ?? 0
                  const targetProgress = getPointProgressValue(point.value)

                  return (
                    <div key={point.id} className={styles.mappingProgressItem}>
                      <div className={styles.mappingProgressMeta}>
                        <span className={styles.mappingProgressName}>{point.name}</span>
                        <strong className={styles.mappingProgressValue}>{targetProgress}%</strong>
                      </div>
                      <div className={styles.mappingProgressTrack} aria-hidden="true">
                        <span
                          className={styles.mappingProgressFill}
                          style={{ width: `${progress}%` }}
                        />
                      </div>
                    </div>
                  )
                })}
              </div>
            </div>
          </section>
        )}
      </aside>
    </div>
  )
}
