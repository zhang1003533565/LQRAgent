import { useCallback, useRef, useState } from 'react'
import { uploadFile } from '@/services/uploadService'
import type { ClientUploadTask, UploadConfig } from '@/utils/types/upload'

function newClientId() {
  return `upload-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`
}

export function useUploadQueue() {
  const [tasks, setTasks] = useState<ClientUploadTask[]>([])
  const abortRefs = useRef<Record<string, boolean>>({})

  const updateTask = useCallback((clientId: string, patch: Partial<ClientUploadTask>) => {
    setTasks((prev) => prev.map((t) => (t.clientId === clientId ? { ...t, ...patch } : t)))
  }, [])

  const removeTask = useCallback((clientId: string) => {
    setTasks((prev) => prev.filter((t) => t.clientId !== clientId))
    delete abortRefs.current[clientId]
  }, [])

  const cancelTask = useCallback(
    (clientId: string) => {
      abortRefs.current[clientId] = true
      updateTask(clientId, { status: 'canceled' })
    },
    [updateTask],
  )

  const enqueueFiles = useCallback(
    async (files: File[], config: UploadConfig) => {
      const entries: ClientUploadTask[] = files.map((file) => ({
        clientId: newClientId(),
        fileName: file.name,
        fileSizeBytes: file.size,
        mimeType: file.type || 'application/octet-stream',
        status: 'queued',
        progress: 0,
        file,
      }))

      setTasks((prev) => [...entries, ...prev])

      for (const entry of entries) {
        if (abortRefs.current[entry.clientId]) continue

        updateTask(entry.clientId, { status: 'uploading', progress: 0 })
        try {
          const uploaded = await uploadFile({
            file: entry.file!,
            learningPathId: config.learningPathId,
            knowledgePointIds: config.knowledgePointIds,
            autoParse: config.autoParse,
            autoGenerateResource: config.autoGenerateResource,
            onProgress: (p) => {
              if (!abortRefs.current[entry.clientId]) {
                updateTask(entry.clientId, { progress: p })
              }
            },
          })

          if (abortRefs.current[entry.clientId]) continue

          updateTask(entry.clientId, {
            status: 'success',
            progress: 100,
            uploadedFileId: uploaded.id,
          })
        } catch (e) {
          if (abortRefs.current[entry.clientId]) continue
          updateTask(entry.clientId, {
            status: 'failed',
            errorMessage: e instanceof Error ? e.message : '上传失败',
          })
        }
      }
    },
    [updateTask],
  )

  const retryTask = useCallback(
    async (clientId: string, config: UploadConfig) => {
      const task = tasks.find((t) => t.clientId === clientId)
      if (!task?.file) return

      abortRefs.current[clientId] = false
      updateTask(clientId, { status: 'uploading', progress: 0, errorMessage: undefined })

      try {
        const uploaded = await uploadFile({
          file: task.file,
          learningPathId: config.learningPathId,
          knowledgePointIds: config.knowledgePointIds,
          autoParse: config.autoParse,
          autoGenerateResource: config.autoGenerateResource,
          onProgress: (p) => updateTask(clientId, { progress: p }),
        })
        updateTask(clientId, {
          status: 'success',
          progress: 100,
          uploadedFileId: uploaded.id,
        })
      } catch (e) {
        updateTask(clientId, {
          status: 'failed',
          errorMessage: e instanceof Error ? e.message : '上传失败',
        })
      }
    },
    [tasks, updateTask],
  )

  const clearCompleted = useCallback(() => {
    setTasks((prev) => prev.filter((t) => t.status !== 'success' && t.status !== 'canceled'))
  }, [])

  const activeCount = tasks.filter(
    (t) => t.status === 'queued' || t.status === 'uploading',
  ).length

  return {
    tasks,
    activeCount,
    enqueueFiles,
    cancelTask,
    retryTask,
    removeTask,
    clearCompleted,
  }
}
