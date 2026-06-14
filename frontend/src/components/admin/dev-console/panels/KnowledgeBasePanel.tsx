/**
 * 知识库管理面板 — 查看已上传文档、KB 状态、向量块内容
 */
import { useEffect, useState } from 'react'
import { 
  listAdminUploadTasks, 
  deleteAdminUploadTask,
  listVectorChunksByTask,
  deleteVectorChunk,
  type VectorChunk
} from '@/api/admin/admin'
import type { UploadTask } from '@/api/student/upload'
import { Card, CardContent, CardHeader, CardTitle, ConsoleBadge } from '@/components/admin/dev-console/ui'
import { panel } from './panelStyles'

type ChunkImage = {
  alt: string
  url: string
}

function getChunkImages(content: string): ChunkImage[] {
  const images: ChunkImage[] = []
  const pattern = /!\[([^\]]*)\]\(([^)]+)\)/g
  let match: RegExpExecArray | null
  while ((match = pattern.exec(content)) !== null) {
    images.push({ alt: match[1] || '文档图片', url: match[2] })
  }
  return images
}

function stripMarkdownImages(content: string): string {
  return content.replace(/!\[[^\]]*\]\([^)]+\)/g, '').trim()
}

function resolveKnowledgeImageUrl(url: string): string {
  if (/^https?:\/\//i.test(url)) return url
  if (url.startsWith('/api/v1/knowledge/')) {
    const aiServerBase = import.meta.env.VITE_AI_SERVER_URL || 'http://localhost:8001'
    return `${aiServerBase.replace(/\/$/, '')}${url}`
  }
  return url
}

function ChunkContent({ content }: { content: string }) {
  const images = getChunkImages(content)
  const text = stripMarkdownImages(content)

  return (
    <div className="space-y-3">
      {text && (
        <div className="text-sm text-console-text whitespace-pre-wrap break-words max-h-[150px] overflow-y-auto">
          {text}
        </div>
      )}
      {images.length > 0 && (
        <div className="grid grid-cols-2 gap-3">
          {images.map((image, index) => {
            const imageUrl = resolveKnowledgeImageUrl(image.url)
            return (
              <a
                key={`${image.url}-${index}`}
                href={imageUrl}
                target="_blank"
                rel="noreferrer"
                className="block rounded-md border border-console-border bg-black/20 p-2 hover:border-console-blue/60 transition-colors"
              >
                <img
                  src={imageUrl}
                  alt={image.alt}
                  className="max-h-48 w-full rounded object-contain"
                  loading="lazy"
                />
                <div className="mt-1 truncate text-xs text-console-muted">{image.alt}</div>
              </a>
            )
          })}
        </div>
      )}
    </div>
  )
}

export default function KnowledgeBasePanel() {
  const [tasks, setTasks] = useState<UploadTask[]>([])
  const [loading, setLoading] = useState(true)
  const [expandedTaskId, setExpandedTaskId] = useState<number | null>(null)
  const [chunksMap, setChunksMap] = useState<Map<number, VectorChunk[]>>(new Map())
  const [loadingChunks, setLoadingChunks] = useState<Set<number>>(new Set())

  useEffect(() => {
    void (async () => {
      try {
        setTasks(await listAdminUploadTasks(100))
      } finally {
        setLoading(false)
      }
    })()
  }, [])

  const completed = tasks.filter(t => t.status === 'COMPLETED')
  const pending = tasks.filter(t => t.status === 'PENDING' || t.status === 'PROCESSING')
  const failed = tasks.filter(t => t.status === 'FAILED')
  
  // 计算向量化统计
  const totalChunks = completed.reduce((sum, t) => sum + (t.vectorChunkCount || 0), 0)
  const totalTokens = completed.reduce((sum, t) => sum + (t.vectorTotalTokens || 0), 0)

  const toggleExpand = async (taskId: number) => {
    if (expandedTaskId === taskId) {
      setExpandedTaskId(null)
      return
    }
    
    setExpandedTaskId(taskId)
    
    if (!chunksMap.has(taskId)) {
      setLoadingChunks(prev => new Set([...prev, taskId]))
      try {
        const chunks = await listVectorChunksByTask(taskId)
        setChunksMap(prev => new Map([...prev, [taskId, chunks]]))
      } finally {
        setLoadingChunks(prev => {
          const next = new Set(prev)
          next.delete(taskId)
          return next
        })
      }
    }
  }

  const handleDeleteChunk = async (chunkId: number, taskId: number) => {
    if (!confirm('确定要删除这个向量块吗？')) return
    
    try {
      await deleteVectorChunk(chunkId)
      setChunksMap(prev => {
        const chunks = prev.get(taskId) || []
        const updated = chunks.filter(c => c.id !== chunkId)
        return new Map([...prev, [taskId, updated]])
      })
    } catch (err) {
      alert('删除失败')
    }
  }

  const handleDeleteTask = async (taskId: number) => {
    if (!confirm('确定要删除这个文档及其所有向量块吗？')) return
    
    try {
      await deleteAdminUploadTask(taskId)
      setTasks(prev => prev.filter(t => t.id !== taskId))
      setChunksMap(prev => {
        const next = new Map(prev)
        next.delete(taskId)
        return next
      })
      if (expandedTaskId === taskId) {
        setExpandedTaskId(null)
      }
    } catch (err) {
      alert('删除失败')
    }
  }

  return (
    <div className="space-y-4">
      {/* 概览卡片 */}
      <div className="grid grid-cols-5 gap-3">
        {[
          { label: '已入库', value: completed.length, color: 'text-console-green' },
          { label: '处理中', value: pending.length, color: 'text-console-yellow' },
          { label: '失败', value: failed.length, color: 'text-console-red' },
          { label: '切分块数', value: totalChunks, color: 'text-console-blue' },
          { label: '总 Token', value: totalTokens.toLocaleString(), color: 'text-console-purple' },
        ].map(s => (
          <Card key={s.label}>
            <CardContent className="py-3 text-center">
              <p className={`text-2xl font-bold ${s.color}`}>{s.value}</p>
              <p className="text-xs text-console-muted">{s.label}</p>
            </CardContent>
          </Card>
        ))}
      </div>

      {/* 文档列表 */}
      <Card>
        <CardHeader>
          <CardTitle>知识库文档</CardTitle>
          <p className={panel.desc}>共 {tasks.length} 个文件，已向量化 {completed.length} 个</p>
        </CardHeader>
        <CardContent>
          {loading ? (
            <p className={panel.hint}>加载中...</p>
          ) : tasks.length === 0 ? (
            <p className={panel.hint}>暂无文档，请通过「上传队列」或管理员接口上传</p>
          ) : (
            <div className="space-y-2">
              {tasks.map(t => (
                <div key={t.id} className="rounded-md border border-console-border overflow-hidden">
                  {/* 文档行 */}
                  <div className="flex items-center gap-2 px-4 py-3 hover:bg-console-hover-bg/50 transition-colors">
                    {/* 展开按钮 */}
                    <button
                      onClick={() => void toggleExpand(t.id)}
                      className="flex-shrink-0 h-6 w-6 flex items-center justify-center rounded border border-console-border text-console-muted hover:text-console-text transition-colors"
                    >
                      {expandedTaskId === t.id ? '−' : '+'}
                    </button>
                    
                    {/* 文件名 */}
                    <span className="flex-1 text-sm text-console-text">{t.fileName}</span>
                    
                    {/* 用户 */}
                    <span className="text-xs text-console-muted">{t.userId}</span>
                    
                    {/* 范围 */}
                    <ConsoleBadge variant={t.kbScope === 'PUBLIC' ? 'success' : 'muted'}>
                      {t.kbScope === 'PUBLIC' ? '公共' : '私有'}
                    </ConsoleBadge>
                    
                    {/* 状态 */}
                    <ConsoleBadge variant={
                      t.status === 'COMPLETED' ? 'success' :
                      t.status === 'FAILED' ? 'danger' : 'muted'
                    }>
                      {t.status === 'COMPLETED' ? '已入库' :
                       t.status === 'FAILED' ? '失败' :
                       t.status === 'PROCESSING' ? '处理中' : '排队中'}
                    </ConsoleBadge>
                    
                    {/* 切分块数 */}
                    <span className="inline-flex items-center gap-1 rounded bg-blue-500/10 px-2 py-0.5 text-xs font-medium text-blue-400">
                      {t.vectorChunkCount || '-'} 块
                    </span>
                    
                    {/* Token 数 */}
                    <span className="inline-flex items-center gap-1 rounded bg-purple-500/10 px-2 py-0.5 text-xs font-medium text-purple-400">
                      {(t.vectorTotalTokens || 0).toLocaleString()}
                    </span>
                    
                    {/* 索引名称 */}
                    <span className="text-xs text-console-muted max-w-[120px] truncate" title={t.vectorIndexName || ''}>
                      {t.vectorIndexName || '-'}
                    </span>
                    
                    {/* 映射知识点 */}
                    <span className="text-xs text-console-muted max-w-[150px] truncate" title={t.mappedKpIds || ''}>
                      {t.mappedKpIds || '-'}
                    </span>
                    
                    {/* 上传时间 */}
                    <span className="text-xs text-console-muted">{t.createdAt}</span>
                    
                    {/* 删除按钮 */}
                    <button
                      onClick={() => void handleDeleteTask(t.id)}
                      className="flex-shrink-0 px-2 py-1 text-xs text-red-400 hover:text-red-500 hover:bg-red-500/10 rounded transition-colors"
                    >
                      删除
                    </button>
                  </div>
                  
                  {/* 展开的向量块详情 */}
                  {expandedTaskId === t.id && (
                    <div className="border-t border-console-border bg-console-bg/50">
                      {loadingChunks.has(t.id) ? (
                        <div className="px-4 py-8 text-center">
                          <p className={panel.hint}>加载向量块中...</p>
                        </div>
                      ) : (
                        <div className="p-4 space-y-3 max-h-[600px] overflow-y-auto">
                          {chunksMap.get(t.id)?.length === 0 ? (
                            <p className={panel.hint}>暂无向量块数据</p>
                          ) : (
                            (chunksMap.get(t.id) || []).map((chunk) => (
                              <div key={chunk.id} className="rounded-md border border-console-border p-3 bg-white/5">
                                <div className="flex items-center justify-between mb-2">
                                  <div className="flex items-center gap-2">
                                    <span className="text-xs font-medium text-console-blue">
                                      块 #{chunk.chunkIndex + 1}
                                    </span>
                                    <span className="text-xs text-purple-400">
                                      {chunk.tokenCount} tokens
                                    </span>
                                    {chunk.kpId && (
                                      <ConsoleBadge variant="success">{chunk.kpId}</ConsoleBadge>
                                    )}
                                  </div>
                                  <button
                                    onClick={() => void handleDeleteChunk(chunk.id, t.id)}
                                    className="text-xs text-red-400 hover:text-red-500 transition-colors"
                                  >
                                    删除块
                                  </button>
                                </div>
                                <ChunkContent content={chunk.content} />
                                {chunk.metadata && (
                                  <div className="mt-2 text-xs text-console-muted">
                                    <span className="font-medium">元数据:</span> {chunk.metadata}
                                  </div>
                                )}
                              </div>
                            ))
                          )}
                        </div>
                      )}
                    </div>
                  )}
                </div>
              ))}
            </div>
          )}
        </CardContent>
      </Card>
    </div>
  )
}