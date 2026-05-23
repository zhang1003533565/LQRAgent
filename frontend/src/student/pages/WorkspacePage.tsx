import { Routes, Route } from 'react-router-dom'
import {
  WorkspaceShell,
  ChatView,
  UploadView,
  ProfileView,
} from '@/student/components/workspace'
import {
  CoursesView,
  RecordsView,
  KnowledgeView,
  NotesView,
} from '@/student/components/workspace/placeholder'

export default function WorkspacePage() {
  return (
    <Routes>
      <Route element={<WorkspaceShell />}>
        <Route index element={<ChatView />} />
        <Route path="upload" element={<UploadView />} />
        <Route path="profile" element={<ProfileView />} />
        <Route path="learning-path" element={<CoursesView />} />
        <Route path="resources" element={<RecordsView />} />
        <Route path="quiz" element={<KnowledgeView />} />
        <Route path="notes" element={<NotesView />} />
      </Route>
    </Routes>
  )
}
