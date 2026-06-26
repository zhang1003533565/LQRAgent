import type { PracticeResult, PracticeSession } from '@/utils/types/quiz'

const SESSION_KEY = 'lqragent-quiz-sessions'
const FAVORITE_KEY = 'lqragent-quiz-favorites'
const MARKED_KEY = 'lqragent-quiz-marked'

function readJson<T>(key: string, fallback: T): T {
  try {
    const raw = localStorage.getItem(key)
    if (!raw) return fallback
    return JSON.parse(raw) as T
  } catch {
    return fallback
  }
}

function writeJson<T>(key: string, value: T) {
  localStorage.setItem(key, JSON.stringify(value))
}

export function getStoredSessions(): Record<string, PracticeSession> {
  return readJson(SESSION_KEY, {})
}

export function saveSession(session: PracticeSession) {
  const all = getStoredSessions()
  all[session.id] = session
  writeJson(SESSION_KEY, all)
}

export function getStoredSession(sessionId: string): PracticeSession | null {
  return getStoredSessions()[sessionId] || null
}

export function deleteSession(sessionId: string) {
  const all = getStoredSessions()
  delete all[sessionId]
  writeJson(SESSION_KEY, all)
}

export function savePracticeResult(result: PracticeResult) {
  const key = 'lqragent-quiz-results'
  const all = readJson<PracticeResult[]>(key, [])
  all.unshift(result)
  writeJson(key, all.slice(0, 50))
}

export function getFavoriteQuestionIds(): Set<number> {
  const ids = readJson<number[]>(FAVORITE_KEY, [])
  return new Set(ids)
}

export function setQuestionFavorite(questionId: number, favorite: boolean) {
  const ids = new Set(getFavoriteQuestionIds())
  if (favorite) ids.add(questionId)
  else ids.delete(questionId)
  writeJson(FAVORITE_KEY, Array.from(ids))
}

export function getMarkedQuestionIds(): Set<number> {
  const ids = readJson<number[]>(MARKED_KEY, [])
  return new Set(ids)
}

export function setQuestionMarked(questionId: number, marked: boolean) {
  const ids = new Set(getMarkedQuestionIds())
  if (marked) ids.add(questionId)
  else ids.delete(questionId)
  writeJson(MARKED_KEY, Array.from(ids))
}
