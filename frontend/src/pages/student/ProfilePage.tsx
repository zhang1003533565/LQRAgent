import { useState, useEffect, useCallback } from 'react'
import ReactECharts from 'echarts-for-react'
import { getProfileDetail, patchProfile } from '@/api/student/profile'
import type { ProfileDetail } from '@/utils/types/profile'
import {
  RefreshCw,
  Download,
  User,
  Lightbulb,
  AlertTriangle,
  ArrowRight,
  MoreHorizontal,
  Target,
  Brain,
  Zap,
  BookOpen,
  Clock,
  BarChart3,
} from 'lucide-react'

/* ===== 数据清洗 ===== */

/** 将 kp_xxx 技术 ID 转译为中文名称 */
function sanitizeKpTitle(raw: string): string {
  // 已知映射表
  const KNOWN_MAP: Record<string, string> = {
    kp_python_function: 'Python 函数定义与调用',
    kp_python_closure: '闭包机制与作用域',
    kp_python_decorator: 'Python 装饰器',
    kp_python_variable: 'Python 变量与数据类型',
    kp_python_control_flow: 'Python 控制流',
    kp_python_class: 'Python 类与面向对象',
    kp_python_module: 'Python 模块与包管理',
    kp_python_exception: 'Python 异常处理',
    kp_python_file_io: 'Python 文件读写',
    kp_python_list: 'Python 列表与元组',
    kp_python_dict: 'Python 字典与集合',
    kp_python_generator: 'Python 生成器与迭代器',
    kp_python_lambda: 'Python Lambda 表达式',
    kp_python_regex: 'Python 正则表达式',
    kp_python_threading: 'Python 多线程与并发',
    kp_python_async: 'Python 异步编程',
    kp_math_linear_algebra: '线性代数基础',
    kp_math_probability: '概率论基础',
    kp_math_statistics: '统计学基础',
    kp_ml_supervised: '监督学习',
    kp_ml_unsupervised: '无监督学习',
    kp_ml_neural_network: '神经网络基础',
    kp_ml_deep_learning: '深度学习入门',
    kp_data_pandas: 'Pandas 数据分析',
    kp_data_numpy: 'NumPy 数值计算',
    kp_data_matplotlib: 'Matplotlib 数据可视化',
    kp_data_sklearn: 'Scikit-learn 机器学习',
    kp_placeholder_1: '示例知识点',
  }

  if (KNOWN_MAP[raw]) return KNOWN_MAP[raw]

  // 通用清洗：去前缀、下划线转空格、首字母大写
  return raw
    .replace(/^kp_/, '')
    .replace(/dynamic_\d+/g, '')
    .replace(/_/g, ' ')
    .replace(/\b\w/g, (c) => c.toUpperCase())
    .trim()
}

/* ===== 维度评分计算 ===== */

interface DimensionScores {
  knowledge: number
  application: number
  efficiency: number
  thinking: number
  habit: number
  attitude: number
}

function computeScores(profile: ProfileDetail): DimensionScores {
  const km = profile.knowledgeMap ?? []
  const total = km.length || 1
  const mastered = km.filter((k) => k.status === 'MASTERED').length
  const avgMastery =
    km.length > 0
      ? Math.round(km.reduce((s, k) => s + (k.mastery ?? 0), 0) / km.length)
      : 0

  return {
    knowledge: Math.max(10, avgMastery),
    application: Math.max(10, Math.min(100, avgMastery + 5)),
    efficiency: profile.learningPace === 'FAST' ? 85 : profile.learningPace === 'SLOW' ? 55 : 70,
    thinking: Math.max(10, Math.min(100, Math.round((mastered / total) * 100) + 10)),
    habit: profile.cognitiveStyle ? 70 : 45,
    attitude: profile.streakDays ? Math.min(100, 50 + (profile.streakDays ?? 0) * 3) : 50,
  }
}

/* ===== 洞察生成 ===== */

interface Insights {
  strengths: string[]
  weaknesses: string[]
  nextSteps: string[]
}

function generateInsights(profile: ProfileDetail, scores: DimensionScores): Insights {
  const strengths: string[] = []
  const weaknesses: string[] = []
  const nextSteps: string[] = []

  if (scores.knowledge >= 70) strengths.push('知识掌握扎实，基础概念理解透彻')
  if (scores.attitude >= 70) strengths.push('学习态度积极，持续性强')
  if (scores.application >= 70) strengths.push('应用能力良好，能将知识用于实践')
  if (scores.efficiency >= 70) strengths.push('学习效率高，时间利用率优秀')
  if (strengths.length === 0) strengths.push('各维度均有提升空间，持续学习是关键')

  if (scores.knowledge < 50) weaknesses.push('知识掌握不足，需加强基础概念学习')
  if (scores.efficiency < 60) weaknesses.push('学习效率偏低，建议优化学习方法')
  if (scores.habit < 55) weaknesses.push('学习习惯尚未形成，建议制定固定学习计划')
  if (scores.thinking < 50) weaknesses.push('思维能力有待提升，建议多做综合练习')

  // 从 knowledgeMap 提取薄弱点
  const weakKps = (profile.knowledgeMap ?? [])
    .filter((k) => (k.mastery ?? 0) < 40)
    .map((k) => sanitizeKpTitle(k.kpId || k.title))
    .slice(0, 4)
  if (weakKps.length > 0) {
    weaknesses.push(`薄弱知识点：${weakKps.join('、')}`)
  }

  if (weakKps.length > 0) {
    nextSteps.push(`针对「${weakKps[0]}」进行专项突破练习`)
  }
  if (scores.efficiency < 70) {
    nextSteps.push('采用番茄工作法，每天固定 2 个 25 分钟学习块')
  }
  if (scores.habit < 60) {
    nextSteps.push('建立每日学习打卡习惯，连续 7 天可形成初步惯性')
  }
  nextSteps.push('完成当前学习路径的下一个阶段节点')

  return { strengths, weaknesses, nextSteps }
}

/* ===== 维度定义 ===== */

const DIMENSION_DEFS = [
  { key: 'knowledge', label: '知识掌握', icon: BookOpen, color: '#3b82f6', bg: '#eff6ff' },
  { key: 'application', label: '应用能力', icon: Zap, color: '#8b5cf6', bg: '#f5f3ff' },
  { key: 'efficiency', label: '学习效率', icon: Clock, color: '#10b981', bg: '#ecfdf5' },
  { key: 'thinking', label: '思维能力', icon: Brain, color: '#f59e0b', bg: '#fffbeb' },
  { key: 'habit', label: '学习习惯', icon: Target, color: '#ef4444', bg: '#fef2f2' },
  { key: 'attitude', label: '学习态度', icon: BarChart3, color: '#06b6d4', bg: '#ecfeff' },
] as const

/* ===== 子组件 ===== */

function KnowledgeProgressBar({
  title,
  mastery,
  animated,
  delay,
}: {
  title: string
  mastery: number
  animated: boolean
  delay: number
}) {
  const isMastered = mastery >= 80
  const color = isMastered ? '#22c55e' : mastery >= 40 ? '#3b82f6' : '#ef4444'

  return (
    <div className="flex items-center gap-3 py-2">
      <span className="min-w-0 flex-1 truncate text-[13px] font-medium text-slate-700">
        {title}
      </span>
      <div className="flex w-[120px] items-center gap-2">
        <div className="h-2 flex-1 overflow-hidden rounded-full bg-slate-100">
          <div
            className="h-full rounded-full transition-all duration-700 ease-out"
            style={{
              width: animated ? `${mastery}%` : '0%',
              background: `linear-gradient(90deg, ${color}dd, ${color})`,
              transitionDelay: `${delay}ms`,
            }}
          />
        </div>
        <span className="w-9 text-right text-[11px] font-semibold text-slate-500">
          {mastery}%
        </span>
      </div>
    </div>
  )
}

/* ===== 主组件 ===== */

export default function ProfilePage() {
  const [profile, setProfile] = useState<ProfileDetail | null>(null)
  const [loading, setLoading] = useState(true)
  const [refreshing, setRefreshing] = useState(false)
  const [animated, setAnimated] = useState(false)

  const fetchData = useCallback(async () => {
    try {
      const detail = await getProfileDetail()
      setProfile(detail)
    } catch {
      // ignore
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    fetchData()
  }, [fetchData])

  useEffect(() => {
    if (!loading && profile) {
      const raf = requestAnimationFrame(() =>
        requestAnimationFrame(() => setAnimated(true)),
      )
      return () => cancelAnimationFrame(raf)
    }
  }, [loading, profile])

  const handleRefresh = async () => {
    setRefreshing(true)
    try {
      await patchProfile({})
      await fetchData()
    } finally {
      setRefreshing(false)
    }
  }

  const handleExport = () => {
    if (!profile) return
    const scores = computeScores(profile)
    const insights = generateInsights(profile, scores)
    const lines = [
      '# 学习画像报告',
      '',
      '## 六维度评分',
      ...DIMENSION_DEFS.map(
        (d) => `- ${d.label}：${scores[d.key as keyof DimensionScores]}`,
      ),
      '',
      '## 优势',
      ...insights.strengths.map((s) => `- ${s}`),
      '',
      '## 短板',
      ...insights.weaknesses.map((w) => `- ${w}`),
      '',
      '## 下一步',
      ...insights.nextSteps.map((n) => `- ${n}`),
    ]
    const blob = new Blob([lines.join('\n')], { type: 'text/markdown' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = '学习画像报告.md'
    a.click()
    URL.revokeObjectURL(url)
  }

  if (loading) {
    return (
      <div className="flex h-full items-center justify-center bg-[#f8fafc]">
        <div className="flex items-center gap-2 text-sm text-slate-400">
          <div className="h-4 w-4 animate-spin rounded-full border-2 border-slate-200 border-t-blue-400" />
          加载中…
        </div>
      </div>
    )
  }

  if (!profile) {
    return (
      <div className="flex h-full items-center justify-center bg-[#f8fafc]">
        <div className="text-center">
          <User size={32} className="mx-auto mb-2 text-slate-300" />
          <p className="text-sm text-slate-400">暂无画像数据</p>
        </div>
      </div>
    )
  }

  const scores = computeScores(profile)
  const insights = generateInsights(profile, scores)
  const knowledgeMap = (profile.knowledgeMap ?? []).map((k) => ({
    ...k,
    displayTitle: sanitizeKpTitle(k.kpId || k.title),
  }))

  const dimensionCards = DIMENSION_DEFS.map((d) => ({
    ...d,
    value: scores[d.key as keyof DimensionScores],
  }))

  return (
    <div className="flex h-full min-h-0 flex-col overflow-y-auto bg-[#f8fafc]">
      {/* 页面头部 */}
      <div className="flex items-center justify-between border-b border-slate-100 bg-white px-6 py-4">
        <div>
          <h1 className="text-lg font-bold text-slate-800">学习画像</h1>
          <p className="mt-0.5 text-[12px] text-slate-400">
            基于学习数据生成的多维度能力画像与动态分析
          </p>
        </div>
        <div className="flex items-center gap-2">
          <button
            type="button"
            onClick={handleRefresh}
            disabled={refreshing}
            className="inline-flex items-center gap-1.5 rounded-lg border border-slate-200 px-3 py-1.5 text-xs font-medium text-slate-600 transition-colors hover:bg-slate-50 disabled:opacity-50"
          >
            <RefreshCw size={12} className={refreshing ? 'animate-spin' : ''} />
            {refreshing ? '刷新中…' : '刷新画像'}
          </button>
          <button
            type="button"
            onClick={handleExport}
            className="inline-flex items-center gap-1.5 rounded-lg border border-slate-200 px-3 py-1.5 text-xs font-medium text-slate-600 transition-colors hover:bg-slate-50"
          >
            <Download size={12} />
            导出画像
          </button>
        </div>
      </div>

      <div className="space-y-6 px-6 py-5">
        {/* ===== 第一层：雷达图 + 知识点掌握（40% + 60%） ===== */}
        <div className="grid grid-cols-1 gap-6 lg:grid-cols-[40%_1fr]">
          {/* 左侧：能力雷达图 */}
          <div className="rounded-xl border border-slate-100 bg-white p-6 shadow-sm">
            <h2 className="mb-1 text-[14px] font-semibold text-slate-700">能力雷达图</h2>
            <p className="mb-4 text-[12px] text-slate-400">六维能力模型可视化</p>
            <ReactECharts
              option={{
                radar: {
                  center: ['50%', '52%'],
                  radius: '65%',
                  indicator: dimensionCards.map((d) => ({
                    name: d.label,
                    max: 100,
                  })),
                  axisName: {
                    color: '#94a3b8',
                    fontSize: 12,
                    fontWeight: 600,
                    padding: [3, 5],
                  },
                  splitArea: {
                    areaStyle: { color: ['transparent', 'transparent'] },
                  },
                  splitLine: {
                    lineStyle: { color: '#f1f5f9', width: 1 },
                  },
                  axisLine: {
                    lineStyle: { color: '#f1f5f9', width: 1 },
                  },
                },
                series: [
                  {
                    type: 'radar',
                    data: [
                      {
                        value: dimensionCards.map((d) => d.value),
                        name: '能力画像',
                        areaStyle: { color: 'rgba(59,130,246,0.08)' },
                        lineStyle: { color: '#3b82f6', width: 2 },
                        itemStyle: { color: '#3b82f6' },
                        symbol: 'circle',
                        symbolSize: 5,
                      },
                    ],
                  },
                ],
              }}
              style={{ height: 300 }}
              opts={{ renderer: 'svg' }}
            />
          </div>

          {/* 右侧：知识点掌握流 */}
          <div className="rounded-xl border border-slate-100 bg-white p-6 shadow-sm">
            <div className="mb-4 flex items-center justify-between">
              <div>
                <h2 className="text-[14px] font-semibold text-slate-700">知识点掌握流</h2>
                <p className="text-[12px] text-slate-400">
                  已掌握 {knowledgeMap.filter((k) => (k.mastery ?? 0) >= 80).length}/
                  {knowledgeMap.length} 个知识点
                </p>
              </div>
              <button
                type="button"
                className="flex h-7 w-7 items-center justify-center rounded-md text-slate-300 transition-colors hover:bg-slate-50 hover:text-slate-500"
              >
                <MoreHorizontal size={16} />
              </button>
            </div>

            {knowledgeMap.length > 0 ? (
              <div className="max-h-[360px] overflow-y-auto pr-1">
                {/* 双列布局 */}
                <div className="grid grid-cols-1 gap-x-6 gap-y-0 sm:grid-cols-2">
                  {knowledgeMap.map((kp, i) => (
                    <KnowledgeProgressBar
                      key={kp.kpId}
                      title={kp.displayTitle}
                      mastery={kp.mastery ?? 0}
                      animated={animated}
                      delay={i * 60}
                    />
                  ))}
                </div>
              </div>
            ) : (
              <p className="py-8 text-center text-[13px] text-slate-400">
                暂无知识点数据，完成学习后自动生成
              </p>
            )}
          </div>
        </div>

        {/* ===== 第二层：核心洞察三栏 ===== */}
        <div className="grid grid-cols-1 gap-6 md:grid-cols-3">
          {/* 左卡：优势识别 */}
          <div className="rounded-xl border border-slate-100 bg-white p-6 shadow-sm">
            <div className="mb-4 flex items-center gap-3">
              <div className="flex h-9 w-9 items-center justify-center rounded-lg bg-blue-50">
                <Lightbulb size={18} className="text-blue-500" />
              </div>
              <h3 className="text-[14px] font-semibold text-slate-700">
                模式识别与认知优势
              </h3>
            </div>
            <ul className="space-y-3">
              {insights.strengths.map((s, i) => (
                <li
                  key={i}
                  className="flex items-start gap-2 text-[14px] leading-relaxed text-slate-600"
                >
                  <span className="mt-1.5 h-1.5 w-1.5 shrink-0 rounded-full bg-blue-400" />
                  {s}
                </li>
              ))}
            </ul>
          </div>

          {/* 中卡：短板检测 */}
          <div className="rounded-xl border border-slate-100 bg-white p-6 shadow-sm">
            <div className="mb-4 flex items-center gap-3">
              <div className="flex h-9 w-9 items-center justify-center rounded-lg bg-red-50">
                <AlertTriangle size={18} className="text-red-400" />
              </div>
              <h3 className="text-[14px] font-semibold text-slate-700">
                算法与代码短板检测
              </h3>
            </div>
            <ul className="space-y-3">
              {insights.weaknesses.map((w, i) => (
                <li
                  key={i}
                  className="flex items-start gap-2 text-[14px] leading-relaxed text-slate-600"
                >
                  <span className="mt-1.5 h-1.5 w-1.5 shrink-0 rounded-full bg-red-400" />
                  {w}
                </li>
              ))}
            </ul>
          </div>

          {/* 右卡：下一步行动 */}
          <div className="rounded-xl border border-slate-100 bg-white p-6 shadow-sm">
            <div className="mb-4 flex items-center gap-3">
              <div className="flex h-9 w-9 items-center justify-center rounded-lg bg-emerald-50">
                <ArrowRight size={18} className="text-emerald-500" />
              </div>
              <h3 className="text-[14px] font-semibold text-slate-700">
                多智能体下一阶段特训建议
              </h3>
            </div>
            <ul className="space-y-3">
              {insights.nextSteps.map((step, i) => (
                <li
                  key={i}
                  className="flex items-start gap-2 text-[14px] leading-relaxed text-slate-600"
                >
                  <span className="mt-1.5 flex h-5 w-5 shrink-0 items-center justify-center rounded-full bg-emerald-50 text-[10px] font-bold text-emerald-500">
                    {i + 1}
                  </span>
                  {step}
                </li>
              ))}
            </ul>
          </div>
        </div>
      </div>
    </div>
  )
}
