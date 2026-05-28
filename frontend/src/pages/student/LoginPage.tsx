import { useState, type FormEvent } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { User, Lock, Eye, EyeOff, HelpCircle, Home } from 'lucide-react'
import { login, register } from '@/api/student/auth'
import { useAuthStore } from '@/utils/store/authStore'
import loginImg from '@/assets/student/login.png'
import styles from './LoginPage.module.css'

export default function LoginPage() {
  const navigate = useNavigate()
  const setUser = useAuthStore((s) => s.setUser)

  const [isRegister, setIsRegister] = useState(false)
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [confirmPassword, setConfirmPassword] = useState('')
  const [showPassword, setShowPassword] = useState(false)
  const [showConfirmPassword, setShowConfirmPassword] = useState(false)
  const [rememberMe, setRememberMe] = useState(false)
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(false)

  async function handleLogin(e: FormEvent) {
    e.preventDefault()
    setError('')
    setLoading(true)
    try {
      const user = await login({ username, password })
      setUser(user)
      navigate(user.redirectPath, { replace: true })
    } catch (err: unknown) {
      const msg =
        (err as { response?: { data?: { message?: string } } })?.response?.data
          ?.message ?? '登录失败，请检查用户名和密码'
      setError(msg)
    } finally {
      setLoading(false)
    }
  }

  async function handleRegister(e: FormEvent) {
    e.preventDefault()
    setError('')

    if (password !== confirmPassword) {
      setError('两次输入的密码不一致')
      return
    }

    setLoading(true)
    try {
      const user = await register({ username, password })
      setUser(user)
      navigate(user.redirectPath, { replace: true })
    } catch (err: unknown) {
      const msg =
        (err as { response?: { data?: { message?: string } } })?.response?.data
          ?.message ?? '注册失败，请稍后重试'
      setError(msg)
    } finally {
      setLoading(false)
    }
  }

  function toggleMode() {
    setIsRegister((v) => !v)
    setError('')
    setPassword('')
    setConfirmPassword('')
    setShowPassword(false)
    setShowConfirmPassword(false)
  }

  return (
    <div className={styles.container}>
      <div className={styles.left}>
        <img
          src={loginImg}
          alt="智能学习平台"
          className={styles.leftImage}
        />
      </div>

      <div className={styles.right}>
        <div className={styles.topActions}>
          <Link to="/help" className={styles.topLink}>
            <HelpCircle size={18} />
            <span>帮助中心</span>
          </Link>
          <span className={styles.topDivider}>|</span>
          <Link to="/" className={styles.topLink}>
            <Home size={18} />
            <span>返回首页</span>
          </Link>
        </div>

        <div className={styles.rightInner}>
          <div className={styles.card}>
            {isRegister ? (
              <>
                <h2 className={styles.formTitle}>欢迎注册</h2>
                <p className={styles.formSubtitle}>
                  创建 Edu.AI 账号，开启个性化学习之旅
                </p>

                <form onSubmit={handleRegister} className={styles.form}>
                  <div className={styles.inputGroup}>
                    <div className={styles.inputIcon}>
                      <User size={20} />
                    </div>
                    <input
                      id="username"
                      type="text"
                      value={username}
                      onChange={(e) => setUsername(e.target.value)}
                      placeholder="学号 / 账号"
                      required
                      minLength={3}
                      autoFocus
                      className={styles.input}
                    />
                  </div>

                  <div className={styles.inputGroup}>
                    <div className={styles.inputIcon}>
                      <Lock size={20} />
                    </div>
                    <input
                      id="password"
                      type={showPassword ? 'text' : 'password'}
                      value={password}
                      onChange={(e) => setPassword(e.target.value)}
                      placeholder="密码（至少6位）"
                      required
                      minLength={6}
                      className={styles.input}
                    />
                    <button
                      type="button"
                      className={styles.eyeBtn}
                      onClick={() => setShowPassword((v) => !v)}
                      tabIndex={-1}
                    >
                      {showPassword ? <EyeOff size={20} /> : <Eye size={20} />}
                    </button>
                  </div>

                  <div className={styles.inputGroup}>
                    <div className={styles.inputIcon}>
                      <Lock size={20} />
                    </div>
                    <input
                      id="confirmPassword"
                      type={showConfirmPassword ? 'text' : 'password'}
                      value={confirmPassword}
                      onChange={(e) => setConfirmPassword(e.target.value)}
                      placeholder="确认密码"
                      required
                      className={styles.input}
                    />
                    <button
                      type="button"
                      className={styles.eyeBtn}
                      onClick={() => setShowConfirmPassword((v) => !v)}
                      tabIndex={-1}
                    >
                      {showConfirmPassword ? <EyeOff size={20} /> : <Eye size={20} />}
                    </button>
                  </div>

                  {error && <p className={styles.error}>{error}</p>}

                  <button
                    type="submit"
                    disabled={loading}
                    className={styles.btn}
                  >
                    {loading ? '注册中...' : '注册'}
                  </button>

                  <button
                    type="button"
                    className={styles.btnOutline}
                    onClick={toggleMode}
                  >
                    已有账号？去登录
                  </button>
                </form>
              </>
            ) : (
              <>
                <h2 className={styles.formTitle}>欢迎登录</h2>
                <p className={styles.formSubtitle}>
                  登录 Edu.AI 开始你的个性化学习之旅
                </p>

                <form onSubmit={handleLogin} className={styles.form}>
                  <div className={styles.inputGroup}>
                    <div className={styles.inputIcon}>
                      <User size={20} />
                    </div>
                    <input
                      id="username"
                      type="text"
                      value={username}
                      onChange={(e) => setUsername(e.target.value)}
                      placeholder="学号 / 账号"
                      required
                      autoFocus
                      className={styles.input}
                    />
                  </div>

                  <div className={styles.inputGroup}>
                    <div className={styles.inputIcon}>
                      <Lock size={20} />
                    </div>
                    <input
                      id="password"
                      type={showPassword ? 'text' : 'password'}
                      value={password}
                      onChange={(e) => setPassword(e.target.value)}
                      placeholder="密码"
                      required
                      className={styles.input}
                    />
                    <button
                      type="button"
                      className={styles.eyeBtn}
                      onClick={() => setShowPassword((v) => !v)}
                      tabIndex={-1}
                    >
                      {showPassword ? <EyeOff size={20} /> : <Eye size={20} />}
                    </button>
                  </div>

                  <div className={styles.options}>
                    <label className={styles.remember}>
                      <input
                        type="checkbox"
                        checked={rememberMe}
                        onChange={(e) => setRememberMe(e.target.checked)}
                      />
                      <span>记住我</span>
                    </label>
                    <Link to="/forgot-password" className={styles.forgot}>
                      忘记密码?
                    </Link>
                  </div>

                  {error && <p className={styles.error}>{error}</p>}

                  <button
                    type="submit"
                    disabled={loading}
                    className={styles.btn}
                  >
                    {loading ? '登录中...' : '登录'}
                  </button>

                  <button
                    type="button"
                    className={styles.btnOutline}
                    onClick={toggleMode}
                  >
                    注册
                  </button>
                </form>

                <p className={styles.agreement}>
                  登录即表示同意
                  <Link to="/terms">《用户协议》</Link>
                  与
                  <Link to="/privacy">《隐私政策》</Link>
                </p>
              </>
            )}
          </div>
        </div>
      </div>
    </div>
  )
}
