import { Routes, Route, useLocation, matchPath } from 'react-router-dom'
import { WorkspaceShell, ChatView } from '@/components/student/workspace'
import QuizPracticeDrawer from '@/components/student/quiz/QuizPracticeDrawer'
import LearningPathPage from './LearningPathPage'
import LearningResourcesPage from './LearningResourcesPage'
import QuizHomePage from './QuizHomePage'
import QuizTakingPage from './QuizTakingPage'
import UploadPage from './UploadPage'
import ProfilePage from './ProfilePage'
import KnowledgeGraphPage from './KnowledgeGraphPage'

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
          <Route path="resources" element={<LearningResourcesPage />} />
          <Route path="quiz" element={<QuizHomePage />} />
          <Route path="quiz/session/:sessionId" element={<QuizTakingPage />} />
          <Route path="profile" element={<ProfilePage />} />
          <Route path="knowledge-graph" element={<KnowledgeGraphPage />} />

          <Route path="quiz/practice/:questionId" element={backgroundLocation ? <QuizHomePage /> : <QuizPracticeDrawer questionId={activeQuestionId} />} />
        </Route>
      </Routes>

      {showPracticeDrawer ? <QuizPracticeDrawer questionId={activeQuestionId} /> : null}
    </>
  )
}
