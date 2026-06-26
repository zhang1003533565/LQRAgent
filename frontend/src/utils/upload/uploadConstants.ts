/** 前端上传配置 — 待后端 GET /api/upload/config 对接后替换 */
export const UPLOAD_CONFIG = {
  defaultTotalBytes: 5 * 1024 * 1024 * 1024,
  defaultMaxFileSizeBytes: 50 * 1024 * 1024,
  defaultPageSize: 8,
  clientQueuePageSize: 5,
} as const

export const SUPPORTED_EXTENSIONS = [
  'pdf',
  'doc',
  'docx',
  'ppt',
  'pptx',
  'xls',
  'xlsx',
  'md',
  'txt',
  'rst',
  'py',
  'java',
  'kt',
  'js',
  'ts',
  'go',
  'rs',
  'c',
  'cpp',
  'h',
  'png',
  'jpg',
  'jpeg',
  'gif',
  'webp',
  'svg',
  'mp4',
  'avi',
  'mov',
  'mp3',
  'wav',
  'ogg',
  'json',
  'yaml',
  'yml',
  'toml',
  'xml',
  'csv',
] as const

export const SUPPORTED_MIME_HINTS = [
  'application/pdf',
  'application/msword',
  'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
  'application/vnd.ms-powerpoint',
  'application/vnd.openxmlformats-officedocument.presentationml.presentation',
  'text/plain',
  'text/markdown',
  'image/*',
  'audio/*',
  'video/*',
] as const

export function formatBytes(bytes: number): string {
  if (!bytes || bytes <= 0) return '—'
  const units = ['B', 'KB', 'MB', 'GB']
  let v = bytes
  let i = 0
  while (v >= 1024 && i < units.length - 1) {
    v /= 1024
    i += 1
  }
  return `${v.toFixed(i === 0 ? 0 : 1)} ${units[i]}`
}

export function formatDateTime(value?: string): string {
  if (!value) return '—'
  try {
    return new Date(value).toLocaleString('zh-CN', {
      month: 'short',
      day: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
    })
  } catch {
    return '—'
  }
}
