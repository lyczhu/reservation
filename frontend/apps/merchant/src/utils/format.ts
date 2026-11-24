const WEEK_TEXT = ['周日', '周一', '周二', '周三', '周四', '周五', '周六']

export function yuan(cents: number): string {
  return (cents / 100).toFixed(2)
}

export function dateAfter(days: number): string {
  const date = new Date()
  date.setDate(date.getDate() + days)
  return formatDate(date)
}

export function formatDate(date: Date): string {
  const month = `${date.getMonth() + 1}`.padStart(2, '0')
  const day = `${date.getDate()}`.padStart(2, '0')
  return `${date.getFullYear()}-${month}-${day}`
}

/** 日期横滑标签：今天/明天 + MM-dd + 周X */
export function dateLabel(dateStr: string, index: number): { top: string; mid: string; week: string } {
  const date = new Date(`${dateStr}T00:00:00`)
  const mid = `${`${date.getMonth() + 1}`.padStart(2, '0')}-${`${date.getDate()}`.padStart(2, '0')}`
  const top = index === 0 ? '今天' : index === 1 ? '明天' : mid
  return { top, mid, week: WEEK_TEXT[date.getDay()] }
}

export function displayDate(dateStr: string): string {
  if (!dateStr) {
    return ''
  }
  const [, month, day] = dateStr.split('-')
  const date = new Date(`${dateStr}T00:00:00`)
  return `${month}月${day}日 ${WEEK_TEXT[date.getDay()]}`
}

/** 时间线时间：LocalDateTime ISO → MM-dd HH:mm */
export function timeText(value: string | null): string {
  if (!value) {
    return ''
  }
  return value.replace('T', ' ').slice(5, 16)
}
