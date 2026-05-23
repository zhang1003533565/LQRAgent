import { Routes, Route, Navigate } from 'react-router-dom'
import LoginPage from '@/pages/LoginPage'
import RegisterPage from '@/pages/RegisterPage'
import WorkspacePage from '@/pages/WorkspacePage'
import AdminPage from '@/pages/AdminPage'
import ProtectedRoute from '@/app/ProtectedRoute'
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
