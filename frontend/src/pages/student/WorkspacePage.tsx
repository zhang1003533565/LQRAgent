import { Routes, Route, useLocation, matchPath } from 'react-router-dom'
import { WorkspaceShell, ChatView } from '@/components/student/workspace'
import { NotesView } from '@/components/student/workspace/placeholder'
import QuizPracticeDrawer from '@/components/student/quiz/QuizPracticeDrawer'
import LearningPathPage from './LearningPathPage'
import LearningResourcesPage from './LearningResourcesPage'
import QuizPage from './QuizPage'
import UploadPage from './UploadPage'
import ProfilePage from './ProfilePage'
import ProfileCenterPage from './ProfileCenterPage'

type RouteState = {
  backgroundLocation?: Location
}

export default function WorkspacePage() {
  const location = useLocation()
  const state = location.state as RouteState | null
  const backgroundLocation = state?.backgroundLocation
  const practiceMatch = matchPath('/workspace/quiz/practice/:questionId', location.pathname)
  const showPracticeDrawer = Boolean(practiceMatch && backgroundLocation)
  const activeQuestionId = practiceMatch?.params.questionId

  return (
    <>
      <Routes location={backgroundLocation || location}>
        <Route element={<WorkspaceShell />}>
          <Route index element={<ChatView />} />
          <Route path="learning-path" element={<LearningPathPage />} />
          <Route path="upload" element={<UploadPage />} />
          <Route path="profile" element={<ProfilePage />} />
          <Route path="resources" element={<LearningResourcesPage />} />
          <Route path="quiz" element={<QuizPage />} />
          <Route path="profile-center" element={<ProfileCenterPage />} />
          <Route path="notes" element={<NotesView />} />
          <Route path="quiz/practice/:questionId" element={backgroundLocation ? <QuizPage /> : <QuizPracticeDrawer questionId={activeQuestionId} />} />
        </Route>
      </Routes>

      {showPracticeDrawer ? <QuizPracticeDrawer questionId={activeQuestionId} /> : null}
    </>
  )
}
