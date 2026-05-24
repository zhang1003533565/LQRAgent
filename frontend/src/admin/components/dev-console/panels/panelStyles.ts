/** Dev Console 面板共用 Tailwind 类名 */
export const panel = {
  hint: 'text-sm text-console-muted',
  title: 'text-base font-medium text-console-text',
  subTitle: 'mt-6 text-sm font-medium text-console-text',
  desc: 'text-sm text-console-muted leading-relaxed',
  msgOk: 'rounded-md border border-console-green/30 bg-console-green/10 px-3 py-2 text-sm text-console-green',
  msgErr: 'rounded-md border border-console-red/30 bg-console-red/10 px-3 py-2 text-sm text-console-red',
  msg: 'rounded-md border border-console-border bg-console-bg/60 px-3 py-2 text-sm text-console-text',
  input:
    'w-full rounded-md border border-console-border bg-console-bg px-3 py-2 text-sm text-console-text outline-none focus:border-console-blue',
  select:
    'w-full rounded-md border border-console-border bg-console-bg px-3 py-2 text-sm text-console-text outline-none focus:border-console-blue',
  label: 'flex flex-col gap-1.5 text-xs text-console-muted',
  grid: 'grid gap-4 sm:grid-cols-2',
  primaryBtn:
    'rounded-md bg-console-blue px-4 py-2 text-sm font-medium text-white hover:bg-blue-500 disabled:opacity-50',
  secondaryBtn:
    'rounded-md border border-console-border bg-console-card px-4 py-2 text-sm text-console-text hover:bg-console-border/40 disabled:opacity-50',
  dangerBtn: 'text-sm text-console-red hover:underline',
  linkBtn: 'text-sm text-console-blue hover:underline',
  table: 'w-full border-collapse text-left text-sm',
  th: 'border-b border-console-border px-3 py-2 font-medium text-console-muted',
  td: 'border-b border-console-border/60 px-3 py-2 text-console-text',
  presetBtn:
    'rounded-md border border-console-border px-3 py-1.5 text-xs text-console-muted hover:bg-console-border/30',
  presetActive:
    'rounded-md border border-console-blue bg-console-blue/15 px-3 py-1.5 text-xs text-console-blue',
}
