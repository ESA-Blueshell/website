/** Hands [file] to the browser as a download named [name]. */
export function saveFile(file: Blob, name: string): void {
  const url = URL.createObjectURL(file)
  const anchor = document.createElement("a")
  anchor.href = url
  anchor.download = name
  document.body.appendChild(anchor)
  anchor.click()
  anchor.remove()
  // Later rather than at once: Safari drops a download whose address is revoked before it starts.
  setTimeout(() => URL.revokeObjectURL(url), 1000)
}
