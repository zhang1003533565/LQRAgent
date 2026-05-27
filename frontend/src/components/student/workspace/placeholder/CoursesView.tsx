import styles from './PlaceholderView.module.css'

export default function CoursesView() {
  return (
    <div className={styles.container}>
      <div className={styles.icon}>🗺️</div>
      <h2 className={styles.title}>学习路径</h2>
      <p className={styles.desc}>学习路径模块即将上线，敬请期待。</p>
    </div>
  )
}
