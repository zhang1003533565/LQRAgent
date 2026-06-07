import { useState, useRef, useCallback, useEffect } from 'react'
import { useAuthStore } from '@/utils/store/authStore'
import { usePathStore } from '@/utils/store/pathStore'

export interface OrchestratorProgress {
  agent: string
  message: string
}

export function useOrchestrator() {
  const token = useAuthStore((s) => s.user?.token)
  const userId = useAuthStore((s) => s.user?.userId)
  const setPath = usePathStore((s) => s.setPath)
  const [progress, setProgress] = useState<OrchestratorProgress[]>([])
  const [running, setRunning] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const wsRef = useRef<WebSocket | null>(null)

  const start = useCallback((goal: string, cycle?: string) => {
    if (!token) return
    
    // 如果有旧连接，先关闭
    if (wsRef.current) {
      wsRef.current.close()
      wsRef.current = null
    }

    setRunning(true)
    setProgress([])
    setError(null)

    const protocol = window.location.protocol === 'https:' ? 'wss' : 'ws'
    const wsHost = window.location.port === '5173' ? 'localhost:8080' : window.location.host
    const url = `${protocol}://${wsHost}/ws/orchestrator?token=${token}`

    const ws = new WebSocket(url)
    wsRef.current = ws

    ws.onopen = () => {
      ws.send(JSON.stringify({ 
        type: 'learn', 
        goal, 
        userId: userId ? String(userId) : '2',
        cycle: cycle || '2周'
      }))
    }

    ws.onmessage = (event) => {
      try {
        const data = JSON.parse(event.data)

        switch (data.type) {
          case 'progress':
            setProgress((prev) => [...prev, { agent: data.agent, message: data.message }])
            break

          case 'complete':
            // 提取路径数据：优先 path key，回退到 result key
            const agentResult = data.results?.learning_path_agent
            const pathData = agentResult?.path || agentResult?.result
            if (pathData && typeof pathData === 'object' && pathData.nodes) {
              setPath(pathData)
            }
            setRunning(false)
            ws.close()
            wsRef.current = null
            break

          case 'error':
          case 'agent_error':
            setError(data.error || data.message || '未知错误')
            setRunning(false)
            ws.close()
            wsRef.current = null
            break
        }
      } catch (e) {
        console.error('Parse error:', e)
      }
    }

    ws.onerror = () => {
      setError('连接失败')
      setRunning(false)
      wsRef.current = null
    }

    ws.onclose = () => {
      if (running) {
        setRunning(false)
      }
      wsRef.current = null
    }
  }, [token, userId, setPath])

  const stop = useCallback(() => {
    if (wsRef.current) {
      wsRef.current.close()
      wsRef.current = null
    }
    setRunning(false)
  }, [])

  useEffect(() => {
    return () => {
      if (wsRef.current) {
        wsRef.current.close()
      }
    }
  }, [])

  return { start, stop, progress, running, error }
}
