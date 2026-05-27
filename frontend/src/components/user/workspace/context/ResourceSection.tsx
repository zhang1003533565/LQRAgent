import { useState } from 'react'
import { useArtifactStore } from '@/components/user/store/artifactStore'
import { usePathStore } from '@/components/user/store/pathStore'
import { generateResource } from '@/api/user/resources'
import type { ResourceType } from '@/components/user/types/media-resource'
import { EmptyState, PlaceholderBanner } from '@/components/user/ui'
import MediaPreview from './MediaPreview'
import styles from './ResourceSection.module.css'

const TYPES: { key: ResourceType; label: string }[] = [
  { key: 'LESSON', label: '讲义' },
  { key: 'QUIZ', label: '题目' },
  { key: 'CODE_CASE', label: '代码' },
  { key: 'ILLUSTRATION', label: '示意图' },
  { key: 'VIDEO_CLIP', label: '视频' },
]

export default function ResourceSection() {
  const [activeType, setActiveType] = useState<ResourceType>('LESSON')
  const [loading, setLoading] = useState(false)
  const resources = useArtifactStore((s) => s.resources)
  const selectedKpId = usePathStore((s) => s.selectedKpId)
  const setResources = useArtifactStore((s) => s.setResources)

  const current =
    resources.find(
      (r) => r.resourceType === activeType && r.kpId === (selectedKpId ?? ''),
    ) ?? null

  async function handleLoad() {
    if (!selectedKpId) return
    setLoading(true)
    try {
      const res = await generateResource({ kpId: selectedKpId, resourceType: activeType })
      const others = resources.filter(
        (r) => !(r.kpId === res.kpId && r.resourceType === res.resourceType),
      )
      setResources([...others, res])
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className={styles.page}>
      <PlaceholderBanner label="资源生成 API 未实现" hint="当前为本地占位内容" />

      <div className={styles.typeRow}>
        {TYPES.map((t) => (
          <button
            key={t.key}
            type="button"
            className={activeType === t.key ? styles.typeActive : styles.type}
            onClick={() => setActiveType(t.key)}
          >
            {t.label}
          </button>
        ))}
      </div>

      {!selectedKpId ? (
        <EmptyState title="请先在「学习路径」中选择一个节点" />
      ) : (
        <>
          <button
            type="button"
            className={styles.loadBtn}
            onClick={handleLoad}
            disabled={loading}
          >
            {loading ? '加载中...' : '加载占位资源'}
          </button>
          {activeType === 'ILLUSTRATION' || activeType === 'VIDEO_CLIP' ? (
            <MediaPreview resource={current} />
          ) : current?.content ? (
            <article className={styles.content}>{current.content}</article>
          ) : (
            <EmptyState title="暂无内容" description="点击上方按钮加载占位资源" />
          )}
        </>
      )}
    </div>
  )
}
