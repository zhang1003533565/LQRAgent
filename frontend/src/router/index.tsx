import { Routes, Route, Navigate } from 'react-router-dom'
import ProtectedRoute from '@/components/student/auth/ProtectedRoute'
import DevConsolePage from '@/pages/admin/DevConsolePage'
import LoginPage from '@/pages/student/LoginPage'
import RegisterPage from '@/pages/student/RegisterPage'
import WorkspacePage from '@/pages/student/WorkspacePage'

function AdminConsoleRoute() {
  return (
    <ProtectedRoute allowedRoles={['admin']}>
      <DevConsolePage />
    </ProtectedRoute>
  )
}

export default function RouterConfig() {
  return (
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
  )
}
