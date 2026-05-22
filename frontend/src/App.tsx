import { Routes, Route, Navigate } from 'react-router-dom'
import LoginPage from '@/pages/LoginPage'
import WorkspacePage from '@/pages/WorkspacePage'
import AdminPage from '@/pages/AdminPage'
import ProtectedRoute from '@/components/ProtectedRoute'
import styles from './App.module.css'

export default function App() {
  return (
    <div className={styles.shell}>
    <Routes>
      <Route path="/login" element={<LoginPage />} />

      {/* 学生工作台 */}
      <Route
        path="/workspace/*"
        element={
          <ProtectedRoute allowedRoles={['student']}>
            <WorkspacePage />
          </ProtectedRoute>
        }
      />

      {/* 管理页 */}
      <Route
        path="/admin/*"
        element={
          <ProtectedRoute allowedRoles={['admin']}>
            <AdminPage />
          </ProtectedRoute>
        }
      />

      {/* 默认跳转登录 */}
      <Route path="*" element={<Navigate to="/login" replace />} />
    </Routes>
    </div>
  )
}
