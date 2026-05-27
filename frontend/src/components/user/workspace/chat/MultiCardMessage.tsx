import type { MultiCardBlock } from '@/components/user/types/multi-card'
import styles from './MultiCardMessage.module.css'

interface Props {
  cards: MultiCardBlock[]
}

export default function MultiCardMessage({ cards }: Props) {
  return (
    <div className={styles.grid}>
      {cards.map((block, i) => (
        <div key={i} className={styles.block}>
          {block.type === 'text' && (
            <p className={styles.text}>{block.content}</p>
          )}
          {block.type === 'image' && block.url && (
            <figure>
              <img src={block.url} alt={block.title ?? '配图'} />
              {block.title && <figcaption>{block.title}</figcaption>}
            </figure>
          )}
          {block.type === 'video' && block.url && (
            <video src={block.url} controls poster={block.url}>
              视频预览（占位）
            </video>
          )}
          {block.type === 'artifact_ref' && (
            <p className={styles.ref}>
              关联产物：{block.artifactKind ?? 'artifact'}
            </p>
          )}
        </div>
      ))}
    </div>
  )
}
