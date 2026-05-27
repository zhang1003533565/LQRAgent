import styles from './PlaceholderView.module.css'

export default function NotesView() {
  return (
    <div className={styles.container}>
      <div className={styles.icon}>📄</div>
      <h2 className={styles.title}>笔记本</h2>
      <p className={styles.desc}>笔记本功能即将上线，敬请期待</p>
    </div>
  )
}
