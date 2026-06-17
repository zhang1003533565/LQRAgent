import styles from './VideoPlayer.module.css'

interface Props {
  url: string
  caption?: string
}

export default function VideoPlayer({ url, caption }: Props) {
  if (!url) return null

  return (
    <div className={styles.wrapper}>
      <video
        className={styles.video}
        src={url}
        controls
        preload="metadata"
      >
        您的浏览器不支持视频播放
      </video>
      {caption && <p className={styles.caption}>{caption}</p>}
    </div>
  )
}
