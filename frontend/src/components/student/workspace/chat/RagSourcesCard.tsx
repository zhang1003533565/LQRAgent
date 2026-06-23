import { useState } from 'react'
import type { RagSource } from '@/utils/types/artifact'
import styles from './RagSourcesCard.module.css'

interface Props {
  sources: RagSource[]
}

function SourceIcon() {
  return (
    <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
      <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z" />
      <polyline points="14 2 14 8 20 8" />
      <line x1="16" y1="13" x2="8" y2="13" />
      <line x1="16" y1="17" x2="8" y2="17" />
    </svg>
  )
}

function ChevronIcon({ open }: { open: boolean }) {
  return (
    <svg
      width="14"
      height="14"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="2"
      className={open ? styles.chevronOpen : styles.chevron}
    >
      <polyline points="6 9 12 15 18 9" />
    </svg>
  )
}

function formatScore(score: number | string | undefined): string {
  if (score === undefined || score === '') return ''
  const num = typeof score === 'string' ? parseFloat(score) : score
  if (isNaN(num)) return ''
  return `${(num * 100).toFixed(0)}%`
}

function getFileName(path: string): string {
  if (!path) return ''
  // Extract file name from path
  const parts = path.replace(/\\/g, '/').split('/')
  return parts[parts.length - 1] || path
}

export default function RagSourcesCard({ sources }: Props) {
  const [expanded, setExpanded] = useState(false)
  const [openIdx, setOpenIdx] = useState<number | null>(null)

  if (!sources || sources.length === 0) return null

  // Deduplicate by source file name
  const uniqueFiles = [...new Map(sources.map(s => [s.source || s.title, s])).values()]

  return (
    <div className={styles.container}>
      <button
        className={styles.header}
        onClick={() => setExpanded(!expanded)}
      >
        <div className={styles.headerLeft}>
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="#6366f1" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
            <path d="M2 3h6a4 4 0 0 1 4 4v14a3 3 0 0 0-3-3H2z" />
            <path d="M22 3h-6a4 4 0 0 0-4 4v14a3 3 0 0 1 3-3h7z" />
          </svg>
          <span className={styles.headerText}>
            参考了 {sources.length} 个知识片段
            {uniqueFiles.length > 1 && `（来自 ${uniqueFiles.length} 个文件）`}
          </span>
        </div>
        <ChevronIcon open={expanded} />
      </button>

      {expanded && (
        <div className={styles.body}>
          {sources.map((src, i) => {
            const isOpen = openIdx === i
            const score = formatScore(src.score)
            return (
              <div key={i} className={styles.sourceItem}>
                <button
                  className={styles.sourceHeader}
                  onClick={() => setOpenIdx(isOpen ? null : i)}
                >
                  <div className={styles.sourceMeta}>
                    <span className={styles.sourceIdx}>{i + 1}</span>
                    <SourceIcon />
                    <span className={styles.sourceTitle}>{src.title || getFileName(src.source)}</span>
                    {src.kbName && <span className={styles.pageTag}>{src.kbName}</span>}
                    {src.page && <span className={styles.pageTag}>p.{src.page}</span>}
                    {score && <span className={styles.scoreTag}>{score}</span>}
                  </div>
                  <ChevronIcon open={isOpen} />
                </button>
                {isOpen && (
                  <div className={styles.sourceContent}>
                    <p>{src.content}</p>
                    {src.source && (
                      <div className={styles.sourcePath}>{src.source}</div>
                    )}
                  </div>
                )}
              </div>
            )
          })}
        </div>
      )}
    </div>
  )
}
