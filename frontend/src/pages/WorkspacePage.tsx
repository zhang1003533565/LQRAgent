import { Routes, Route } from 'react-router-dom'
import ChatPanel from '@/components/workspace/ChatPanel'
import LearningPathPanel from '@/components/workspace/LearningPathPanel'
import UploadQueuePanel from '@/components/workspace/UploadQueuePanel'
import ProfileSummaryCard from '@/components/workspace/ProfileSummaryCard'
import styles from './WorkspacePage.module.css'

/**
 * 学生工作台 — 三栏布局。
 * 左：会话入口 + 功能导航
 * 中：聊天主区域（流式输出）
 * 右：学习路径 + 资源卡片 + 推荐动作
 */
export default function WorkspacePage() {
  return (
    <div className={styles.layout}>
      {/* 左侧 */}
      <aside className={styles.left}>
        <ProfileSummaryCard />
        <nav className={styles.nav}>
          <a href="/workspace">聊天学习</a>
          <a href="/workspace/upload">上传资料</a>
          <a href="/workspace/profile">我的画像</a>
        </nav>
      </aside>

      {/* 中间 */}
      <main className={styles.main}>
        <Routes>
          <Route index element={<ChatPanel />} />
          <Route path="upload" element={<UploadQueuePanel />} />
          <Route path="profile" element={<div>学习画像（待实现）</div>} />
        </Routes>
      </main>

      {/* 右侧 */}
      <aside className={styles.right}>
        <LearningPathPanel />
      </aside>
    </div>
  )
}
