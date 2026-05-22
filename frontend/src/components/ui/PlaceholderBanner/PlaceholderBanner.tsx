import styles from './PlaceholderBanner.module.css'

export interface PlaceholderBannerProps {
  label?: string
  hint?: string
}

/** 标记尚未对接后端的模块 */
export default function PlaceholderBanner({
  label = '功能占位',
  hint = '接口就绪后可移除本提示',
}: PlaceholderBannerProps) {
  return (
    <div className={styles.banner} role="status">
      <span className={styles.label}>{label}</span>
      {hint && <span className={styles.hint}>{hint}</span>}
    </div>
  )
}
