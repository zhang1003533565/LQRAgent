import { useState, useEffect, useRef } from 'react'
import { uploadFile, listUploadTasks, type UploadTask, type KbScope } from '@/api/upload'
import styles from './UploadQueuePanel.module.css'

const STATUS_LABEL: Record<string, string> = {
  PENDING: '排队中',
  PROCESSING: '处理中',
  COMPLETED: '已完成',
  FAILED: '失败',
}

const STATUS_COLOR: Record<string, string> = {
  PENDING: '#f6ad55',
  PROCESSING: '#667eea',
  COMPLETED: '#48bb78',
  FAILED: '#fc8181',
}

/**
 * 上传资料面板：选文件 → 上传入队 → 轮询状态。
 */
export default function UploadQueuePanel() {
  const [tasks, setTasks] = useState<UploadTask[]>([])
  const [scope, setScope] = useState<KbScope>('PERSONAL')
  const [uploading, setUploading] = useState(false)
  const [error, setError] = useState('')
  const fileInputRef = useRef<HTMLInputElement>(null)
  const pollRef = useRef<ReturnType<typeof setInterval> | null>(null)

  // 初始加载 + 轮询（5s）
  useEffect(() => {
    fetchTasks()
    pollRef.current = setInterval(fetchTasks, 5000)
    return () => {
      if (pollRef.current) clearInterval(pollRef.current)
    }
  }, [])

  async function fetchTasks() {
    try {
      const data = await listUploadTasks()
      setTasks(data)
    } catch {
      // 静默失败，不打断用户
    }
  }

  async function handleFileChange(e: React.ChangeEvent<HTMLInputElement>) {
    const file = e.target.files?.[0]
    if (!file) return
    setError('')
    setUploading(true)
    try {
      await uploadFile(file, scope)
      await fetchTasks()
    } catch {
      setError('上传失败，请重试')
    } finally {
      setUploading(false)
      // 清空 input，允许重复上传同名文件
      if (fileInputRef.current) fileInputRef.current.value = ''
    }
  }

  return (
    <div className={styles.container}>
      <h3 className={styles.title}>上传资料</h3>

      <div className={styles.uploadRow}>
        <select
          value={scope}
          onChange={(e) => setScope(e.target.value as KbScope)}
          className={styles.select}
        >
          <option value="PERSONAL">个人知识库</option>
          <option value="PUBLIC">公共知识库</option>
        </select>

        <button
          className={styles.uploadBtn}
          disabled={uploading}
          onClick={() => fileInputRef.current?.click()}
        >
          {uploading ? '上传中...' : '选择文件'}
        </button>

        <input
          ref={fileInputRef}
          type="file"
          accept=".pdf,.md,.txt,.docx"
          style={{ display: 'none' }}
          onChange={handleFileChange}
        />
      </div>

      {error && <p className={styles.error}>{error}</p>}

      <p className={styles.hint}>支持 PDF、Markdown、TXT、DOCX，上传后异步处理</p>

      <div className={styles.taskList}>
        {tasks.length === 0 && (
          <p className={styles.empty}>暂无上传任务</p>
        )}
        {tasks.map((task) => (
          <div key={task.id} className={styles.taskItem}>
            <div className={styles.taskInfo}>
              <span className={styles.fileName}>{task.fileName}</span>
              <span className={styles.scope}>
                {task.kbScope === 'PERSONAL' ? '个人' : '公共'}
              </span>
            </div>
            <div className={styles.taskMeta}>
              <span
                className={styles.status}
                style={{ color: STATUS_COLOR[task.status] }}
              >
                {STATUS_LABEL[task.status] ?? task.status}
              </span>
              {task.status === 'FAILED' && task.errorMessage && (
                <span className={styles.errMsg}>{task.errorMessage}</span>
              )}
              <span className={styles.time}>
                {new Date(task.createdAt).toLocaleString('zh-CN', {
                  month: '2-digit',
                  day: '2-digit',
                  hour: '2-digit',
                  minute: '2-digit',
                })}
              </span>
            </div>
          </div>
        ))}
      </div>
    </div>
  )
}
