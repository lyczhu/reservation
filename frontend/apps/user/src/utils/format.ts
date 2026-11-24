const WEEK_TEXT = ['周日', '周一', '周二', '周三', '周四', '周五', '周六']

export function yuan(cents: number): string {
  return (cents / 100).toFixed(2)
}

export function durationText(minutes: number): string {
  if (minutes < 60) {
    return `${minutes}分钟`
  }
  const hours = Math.floor(minutes / 60)
  const rest = minutes % 60
  return rest === 0 ? `${hours}小时` : `${hours}小时${rest}分钟`
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

export function weekdayOf(dateStr: string): number {
  const day = new Date(`${dateStr}T00:00:00`).getDay()
  return day === 0 ? 7 : day
}

export function displayDate(dateStr: string): string {
  const [, month, day] = dateStr.split('-')
  const date = new Date(`${dateStr}T00:00:00`)
  return `${month}月${day}日 ${WEEK_TEXT[date.getDay()]}`
}
