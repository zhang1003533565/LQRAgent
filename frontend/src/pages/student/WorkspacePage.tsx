import { Routes, Route } from 'react-router-dom'
import { WorkspaceShell, ChatView } from '@/components/student/workspace'
import { NotesView } from '@/components/student/workspace/placeholder'
import LearningPathPage from './LearningPathPage'
import LearningResourcesPage from './LearningResourcesPage'
import QuizPage from './QuizPage'
import UploadPage from './UploadPage'
import ProfilePage from './ProfilePage'
import ProfileCenterPage from './ProfileCenterPage'

export default function WorkspacePage() {
  return (
    <Routes>
        <Route element={<WorkspaceShell />}>
        <Route index element={<ChatView />} />
        <Route path="learning-path" element={<LearningPathPage />} />
        <Route path="upload" element={<UploadPage />} />
        <Route path="profile" element={<ProfilePage />} />
        <Route path="resources" element={<LearningResourcesPage />} />
        <Route path="quiz" element={<QuizPage />} />
        <Route path="profile-center" element={<ProfileCenterPage />} />
        <Route path="notes" element={<NotesView />} />
      </Route>
    </Routes>
  )
}
