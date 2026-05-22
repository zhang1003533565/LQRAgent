import { Routes, Route } from 'react-router-dom'
import {
  WorkspaceShell,
  ChatView,
  UploadView,
  ProfileView,
} from '@/features/workspace'

export default function WorkspacePage() {
  return (
    <Routes>
      <Route element={<WorkspaceShell />}>
        <Route index element={<ChatView />} />
        <Route path="upload" element={<UploadView />} />
        <Route path="profile" element={<ProfileView />} />
      </Route>
    </Routes>
  )
}
