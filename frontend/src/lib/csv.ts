export function exportToCsv(filename: string, rows: Record<string, any>[]) {
  if (!rows || !rows.length) {
    const blob = new Blob([], { type: 'text/csv;charset=utf-8;' })
    const link = document.createElement('a')
    link.href = URL.createObjectURL(blob)
    link.download = filename
    document.body.appendChild(link)
    link.click()
    link.remove()
    return
  }

  const keys = Object.keys(rows[0])
  const escape = (val: any) => {
    if (val === null || val === undefined) return ''
    return String(val).replace(/"/g, '""')
  }

  const header = keys.map(k => `"${escape(k)}"`).join(',')
  const lines = rows.map(r => keys.map(k => `"${escape(r[k])}"`).join(','))
  const csv = [header, ...lines].join('\r\n')

  const blob = new Blob([csv], { type: 'text/csv;charset=utf-8;' })
  const link = document.createElement('a')
  link.href = URL.createObjectURL(blob)
  link.download = filename
  document.body.appendChild(link)
  link.click()
  setTimeout(() => {
    URL.revokeObjectURL(link.href)
    link.remove()
  }, 100)
}
