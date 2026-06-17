import { useCallback, useEffect, useMemo, useState } from 'react'
import {
  ArrowLeft,
  ArrowRight,
  Bookmark,
  CheckCircle2,
  ChevronDown,
  ChevronRight,
  CircleHelp,
  Clock3,
  FileQuestion,
  ListChecks,
  Loader2,
  Maximize2,
  RotateCcw,
  Search,
  Settings,
  SlidersHorizontal,
  Sparkles,
  X,
} from 'lucide-react'
import {
  getQuizQuestionDetail,
  listQuizQuestions,
  submitQuiz,
  type QuizQuestionDetail,
  type QuizQuestionListItem,
  type QuizResult,
} from '@/api/student/quiz'
import styles from './QuizPage.module.css'

type ChoiceKey = 'A' | 'B' | 'C' | 'D'
type QuestionFilter = 'all' | 'single' | 'multiple' | 'judge' | 'code'
type StatusFilter = 'all' | 'todo' | 'doing' | 'done'

type QuizGroup = {
  id: string
  title: string
  subtitle: string
  knowledgePoint: string
  pathLabel: string
  questionType: string
  difficulty: number
  questions: QuizQuestionListItem[]
}

type AnswerMap = Record<number, string>
type ResultMap = Record<number, QuizResult>

const typeTabs: Array<{ key: QuestionFilter; label: string }> = [
  { key: 'all', label: '全部' },
  { key: 'single', label: '单选题' },
  { key: 'multiple', label: '多选题' },
  { key: 'judge', label: '判断题' },
  { key: 'code', label: '编程题' },
]

function normalizeQuestionType(type?: string | null): QuestionFilter {
  const value = (type || '').toLowerCase()
  if (['single', 'single_choice', 'choice', 'radio'].includes(value)) return 'single'
  if (['multiple', 'multiple_choice', 'multi'].includes(value)) return 'multiple'
  if (['judge', 'true_false', 'boolean'].includes(value)) return 'judge'
  if (['code', 'programming', 'code_reading', 'fill'].includes(value)) return 'code'
  return 'single'
}

function formatQuestionType(type?: string | null) {
  const normalized = normalizeQuestionType(type)
  if (normalized === 'multiple') return '多选题'
  if (normalized === 'judge') return '判断题'
  if (normalized === 'code') return '编程题'
  return '单选题'
}

function formatDifficulty(difficulty?: number) {
  const value = difficulty || 1
  if (value <= 1) return '基础'
  if (value === 2) return '进阶'
  return '提高'
}

function difficultyClass(difficulty?: number) {
  const value = difficulty || 1
  if (value <= 1) return styles.levelBasic
  if (value === 2) return styles.levelMiddle
  return styles.levelHard
}

function normalizeOptionLabel(key: ChoiceKey, label?: string | null) {
  return (label || '').replace(new RegExp(`^${key}[.、\\s]+`, 'i'), '').trim()
}

function getQuestionOptions(detail: QuizQuestionDetail | null) {
  if (!detail) return []
  const candidates: Array<{ key: ChoiceKey; label?: string | null }> = [
    { key: 'A', label: detail.optionA },
    { key: 'B', label: detail.optionB },
    { key: 'C', label: detail.optionC },
    { key: 'D', label: detail.optionD },
  ]
  return candidates
    .map((item) => ({ key: item.key, label: normalizeOptionLabel(item.key, item.label) }))
    .filter((item) => item.label)
}

function buildGroups(items: QuizQuestionListItem[]) {
  const map = new Map<string, QuizQuestionListItem[]>()
  items.forEach((item) => {
    const knowledgePoint = item.knowledgePoint?.trim() || '综合练习'
    const type = normalizeQuestionType(item.questionType)
    const key = `${knowledgePoint}::${type}`
    const list = map.get(key) || []
    list.push(item)
    map.set(key, list)
  })

  return Array.from(map.entries()).map(([key, questions]) => {
    const first = questions[0]
    const knowledgePoint = first.knowledgePoint?.trim() || key.split('::')[0] || '综合练习'
    const avgDifficulty =
      Math.round(questions.reduce((sum, item) => sum + (item.difficulty || 1), 0) / Math.max(1, questions.length)) || 1
    const typeLabel = formatQuestionType(first.questionType)
    return {
      id: key,
      title: `${knowledgePoint} · ${formatDifficulty(avgDifficulty)}练习`,
      subtitle: `掌握 ${knowledgePoint} 的核心概念与常见题型`,
      knowledgePoint,
      pathLabel: `学习路径 > Python基础入门`,
      questionType: first.questionType,
      difficulty: avgDifficulty,
      questions,
    }
  })
}

function getGroupStatus(group: QuizGroup, answers: AnswerMap): StatusFilter {
  const answered = group.questions.filter((item) => answers[item.id]).length
  if (answered === 0) return 'todo'
  if (answered >= group.questions.length) return 'done'
  return 'doing'
}

function formatStatus(status: StatusFilter) {
  if (status === 'done') return '已完成'
  if (status === 'doing') return '进行中'
  return '未开始'
}

function makeFallbackGroups(): QuizGroup[] {
  const names = [
    'Python简介与环境搭建',
    '输入输出',
    '变量与数据类型',
    '运算符',
    '列表（List）',
    '字典（Dict）',
    '条件判断（if）',
    'for 循环与 range()',
    'while 循环',
    '函数定义与调用',
  ]

  return names.map((name, index) => {
    const difficulty = index < 4 ? 1 : index < 8 ? 2 : 3
    const total = [12, 8, 15, 10, 16, 14, 10, 12, 11, 18][index] || 12
    const questions = Array.from({ length: total }, (_, questionIndex) => ({
      id: -(index * 100 + questionIndex + 1),
      title: `${name} 第 ${questionIndex + 1} 题`,
      questionType: index % 5 === 2 ? 'multiple' : 'single',
      difficulty,
      knowledgePoint: name,
      status: 1,
    }))
    return {
      id: `fallback-${index}`,
      title: `${name} · ${formatDifficulty(difficulty)}练习`,
      subtitle: `掌握 ${name} 的基础用法与常见场景`,
      knowledgePoint: name,
      pathLabel: '学习路径 > Python基础入门',
      questionType: questions[0].questionType,
      difficulty,
      questions,
    }
  })
}

export default function QuizPage() {
  const [groups, setGroups] = useState<QuizGroup[]>([])
  const [loading, setLoading] = useState(true)
  const [loadFailed, setLoadFailed] = useState(false)
  const [groupSearch, setGroupSearch] = useState('')
  const [keywordSearch, setKeywordSearch] = useState('')
  const [typeFilter, setTypeFilter] = useState<QuestionFilter>('all')
  const [difficultyFilter, setDifficultyFilter] = useState('all')
  const [statusFilter, setStatusFilter] = useState<StatusFilter>('all')
  const [selectedGroupId, setSelectedGroupId] = useState<string | null>(null)
  const [activeIndex, setActiveIndex] = useState(0)
  const [drawerOpen, setDrawerOpen] = useState(false)
  const [detailMap, setDetailMap] = useState<Record<number, QuizQuestionDetail>>({})
  const [detailLoading, setDetailLoading] = useState(false)
  const [answers, setAnswers] = useState<AnswerMap>({})
  const [results, setResults] = useState<ResultMap>({})
  const [bookmarks, setBookmarks] = useState<Set<number>>(new Set())
  const [submitting, setSubmitting] = useState(false)

  const fetchGroups = useCallback(() => {
    setLoading(true)
    setLoadFailed(false)
    listQuizQuestions({ page: 1, size: 1000 })
      .then((res) => {
        const built = buildGroups(res.items || [])
        setGroups(built.length > 0 ? built : makeFallbackGroups())
      })
      .catch(() => {
        setGroups(makeFallbackGroups())
        setLoadFailed(true)
      })
      .finally(() => setLoading(false))
  }, [])

  useEffect(() => {
    fetchGroups()
  }, [fetchGroups])

  const selectedGroup = useMemo(
    () => groups.find((item) => item.id === selectedGroupId) || null,
    [groups, selectedGroupId],
  )

  const currentQuestionItem = selectedGroup?.questions[activeIndex] || null
  const currentDetail = currentQuestionItem ? detailMap[currentQuestionItem.id] || null : null
  const currentOptions = useMemo(() => getQuestionOptions(currentDetail), [currentDetail])
  const currentAnswer = currentQuestionItem ? answers[currentQuestionItem.id] || '' : ''
  const currentResult = currentQuestionItem ? results[currentQuestionItem.id] : undefined
  const answeredInGroup = selectedGroup?.questions.filter((item) => answers[item.id]).length || 0
  const progress = selectedGroup ? Math.round((answeredInGroup / Math.max(1, selectedGroup.questions.length)) * 100) : 0

  useEffect(() => {
    if (!drawerOpen || !currentQuestionItem) return
    if (currentQuestionItem.id < 0 || detailMap[currentQuestionItem.id]) return

    let active = true
    setDetailLoading(true)
    getQuizQuestionDetail(currentQuestionItem.id)
      .then((res) => {
        if (!active) return
        setDetailMap((prev) => ({ ...prev, [currentQuestionItem.id]: res }))
      })
      .finally(() => {
        if (active) setDetailLoading(false)
      })

    return () => {
      active = false
    }
  }, [currentQuestionItem, detailMap, drawerOpen])

  const filteredGroups = useMemo(() => {
    return groups.filter((group) => {
      const groupStatus = getGroupStatus(group, answers)
      const normalizedType = normalizeQuestionType(group.questionType)
      const titleMatch = group.title.toLowerCase().includes(groupSearch.trim().toLowerCase())
      const keyword = keywordSearch.trim().toLowerCase()
      const keywordMatch =
        !keyword ||
        group.knowledgePoint.toLowerCase().includes(keyword) ||
        group.pathLabel.toLowerCase().includes(keyword) ||
        group.questions.some((item) => item.title.toLowerCase().includes(keyword))
      const typeMatch = typeFilter === 'all' || normalizedType === typeFilter
      const difficultyMatch = difficultyFilter === 'all' || formatDifficulty(group.difficulty) === difficultyFilter
      const statusMatch = statusFilter === 'all' || groupStatus === statusFilter
      return titleMatch && keywordMatch && typeMatch && difficultyMatch && statusMatch
    })
  }, [answers, difficultyFilter, groupSearch, groups, keywordSearch, statusFilter, typeFilter])

  const stats = useMemo(() => {
    const questionTotal = groups.reduce((sum, group) => sum + group.questions.length, 0)
    const done = groups.filter((group) => getGroupStatus(group, answers) === 'done').length
    return [
      { label: '题组总数', value: groups.length || 0, unit: '个', icon: FileQuestion },
      { label: '题目总数', value: questionTotal || 0, unit: '题', icon: ListChecks },
      { label: '待完成', value: Math.max(0, groups.length - done), unit: '个', icon: Clock3 },
      { label: '已完成', value: done, unit: '个', icon: CheckCircle2 },
    ]
  }, [answers, groups])

  const openGroup = (group: QuizGroup) => {
    setSelectedGroupId(group.id)
    setActiveIndex(0)
    setDrawerOpen(true)
  }

  const closeDrawer = () => {
    setDrawerOpen(false)
  }

  const resetFilters = () => {
    setGroupSearch('')
    setKeywordSearch('')
    setTypeFilter('all')
    setDifficultyFilter('all')
    setStatusFilter('all')
  }

  const selectAnswer = (value: string) => {
    if (!currentQuestionItem || currentResult) return
    setAnswers((prev) => ({ ...prev, [currentQuestionItem.id]: value }))
  }

  const handleSubmitCurrent = async () => {
    if (!currentQuestionItem || !currentAnswer || currentQuestionItem.id < 0 || submitting) return
    setSubmitting(true)
    try {
      const result = await submitQuiz({
        questionId: currentQuestionItem.id,
        kpId: currentDetail?.knowledgePoint || currentQuestionItem.knowledgePoint || undefined,
        answer: currentAnswer,
      })
      setResults((prev) => ({ ...prev, [currentQuestionItem.id]: result }))
    } finally {
      setSubmitting(false)
    }
  }

  const handleSubmitGroup = async () => {
    if (!selectedGroup || submitting) return
    setSubmitting(true)
    try {
      const validQuestions = selectedGroup.questions.filter((item) => item.id > 0 && answers[item.id] && !results[item.id])
      for (const question of validQuestions) {
        const result = await submitQuiz({
          questionId: question.id,
          kpId: question.knowledgePoint || undefined,
          answer: answers[question.id],
        })
        setResults((prev) => ({ ...prev, [question.id]: result }))
      }
    } finally {
      setSubmitting(false)
    }
  }

  const toggleBookmark = () => {
    if (!currentQuestionItem) return
    setBookmarks((prev) => {
      const next = new Set(prev)
      if (next.has(currentQuestionItem.id)) next.delete(currentQuestionItem.id)
      else next.add(currentQuestionItem.id)
      return next
    })
  }

  const fallbackDetail: QuizQuestionDetail | null = currentQuestionItem && currentQuestionItem.id < 0
    ? {
        id: currentQuestionItem.id,
        title: '关于 Python 开发环境搭建，下列说法正确的是？',
        questionType: currentQuestionItem.questionType,
        optionA: 'Python 是一种高级编程语言，安装解释器后即可通过命令行或 IDE 编写并运行代码。',
        optionB: '只要安装了 IDE 就能直接运行 Python 代码，无需安装 Python 解释器。',
        optionC: 'Python 脚本文件的扩展名必须是 .py 才能被执行。',
        optionD: '环境变量的配置只对系统生效，与运行 Python 无关。',
        difficulty: currentQuestionItem.difficulty,
        knowledgePoint: currentQuestionItem.knowledgePoint,
        status: 1,
        analysis: '安装解释器是运行 Python 代码的基础，IDE 主要提供编辑、调试和运行辅助。',
      }
    : null

  const displayDetail = currentDetail || fallbackDetail
  const displayOptions = displayDetail ? getQuestionOptions(displayDetail) : currentOptions
  const isChoice = displayOptions.length > 0

  return (
    <section className={styles.page}>
      <header className={styles.header}>
        <div>
          <h1 className={styles.title}>练习题库</h1>
          <p className={styles.subtitle}>
            选择题组开始练习，右侧将弹出作答面板
            <button type="button" className={styles.helpLink}>
              <CircleHelp size={14} />
              使用说明
            </button>
          </p>
        </div>

        <button type="button" className={styles.manageButton}>
          <Settings size={15} />
          题库管理
        </button>
      </header>

      <section className={styles.statGrid}>
        {stats.map((item) => {
          const Icon = item.icon
          return (
            <article key={item.label} className={styles.statCard}>
              <span className={styles.statIcon}><Icon size={19} /></span>
              <span className={styles.statLabel}>{item.label}</span>
              <strong className={styles.statValue}>{item.value}<small>{item.unit}</small></strong>
            </article>
          )
        })}
      </section>

      <section className={styles.bankCard}>
        <div className={styles.searchRow}>
          <label className={styles.searchBox}>
            <Search size={16} />
            <input value={groupSearch} onChange={(event) => setGroupSearch(event.target.value)} placeholder="搜索题组名称" />
          </label>
          <label className={styles.searchBox}>
            <Search size={16} />
            <input value={keywordSearch} onChange={(event) => setKeywordSearch(event.target.value)} placeholder="搜索知识点/关键词" />
          </label>

          <div className={styles.typeTabs}>
            {typeTabs.map((tab) => (
              <button
                key={tab.key}
                type="button"
                className={typeFilter === tab.key ? `${styles.typeTab} ${styles.typeTabActive}` : styles.typeTab}
                onClick={() => setTypeFilter(tab.key)}
              >
                {tab.label}
              </button>
            ))}
          </div>
        </div>

        <div className={styles.filterRow}>
          <label className={styles.selectButton}>
            难度：
            <select value={difficultyFilter} onChange={(event) => setDifficultyFilter(event.target.value)}>
              <option value="all">全部</option>
              <option value="基础">基础</option>
              <option value="进阶">进阶</option>
              <option value="提高">提高</option>
            </select>
            <ChevronDown size={14} />
          </label>
          <label className={styles.selectButton}>
            所属路径：全部
            <select value="all" onChange={() => undefined} aria-label="所属路径">
              <option value="all">全部</option>
            </select>
            <ChevronDown size={14} />
          </label>
          <label className={styles.selectButton}>
            完成状态：
            <select value={statusFilter} onChange={(event) => setStatusFilter(event.target.value as StatusFilter)}>
              <option value="all">全部</option>
              <option value="todo">未开始</option>
              <option value="doing">进行中</option>
              <option value="done">已完成</option>
            </select>
            <ChevronDown size={14} />
          </label>
          <button type="button" className={styles.resetButton} onClick={resetFilters}>
            <RotateCcw size={14} />
            重置
          </button>
          <button type="button" className={styles.sortButton}>
            <SlidersHorizontal size={14} />
            默认排序
            <ChevronDown size={14} />
          </button>
        </div>

        {loadFailed ? <div className={styles.notice}>题库接口暂不可用，当前展示前端兜底题组样式。</div> : null}

        <div className={styles.table}>
          <div className={styles.tableHead}>
            <span>题组名称</span>
            <span>所属知识点 / 学习路径</span>
            <span>题型</span>
            <span>难度</span>
            <span>题目数</span>
            <span>预计时长</span>
            <span>完成状态</span>
            <span>操作</span>
          </div>

          <div className={styles.tableBody}>
            {loading ? (
              <div className={styles.loadingState}><Loader2 size={18} className={styles.spin} />题库加载中...</div>
            ) : filteredGroups.length === 0 ? (
              <div className={styles.emptyState}>暂无符合条件的题组</div>
            ) : (
              filteredGroups.map((group) => {
                const status = getGroupStatus(group, answers)
                const answered = group.questions.filter((item) => answers[item.id]).length
                const active = group.id === selectedGroupId && drawerOpen
                return (
                  <button
                    key={group.id}
                    type="button"
                    className={active ? `${styles.tableRow} ${styles.tableRowActive}` : styles.tableRow}
                    onClick={() => openGroup(group)}
                  >
                    <span className={styles.groupCell}>
                      <strong>{group.title}</strong>
                      <small>{group.subtitle}</small>
                    </span>
                    <span className={styles.pathCell}>
                      <strong>{group.knowledgePoint}</strong>
                      <small>{group.pathLabel}</small>
                    </span>
                    <span className={styles.tagGroup}>
                      <i>{formatQuestionType(group.questionType)}</i>
                      {normalizeQuestionType(group.questionType) !== 'single' ? <i>保留题</i> : null}
                    </span>
                    <span><em className={`${styles.levelTag} ${difficultyClass(group.difficulty)}`}>{formatDifficulty(group.difficulty)}</em></span>
                    <span>{group.questions.length} 题</span>
                    <span>{Math.max(8, Math.ceil(group.questions.length * 1.25))} 分钟</span>
                    <span className={styles.statusCell}>
                      <i className={`${styles.statusDot} ${styles[`status${status}`]}`} />
                      {formatStatus(status)}
                      {status === 'doing' ? <small>{answered}/{group.questions.length}</small> : null}
                    </span>
                    <span className={styles.actionCell}><ChevronRight size={18} /></span>
                  </button>
                )
              })
            )}
          </div>
        </div>
      </section>

      {drawerOpen && selectedGroup ? (
        <div className={styles.drawerLayer} role="presentation">
          <button type="button" className={styles.drawerBackdrop} aria-label="关闭答题面板" onClick={closeDrawer} />
          <aside className={styles.practiceDrawer}>
            <header className={styles.drawerHeader}>
              <div>
                <h2>{selectedGroup.title}</h2>
                <div className={styles.drawerBadges}>
                  <span>{formatQuestionType(selectedGroup.questionType)}</span>
                  <span className={styles.greenBadge}>{formatDifficulty(selectedGroup.difficulty)}</span>
                  <span>共 {selectedGroup.questions.length} 题</span>
                  <span>当前第 {activeIndex + 1} 题</span>
                </div>
              </div>
              <div className={styles.drawerActions}>
                <button type="button" aria-label="放大面板"><Maximize2 size={18} /></button>
                <button type="button" aria-label="关闭面板" onClick={closeDrawer}><X size={20} /></button>
              </div>
            </header>

            <div className={styles.drawerScroll}>
              <div className={styles.drawerProgress}>
                <span>答题进度</span>
                <div><i style={{ width: `${progress}%` }} /></div>
                <strong>{activeIndex + 1} / {selectedGroup.questions.length}</strong>
              </div>

              <article className={styles.questionPanel}>
                {detailLoading && !displayDetail ? (
                  <div className={styles.questionLoading}><Loader2 size={18} className={styles.spin} />题目加载中...</div>
                ) : (
                  <>
                    <h3>{activeIndex + 1}. {displayDetail?.title || currentQuestionItem?.title || '题目加载中'}</h3>
                    {displayDetail?.codeContent ? <pre className={styles.codeBlock}>{displayDetail.codeContent}</pre> : null}

                    {isChoice ? (
                      <div className={styles.optionList}>
                        {displayOptions.map((option) => {
                          const selected = currentAnswer === option.key
                          const submitted = Boolean(currentResult)
                          const correct = currentResult?.correctAnswer?.toUpperCase() === option.key
                          const wrong = submitted && selected && !correct
                          return (
                            <button
                              key={option.key}
                              type="button"
                              className={
                                correct
                                  ? `${styles.optionButton} ${styles.optionCorrect}`
                                  : wrong
                                    ? `${styles.optionButton} ${styles.optionWrong}`
                                    : selected
                                      ? `${styles.optionButton} ${styles.optionActive}`
                                      : styles.optionButton
                              }
                              onClick={() => selectAnswer(option.key)}
                            >
                              <span>{option.key}</span>
                              <strong>{option.label}</strong>
                            </button>
                          )
                        })}
                      </div>
                    ) : (
                      <textarea
                        className={styles.textAnswer}
                        value={currentAnswer}
                        onChange={(event) => selectAnswer(event.target.value)}
                        placeholder="请输入答案"
                      />
                    )}
                  </>
                )}
              </article>

              <section className={styles.navigatorCard}>
                <h3>题目导航</h3>
                <div className={styles.questionNav}>
                  {selectedGroup.questions.map((question, index) => {
                    const done = Boolean(answers[question.id])
                    return (
                      <button
                        key={question.id}
                        type="button"
                        className={
                          index === activeIndex
                            ? `${styles.navDot} ${styles.navDotCurrent}`
                            : done
                              ? `${styles.navDot} ${styles.navDotDone}`
                              : styles.navDot
                        }
                        onClick={() => setActiveIndex(index)}
                      >
                        {index + 1}
                      </button>
                    )
                  })}
                </div>
              </section>

              <section className={styles.sourceCard}>
                <h3>学习与来源</h3>
                <p><strong>来源知识点：</strong>{selectedGroup.knowledgePoint}</p>
                <p><strong>练习目标：</strong>掌握 {selectedGroup.knowledgePoint}、解题思路与常见误区。</p>
                {currentResult?.analysis || displayDetail?.analysis ? (
                  <p className={styles.analysisText}><strong>解析：</strong>{currentResult?.analysis || displayDetail?.analysis}</p>
                ) : null}
              </section>
            </div>

            <footer className={styles.drawerFooter}>
              <button type="button" onClick={() => setActiveIndex((index) => Math.max(0, index - 1))} disabled={activeIndex === 0}>
                <ArrowLeft size={16} />
                上一题
              </button>
              <button type="button" onClick={toggleBookmark} className={bookmarks.has(currentQuestionItem?.id || 0) ? styles.footerActive : undefined}>
                <Bookmark size={16} />
                标记本题
              </button>
              <button type="button" disabled={!currentResult}>
                查看解析
              </button>
              <button type="button" className={styles.nextButton} onClick={() => setActiveIndex((index) => Math.min(selectedGroup.questions.length - 1, index + 1))} disabled={activeIndex >= selectedGroup.questions.length - 1}>
                下一题
                <ArrowRight size={16} />
              </button>
              <button type="button" className={styles.submitButton} onClick={currentAnswer && !currentResult ? handleSubmitCurrent : handleSubmitGroup} disabled={submitting || !currentAnswer}>
                {submitting ? <Loader2 size={16} className={styles.spin} /> : <Sparkles size={16} />}
                {currentAnswer && !currentResult ? '提交本题' : '提交本题组'}
              </button>
            </footer>
          </aside>
        </div>
      ) : null}
    </section>
  )
}
