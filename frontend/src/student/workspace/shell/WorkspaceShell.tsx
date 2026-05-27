import { Outlet } from 'react-router-dom'
import WorkspaceSidebar from './WorkspaceSidebar'
import styles from './WorkspaceShell.module.css'

/** 工作台壳：左导航 + 右主区（对齐 DeepTutor flex 布局） */
export default function WorkspaceShell() {
  return (
    <div className={styles.shell}>
      <WorkspaceSidebar />
      <main className={styles.main}>
        <Outlet />
      </main>
    </div>
  )
}
