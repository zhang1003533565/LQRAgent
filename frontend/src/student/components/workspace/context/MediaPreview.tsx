import type { LearningResource } from '@/shared/types/media-resource'
import { EmptyState } from '@/shared/components/ui'
import styles from './MediaPreview.module.css'

interface Props {
  resource: LearningResource | null
}

export default function MediaPreview({ resource }: Props) {
  if (!resource?.mediaUrl) {
    return (
      <EmptyState
        title="暂无媒体预览"
        description="示意图或视频生成后将在此展示"
      />
    )
  }

  const isVideo =
    resource.resourceType === 'VIDEO_CLIP' ||
    resource.mediaMime?.startsWith('video/')

  return (
    <div className={styles.wrap}>
      {resource.title && <p className={styles.title}>{resource.title}</p>}
      {isVideo ? (
        <video className={styles.media} src={resource.mediaUrl} controls />
      ) : (
        <img
          className={styles.media}
          src={resource.mediaUrl}
          alt={resource.title}
        />
      )}
    </div>
  )
}
