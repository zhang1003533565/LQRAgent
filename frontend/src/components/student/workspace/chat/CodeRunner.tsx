import { useState } from 'react'
import styles from './CodeRunner.module.css'

interface Props {
  code: string
  language: string
}

export default function CodeRunner({ code, language }: Props) {
  const [output, setOutput] = useState<string | null>(null)
  const [running, setRunning] = useState(false)
  const [showCode, setShowCode] = useState(true)

  const handleRun = async () => {
    setRunning(true)
    setOutput('执行中...')

    try {
      // 调用后端代码执行接口
      const resp = await fetch('/api/code/execute', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ code, language }),
      })

      if (!resp.ok) {
        // 后端没有代码执行接口时，显示提示
        setOutput('代码执行服务未配置。请在 AI 服务中启用代码执行功能。')
        return
      }

      const data = await resp.json()
      setOutput(data.output ?? data.error ?? '执行完成（无输出）')
    } catch {
      setOutput('代码执行服务不可用。代码仅供参考。')
    } finally {
      setRunning(false)
    }
  }

  const handleCopy = () => {
    navigator.clipboard.writeText(code)
  }

  return (
    <div className={styles.wrapper}>
      <div className={styles.toolbar}>
        <span className={styles.lang}>{language}</span>
        <div className={styles.actions}>
          <button className={styles.btn} onClick={handleCopy} title="复制代码">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
              <rect x="9" y="9" width="13" height="13" rx="2"/>
              <path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1"/>
            </svg>
            复制
          </button>
          <button
            className={styles.btn}
            onClick={() => setShowCode(v => !v)}
          >
            {showCode ? '收起' : '展开'}
          </button>
          {['python', 'js', 'javascript', 'typescript'].includes(language.toLowerCase()) && (
            <button
              className={styles.runBtn}
              onClick={handleRun}
              disabled={running}
            >
              {running ? '执行中...' : '▶ 运行'}
            </button>
          )}
        </div>
      </div>

      {showCode && (
        <pre className={styles.code}>
          <code>{code}</code>
        </pre>
      )}

      {output !== null && (
        <div className={styles.output}>
          <div className={styles.outputLabel}>输出结果：</div>
          <pre className={styles.outputCode}>{output}</pre>
        </div>
      )}
    </div>
  )
}
