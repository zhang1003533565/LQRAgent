import { useNavigate } from 'react-router-dom'
import styles from './QuizEmptyPage.module.css'

type QuickPractice = {
  title: string
  description: string
  tone: 'blue' | 'green' | 'violet'
  icon: 'book' | 'wrong' | 'dice'
}

type ChapterRow = {
  index: number
  title: string
  summary: string
  count: string
}

type PracticeCard = {
  title: string
  description: string
  level: string
  levelTone: 'green' | 'orange' | 'violet'
  action: string
  tone: 'blue' | 'orange' | 'violet'
  icon: 'target' | 'fire' | 'warning'
}

type RecentCard = {
  title: string
  progress: number
  accuracy: number
  tone: 'green' | 'violet' | 'blue'
  icon: 'tree' | 'flow' | 'module'
}

const quickPractices: QuickPractice[] = [
  {
    title: '基础题库',
    description: '按知识点分章练习，打牢基础',
    tone: 'blue',
    icon: 'book',
  },
  {
    title: '错题回顾',
    description: '回顾错题，查漏补缺，巩固薄弱点',
    tone: 'green',
    icon: 'wrong',
  },
  {
    title: '随机练习',
    description: '随机抽题，检验学习效果',
    tone: 'violet',
    icon: 'dice',
  },
]

const chapterRows: ChapterRow[] = [
  {
    index: 1,
    title: 'Python开发环境与第一个程序',
    summary: '安装与配置、运行第一个程序、注释与输出',
    count: '12题',
  },
  {
    index: 2,
    title: '变量与数据类型',
    summary: '变量、整数与浮点数、字符串、布尔值、类型转换',
    count: '18题',
  },
  {
    index: 3,
    title: '条件判断与循环',
    summary: 'if 条件判断、for 循环、while 循环、break 与 continue',
    count: '20题',
  },
  {
    index: 4,
    title: '函数与模块',
    summary: '定义函数、参数与返回值、模块导入与使用',
    count: '16题',
  },
  {
    index: 5,
    title: '列表/元组/字典',
    summary: '列表操作、元组、字典、常用方法与遍历',
    count: '22题',
  },
  {
    index: 6,
    title: '文件读写基础',
    summary: '文件打开与关闭、读写文本文件、with 语句',
    count: '14题',
  },
]

const practiceCards: PracticeCard[] = [
  {
    title: '入门诊断题',
    description: '全面评估基础水平，定位薄弱点',
    level: '难度：基础',
    levelTone: 'green',
    action: '开始诊断',
    tone: 'blue',
    icon: 'target',
  },
  {
    title: '高频考点练习',
    description: '精选高频知识点，提升解题能力',
    level: '难度：中等',
    levelTone: 'orange',
    action: '开始练习',
    tone: 'orange',
    icon: 'fire',
  },
  {
    title: '语法易错题',
    description: '聚焦常见语法陷阱，减少失分',
    level: '难度：进阶',
    levelTone: 'violet',
    action: '开始练习',
    tone: 'violet',
    icon: 'warning',
  },
]

const recentCards: RecentCard[] = [
  { title: '变量与数据类型', progress: 70, accuracy: 85, tone: 'green', icon: 'tree' },
  { title: '条件判断与循环', progress: 40, accuracy: 78, tone: 'violet', icon: 'flow' },
  { title: '函数与模块', progress: 20, accuracy: 65, tone: 'blue', icon: 'module' },
]

const weeklyTrend = [
  { label: '周一', value: 12 },
  { label: '周二', value: 15 },
  { label: '周三', value: 18 },
  { label: '周四', value: 14 },
  { label: '周五', value: 18 },
  { label: '周六', value: 16 },
  { label: '周日', value: 10 },
]

function SearchIcon() {
  return (
    <svg viewBox="0 0 24 24" aria-hidden="true" className={styles.headerIcon}>
      <circle cx="11" cy="11" r="6.5" fill="none" stroke="currentColor" strokeWidth="2" />
      <path d="M16.2 16.2L21 21" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" />
    </svg>
  )
}

function BellIcon() {
  return (
    <svg viewBox="0 0 24 24" aria-hidden="true" className={styles.bellIcon}>
      <path
        d="M12 4.5a4 4 0 0 1 4 4v2.2c0 1 .28 1.97.81 2.82l1.01 1.63A1.2 1.2 0 0 1 16.8 17H7.2a1.2 1.2 0 0 1-1.02-1.85l1.01-1.63A5.4 5.4 0 0 0 8 10.7V8.5a4 4 0 0 1 4-4Z"
        fill="none"
        stroke="currentColor"
        strokeWidth="2"
      />
      <path d="M10 19a2 2 0 0 0 4 0" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" />
    </svg>
  )
}

function SectionThumbIcon() {
  return (
    <svg viewBox="0 0 24 24" aria-hidden="true" className={styles.sectionIcon}>
      <path
        d="M9 10V5.8A2.8 2.8 0 0 1 11.8 3l.8 4.2h3.2a2 2 0 0 1 2 2l-1 6a2.6 2.6 0 0 1-2.56 2.18H9.8A2.8 2.8 0 0 1 7 14.6V10h2Z"
        fill="none"
        stroke="currentColor"
        strokeWidth="1.8"
        strokeLinejoin="round"
      />
      <path d="M4.5 10H7v7H4.5a1.5 1.5 0 0 1-1.5-1.5v-4A1.5 1.5 0 0 1 4.5 10Z" fill="none" stroke="currentColor" strokeWidth="1.8" />
    </svg>
  )
}

function PracticeSectionIcon() {
  return (
    <svg viewBox="0 0 24 24" aria-hidden="true" className={styles.sectionIcon}>
      <path d="M12 2l1.8 7.2L21 11l-7.2 1.8L12 20l-1.8-7.2L3 11l7.2-1.8L12 2Z" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinejoin="round" />
    </svg>
  )
}

function BannerGraphic() {
  return (
    <div className={styles.bannerGraphic} aria-hidden="true">
      <div className={styles.bannerCard} />
      <div className={styles.bannerBadge} />
      <div className={styles.bannerPen} />
      <div className={styles.bannerBubbleLeft} />
      <div className={styles.bannerBubbleRight} />
    </div>
  )
}

function QuickIcon({ icon }: { icon: QuickPractice['icon'] }) {
  return <div className={`${styles.quickIllustration} ${styles[`quick${icon}`]}`} aria-hidden="true" />
}

function RecentIcon({ icon, tone }: { icon: RecentCard['icon']; tone: RecentCard['tone'] }) {
  return (
    <div className={`${styles.recentIcon} ${styles[`recentTone${tone}`]}`} aria-hidden="true">
      <div className={styles[`recent${icon}`]} />
    </div>
  )
}

function PracticeIcon({ icon, tone }: { icon: PracticeCard['icon']; tone: PracticeCard['tone'] }) {
  return (
    <div className={`${styles.practiceIcon} ${styles[`practiceTone${tone}`]}`} aria-hidden="true">
      <div className={styles[`practice${icon}`]} />
    </div>
  )
}

export default function QuizEmptyPage() {
  const navigate = useNavigate()

  return (
    <section className={styles.page}>
      <div className={styles.heroGlow} />

      <header className={styles.header}>
        <div>
          <h1 className={styles.title}>答题练习</h1>
          <p className={styles.courseText}>
            当前课程：
            <span>Python基础</span>
          </p>
        </div>

        <div className={styles.headerActions}>
          <div className={styles.searchBar}>
            <SearchIcon />
            <span>搜索视频、考题、项目...</span>
          </div>
        </div>
      </header>

      <div className={styles.content}>
        <main className={styles.main}>
          <section className={styles.banner}>
            <BannerGraphic />

            <div className={styles.bannerBody}>
              <p className={styles.bannerTitle}>
                还没有专属练习。完成一次入门诊断后，
                <br />
                系统会根据你的薄弱知识点推荐题目。
              </p>
            </div>

            <div className={styles.bannerActions}>
              <button type="button" className={styles.primaryButton}>开始入门诊断</button>
              <button
                type="button"
                className={styles.secondaryButton}
                onClick={() => navigate('/workspace/learning-path')}
              >
                生成学习路径
              </button>
            </div>
          </section>

          <section className={styles.section}>
            <div className={styles.sectionHeader}>
              <div className={styles.sectionTitleWrap}>
                <SectionThumbIcon />
                <h2 className={styles.sectionTitle}>可先练习这些内容</h2>
              </div>
            </div>

            <div className={styles.quickGrid}>
              {quickPractices.map((item) => (
                <article key={item.title} className={styles.quickCard}>
                  <QuickIcon icon={item.icon} />
                  <div className={styles.quickInfo}>
                    <h3 className={styles.quickTitle}>{item.title}</h3>
                    <p className={styles.quickDescription}>{item.description}</p>
                  </div>
                  <span className={styles.quickArrow}>›</span>
                </article>
              ))}
            </div>
          </section>

          <section className={styles.section}>
            <div className={styles.sectionHeader}>
              <h2 className={styles.sectionTitle}>基础题库</h2>
            </div>

            <section className={styles.tablePanel}>
              <div className={styles.tableHead}>
                <span>章节</span>
                <span>知识点概览</span>
                <span>题目数</span>
                <span>操作</span>
              </div>

              <div className={styles.tableBody}>
                {chapterRows.map((row) => (
                  <article key={row.index} className={styles.tableRow}>
                    <div className={styles.chapterCell}>
                      <span className={styles.chapterIndex}>{row.index}</span>
                      <span className={styles.chapterTitle}>{row.title}</span>
                    </div>
                    <div className={styles.summaryCell}>{row.summary}</div>
                    <div className={styles.countCell}>{row.count}</div>
                    <div className={styles.actionCell}>
                      <button type="button" className={styles.rowButton}>开始练习</button>
                    </div>
                  </article>
                ))}
              </div>

              <button type="button" className={styles.moreChapters}>
                查看全部章节（共12章）
              </button>
            </section>
          </section>

          <section className={styles.section}>
            <div className={styles.sectionHeader}>
              <div className={styles.sectionTitleWrap}>
                <PracticeSectionIcon />
                <h2 className={styles.sectionTitle}>推荐练习</h2>
              </div>
            </div>

            <div className={styles.practiceGrid}>
              {practiceCards.map((item) => (
                <article key={item.title} className={styles.practiceCard}>
                  <PracticeIcon icon={item.icon} tone={item.tone} />
                  <div className={styles.practiceInfo}>
                    <h3 className={styles.practiceTitle}>{item.title}</h3>
                    <p className={styles.practiceDescription}>{item.description}</p>
                    <div className={styles.practiceFooter}>
                      <span className={`${styles.practiceLevel} ${styles[`level${item.levelTone}`]}`}>{item.level}</span>
                      <button type="button" className={styles.startButton}>{item.action}</button>
                    </div>
                  </div>
                </article>
              ))}
            </div>
          </section>
        </main>

        <aside className={styles.sidebar}>
          <section className={styles.sidebarCard}>
            <div className={styles.sidebarHeader}>
              <h2 className={styles.sidebarTitle}>最近练习</h2>
              <button type="button" className={styles.sidebarLink}>查看全部</button>
            </div>

            <div className={styles.recentList}>
              {recentCards.map((item) => (
                <article key={item.title} className={styles.recentCard}>
                  <RecentIcon icon={item.icon} tone={item.tone} />
                  <div className={styles.recentInfo}>
                    <h3 className={styles.recentTitle}>{item.title}</h3>
                    <div className={styles.recentMeta}>
                      <span>练习进度 {item.progress}%</span>
                      <span>正确率 {item.accuracy}%</span>
                    </div>
                    <div className={styles.progressTrack}>
                      <span className={styles.progressFill} style={{ width: `${item.progress}%` }} />
                    </div>
                  </div>
                </article>
              ))}
            </div>
          </section>

          <section className={styles.sidebarCard}>
            <div className={styles.sidebarHeader}>
              <h2 className={styles.sidebarTitle}>今日目标</h2>
              <button type="button" className={styles.sidebarLink}>编辑</button>
            </div>

            <div className={styles.goalList}>
              <article className={styles.goalRow}>
                <span className={`${styles.goalDot} ${styles.goalDone}`}>✓</span>
                <div className={styles.goalContent}>
                  <div className={styles.goalLabelRow}>
                    <span>完成 20 题练习</span>
                    <strong>18 / 20</strong>
                  </div>
                  <div className={styles.goalTrack}><span style={{ width: '90%' }} /></div>
                </div>
              </article>

              <article className={styles.goalRow}>
                <span className={`${styles.goalDot} ${styles.goalGood}`}>✓</span>
                <div className={styles.goalContent}>
                  <div className={styles.goalLabelRow}>
                    <span>正确率达到 80%</span>
                    <strong>82%</strong>
                  </div>
                  <div className={`${styles.goalTrack} ${styles.goalTrackGreen}`}><span style={{ width: '82%' }} /></div>
                </div>
              </article>

              <article className={styles.goalRow}>
                <span className={styles.goalDot}>○</span>
                <div className={styles.goalContent}>
                  <div className={styles.goalLabelRow}>
                    <span>学习时长 30 分钟</span>
                    <strong>24 / 30 分钟</strong>
                  </div>
                  <div className={styles.goalTrack}><span style={{ width: '80%' }} /></div>
                </div>
              </article>
            </div>

            <div className={styles.streakCard}>
              <span className={styles.streakFire}>🔥</span>
              <span>连续学习</span>
              <strong>5 天</strong>
            </div>
          </section>

          <section className={styles.sidebarCard}>
            <div className={styles.sidebarHeader}>
              <h2 className={styles.sidebarTitle}>学习数据</h2>
              <button type="button" className={styles.sidebarLink}>查看详情</button>
            </div>

            <div className={styles.statsGrid}>
              <article className={styles.statCard}>
                <span className={styles.statLabel}>今日练习</span>
                <strong className={styles.statValue}>18<span>题</span></strong>
                <span className={styles.statCompare}>较昨日 ↑ 8</span>
              </article>
              <article className={styles.statCard}>
                <span className={styles.statLabel}>正确率</span>
                <strong className={styles.statValue}>82<span>%</span></strong>
                <span className={styles.statCompare}>较昨日 ↑ 12%</span>
              </article>
              <article className={styles.statCard}>
                <span className={styles.statLabel}>连续学习</span>
                <strong className={styles.statValue}>5<span>天</span></strong>
                <span className={styles.statCompare}>最长 7 天</span>
              </article>
            </div>

            <div className={styles.chartBlock}>
              <p className={styles.chartTitle}>本周练习趋势（题）</p>
              <div className={styles.chartBars}>
                {weeklyTrend.map((item) => (
                  <div key={item.label} className={styles.chartItem}>
                    <span className={styles.chartValue}>{item.value}</span>
                    <span className={styles.chartBar} style={{ height: `${item.value * 5}px` }} />
                    <span className={styles.chartLabel}>{item.label}</span>
                  </div>
                ))}
              </div>
            </div>
          </section>
        </aside>
      </div>
    </section>
  )
}
