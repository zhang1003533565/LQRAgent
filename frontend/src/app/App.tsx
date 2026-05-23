import { Routes, Route, Navigate } from 'react-router-dom'
import ProtectedRoute from '@/components/auth/ProtectedRoute'
import AdminPage from '@/admin/pages/AdminPage'
import LoginPage from '@/student/pages/LoginPage'
import RegisterPage from '@/student/pages/RegisterPage'
import WorkspacePage from '@/student/pages/WorkspacePage'
import styles from './App.module.css'

export default function App() {
  return (
    <div className={styles.shell}>
      <Routes>
        <Route path="/login" element={<LoginPage />} />
        <Route path="/register" element={<RegisterPage />} />

        <Route
          path="/workspace/*"
          element={
            <ProtectedRoute allowedRoles={['student']}>
              <WorkspacePage />
            </ProtectedRoute>
          }
        />

        <Route
          path="/admin/*"
          element={
            <ProtectedRoute allowedRoles={['admin']}>
              <AdminPage />
            </ProtectedRoute>
          }
        />

        <Route path="*" element={<Navigate to="/login" replace />} />
      </Routes>
    </div>
  )
}
