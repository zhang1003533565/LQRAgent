import { Routes, Route } from 'react-router-dom'
import { WorkspaceShell, ChatView, UploadView, ProfileView } from '@/student/workspace'
import { NotesView } from '@/student/workspace/placeholder'
import LearningPathPage from './LearningPathPage'
import LearningResourcesPage from './LearningResourcesPage'
import QuizPage from './QuizPage'

export default function WorkspacePage() {
  return (
    <Routes>
      <Route element={<WorkspaceShell />}>
        <Route index element={<ChatView />} />
        <Route path="learning-path" element={<LearningPathPage />} />
        <Route path="upload" element={<UploadView />} />
        <Route path="profile" element={<ProfileView />} />
        <Route path="resources" element={<LearningResourcesPage />} />
        <Route path="quiz" element={<QuizPage />} />
        <Route path="notes" element={<NotesView />} />
      </Route>
    </Routes>
  )
}
