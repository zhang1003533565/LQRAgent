import { PlaceholderBanner } from '@/components/user/ui'
import styles from './UploadAnalysisCard.module.css'

/** P3：上传完成后的知识点映射与分析摘要（占位） */
export default function UploadAnalysisCard() {
  return (
    <div className={styles.card}>
      <PlaceholderBanner
        label="分析结果待对接"
        hint="上传 worker 写入知识库后展示映射知识点"
      />
      <ul className={styles.mock}>
        <li>示例映射：Python 基础语法</li>
        <li>示例映射：控制流与循环</li>
      </ul>
      <p className={styles.summary}>
        文档摘要（占位）：系统将在此展示 AI 对上传资料的结构化分析。
      </p>
    </div>
  )
}
