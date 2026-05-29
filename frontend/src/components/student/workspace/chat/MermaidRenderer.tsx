import { useEffect, useState } from 'react'

const CDN_URL = 'https://cdn.jsdelivr.net/npm/mermaid@11/dist/mermaid.min.js'

let mermaidLoaded = false
let mermaidLoading = false

function loadMermaid(): Promise<void> {
  if (mermaidLoaded) return Promise.resolve()
  if (mermaidLoading) {
    return new Promise((resolve) => {
      const check = setInterval(() => {
        if (mermaidLoaded) { clearInterval(check); resolve() }
      }, 50)
    })
  }
  mermaidLoading = true
  return new Promise((resolve, reject) => {
    const script = document.createElement('script')
    script.src = CDN_URL
    script.onload = () => {
      // @ts-expect-error mermaid loaded via CDN
      window.mermaid?.initialize({ startOnLoad: false, theme: 'default', securityLevel: 'loose' })
      mermaidLoaded = true
      resolve()
    }
    script.onerror = () => reject(new Error('Failed to load mermaid from CDN'))
    document.head.appendChild(script)
  })
}

interface Props {
  code: string
}

export default function MermaidRenderer({ code }: Props) {
  const [svg, setSvg] = useState('')
  const [error, setError] = useState(false)

  useEffect(() => {
    let cancelled = false
    async function render() {
      try {
        await loadMermaid()
        // @ts-expect-error mermaid loaded via CDN
        const mermaid = window.mermaid
        if (!mermaid) throw new Error('mermaid not available')
        const id = `mermaid-${Math.random().toString(36).slice(2, 9)}`
        const { svg: rendered } = await mermaid.render(id, code.trim())
        if (!cancelled) setSvg(rendered)
      } catch {
        if (!cancelled) setError(true)
      }
    }
    render()
    return () => { cancelled = true }
  }, [code])

  if (error) {
    return (
      <div style={{ padding: 12, background: 'rgba(229,62,62,0.06)', borderRadius: 8, fontSize: 13, color: '#e53e3e' }}>
        Mermaid 渲染失败，原始代码：
        <pre style={{ marginTop: 8, whiteSpace: 'pre-wrap', fontSize: 12, color: '#526989' }}>{code}</pre>
      </div>
    )
  }

  if (!svg) {
    return <div style={{ padding: 12, color: '#8b9ab6', fontSize: 13 }}>渲染中...</div>
  }

  return (
    <div
      className="mermaid-rendered"
      style={{ overflow: 'auto', textAlign: 'center' }}
      dangerouslySetInnerHTML={{ __html: svg }}
    />
  )
}
