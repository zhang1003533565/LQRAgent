export function getGreeting(name: string): string {
  const hour = new Date().getHours()
  const period = hour < 12 ? '上午好' : hour < 18 ? '下午好' : '晚上好'
  return `${period}，${name}! 👋`
}
