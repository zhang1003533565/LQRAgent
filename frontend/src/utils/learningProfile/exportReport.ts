/** 将 Markdown 报告转为可打印 HTML（支持中文系统字体） */
export function markdownReportToHtml(markdown: string): string {
  const escaped = markdown
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')

  const lines = escaped.split('\n')
  const body: string[] = []

  for (const line of lines) {
    if (line.startsWith('# ')) {
      body.push(`<h1>${line.slice(2)}</h1>`)
    } else if (line.startsWith('## ')) {
      body.push(`<h2>${line.slice(3)}</h2>`)
    } else if (line.startsWith('- ')) {
      body.push(`<p class="bullet">${line.slice(2)}</p>`)
    } else if (line.trim() === '') {
      body.push('<br/>')
    } else {
      body.push(`<p>${line}</p>`)
    }
  }

  return `<!DOCTYPE html>
<html lang="zh-CN">
<head>
  <meta charset="utf-8"/>
  <title>学习画像报告</title>
  <style>
    body { font-family: "Microsoft YaHei", "PingFang SC", system-ui, sans-serif; padding: 32px; color: #0f172a; line-height: 1.65; max-width: 720px; margin: 0 auto; }
    h1 { font-size: 22px; margin-bottom: 8px; }
    h2 { font-size: 16px; margin: 20px 0 8px; color: #2563eb; }
    p { margin: 4px 0; font-size: 14px; }
    p.bullet { padding-left: 12px; }
    p.bullet::before { content: "• "; color: #64748b; }
    @media print { body { padding: 16px; } }
  </style>
</head>
<body>${body.join('\n')}</body>
</html>`
}

export function downloadMarkdownFile(content: string, fileName: string) {
  const blob = new Blob([content], { type: 'text/markdown;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = fileName.endsWith('.md') ? fileName : `${fileName}.md`
  a.click()
  URL.revokeObjectURL(url)
}

/** 浏览器「打印为 PDF」 */
export function printProfileReportAsPdf(markdown: string) {
  const html = markdownReportToHtml(markdown)
  const win = window.open('', '_blank', 'noopener,noreferrer')
  if (!win) {
    throw new Error('无法打开打印窗口，请允许弹窗后重试')
  }
  win.document.write(html)
  win.document.close()
  win.focus()
  setTimeout(() => {
    win.print()
  }, 300)
}
