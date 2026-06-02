import { useNavigate } from 'react-router-dom'
import styles from './LearningResourcesEmptyPage.module.css'

type ResourceCard = {
  title: string
  subtitle: string
  tags: string[]
  meta: string
  rating: string
  tone: 'blue' | 'green' | 'violet' | 'gold' | 'teal'
  icon: 'monitor' | 'tree' | 'flow' | 'code' | 'stack' | 'file'
}

type TopicCard = {
  title: string
  subtitle: string
  meta: string
  tone: 'blue' | 'indigo' | 'sky' | 'orange'
  icon: 'oop' | 'spider' | 'cube' | 'chart'
}

type ProjectCard = {
  title: string
  subtitle: string
  tags: string[]
  meta: string
  tone: 'target' | 'report' | 'todo' | 'pie'
  icon: 'target' | 'report' | 'todo' | 'pie'
}

const basicResources: ResourceCard[] = [
  {
    title: 'Python开发环境\n与第一个程序',
    subtitle: '',
    tags: ['视频', '入门'],
    meta: '25 分钟',
    rating: '4.8',
    tone: 'blue',
    icon: 'monitor',
  },
  {
    title: '变量与数据类型',
    subtitle: '',
    tags: ['视频', '练习'],
    meta: '32 分钟',
    rating: '4.7',
    tone: 'green',
    icon: 'tree',
  },
  {
    title: '条件判断与循环',
    subtitle: '',
    tags: ['视频', '练习'],
    meta: '28 分钟',
    rating: '4.8',
    tone: 'violet',
    icon: 'flow',
  },
  {
    title: '函数与模块',
    subtitle: '',
    tags: ['文档', '练习'],
    meta: '18 分钟',
    rating: '4.6',
    tone: 'gold',
    icon: 'code',
  },
  {
    title: '列表、元组、字典\n与集合',
    subtitle: '',
    tags: ['视频', '练习'],
    meta: '35 分钟',
    rating: '4.8',
    tone: 'teal',
    icon: 'stack',
  },
  {
    title: '文件读写基础',
    subtitle: '',
    tags: ['视频', '文档'],
    meta: '22 分钟',
    rating: '4.7',
    tone: 'blue',
    icon: 'file',
  },
]

const hotTopics: TopicCard[] = [
  { title: '面向对象编程', subtitle: '类与对象、继承与多态', meta: '4.8 · 12 个资源', tone: 'blue', icon: 'oop' },
  { title: 'Python爬虫入门', subtitle: 'Requests + BeautifulSoup', meta: '4.7 · 10 个资源', tone: 'indigo', icon: 'spider' },
  { title: 'NumPy基础', subtitle: '数组计算与科学计算', meta: '4.8 · 9 个资源', tone: 'sky', icon: 'cube' },
  { title: 'Matplotlib数据可视化', subtitle: '绘图基础与常用图表', meta: '4.7 · 11 个资源', tone: 'orange', icon: 'chart' },
]

const projectCards: ProjectCard[] = [
  { title: '猜数字小游戏', subtitle: '实现一个命令行猜数字游戏', tags: ['初级', '练习'], meta: '30 分钟', tone: 'target', icon: 'target' },
  { title: '学生成绩管理系统', subtitle: '增删改查学生成绩信息', tags: ['中级', '项目'], meta: '2-3 小时', tone: 'report', icon: 'report' },
  { title: '简易待办清单', subtitle: '命令行待办事项管理', tags: ['初级', '练习'], meta: '1 小时', tone: 'todo', icon: 'todo' },
  { title: '数据可视化小练习', subtitle: '用 Matplotlib 绘制图表', tags: ['初级', '练习'], meta: '1-2 小时', tone: 'pie', icon: 'pie' },
]

const recentLearning = [
  { title: '变量与数据类型', subtitle: '上次学到：数据类型转换', progress: 60, tone: 'green', icon: 'tree' as const },
  { title: 'Python开发环境与第一个程序', subtitle: '上次学到：运行第一个程序', progress: 80, tone: 'blue', icon: 'monitor' as const },
  { title: '条件判断与循环', subtitle: '上次学到：for循环基础', progress: 40, tone: 'violet', icon: 'flow' as const },
]

const weekDays = ['一', '二', '三', '四', '五', '六', '日']
const weekDates = ['19', '20', '21', '22', '23', '24', '25']

function SearchIcon() {
  return (
    <svg viewBox="0 0 24 24" aria-hidden="true" className={styles.headerIcon}>
      <circle cx="11" cy="11" r="6.5" fill="none" stroke="currentColor" strokeWidth="2" />
      <path d="M16.2 16.2L21 21" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" />
    </svg>
  )
}

function SparkIcon() {
  return (
    <svg viewBox="0 0 24 24" aria-hidden="true" className={styles.sectionIcon}>
      <path
        d="M12 2l2.1 5.9L20 10l-5.9 2.1L12 18l-2.1-5.9L4 10l5.9-2.1L12 2Z"
        fill="none"
        stroke="currentColor"
        strokeWidth="1.9"
        strokeLinejoin="round"
      />
    </svg>
  )
}

function TopicIcon() {
  return (
    <svg viewBox="0 0 24 24" aria-hidden="true" className={styles.sectionIcon}>
      <path
        d="M10.5 4a2.5 2.5 0 1 1 0 5 2.5 2.5 0 0 1 0-5Zm6 7a2 2 0 1 1 0 4 2 2 0 0 1 0-4ZM7 14a2 2 0 1 1 0 4 2 2 0 0 1 0-4Z"
        fill="none"
        stroke="currentColor"
        strokeWidth="1.8"
      />
      <path
        d="M12.7 8.2l2.2 3M9.2 9l-1 5m1.6 2h4.7"
        fill="none"
        stroke="currentColor"
        strokeWidth="1.8"
        strokeLinecap="round"
      />
    </svg>
  )
}

function ProjectIcon() {
  return (
    <svg viewBox="0 0 24 24" aria-hidden="true" className={styles.sectionIcon}>
      <path d="M12 4v6M12 20v-6M4 12h6M20 12h-6" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" />
      <path
        d="M12 7.2l1 3.8 3.8 1-3.8 1-1 3.8-1-3.8-3.8-1 3.8-1 1-3.8Z"
        fill="none"
        stroke="currentColor"
        strokeWidth="1.8"
        strokeLinejoin="round"
      />
    </svg>
  )
}

function ResourceArtwork({ icon, tone }: { icon: ResourceCard['icon']; tone: ResourceCard['tone'] }) {
  return (
    <div className={`${styles.resourceArtwork} ${styles[`tone${tone}`]}`}>
      <div className={`${styles.artInner} ${styles[`art${icon}`]}`} />
    </div>
  )
}

function TopicArtwork({ icon, tone }: { icon: TopicCard['icon']; tone: TopicCard['tone'] }) {
  return (
    <div className={`${styles.topicArtwork} ${styles[`topic${tone}`]}`}>
      <div className={`${styles.topicInner} ${styles[`topicIcon${icon}`]}`} />
    </div>
  )
}

function ProjectArtwork({ icon, tone }: { icon: ProjectCard['icon']; tone: ProjectCard['tone'] }) {
  return (
    <div className={`${styles.projectArtwork} ${styles[`project${tone}`]}`}>
      <div className={`${styles.projectInner} ${styles[`projectIcon${icon}`]}`} />
    </div>
  )
}

export default function LearningResourcesEmptyPage() {
  const navigate = useNavigate()

  return (
    <section className={styles.page}>
      <div className={styles.heroGlow} />

      <header className={styles.header}>
        <div>
          <h1 className={styles.title}>学习资源</h1>
          <p className={styles.courseText}>
            当前课程：
            <span>Python基础</span>
          </p>
        </div>

        <div className={styles.headerActions}>
          <div className={styles.searchBar}>
            <SearchIcon />
            <span>搜索资源、专题、项目...</span>
          </div>
        </div>
      </header>

      <div className={styles.content}>
        <main className={styles.main}>
          <section className={styles.banner}>
            <div className={styles.bannerGraphic}>
              <div className={styles.bannerCard} />
              <div className={styles.bannerPath} />
              <div className={styles.bannerBubbleLeft} />
              <div className={styles.bannerBubbleRight} />
            </div>

            <div className={styles.bannerBody}>
              <p className={styles.bannerTitle}>暂无学习路径，学习资源将随路径自动生成，可定制路径或浏览基础资源</p>
            </div>

            <div className={styles.bannerActions}>
              <button
                type="button"
                className={styles.primaryButton}
                onClick={() => navigate('/workspace/learning-path')}
              >
                去定制学习路径
              </button>
            </div>
          </section>

          <section className={styles.section}>
            <div className={styles.sectionHeader}>
              <div className={styles.sectionTitleWrap}>
                <SparkIcon />
                <h2 className={styles.sectionTitle}>基础资源推荐</h2>
              </div>
              <button type="button" className={styles.moreButton}>查看全部</button>
            </div>

            <div className={styles.resourceGrid}>
              {basicResources.map((item) => (
                <article key={item.title} className={styles.resourceCard}>
                  <ResourceArtwork icon={item.icon} tone={item.tone} />
                  <div className={styles.resourceInfo}>
                    <h3 className={styles.cardTitle}>{item.title}</h3>
                    {item.subtitle ? <p className={styles.cardSubtitle}>{item.subtitle}</p> : null}
                    <div className={styles.tagRow}>
                      {item.tags.map((tag) => (
                        <span
                          key={tag}
                          className={`${styles.tag} ${tag === '视频' ? styles.tagBlue : tag === '文档' ? styles.tagBlueSoft : tag === '项目' ? styles.tagBlueSoft : tag === '入门' ? styles.tagGold : styles.tagGreen}`}
                        >
                          {tag}
                        </span>
                      ))}
                    </div>
                    <div className={styles.metaRow}>
                      <span>{item.meta}</span>
                      <span className={styles.metaDot}>·</span>
                      <span className={styles.star}>★</span>
                      <span>{item.rating}</span>
                    </div>
                  </div>
                </article>
              ))}
            </div>
          </section>

          <section className={styles.section}>
            <div className={styles.sectionHeader}>
              <div className={styles.sectionTitleWrap}>
                <TopicIcon />
                <h2 className={styles.sectionTitle}>热门专题</h2>
              </div>
              <button type="button" className={styles.moreButton}>查看全部</button>
            </div>

            <div className={styles.topicGrid}>
              {hotTopics.map((item) => (
                <article key={item.title} className={styles.topicCard}>
                  <TopicArtwork icon={item.icon} tone={item.tone} />
                  <div>
                    <h3 className={styles.topicTitle}>{item.title}</h3>
                    <p className={styles.topicSubtitle}>{item.subtitle}</p>
                    <p className={styles.topicMeta}>
                      <span className={styles.star}>★</span>
                      {item.meta}
                    </p>
                  </div>
                </article>
              ))}
            </div>
          </section>

          <section className={styles.section}>
            <div className={styles.sectionHeader}>
              <div className={styles.sectionTitleWrap}>
                <ProjectIcon />
                <h2 className={styles.sectionTitle}>实践项目推荐</h2>
              </div>
              <button type="button" className={styles.moreButton}>查看全部</button>
            </div>

            <div className={styles.projectGrid}>
              {projectCards.map((item) => (
                <article key={item.title} className={styles.projectCard}>
                  <ProjectArtwork icon={item.icon} tone={item.tone} />
                  <div className={styles.projectInfo}>
                    <h3 className={styles.projectTitle}>{item.title}</h3>
                    <p className={styles.projectSubtitle}>{item.subtitle}</p>
                    <div className={styles.tagRow}>
                      {item.tags.map((tag) => (
                        <span
                          key={tag}
                          className={`${styles.tag} ${tag === '项目' ? styles.tagBlueSoft : tag === '中级' ? styles.tagBlueSoft : styles.tagGreen}`}
                        >
                          {tag}
                        </span>
                      ))}
                    </div>
                    <div className={styles.projectMeta}>
                      <span className={styles.clock}>◷</span>
                      <span>{item.meta}</span>
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
              <h2 className={styles.sidebarTitle}>最近学习</h2>
              <button type="button" className={styles.sidebarLink}>查看全部</button>
            </div>

            <div className={styles.recentList}>
              {recentLearning.map((item) => (
                <article key={item.title} className={styles.recentItem}>
                  <ResourceArtwork icon={item.icon} tone={item.tone} />
                  <div className={styles.recentInfo}>
                    <h3 className={styles.recentTitle}>{item.title}</h3>
                    <p className={styles.recentSubtitle}>{item.subtitle}</p>
                    <p className={styles.recentProgressLabel}>进度 {item.progress}%</p>
                    <div className={styles.progressTrack}>
                      <span className={styles.progressFill} style={{ width: `${item.progress}%` }} />
                    </div>
                  </div>
                </article>
              ))}
            </div>
          </section>

          <section className={styles.sidebarCard}>
            <h2 className={styles.sidebarTitle}>今日推荐</h2>
            <article className={styles.dailyCard}>
              <div>
                <h3 className={styles.dailyTitle}>一文搞懂 Python 列表推导式</h3>
                <p className={styles.dailySubtitle}>用更简洁的代码处理列表数据</p>
                <div className={styles.dailyMeta}>
                  <span className={`${styles.tag} ${styles.tagBlueSoft}`}>文稿</span>
                  <span>8 分钟阅读</span>
                </div>
              </div>
              <div className={styles.bulbWrap}>
                <div className={styles.bulbCore} />
              </div>
            </article>
          </section>

          <section className={styles.sidebarCard}>
            <h2 className={styles.sidebarTitle}>学习数据</h2>
            <div className={styles.statsRow}>
              <div>
                <p className={styles.statsLabel}>今日学习</p>
                <p className={styles.statsValue}>35<span>分钟</span></p>
              </div>
              <div>
                <p className={styles.statsLabel}>本周学习</p>
                <p className={styles.statsValue}>5<span>天</span></p>
              </div>
              <div>
                <p className={styles.statsLabel}>累计学习</p>
                <p className={styles.statsValue}>18<span>小时</span></p>
              </div>
            </div>

            <div className={styles.calendar}>
              <div className={styles.weekRow}>
                {weekDays.map((day) => (
                  <span key={day} className={styles.weekDay}>{day}</span>
                ))}
              </div>
              <div className={styles.dateRow}>
                {weekDates.map((date) => (
                  <span
                    key={date}
                    className={date === '23' ? `${styles.dateCell} ${styles.dateActive}` : styles.dateCell}
                  >
                    {date}
                  </span>
                ))}
              </div>
              <div className={styles.dotRow}>
                {weekDates.map((date) => (
                  <span
                    key={date}
                    className={
                      date === '23'
                        ? `${styles.dayDot} ${styles.dayDotActive}`
                        : date === '24' || date === '21'
                          ? `${styles.dayDot} ${styles.dayDotStudy}`
                          : styles.dayDot
                    }
                  />
                ))}
              </div>
            </div>

            <div className={styles.legend}>
              <span><i className={styles.legendDotMuted} /> 学习日</span>
              <span><i className={styles.legendDotBlue} /> 今日</span>
            </div>
          </section>
        </aside>
      </div>
    </section>
  )
}
