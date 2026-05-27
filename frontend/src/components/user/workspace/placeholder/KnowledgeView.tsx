import styles from './PlaceholderView.module.css'

export default function KnowledgeView() {
  return (
    <div className={styles.container}>
      <div className={styles.icon}>✍️</div>
      <h2 className={styles.title}>答题</h2>
      <p className={styles.desc}>答题模块即将上线，敬请期待。</p>
    </div>
  )
}
