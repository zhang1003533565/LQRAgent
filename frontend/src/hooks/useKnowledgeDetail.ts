import { useCallback, useEffect, useState } from 'react'
import { getResources } from '@/api/student/resources'
import { getQuizQuestions } from '@/api/student/quiz'
import type { LayoutNode } from '@/types/knowledgeGraph'
import type { LearningResource } from '@/utils/types/media-resource'

export type DetailTab = 'overview' | 'prerequisites' | 'resources' | 'practice'

export function useKnowledgeDetail(selectedNode: LayoutNode | null) {
  const [activeTab, setActiveTab] = useState<DetailTab>('overview')
  const [resources, setResources] = useState<LearningResource[]>([])
  const [quizResources, setQuizResources] = useState<LearningResource[]>([])
  const [detailLoading, setDetailLoading] = useState(false)

  useEffect(() => {
    setActiveTab('overview')
    if (!selectedNode) {
      setResources([])
      setQuizResources([])
      return
    }
    let cancelled = false
    setDetailLoading(true)
    Promise.all([
      getResources(selectedNode.id),
      getQuizQuestions(selectedNode.id),
    ])
      .then(([resList, quizList]) => {
        if (cancelled) return
        setResources(resList.filter((r) => r.resourceType !== 'QUIZ'))
        setQuizResources(quizList)
      })
      .catch(() => {
        if (!cancelled) {
          setResources([])
          setQuizResources([])
        }
      })
      .finally(() => {
        if (!cancelled) setDetailLoading(false)
      })
    return () => {
      cancelled = true
    }
  }, [selectedNode?.id])

  const resetTab = useCallback(() => setActiveTab('overview'), [])

  return {
    activeTab,
    setActiveTab,
    resources,
    quizResources,
    detailLoading,
    resetTab,
  }
}
