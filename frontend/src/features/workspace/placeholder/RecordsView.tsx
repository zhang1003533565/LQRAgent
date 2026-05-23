import styles from './PlaceholderView.module.css'

export default function RecordsView() {
  return (
    <div className={styles.container}>
      <div className={styles.icon}>📚</div>
      <h2 className={styles.title}>学习资源展示</h2>
      <p className={styles.desc}>学习资源展示模块即将上线，敬请期待。</p>
    </div>
  )
}
