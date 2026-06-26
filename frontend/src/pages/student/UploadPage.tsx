import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import {
  ConfirmDialog,
  FileFilterBar,
  FileParseDrawer,
  FileRelationModal,
  PageHeader,
  RecentUploadsTable,
  UploadedFileList,
  UploadAuxPanel,
  UploadConfigPanel,
  UploadDropzone,
  UploadTaskQueue,
} from '@/components/student/workspace/upload'
import {
  deleteUploadedFile,
  generateResourceFromFile,
  getFileDownloadUrl,
  loadUploadConfigFromServer,
  pruneLegacyFileSizes,
  retryParseFile,
  updateFileRelations,
} from '@/services/uploadService'
import { useFileParseResult } from '@/utils/hooks/useFileParseResult'
import { useKnowledgePointOptions } from '@/utils/hooks/useKnowledgePointOptions'
import { useLearningPathOptions } from '@/utils/hooks/useLearningPathOptions'
import { useStorageUsage } from '@/utils/hooks/useStorageUsage'
import { useUploadedFiles } from '@/utils/hooks/useUploadedFiles'
import { useUploadQueue } from '@/utils/hooks/useUploadQueue'
import { useUploadStats } from '@/utils/hooks/useUploadStats'
import { syncWorkspaceFromSearchParams } from '@/utils/navigation/workspaceNav'
import { usePathStore } from '@/utils/store/pathStore'
import type {
  ParseStatus,
  SourceType,
  UploadedFile,
  UploadedFileSort,
  UploadedFilesFilters,
  UploadConfig,
} from '@/utils/types/upload'

const DEFAULT_CONFIG: UploadConfig = {
  autoParse: true,
  autoGenerateResource: false,
}

const RECENT_LIMIT = 5

export default function UploadPage() {
  const navigate = useNavigate()
  const [searchParams] = useSearchParams()
  const selectedKpId = usePathStore((s) => s.selectedKpId)
  const dropzoneRef = useRef<HTMLDivElement>(null)
  const recordsRef = useRef<HTMLDivElement>(null)

  const [config, setConfig] = useState<UploadConfig>(DEFAULT_CONFIG)
  const [kpKeyword, setKpKeyword] = useState('')
  const [keyword, setKeyword] = useState('')
  const [debouncedKeyword, setDebouncedKeyword] = useState('')
  const [sourceType, setSourceType] = useState<SourceType | 'all'>('all')
  const [parseStatus, setParseStatus] = useState<ParseStatus | 'all'>('all')
  const [learningPathId, setLearningPathId] = useState('all')
  const [sort, setSort] = useState<UploadedFileSort>('uploadedAt')
  const [showAllRecords, setShowAllRecords] = useState(false)

  const [selectedFile, setSelectedFile] = useState<UploadedFile | null>(null)
  const [batchMode, setBatchMode] = useState(false)
  const [batchSelectedIds, setBatchSelectedIds] = useState<string[]>([])
  const [drawerOpen, setDrawerOpen] = useState(false)
  const [relationOpen, setRelationOpen] = useState(false)
  const [relationFile, setRelationFile] = useState<UploadedFile | null>(null)
  const [deleteTarget, setDeleteTarget] = useState<UploadedFile | null>(null)
  const [batchDeleteOpen, setBatchDeleteOpen] = useState(false)
  const [actionLoadingId, setActionLoadingId] = useState<string | null>(null)
  const [relationLoading, setRelationLoading] = useState(false)
  const [deleteLoading, setDeleteLoading] = useState(false)
  const [toast, setToast] = useState<string | null>(null)

  useEffect(() => {
    syncWorkspaceFromSearchParams(searchParams)
  }, [searchParams])

  useEffect(() => {
    void loadUploadConfigFromServer()
    void pruneLegacyFileSizes()
  }, [])

  useEffect(() => {
    if (selectedKpId) {
      setConfig((prev) => ({
        ...prev,
        knowledgePointIds: [selectedKpId],
      }))
    }
  }, [selectedKpId])

  const { data: storage, loading: storageLoading, error: storageError, refresh: refreshStorage } =
    useStorageUsage()
  const { refresh: refreshStats } = useUploadStats()
  const { options: pathOptions, loading: pathLoading } = useLearningPathOptions()
  const { options: kpOptions, loading: kpLoading } = useKnowledgePointOptions(
    kpKeyword,
    config.learningPathId,
  )

  useEffect(() => {
    const timer = setTimeout(() => setDebouncedKeyword(keyword), 300)
    return () => clearTimeout(timer)
  }, [keyword])

  const fileFilters = useMemo<UploadedFilesFilters>(
    () => ({
      keyword: debouncedKeyword,
      sourceType,
      parseStatus,
      learningPathId,
      sort,
      pageSize: showAllRecords ? 8 : RECENT_LIMIT,
    }),
    [debouncedKeyword, sourceType, parseStatus, learningPathId, sort, showAllRecords],
  )

  const {
    list: files,
    total,
    page,
    totalPages,
    loading: filesLoading,
    error: filesError,
    refresh: refreshFiles,
    goToPage,
  } = useUploadedFiles(fileFilters)

  const recentFiles = showAllRecords ? files : files.slice(0, RECENT_LIMIT)

  const {
    tasks: queueTasks,
    enqueueFiles,
    cancelTask,
    retryTask,
    clearCompleted,
    activeCount,
  } = useUploadQueue()

  const {
    data: parseResult,
    loading: parseLoading,
    refresh: refreshParse,
  } = useFileParseResult(
    selectedFile?.id ?? null,
    selectedFile?.parseStatus === 'processing' || selectedFile?.parseStatus === 'pending',
  )

  const needPolling = files.some(
    (f) => f.parseStatus === 'processing' || f.parseStatus === 'pending',
  )

  useEffect(() => {
    if (!needPolling) return
    const timer = setInterval(() => {
      void refreshFiles()
      void refreshStats()
    }, 5000)
    return () => clearInterval(timer)
  }, [needPolling, refreshFiles, refreshStats])

  useEffect(() => {
    if (activeCount === 0 && queueTasks.some((t) => t.status === 'success')) {
      void refreshAll()
    }
  }, [activeCount, queueTasks])

  const showToast = useCallback((message: string) => {
    setToast(message)
    setTimeout(() => setToast(null), 4000)
  }, [])

  const refreshAll = useCallback(async () => {
    await Promise.all([refreshStorage(), refreshStats(), refreshFiles()])
  }, [refreshFiles, refreshStats, refreshStorage])

  const handleFilesSelected = useCallback(
    async (selected: File[]) => {
      await enqueueFiles(selected, config)
    },
    [config, enqueueFiles],
  )

  const handlePreview = useCallback(
    async (file: UploadedFile) => {
      try {
        const url = await getFileDownloadUrl(file.id)
        window.open(url, '_blank', 'noopener,noreferrer')
      } catch {
        showToast('暂不支持预览，请稍后重试')
      }
    },
    [showToast],
  )

  const handleGenerate = useCallback(
    async (file: UploadedFile) => {
      setActionLoadingId(file.id)
      try {
        const { resourceId } = await generateResourceFromFile({ fileId: file.id })
        showToast('学习资源已生成')
        navigate(`/workspace/resources?resourceId=${encodeURIComponent(resourceId)}`)
      } catch (e) {
        showToast(e instanceof Error ? e.message : '生成资源失败')
      } finally {
        setActionLoadingId(null)
      }
    },
    [navigate, showToast],
  )

  const handleDelete = useCallback(async () => {
    if (!deleteTarget) return
    setDeleteLoading(true)
    try {
      await deleteUploadedFile(deleteTarget.id)
      if (selectedFile?.id === deleteTarget.id) setSelectedFile(null)
      setDeleteTarget(null)
      showToast('已删除')
      await refreshAll()
    } catch (e) {
      showToast(e instanceof Error ? e.message : '删除失败')
    } finally {
      setDeleteLoading(false)
    }
  }, [deleteTarget, refreshAll, selectedFile?.id, showToast])

  const handleBatchDelete = useCallback(async () => {
    setDeleteLoading(true)
    try {
      for (const id of batchSelectedIds) {
        await deleteUploadedFile(id)
      }
      setBatchSelectedIds([])
      setBatchDeleteOpen(false)
      showToast('批量删除完成')
      await refreshAll()
    } catch (e) {
      showToast(e instanceof Error ? e.message : '批量删除失败')
    } finally {
      setDeleteLoading(false)
    }
  }, [batchSelectedIds, refreshAll, showToast])

  const handleRetryParse = useCallback(
    async (file: UploadedFile) => {
      setActionLoadingId(file.id)
      try {
        await retryParseFile(file.id)
        showToast('已提交重新解析')
        await refreshFiles()
        if (selectedFile?.id === file.id) await refreshParse()
      } catch (e) {
        showToast(e instanceof Error ? e.message : '重新解析失败')
      } finally {
        setActionLoadingId(null)
      }
    },
    [refreshFiles, refreshParse, selectedFile?.id, showToast],
  )

  const handleSaveRelations = useCallback(
    async (payload: {
      learningPathId?: string
      knowledgePointIds?: string[]
      tags?: string[]
    }) => {
      if (!relationFile) return
      setRelationLoading(true)
      try {
        await updateFileRelations({
          fileId: relationFile.id,
          ...payload,
        })
        showToast('关联信息已保存')
        setRelationOpen(false)
        await refreshFiles()
      } catch (e) {
        showToast(e instanceof Error ? e.message : '保存失败')
      } finally {
        setRelationLoading(false)
      }
    },
    [relationFile, refreshFiles, showToast],
  )

  const scrollToRecords = () => {
    recordsRef.current?.scrollIntoView({ behavior: 'smooth', block: 'start' })
  }

  return (
    <div className="flex h-full min-h-0 overflow-hidden bg-[#F6F9FE]">
      <main className="min-w-0 flex-1 overflow-y-auto p-6">
        <div className="space-y-5">
          {toast ? (
            <div className="rounded-xl border border-[#FECACA] bg-[#FEE2E2] px-4 py-3 text-sm text-[#EF4444]">
              {toast}
            </div>
          ) : null}

          <PageHeader
            onOpenRecords={() => {
              setShowAllRecords(true)
              scrollToRecords()
            }}
          />

          <div ref={dropzoneRef}>
            <UploadDropzone
              storage={storage}
              disabled={activeCount > 0}
              onFilesSelected={(files) => void handleFilesSelected(files)}
              onValidationError={showToast}
              onUnsupportedAction={(label) => showToast(`${label}功能开发中`)}
            />
          </div>

          <UploadConfigPanel
            config={config}
            pathOptions={pathOptions}
            pathLoading={pathLoading}
            kpOptions={kpOptions}
            kpLoading={kpLoading}
            kpKeyword={kpKeyword}
            onKpKeywordChange={setKpKeyword}
            onChange={(patch) => setConfig((c) => ({ ...c, ...patch }))}
          />

          <UploadTaskQueue
            tasks={queueTasks}
            config={config}
            onCancel={cancelTask}
            onRetry={(id) => void retryTask(id, config)}
            onClearCompleted={clearCompleted}
          />

          <div ref={recordsRef}>
            {showAllRecords ? (
              <>
                <FileFilterBar
                  keyword={keyword}
                  sourceType={sourceType}
                  parseStatus={parseStatus}
                  learningPathId={learningPathId}
                  sort={sort}
                  pathOptions={pathOptions}
                  onKeywordChange={setKeyword}
                  onSourceTypeChange={setSourceType}
                  onParseStatusChange={setParseStatus}
                  onLearningPathChange={setLearningPathId}
                  onSortChange={setSort}
                  onReset={() => {
                    setKeyword('')
                    setSourceType('all')
                    setParseStatus('all')
                    setLearningPathId('all')
                    setSort('uploadedAt')
                  }}
                />
                <div className="mt-4">
                  <UploadedFileList
                    files={files}
                    total={total}
                    page={page}
                    totalPages={totalPages}
                    loading={filesLoading}
                    error={filesError}
                    selectedId={selectedFile?.id}
                    batchMode={batchMode}
                    selectedIds={batchSelectedIds}
                    onSelect={(file) => setSelectedFile(file)}
                    onCheck={(id, checked) => {
                      setBatchSelectedIds((prev) =>
                        checked ? [...prev, id] : prev.filter((x) => x !== id),
                      )
                    }}
                    onPreview={(file) => void handlePreview(file)}
                    onViewParse={(file) => {
                      setSelectedFile(file)
                      setDrawerOpen(true)
                    }}
                    onGenerate={(file) => void handleGenerate(file)}
                    onRelate={(file) => {
                      setRelationFile(file)
                      setRelationOpen(true)
                    }}
                    onDelete={(file) => setDeleteTarget(file)}
                    onRetryParse={(file) => void handleRetryParse(file)}
                    onBatchDelete={() => setBatchDeleteOpen(true)}
                    onPageChange={goToPage}
                    onRetry={() => void refreshFiles()}
                    actionLoadingId={actionLoadingId}
                  />
                </div>
              </>
            ) : (
              <RecentUploadsTable
                files={recentFiles}
                total={total}
                loading={filesLoading}
                error={filesError}
                selectedId={selectedFile?.id}
                onSelect={(file) => setSelectedFile(file)}
                onViewAll={() => setShowAllRecords(true)}
                onPreview={(file) => void handlePreview(file)}
                onViewParse={(file) => {
                  setSelectedFile(file)
                  setDrawerOpen(true)
                }}
                onGenerate={(file) => void handleGenerate(file)}
                onRetry={() => void refreshFiles()}
              />
            )}
          </div>

          <p className="pb-2 text-center text-xs text-[#94A3B8]">
            Edu.AI © 2026 · 让学习更高效、更智能、更个性化
          </p>
        </div>
      </main>

      <UploadAuxPanel
        storage={storage}
        storageLoading={storageLoading}
        storageError={storageError}
        selectedFile={selectedFile}
        parseResult={parseResult}
        uploading={activeCount > 0}
        onRefreshStorage={() => void refreshStorage()}
        onPreviewExample={() => {
          if (selectedFile) setDrawerOpen(true)
        }}
      />

      <FileParseDrawer
        open={drawerOpen}
        file={selectedFile}
        parseResult={parseResult}
        loading={parseLoading}
        onClose={() => setDrawerOpen(false)}
        onGenerate={() => {
          if (selectedFile) void handleGenerate(selectedFile)
        }}
        onRetry={() => {
          if (selectedFile) void handleRetryParse(selectedFile)
        }}
      />

      <FileRelationModal
        open={relationOpen}
        file={relationFile}
        pathOptions={pathOptions}
        kpOptions={kpOptions}
        loading={relationLoading}
        onClose={() => setRelationOpen(false)}
        onSave={handleSaveRelations}
      />

      <ConfirmDialog
        open={deleteTarget != null}
        title="删除资料"
        description={`确定删除「${deleteTarget?.name}」吗？此操作不可恢复。`}
        confirmLabel="删除"
        loading={deleteLoading}
        onConfirm={() => void handleDelete()}
        onCancel={() => setDeleteTarget(null)}
      />

      <ConfirmDialog
        open={batchDeleteOpen}
        title="批量删除"
        description={`确定删除已选的 ${batchSelectedIds.length} 个资料吗？`}
        confirmLabel="删除"
        loading={deleteLoading}
        onConfirm={() => void handleBatchDelete()}
        onCancel={() => setBatchDeleteOpen(false)}
      />
    </div>
  )
}
