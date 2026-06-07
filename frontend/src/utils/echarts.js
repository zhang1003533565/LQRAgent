const DEFAULT_COLOR_PALETTE = [
  '#2563eb',
  '#14b8a6',
  '#f59e0b',
  '#ef4444',
  '#8b5cf6',
  '#06b6d4',
  '#84cc16',
  '#f97316',
]

const DEFAULT_GRID = {
  left: 16,
  right: 16,
  top: 48,
  bottom: 24,
  containLabel: true,
}

const DEFAULT_TEXT_STYLE = {
  color: '#475569',
  fontSize: 12,
}

const DEFAULT_AXIS_LINE = {
  lineStyle: {
    color: '#cbd5e1',
  },
}

const DEFAULT_AXIS_TICK = {
  show: false,
}

const DEFAULT_SPLIT_LINE = {
  lineStyle: {
    color: '#e2e8f0',
    type: 'dashed',
  },
}

const DEFAULT_TOOLTIP = {
  trigger: 'axis',
  backgroundColor: 'rgba(15, 23, 42, 0.92)',
  borderWidth: 0,
  padding: [10, 12],
  textStyle: {
    color: '#f8fafc',
    fontSize: 12,
  },
  axisPointer: {
    type: 'line',
    lineStyle: {
      color: '#94a3b8',
      type: 'dashed',
    },
  },
}

const DEFAULT_LEGEND = {
  top: 12,
  right: 16,
  icon: 'roundRect',
  itemWidth: 10,
  itemHeight: 10,
  textStyle: DEFAULT_TEXT_STYLE,
}

const DEFAULT_X_AXIS = {
  type: 'category',
  boundaryGap: false,
  axisLine: DEFAULT_AXIS_LINE,
  axisTick: DEFAULT_AXIS_TICK,
  axisLabel: DEFAULT_TEXT_STYLE,
}

const DEFAULT_Y_AXIS = {
  type: 'value',
  axisLine: {
    show: false,
  },
  axisTick: DEFAULT_AXIS_TICK,
  axisLabel: DEFAULT_TEXT_STYLE,
  splitLine: DEFAULT_SPLIT_LINE,
}

const DEFAULT_LINE_SERIES = {
  type: 'line',
  smooth: true,
  symbol: 'circle',
  symbolSize: 8,
  showSymbol: false,
  lineStyle: {
    width: 3,
  },
  itemStyle: {
    borderWidth: 2,
    borderColor: '#ffffff',
  },
  emphasis: {
    focus: 'series',
  },
}

const DEFAULT_BAR_SERIES = {
  type: 'bar',
  barMaxWidth: 32,
  borderRadius: [8, 8, 0, 0],
  emphasis: {
    focus: 'series',
  },
}

const DEFAULT_PIE_SERIES = {
  type: 'pie',
  radius: ['40%', '70%'],
  avoidLabelOverlap: false,
  itemStyle: {
    borderRadius: 10,
    borderColor: '#fff',
    borderWidth: 2,
  },
  label: {
    show: false,
    position: 'center',
  },
  emphasis: {
    label: {
      show: true,
      fontSize: 24,
      fontWeight: 'bold',
      color: '#0f172a',
    },
  },
  labelLine: {
    show: false,
  },
}

function isPlainObject(value) {
  return Object.prototype.toString.call(value) === '[object Object]'
}

function mergeArrayItems(baseArray = [], customArray = []) {
  const maxLength = Math.max(baseArray.length, customArray.length)

  return Array.from({ length: maxLength }, (_, index) => {
    const baseItem = baseArray[index]
    const customItem = customArray[index]

    if (customItem === undefined) {
      return baseItem
    }

    if (isPlainObject(baseItem) && isPlainObject(customItem)) {
      return deepMerge(baseItem, customItem)
    }

    return customItem
  })
}

function deepMerge(baseConfig = {}, customConfig = {}) {
  const result = { ...baseConfig }

  Object.keys(customConfig).forEach((key) => {
    const baseValue = result[key]
    const customValue = customConfig[key]

    if (Array.isArray(baseValue) && Array.isArray(customValue)) {
      result[key] = mergeArrayItems(baseValue, customValue)
      return
    }

    if (isPlainObject(baseValue) && isPlainObject(customValue)) {
      result[key] = deepMerge(baseValue, customValue)
      return
    }

    result[key] = customValue
  })

  return result
}

function createSeriesDefaults(series = []) {
  return series.map((item) => {
    if (!item?.type || item.type === 'line') {
      return deepMerge(DEFAULT_LINE_SERIES, item)
    }

    if (item.type === 'bar') {
      return deepMerge(DEFAULT_BAR_SERIES, item)
    }

    if (item.type === 'pie') {
      return deepMerge(DEFAULT_PIE_SERIES, item)
    }

    return item
  })
}

export function createBaseChartOption(config = {}) {
  const {
    title,
    color = DEFAULT_COLOR_PALETTE,
    grid,
    tooltip,
    legend,
    xAxis,
    yAxis,
    series = [],
    ...rest
  } = config

  const resolvedXAxis =
    xAxis === null
      ? undefined
      : Array.isArray(xAxis)
        ? mergeArrayItems([], xAxis).map((axis) => deepMerge(DEFAULT_X_AXIS, axis || {}))
        : deepMerge(DEFAULT_X_AXIS, xAxis || {})

  const resolvedYAxis =
    yAxis === null
      ? undefined
      : Array.isArray(yAxis)
        ? mergeArrayItems([], yAxis).map((axis) => deepMerge(DEFAULT_Y_AXIS, axis || {}))
        : deepMerge(DEFAULT_Y_AXIS, yAxis || {})

  const option = {
    color,
    grid: deepMerge(DEFAULT_GRID, grid || {}),
    tooltip: deepMerge(DEFAULT_TOOLTIP, tooltip || {}),
    legend: deepMerge(DEFAULT_LEGEND, legend || {}),
    series: createSeriesDefaults(series),
    ...rest,
  }

  if (resolvedXAxis !== undefined) {
    option.xAxis = resolvedXAxis
  }

  if (resolvedYAxis !== undefined) {
    option.yAxis = resolvedYAxis
  }

  if (title) {
    option.title = {
      text: title,
      left: 16,
      top: 12,
      textStyle: {
        color: '#0f172a',
        fontSize: 14,
        fontWeight: 600,
      },
    }
  }

  return option
}

export function createLineChartOption(config = {}) {
  return createBaseChartOption({
    ...config,
    xAxis: deepMerge(
      DEFAULT_X_AXIS,
      config.xAxis || {},
    ),
    series: (config.series || []).map((item) => ({
      ...item,
      type: 'line',
    })),
  })
}

export function createBarChartOption(config = {}) {
  return createBaseChartOption({
    ...config,
    xAxis: deepMerge(
      {
        ...DEFAULT_X_AXIS,
        boundaryGap: true,
      },
      config.xAxis || {},
    ),
    series: (config.series || []).map((item) => ({
      ...item,
      type: 'bar',
    })),
  })
}

export function createDonutChartOption(config = {}) {
  return createBaseChartOption({
    ...config,
    tooltip: deepMerge(
      {
        ...DEFAULT_TOOLTIP,
        trigger: 'item',
      },
      config.tooltip || {},
    ),
    legend: deepMerge(
      {
        ...DEFAULT_LEGEND,
        top: '5%',
        left: 'center',
        right: 'auto',
      },
      config.legend || {},
    ),
    xAxis: null,
    yAxis: null,
    series: (config.series || []).map((item) => ({
      ...item,
      type: 'pie',
    })),
  })
}

export {
  DEFAULT_BAR_SERIES,
  DEFAULT_COLOR_PALETTE,
  DEFAULT_GRID,
  DEFAULT_LEGEND,
  DEFAULT_LINE_SERIES,
  DEFAULT_PIE_SERIES,
  DEFAULT_TOOLTIP,
  DEFAULT_X_AXIS,
  DEFAULT_Y_AXIS,
  deepMerge,
}
