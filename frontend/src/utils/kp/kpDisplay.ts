import type { PathNode } from '@/utils/types/learning-path'

const KNOWN_MAP: Record<string, string> = {
  kp_input_output: '输入输出',
  kp_variables: '变量与数据类型',
  kp_string: '字符串',
  kp_list: '列表',
  kp_dict: '字典',
  kp_control_flow: '控制流',
  kp_function: '函数',
  kp_python_function: 'Python 函数定义与调用',
  kp_python_closure: '闭包机制与作用域',
  kp_python_decorator: 'Python 装饰器',
  kp_python_variable: 'Python 变量与数据类型',
  kp_python_control_flow: 'Python 控制流',
  kp_python_class: 'Python 类与面向对象',
  kp_python_module: 'Python 模块与包管理',
  kp_python_exception: 'Python 异常处理',
  kp_python_file_io: 'Python 文件读写',
  kp_python_list: 'Python 列表与元组',
  kp_python_dict: 'Python 字典与集合',
  kp_python_generator: 'Python 生成器与迭代器',
  kp_python_lambda: 'Python Lambda 表达式',
  kp_python_regex: 'Python 正则表达式',
  kp_python_threading: 'Python 多线程与并发',
  kp_python_async: 'Python 异步编程',
}

/** 将 kp_xxx 技术 ID 转译为可读名称 */
export function sanitizeKpTitle(raw: string): string {
  const key = raw.trim()
  if (!key) return '综合练习'
  if (KNOWN_MAP[key]) return KNOWN_MAP[key]

  return key
    .replace(/^kp_/, '')
    .replace(/dynamic_\d+/g, '')
    .replace(/_/g, ' ')
    .replace(/\b\w/g, (c) => c.toUpperCase())
    .trim()
}

/** 优先用路径节点标题，否则回退到清洗后的 kpId */
export function resolveKpDisplay(kpId: string, nodes: PathNode[]): string {
  const normalized = kpId.trim()
  if (!normalized) return '综合练习'

  const node = nodes.find(
    (n) => n.kpId === normalized || n.kpId === normalized.replace(/^kp_/, ''),
  )
  if (node?.title) return node.title

  return sanitizeKpTitle(normalized)
}

export function buildPathLabel(goal: string): string {
  const trimmed = goal.trim()
  return trimmed ? `学习路径 > ${trimmed}` : '学习路径'
}
