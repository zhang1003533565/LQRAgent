import styles from './UploadPage.module.css'

const files = [
  { name: '导数与微分复习笔记.pdf', size: '2.34 MB', type: 'pdf' },
  { name: '函数求导解题整理.docx', size: '1.18 MB', type: 'word' },
] as const

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
  {
    title: '核心概念学习',
    desc: '系统复习导数的核心概念与性质，夯实基础。',
    icon: '🎓',
  },
  {
    title: '例题训练',
    desc: '精选典型例题精讲精练，提升解题能力。',
    icon: '▤',
  },
  {
    title: '查漏补缺',
    desc: '针对薄弱知识点专项练习，巩固提升。',
    icon: '✓',
  },
] as const

const suggestions = [
  {
    title: '建议学习顺序',
    desc: '链式法则 → 隐函数求导 → 例题训练',
    icon: '☰',
  },
  {
    title: '推荐资源',
    desc: '精选微课 / 题目 / 示范解题',
    icon: '▣',
  },
  {
    title: '画像联动',
    desc: '后续完成练习后可同步更新学习画像',
    icon: '●',
  },
] as const

function FileBadge({ type }: { type: 'pdf' | 'word' }) {
  return <span className={`${styles.fileBadge} ${type === 'pdf' ? styles.filePdf : styles.fileWord}`}>{type === 'pdf' ? 'PDF' : 'W'}</span>
}

export default function UploadPage() {
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
          <button type="button" className={styles.secondaryBtn}>
            <span className={styles.btnIcon}>↻</span>
            刷新任务
          </button>
          <button type="button" className={styles.primaryBtn}>
            <span className={styles.btnIcon}>☁</span>
            继续上传
          </button>
        </div>
      </header>

      <section className={styles.infoPanel}>
        <div className={styles.infoGrid}>
          <div className={styles.infoBlock}>
            <div className={styles.infoIcon}>▯</div>
            <div>
              <span className={styles.infoLabel}>当前课程：</span>
              <strong>高等数学（上）</strong>
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

            <div className={styles.uploadDropzone}>
              <div className={styles.uploadIcon}>⇪</div>
              <p className={styles.uploadText}>拖拽文件到此处，或点击选择文件</p>
              <button type="button" className={styles.uploadBtn}>
                选择文件
              </button>
            </div>

            <div className={styles.taskSection}>
              <h3 className={styles.subTitle}>分析任务列表</h3>
              <div className={styles.fileList}>
                {files.map((file) => (
                  <article key={file.name} className={styles.fileRow}>
                    <div className={styles.fileMain}>
                      <FileBadge type={file.type} />
                      <span className={styles.fileName}>{file.name}</span>
                    </div>
                    <span className={styles.fileSize}>{file.size}</span>
                    <span className={styles.fileStatus}>●</span>
                    <button type="button" className={styles.fileDelete}>
                      🗑
                    </button>
                  </article>
                ))}
              </div>
            </div>
          </section>

          <section className={styles.panel}>
            <div className={styles.sectionHead}>
              <h2 className={styles.panelTitle}>分析摘要</h2>
              <div className={styles.summaryMeta}>
                <span>完成时间：2024-05-16 14:32</span>
                <span className={styles.doneBadge}>分析完成</span>
              </div>
            </div>

            <div className={styles.summaryCard}>
              <div className={styles.summaryItem}>
                <span className={styles.summaryIcon}>▣</span>
                <div>
                  <h3>内容摘要</h3>
                  <p>本资料重点梳理了导数定义、几何意义、求导法则与隐函数求导，对典型例题进行了归纳和解析，整体结构清晰。</p>
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
        </div>

        <div className={styles.rightColumn}>
          <section className={styles.panel}>
            <div className={styles.sectionHead}>
              <h2 className={styles.panelTitle}>映射知识点</h2>
              <span className={styles.sectionMeta}>点击知识点可联动到学习路径</span>
            </div>

            <div className={styles.knowledgeCard}>
              {knowledgePoints.map((point) => (
                <button key={point.name} type="button" className={styles.knowledgeRow}>
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
                    <button type="button" className={styles.pathBtn}>
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
