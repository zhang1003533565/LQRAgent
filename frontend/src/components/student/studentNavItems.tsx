import type { JSX } from 'react'

export type NavItem = {
  to: string
  label: string
  end?: boolean
  icon: JSX.Element
}

export const STUDENT_NAV_ITEMS: NavItem[] = [
  {
    to: '/workspace',
    end: true,
    label: '聊天学习',
    icon: (
      <svg viewBox="0 0 24 24" aria-hidden="true">
        <path
          d="M7 18.5c-1.933 0-3.5-1.567-3.5-3.5v-6c0-1.933 1.567-3.5 3.5-3.5h10c1.933 0 3.5 1.567 3.5 3.5v6c0 1.933-1.567 3.5-3.5 3.5H12l-4.5 3v-3H7Z"
          fill="none"
          stroke="currentColor"
          strokeWidth="1.8"
          strokeLinecap="round"
          strokeLinejoin="round"
        />
        <circle cx="9" cy="12" r="1" fill="currentColor" />
        <circle cx="12" cy="12" r="1" fill="currentColor" />
        <circle cx="15" cy="12" r="1" fill="currentColor" />
      </svg>
    ),
  },
  {
    to: '/workspace/dashboard',
    label: '学习概览',
    icon: (
      <svg viewBox="0 0 24 24" aria-hidden="true">
        <rect x="3" y="3" width="7" height="7" rx="1.5" fill="none" stroke="currentColor" strokeWidth="1.8" />
        <rect x="14" y="3" width="7" height="7" rx="1.5" fill="none" stroke="currentColor" strokeWidth="1.8" />
        <rect x="3" y="14" width="7" height="7" rx="1.5" fill="none" stroke="currentColor" strokeWidth="1.8" />
        <rect x="14" y="14" width="7" height="7" rx="1.5" fill="none" stroke="currentColor" strokeWidth="1.8" />
      </svg>
    ),
  },
  {
    to: '/workspace/learning-path',
    label: '学习路径',
    icon: (
      <svg viewBox="0 0 24 24" aria-hidden="true">
        <path
          d="M4.75 6.75A1.75 1.75 0 0 1 6.5 5h4.25c.93 0 1.79.465 2.31 1.24.52-.775 1.38-1.24 2.31-1.24h2.13a1.75 1.75 0 0 1 1.75 1.75v10.5A1.75 1.75 0 0 1 17.5 19.5h-2.13c-.93 0-1.79.465-2.31 1.24-.52-.775-1.38-1.24-2.31-1.24H6.5a1.75 1.75 0 0 1-1.75-1.75V6.75Z"
          fill="none"
          stroke="currentColor"
          strokeWidth="1.8"
          strokeLinecap="round"
          strokeLinejoin="round"
        />
        <path d="M12.5 7v13" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" />
      </svg>
    ),
  },
  {
    to: '/workspace/resources',
    label: '学习资源',
    icon: (
      <svg viewBox="0 0 24 24" aria-hidden="true">
        <rect
          x="4"
          y="5"
          width="16"
          height="14"
          rx="2.5"
          fill="none"
          stroke="currentColor"
          strokeWidth="1.8"
        />
        <path d="M8 10.5h8M8 14h5" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" />
      </svg>
    ),
  },
  {
    to: '/workspace/quiz',
    label: '答题',
    icon: (
      <svg viewBox="0 0 24 24" aria-hidden="true">
        <path
          d="M12 20.25c4.556 0 8.25-3.694 8.25-8.25S16.556 3.75 12 3.75 3.75 7.444 3.75 12 7.444 20.25 12 20.25Z"
          fill="none"
          stroke="currentColor"
          strokeWidth="1.8"
        />
        <path
          d="M9.7 9.15a2.65 2.65 0 1 1 4.65 1.73c-.52.59-1.32 1.1-1.85 1.7-.3.33-.5.7-.5 1.17"
          fill="none"
          stroke="currentColor"
          strokeWidth="1.8"
          strokeLinecap="round"
          strokeLinejoin="round"
        />
        <circle cx="12" cy="16.4" r="1" fill="currentColor" />
      </svg>
    ),
  },
  {
    to: '/workspace/upload',
    label: '上传',
    icon: (
      <svg viewBox="0 0 24 24" aria-hidden="true">
        <path
          d="M12 15V6.75M8.75 10l3.25-3.25L15.25 10M5.75 15.5v1A1.75 1.75 0 0 0 7.5 18.25h9A1.75 1.75 0 0 0 18.25 16.5v-1"
          fill="none"
          stroke="currentColor"
          strokeWidth="1.8"
          strokeLinecap="round"
          strokeLinejoin="round"
        />
      </svg>
    ),
  },
  {
    to: '/workspace/profile',
    label: '学习画像',
    icon: (
      <svg viewBox="0 0 24 24" aria-hidden="true">
        <circle cx="12" cy="8" r="3.25" fill="none" stroke="currentColor" strokeWidth="1.8" />
        <path
          d="M5.5 18.25c.72-2.76 3.35-4.75 6.5-4.75s5.78 1.99 6.5 4.75"
          fill="none"
          stroke="currentColor"
          strokeWidth="1.8"
          strokeLinecap="round"
        />
      </svg>
    ),
  },
  {
    to: '/workspace/knowledge-graph',
    label: '知识图谱',
    icon: (
      <svg viewBox="0 0 24 24" aria-hidden="true">
        <circle cx="12" cy="6" r="2.5" fill="none" stroke="currentColor" strokeWidth="1.8" />
        <circle cx="6" cy="17" r="2.5" fill="none" stroke="currentColor" strokeWidth="1.8" />
        <circle cx="18" cy="17" r="2.5" fill="none" stroke="currentColor" strokeWidth="1.8" />
        <path d="M10 8l-3 7M14 8l3 7M8.5 17h7" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" />
      </svg>
    ),
  },
]
