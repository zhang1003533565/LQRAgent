import { Routes, Route, Navigate } from 'react-router-dom'
import ProtectedRoute from '@/shared/components/auth/ProtectedRoute'
import DevConsolePage from '@/admin/pages/DevConsolePage'
import LoginPage from '@/student/pages/LoginPage'
import RegisterPage from '@/student/pages/RegisterPage'
import WorkspacePage from '@/student/pages/WorkspacePage'
import styles from './App.module.css'

function AdminConsoleRoute() {
  return (
    <ProtectedRoute allowedRoles={['admin']}>
      <DevConsolePage />
    </ProtectedRoute>
  )
}

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

        <Route path="/admin/console" element={<AdminConsoleRoute />} />
        <Route path="/admin/classic" element={<Navigate to="/admin/console" replace />} />
        <Route path="/admin" element={<Navigate to="/admin/console" replace />} />

        <Route path="*" element={<Navigate to="/login" replace />} />
      </Routes>
    </div>
  )
}
