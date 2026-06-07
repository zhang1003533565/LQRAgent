import React, { useState, useCallback } from 'react'
import { ChatSidebar } from './ChatSidebar'
import { MemoryPanel } from './MemoryPanel'
import { TraceTimeline } from './TraceTimeline'
import type { TraceTimeline as TraceTimelineType } from '@/utils/types/trace'

interface AppLayoutProps {
  children: React.ReactNode
}

/**
 * 应用布局组件
 * 整合侧边栏、主内容区、记忆面板
 */
export const AppLayout: React.FC<AppLayoutProps> = ({ children }) => {
  const [currentSessionId, setCurrentSessionId] = useState<string | null>(null)
  const [showSidebar, setShowSidebar] = useState(true)
  const [showMemoryPanel, setShowMemoryPanel] = useState(false)
  const [showTracePanel, setShowTracePanel] = useState(false)
  const [currentTimeline, setCurrentTimeline] = useState<TraceTimelineType | null>(null)

  // 选择会话
  const handleSelectSession = useCallback((sessionId: string) => {
    setCurrentSessionId(sessionId)
  }, [])

  // 新建会话
  const handleNewSession = useCallback(() => {
    setCurrentSessionId(null)
    // 这里可以触发新建会话的逻辑
  }, [])

  // 切换侧边栏
  const toggleSidebar = useCallback(() => {
    setShowSidebar((prev) => !prev)
  }, [])

  // 切换记忆面板
  const toggleMemoryPanel = useCallback(() => {
    setShowMemoryPanel((prev) => !prev)
    if (showTracePanel) setShowTracePanel(false)
  }, [showTracePanel])

  // 切换追踪面板
  const toggleTracePanel = useCallback(() => {
    setShowTracePanel((prev) => !prev)
    if (showMemoryPanel) setShowMemoryPanel(false)
  }, [showMemoryPanel])

  return (
    <div className="flex h-screen bg-gray-100">
      {/* 左侧边栏 */}
      {showSidebar && (
        <div className="w-64 flex-shrink-0">
          <ChatSidebar
            currentSessionId={currentSessionId}
            onSelectSession={handleSelectSession}
            onNewSession={handleNewSession}
          />
        </div>
      )}

      {/* 主内容区 */}
      <div className="flex-1 flex flex-col overflow-hidden">
        {/* 顶部工具栏 */}
        <div className="h-12 bg-white border-b border-gray-200 flex items-center px-4 gap-2">
          {/* 侧边栏切换 */}
          <button
            onClick={toggleSidebar}
            className="p-2 text-gray-500 hover:text-gray-700 hover:bg-gray-100 rounded"
            title={showSidebar ? '隐藏侧边栏' : '显示侧边栏'}
          >
            <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M4 6h16M4 12h16M4 18h16" />
            </svg>
          </button>

          <div className="h-6 w-px bg-gray-200" />

          {/* 记忆面板切换 */}
          <button
            onClick={toggleMemoryPanel}
            className={`p-2 rounded ${
              showMemoryPanel
                ? 'text-blue-600 bg-blue-50'
                : 'text-gray-500 hover:text-gray-700 hover:bg-gray-100'
            }`}
            title={showMemoryPanel ? '隐藏记忆面板' : '显示记忆面板'}
          >
            <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z" />
            </svg>
          </button>

          {/* 追踪面板切换 */}
          <button
            onClick={toggleTracePanel}
            className={`p-2 rounded ${
              showTracePanel
                ? 'text-blue-600 bg-blue-50'
                : 'text-gray-500 hover:text-gray-700 hover:bg-gray-100'
            }`}
            title={showTracePanel ? '隐藏追踪面板' : '显示追踪面板'}
          >
            <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 19v-6a2 2 0 00-2-2H5a2 2 0 00-2 2v6a2 2 0 002 2h2a2 2 0 002-2zm0 0V9a2 2 0 012-2h2a2 2 0 012 2v10m-6 0a2 2 0 002 2h2a2 2 0 002-2m0 0V5a2 2 0 012-2h2a2 2 0 012 2v14a2 2 0 01-2 2h-2a2 2 0 01-2-2z" />
            </svg>
          </button>
        </div>

        {/* 主内容 */}
        <div className="flex-1 overflow-hidden">
          {children}
        </div>
      </div>

      {/* 右侧面板：记忆或追踪 */}
      {(showMemoryPanel || showTracePanel) && (
        <div className="w-80 flex-shrink-0">
          {showMemoryPanel && (
            <MemoryPanel
              userId="1" // TODO: 从 authStore 获取
              className="h-full"
            />
          )}
          {showTracePanel && (
            <TraceTimeline
              timeline={currentTimeline}
              className="h-full"
            />
          )}
        </div>
      )}
    </div>
  )
}

export default AppLayout
