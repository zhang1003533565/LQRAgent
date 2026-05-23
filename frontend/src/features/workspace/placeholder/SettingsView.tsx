import styles from './PlaceholderView.module.css'

export default function SettingsView() {
  return (
    <div className={styles.container}>
      <div className={styles.icon}>⚙️</div>
      <h2 className={styles.title}>设置</h2>
      <p className={styles.desc}>设置功能即将上线，敬请期待</p>
    </div>
  )
}
