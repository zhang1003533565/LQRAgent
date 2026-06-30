/** Obsidian-style light theme — high-contrast Bento knowledge graph tokens */

export interface GraphThemeConfig {
  backgroundColor: string
  gridColor: string
  edge: {
    defaultColor: string
    activeColor: string
    dimmedColor: string
    width: number
    activeWidth: number
    dimmedOpacity: number
    transitionMs: number
  }
  node: {
    core: {
      fill: string
      border: string
      textColor: string
      fontSize: number
      selectedFill: string
      selectedBorder: string
      searchBorder: string
    }
    leaf: {
      fill: string
      learningFill: string
      dimmedFill: string
      textColor: string
      fontSize: number
      badgeBackground: string
      badgePaddingX: number
      badgePaddingY: number
    }
  }
  label: {
    zoomHideThreshold: number
    zoomMeso: number
    zoomMicroFade: number
    floatOffsetX: number
    transitionMs: number
    dimmedColor: string
  }
  canvas: {
    dotStep: number
    dotRadius: number
  }
}

export const graphLightConfig: GraphThemeConfig = {
  backgroundColor: '#F8FAFC',
  gridColor: '#E2E8F0',
  edge: {
    defaultColor: 'rgba(148, 163, 184, 0.45)',
    activeColor: '#2563EB',
    dimmedColor: 'rgba(148, 163, 184, 0.45)',
    width: 0.8,
    activeWidth: 1.5,
    dimmedOpacity: 0.12,
    transitionMs: 200,
  },
  node: {
    core: {
      fill: '#EFF6FF',
      border: '#3B82F6',
      textColor: '#0F172A',
      fontSize: 14,
      selectedFill: '#FFF7ED',
      selectedBorder: '#F97316',
      searchBorder: 'rgba(245, 158, 11, 0.85)',
    },
    leaf: {
      fill: '#475569',
      learningFill: '#2563EB',
      dimmedFill: '#94A3B8',
      textColor: '#334155',
      fontSize: 12,
      badgeBackground: 'rgba(248, 250, 252, 0.75)',
      badgePaddingX: 4,
      badgePaddingY: 2,
    },
  },
  label: {
    zoomHideThreshold: 0.6,
    zoomMeso: 1.2,
    zoomMicroFade: 1.4,
    floatOffsetX: 8,
    transitionMs: 180,
    dimmedColor: '#64748B',
  },
  canvas: {
    dotStep: 24,
    dotRadius: 0.65,
  },
}

/** @deprecated alias — prefer graphLightConfig */
export const GRAPH_THEME = graphLightConfig

/** Backward-compatible derived config for focus/transition modules */
export const GRAPH_VISUAL = {
  canvas: {
    bg: graphLightConfig.backgroundColor,
    dot: graphLightConfig.gridColor,
    dotStep: graphLightConfig.canvas.dotStep,
    dotRadius: graphLightConfig.canvas.dotRadius,
  },
  edge: {
    idle: {
      stroke: graphLightConfig.edge.defaultColor,
      opacity: 1,
      width: graphLightConfig.edge.width,
    },
    activeHover: {
      stroke: graphLightConfig.edge.activeColor,
      opacity: 0.85,
      width: graphLightConfig.edge.activeWidth,
    },
    activeSelected: {
      stroke: graphLightConfig.edge.activeColor,
      opacity: 0.85,
      width: graphLightConfig.edge.activeWidth,
    },
    dimmed: {
      stroke: graphLightConfig.edge.dimmedColor,
      opacity: graphLightConfig.edge.dimmedOpacity,
      width: graphLightConfig.edge.width,
    },
    transitionMs: graphLightConfig.edge.transitionMs,
  },
  node: {
    coreBorder: graphLightConfig.node.core.border,
    searchStroke: graphLightConfig.node.core.searchBorder,
    dotFill: {
      mastered: graphLightConfig.node.leaf.fill,
      learning: graphLightConfig.node.leaf.learningFill,
      weak: graphLightConfig.node.leaf.fill,
      unlearned: graphLightConfig.node.leaf.fill,
    },
    dotDimmed: graphLightConfig.node.leaf.dimmedFill,
  },
  label: {
    zoomMacro: graphLightConfig.label.zoomHideThreshold,
    zoomMeso: graphLightConfig.label.zoomMeso,
    zoomMicroFade: graphLightConfig.label.zoomMicroFade,
    floatOffsetX: graphLightConfig.label.floatOffsetX,
    coreOffsetY: 0,
    transitionMs: graphLightConfig.label.transitionMs,
    color: {
      primary: graphLightConfig.node.core.textColor,
      secondary: graphLightConfig.node.leaf.textColor,
      dimmed: graphLightConfig.label.dimmedColor,
    },
    font: {
      core: { size: graphLightConfig.node.core.fontSize, weight: 700 },
      secondary: { size: graphLightConfig.node.leaf.fontSize, weight: 500 },
      tertiary: { size: 11, weight: 500 },
    },
  },
} as const

export type LabelTier = 1 | 2 | 3
export type LabelLodBand = 'macro' | 'meso' | 'micro'

export function getLabelLodBand(zoom: number): LabelLodBand {
  if (zoom < GRAPH_VISUAL.label.zoomMacro) return 'macro'
  if (zoom < GRAPH_VISUAL.label.zoomMeso) return 'meso'
  return 'micro'
}

export function smoothstep(edge0: number, edge1: number, x: number): number {
  if (edge0 === edge1) return x >= edge1 ? 1 : 0
  const t = Math.min(1, Math.max(0, (x - edge0) / (edge1 - edge0)))
  return t * t * (3 - 2 * t)
}

export function getCoreNodeFill(
  isSelected: boolean,
  isCurrentLearning: boolean,
): { fill: string; border: string } {
  if (isSelected || isCurrentLearning) {
    return {
      fill: graphLightConfig.node.core.selectedFill,
      border: graphLightConfig.node.core.selectedBorder,
    }
  }
  return {
    fill: graphLightConfig.node.core.fill,
    border: graphLightConfig.node.core.border,
  }
}
