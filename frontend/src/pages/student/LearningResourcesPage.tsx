import { useCallback, useEffect, useMemo, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import {
  Bot,
  Check,
  ChevronRight,
  Code2,
  FileText,
  Image,
  Loader2,
  Play,
  RefreshCw,
  Sparkles,
  Trash2,
  Video,
  X,
} from 'lucide-react'
import { getResources, generateResource } from '@/api/student/resources'
import { generateImage, getVideoTaskStatus, submitVideoTask } from '@/api/student/media'
import { trackBehavior } from '@/utils/tracker'
import { usePathStore } from '@/utils/store/pathStore'
import { useArtifactStore } from '@/utils/store/artifactStore'
import { syncKpFromSearchParams } from '@/utils/navigation/workspaceNav'
import type { PathNode } from '@/utils/types/learning-path'
import type { LearningResource, ResourceType } from '@/utils/types/media-resource'
import styles from './LearningResourcesPage.module.css'

type ResourceTab = {
  type: ResourceType
  label: string
  icon: typeof FileText
}

type GenerateMode = 'image' | 'video'
type Difficulty = 'basic' | 'advanced'
type StyleType = 'diagram' | 'card' | 'animation'
type Purpose = 'concept' | 'quiz' | 'review'

const RESOURCE_TABS: ResourceTab[] = [
  { type: 'LESSON', label: '讲义梳理', icon: FileText },
  { type: 'CODE_CASE', label: '代码示例', icon: Code2 },
  { type: 'ILLUSTRATION', label: '示意图', icon: Image },
  { type: 'VIDEO_CLIP', label: '学习视频', icon: Play },
  { type: 'QUIZ', label: 'AI生成', icon: Sparkles },
]

const PATH_STEPS_FALLBACK = ['环境搭建', '基础语法', '条件判断', '循环结构', '综合练习']

const QUICK_REQUESTS = ['当前阶段知识点', '变量', '输入输出', '注释', '流程图', '对比图', '3分钟讲解视频']

const SAMPLE_VIDEOS = [
  {
    title: '变量与数据类型 3 分钟讲解',
    desc: '快速理解变量的定义、数据类型分类及示例，帮你建立清晰的知识框架。',
    time: '03:12',
    tone: 'blue',
  },
  {
    title: 'input() 与 print() 快速理解',
    desc: '3 分钟带你掌握输入输出的使用方法与常见场景，轻松上手编程交互。',
    time: '02:48',
    tone: 'green',
  },
]

function getResourceTitle(type: ResourceType) {
  const map: Record<ResourceType, string> = {
    LESSON: '基础语法讲义',
    CODE_CASE: 'Python 变量代码示例',
    QUIZ: '阶段练习题',
    ILLUSTRATION: '变量与数据类型关系图',
    VIDEO_CLIP: '基础语法学习视频',
  }
  return map[type]
}

function getDefaultPrompt(title: string) {
  return `帮我生成一张解释 ${title} 的示意图`
}

function getPreferenceText(difficulty: Difficulty, styleType: StyleType, purpose: Purpose) {
  const difficultyText = difficulty === 'basic' ? '基础' : '进阶'
  const styleText: Record<StyleType, string> = {
    diagram: '简洁图解',
    card: '卡片总结',
    animation: '动态讲解',
  }
  const purposeText: Record<Purpose, string> = {
    concept: '课堂预习',
    quiz: '课后复习',
    review: '考前速记',
  }
  return `难度：${difficultyText}；风格：${styleText[styleType]}；用途：${purposeText[purpose]}`
}

function normalizeMediaUrl(url?: string) {
  if (!url) return null
  if (/^(https?:|data:|blob:)/i.test(url)) return url
  return url.startsWith('/') ? url : `/${url}`
}

function buildImagePreviewTitle(source: string | undefined, fallbackTitle: string) {
  const mainPart = (source || '').split(/[，,；;\n]/)[0]
  const cleaned = mainPart
    .replace(/\s+/g, ' ')
    .replace(/^帮我生成一张/i, '')
    .replace(/^生成一张/i, '')
    .replace(/^解释/i, '')
    .replace(/的示意图$/i, '')
    .replace(/示意图$/i, '')
    .trim()

  const base = cleaned || fallbackTitle
  return base.length > 24 ? `${base.slice(0, 24)}...` : `${base} 示意图`
}

function upsertResource(list: LearningResource[], next: LearningResource) {
  const sameIndex = list.findIndex(
    (item) =>
      (next.id > 0 && item.id === next.id) ||
      (item.kpId === next.kpId && item.resourceType === next.resourceType && item.title === next.title),
  )
  if (sameIndex < 0) return [next, ...list]
  return list.map((item, index) => (index === sameIndex ? next : item))
}

function findResource(resources: LearningResource[], type: ResourceType) {
  return resources.find((item) => item.resourceType === type)
}

function StageProgress({ nodes, activeKpId }: { nodes: PathNode[]; activeKpId: string | null }) {
  const steps = nodes.length > 0
    ? nodes.map((node) => node.title)
    : PATH_STEPS_FALLBACK
  const activeIndex = nodes.length > 0
    ? Math.max(0, nodes.findIndex((node) => node.kpId === activeKpId))
    : 1

  return (
    <div className={styles.stageCard}>
      <div className={styles.currentStage}>
        当前阶段：<strong>{steps[activeIndex] ?? steps[0]}</strong>
      </div>
      <div className={styles.stageTrack}>
        {steps.map((step, index) => {
          const isActive = index === activeIndex
          const isDone = index < activeIndex
          return (
            <div key={step} className={styles.stageItem}>
              <div className={`${styles.stageLine} ${isDone || isActive ? styles.stageLineDone : ''}`} />
              <span className={`${styles.stageDot} ${isActive ? styles.stageDotActive : ''}`}>
                {index + 1}
              </span>
              <span className={`${styles.stageLabel} ${isActive ? styles.stageLabelActive : ''}`}>{step}</span>
            </div>
          )
        })}
      </div>
    </div>
  )
}

function VariablesDiagram({ imageUrl }: { imageUrl?: string }) {
  if (imageUrl) {
    return (
      <img className={styles.previewImage} src={imageUrl} alt="AI 生成学习图片" />
    )
  }

  return (
    <div className={styles.diagramCanvas}>
      <div className={styles.diagramLeft}>
        {['int（整数）  例：10', 'float（浮点数）  例：3.14', 'str（字符串）  例："hello"', 'bool（布尔值）  例：True/False'].map((item) => (
          <span key={item}>{item}</span>
        ))}
      </div>
      <div className={styles.diagramCenter}>变量<br />(Variable)</div>
      <div className={styles.diagramRight}>
        {['list（列表）  例：[1, 2, 3]', 'tuple（元组）  例：(1, 2, 3)', 'dict（字典）  例：{"a": 1}', 'set（集合）  例：{1, 2, 3}'].map((item) => (
          <span key={item}>{item}</span>
        ))}
      </div>
    </div>
  )
}

function FlowDiagram() {
  return (
    <div className={styles.flowCanvas}>
      {['开始', 'input() 接收用户输入', '处理数据', 'print() 输出结果', '结束'].map((item, index) => (
        <div key={item} className={styles.flowNodeWrap}>
          <div className={`${styles.flowNode} ${index === 0 || index === 4 ? styles.flowTerminal : ''}`}>
            {item}
          </div>
          {index < 4 && <ChevronRight className={styles.flowArrow} size={18} />}
        </div>
      ))}
      <div className={styles.flowNotes}>
        <span>从键盘读取数据</span>
        <span>进行计算或逻辑处理</span>
        <span>在控制台显示结果</span>
      </div>
    </div>
  )
}

function VideoPreview({ video, videoUrl }: { video?: LearningResource; videoUrl?: string }) {
  const src = video?.mediaUrl || videoUrl
  if (src) {
    return (
      <video className={styles.realVideo} src={src} controls />
    )
  }

  return (
    <div className={styles.videoList}>
      {SAMPLE_VIDEOS.map((item) => (
        <article key={item.title} className={styles.videoRow}>
          <div className={`${styles.videoThumb} ${styles[`videoThumb${item.tone}`]}`}>
            <span>Python 基础</span>
            <strong>{item.title.split(' ')[0]}</strong>
            <div className={styles.playCircle}><Play size={18} fill="currentColor" /></div>
            <em>{item.time}</em>
          </div>
          <div className={styles.videoInfo}>
            <h3>{item.title}</h3>
            <p>{item.desc}</p>
            <button type="button">立即观看</button>
          </div>
        </article>
      ))}
    </div>
  )
}

export default function LearningResourcesPage() {
  const { selectedKpId, nodes } = usePathStore()
  const chatResources = useArtifactStore((s) => s.resources)
  const resourceRefreshToken = useArtifactStore((s) => s.resourceRefreshToken)
  const [resources, setResources] = useState<LearningResource[]>([])
  const [activeTab, setActiveTab] = useState<ResourceType>('LESSON')
  const [mode, setMode] = useState<GenerateMode>('image')
  const [difficulty, setDifficulty] = useState<Difficulty>('basic')
  const [styleType, setStyleType] = useState<StyleType>('diagram')
  const [purpose, setPurpose] = useState<Purpose>('concept')
  const [prompt, setPrompt] = useState('')
  const [loading, setLoading] = useState(false)
  const [generating, setGenerating] = useState<ResourceType | 'bundle' | 'media' | null>(null)
  const [videoStatus, setVideoStatus] = useState<string | null>(null)
  const [generatedImageUrl, setGeneratedImageUrl] = useState<string | null>(null)
  const [generatedImageTitle, setGeneratedImageTitle] = useState<string | null>(null)
  const [generatedVideoUrl, setGeneratedVideoUrl] = useState<string | null>(null)
  const [imageModalOpen, setImageModalOpen] = useState(false)

  const fallbackNode = nodes[1] || nodes[0]
  const kpId = selectedKpId || fallbackNode?.kpId || 'python_basic_syntax'
  const currentNode = nodes.find((node) => node.kpId === kpId) || fallbackNode
  const currentTitle = currentNode?.title || '基础语法入门'

  const [searchParams] = useSearchParams()
  useEffect(() => {
    syncKpFromSearchParams(searchParams)
  }, [searchParams])

  const refreshResources = useCallback(async () => {
    if (!kpId) return
    setLoading(true)
    try {
      const next = await getResources(kpId)
      setResources(next)
      const first = next[0]
      if (first) trackBehavior({ kpId, action: 'view_resource', extra: first.title })
    } finally {
      setLoading(false)
    }
  }, [kpId])

  useEffect(() => {
    void refreshResources()
  }, [refreshResources])

  useEffect(() => {
    if (resourceRefreshToken > 0) {
      void refreshResources()
    }
  }, [resourceRefreshToken, refreshResources])

  const mergedResources = useMemo(() => {
    const chatForKp = chatResources.filter((item) => item.kpId === kpId)
    return chatForKp.reduce(upsertResource, resources)
  }, [chatResources, kpId, resources])

  const hasChatResources = chatResources.some((item) => item.kpId === kpId)

  const illustration = useMemo(() => findResource(mergedResources, 'ILLUSTRATION'), [mergedResources])
  const video = useMemo(() => findResource(mergedResources, 'VIDEO_CLIP'), [mergedResources])
  const lesson = useMemo(() => findResource(mergedResources, 'LESSON'), [mergedResources])
  const codeCase = useMemo(() => findResource(mergedResources, 'CODE_CASE'), [mergedResources])
  const previewImageUrl = illustration?.mediaUrl || generatedImageUrl || undefined
  const previewVideoUrl = video?.mediaUrl || generatedVideoUrl || undefined
  const previewImageTitle = generatedImageTitle
    || (previewImageUrl ? buildImagePreviewTitle(illustration?.generationPrompt || illustration?.title || prompt, currentTitle) : buildImagePreviewTitle(undefined, currentTitle))

  useEffect(() => {
    setPrompt(getDefaultPrompt(currentTitle))
  }, [currentTitle])

  useEffect(() => {
    setGeneratedImageUrl(null)
    setGeneratedImageTitle(null)
    setGeneratedVideoUrl(null)
    setImageModalOpen(false)
  }, [kpId])

  async function handleGenerateText(type: ResourceType) {
    if (!kpId || generating) return
    setGenerating(type)
    try {
      const next = await generateResource({
        kpId,
        resourceType: type,
        prompt: `${prompt}\n${getPreferenceText(difficulty, styleType, purpose)}`,
      })
      setResources((prev) => upsertResource(prev, next))
      setActiveTab(type)
    } finally {
      setGenerating(null)
    }
  }

  async function handleGenerateMedia() {
    if (!kpId || generating) return
    setGenerating('media')
    try {
      if (mode === 'image') {
        const result = await generateImage(kpId, `${prompt}\n${getPreferenceText(difficulty, styleType, purpose)}`)
        const imageUrl = normalizeMediaUrl(result.imageUrl)
        if (result.success && imageUrl) {
          setGeneratedImageUrl(imageUrl)
          setGeneratedImageTitle(buildImagePreviewTitle(prompt, currentTitle))
        }
        await refreshResources()
        setActiveTab('ILLUSTRATION')
        return
      }

      setVideoStatus('视频生成任务已提交')
      const task = await submitVideoTask(`${prompt}\n${getPreferenceText(difficulty, styleType, purpose)}`, 5)
      setVideoStatus('视频生成中，请稍候...')

      const poll = async (times: number): Promise<void> => {
        if (times <= 0) {
          setVideoStatus('生成时间较长，请稍后刷新资源')
          return
        }
        const status = await getVideoTaskStatus(task.taskId)
        if (status.status === 'done' || status.status === 'completed') {
          const videoUrl = normalizeMediaUrl(status.videoUrl)
          if (videoUrl) {
            setGeneratedVideoUrl(videoUrl)
          }
          setVideoStatus(null)
          await refreshResources()
          setActiveTab('VIDEO_CLIP')
          return
        }
        if (status.status === 'failed' || status.status === 'error') {
          setVideoStatus(status.error || '视频生成失败，请重试')
          return
        }
        setVideoStatus(`视频生成中... ${status.status || task.status}`)
        window.setTimeout(() => {
          void poll(times - 1)
        }, 5000)
      }

      await poll(60)
    } finally {
      setGenerating(null)
    }
  }

  async function handleRegenerateBundle() {
    if (!kpId || generating) return
    setGenerating('bundle')
    try {
      const next = await Promise.all(
        (['LESSON', 'CODE_CASE', 'QUIZ'] as ResourceType[]).map((resourceType) =>
          generateResource({ kpId, resourceType, prompt: `${currentTitle} 阶段推荐资源` }),
        ),
      )
      setResources((prev) => next.reduce(upsertResource, prev))
    } finally {
      setGenerating(null)
    }
  }

  const activeResource = findResource(mergedResources, activeTab)
  const openPreviewImage = () => {
    if (previewImageUrl) {
      setImageModalOpen(true)
    }
  }

  return (
    <section className={styles.page}>
      <header className={styles.header}>
        <div>
          <h1>学习资源展示</h1>
          <p>AI 根据当前学习阶段推荐资源，并支持你自主生成学习图片与学习视频</p>
        </div>
        <div className={styles.headerActions}>
          <button type="button" className={styles.ghostButton} onClick={refreshResources} disabled={loading}>
            <RefreshCw size={16} className={loading ? styles.spin : ''} />
            刷新资源
          </button>
          <button type="button" className={styles.primaryButton} onClick={handleRegenerateBundle} disabled={Boolean(generating)}>
            {generating === 'bundle' ? <Loader2 size={16} className={styles.spin} /> : <Sparkles size={16} />}
            重新生成资源
          </button>
        </div>
      </header>

      <StageProgress nodes={nodes} activeKpId={kpId} />

      {hasChatResources ? (
        <div className={styles.chatHint}>
          <Sparkles size={14} />
          聊天已为当前知识点生成资源，已合并展示
        </div>
      ) : null}

      <main className={styles.workspace}>
        <section className={styles.generatorPanel}>
          <div className={styles.panelHead}>
            <div>
              <h2>当前阶段推荐资源</h2>
              <p><Sparkles size={14} /> 不会的内容，可以直接用 AI 生成专题图解或视频帮助理解</p>
            </div>
          </div>

          <div className={styles.tabs}>
            {RESOURCE_TABS.map((tab) => {
              const Icon = tab.icon
              return (
                <button
                  key={tab.type}
                  type="button"
                  className={activeTab === tab.type ? styles.tabActive : styles.tab}
                  onClick={() => setActiveTab(tab.type)}
                >
                  <Icon size={16} />
                  {tab.label}
                </button>
              )
            })}
          </div>

          <div className={styles.aiBox}>
            <h3>AI 定制生成</h3>
            <div className={styles.modeSwitch}>
              <button type="button" className={mode === 'image' ? styles.modeActive : styles.modeButton} onClick={() => setMode('image')}>
                <Image size={15} />
                生成学习图片
              </button>
              <button type="button" className={mode === 'video' ? styles.modeActive : styles.modeButton} onClick={() => setMode('video')}>
                <Video size={15} />
                生成学习视频
              </button>
            </div>

            <label className={styles.promptLabel} htmlFor="resource-prompt">你想生成什么？</label>
            <textarea
              id="resource-prompt"
              className={styles.promptInput}
              value={prompt}
              maxLength={200}
              onChange={(event) => setPrompt(event.target.value)}
              placeholder="帮我生成一张解释 Python 变量与数据类型关系的示意图"
            />
            <div className={styles.counter}>{prompt.length}/200</div>

            <div className={styles.quickArea}>
              <span>快速补全需求</span>
              <div>
                {QUICK_REQUESTS.map((item) => (
                  <button key={item} type="button" onClick={() => setPrompt((prev) => `${prev}，${item}`)}>
                    {item}
                  </button>
                ))}
              </div>
            </div>

            <div className={styles.preferenceBox}>
              <h4>生成偏好</h4>
              <div className={styles.preferenceRow}>
                <span>难度：</span>
                <button type="button" className={difficulty === 'basic' ? styles.choiceActive : styles.choice} onClick={() => setDifficulty('basic')}>基础</button>
                <button type="button" className={difficulty === 'advanced' ? styles.choiceActive : styles.choice} onClick={() => setDifficulty('advanced')}>进阶</button>
              </div>
              <div className={styles.preferenceRow}>
                <span>风格：</span>
                <button type="button" className={styleType === 'diagram' ? styles.choiceActive : styles.choice} onClick={() => setStyleType('diagram')}>简洁图解</button>
                <button type="button" className={styleType === 'card' ? styles.choiceActive : styles.choice} onClick={() => setStyleType('card')}>卡片总结</button>
                <button type="button" className={styleType === 'animation' ? styles.choiceActive : styles.choice} onClick={() => setStyleType('animation')}>动态讲解</button>
              </div>
              <div className={styles.preferenceRow}>
                <span>用途：</span>
                <button type="button" className={purpose === 'concept' ? styles.choiceActive : styles.choice} onClick={() => setPurpose('concept')}>课堂预习</button>
                <button type="button" className={purpose === 'quiz' ? styles.choiceActive : styles.choice} onClick={() => setPurpose('quiz')}>课后复习</button>
                <button type="button" className={purpose === 'review' ? styles.choiceActive : styles.choice} onClick={() => setPurpose('review')}>考前速记</button>
              </div>
            </div>

            <div className={styles.agentTip}>
              <Bot size={22} />
              <div>
                <strong>AI 会结合当前阶段自动理解：</strong>
                <p>当前阶段：{currentTitle}；覆盖知识点：变量、输入输出、注释；推荐输出：示意图 + 短视频</p>
              </div>
            </div>

            <div className={styles.generateActions}>
              <button type="button" className={styles.primaryButton} onClick={handleGenerateMedia} disabled={Boolean(generating)}>
                {generating === 'media' ? <Loader2 size={16} className={styles.spin} /> : <Sparkles size={16} />}
                立即生成
              </button>
              <button type="button" className={styles.ghostButton} onClick={() => setPrompt('')}>
                <Trash2 size={16} />
                清空内容
              </button>
            </div>
          </div>
        </section>

        <section className={styles.previewPanel}>
          <div className={styles.previewHead}>
            <h2>生成结果预览</h2>
            <button type="button" onClick={refreshResources} disabled={loading}>
              {loading ? <Loader2 size={14} className={styles.spin} /> : null}
              查看全部历史生成
              <ChevronRight size={16} />
            </button>
          </div>

          <div className={styles.previewGrid}>
            <article className={styles.previewCard}>
              <h3>变量与数据类型关系图</h3>
              <h3 className={styles.previewDynamicTitle}>{previewImageTitle}</h3>
              <VariablesDiagram imageUrl={previewImageUrl} />
              <button type="button" className={styles.outlineButton} onClick={openPreviewImage} disabled={!previewImageUrl}>
                <Image size={14} />
                查看大图
              </button>
            </article>
            <article className={styles.previewCard}>
              <h3>输入输出流程图</h3>
              <FlowDiagram />
              <button type="button" className={styles.outlineButton}>
                <Image size={14} />
                查看大图
              </button>
            </article>
          </div>

          <VideoPreview video={video} videoUrl={previewVideoUrl} />

          <div className={styles.textResource}>
            <div>
              <h3>{activeResource?.title || getResourceTitle(activeTab)}</h3>
              <p>{activeResource?.content || lesson?.content || codeCase?.content || '选择上方资源类型，或点击“查看全部历史生成”让后端生成对应资源内容。'}</p>
            </div>
            <button type="button" onClick={() => handleGenerateText(activeTab)} disabled={Boolean(generating)}>
              {generating === activeTab ? '生成中...' : '生成该类型'}
            </button>
          </div>

          {videoStatus && <div className={styles.statusBar}>{videoStatus}</div>}
          <div className={styles.noticeBar}>
            <Check size={15} />
            你也可以切换到“生成学习视频”，让 AI 按当前知识点自动产出讲解短视频。
          </div>
        </section>
      </main>

      <section className={styles.recommend}>
        <h2>推荐使用方式</h2>
        {[
          ['1', '提出需求', '描述你想理解的知识点或需要的学习资源'],
          ['2', 'AI 生成图解/视频', 'AI 基于当前阶段知识，自动生成专题资源'],
          ['3', '用资源辅助学习', '通过图解和视频，加深理解，提升学习效果'],
        ].map(([index, title, desc]) => (
          <article key={index}>
            <span>{index}</span>
            <div>
              <h3>{title}</h3>
              <p>{desc}</p>
            </div>
          </article>
        ))}
      </section>

      {imageModalOpen && previewImageUrl && (
        <div className={styles.imageModalOverlay} role="dialog" aria-modal="true" aria-label="查看生成图片大图" onClick={() => setImageModalOpen(false)}>
          <div className={styles.imageModal} onClick={(event) => event.stopPropagation()}>
            <div className={styles.imageModalHead}>
              <h3>{previewImageTitle}</h3>
              <button type="button" aria-label="关闭大图预览" onClick={() => setImageModalOpen(false)}>
                <X size={18} />
              </button>
            </div>
            <div className={styles.imageModalBody}>
              <img src={previewImageUrl} alt={previewImageTitle} />
            </div>
          </div>
        </div>
      )}
    </section>
  )
}
