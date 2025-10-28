const assetUrlMap = import.meta.glob("/src/assets/**/*", {
  eager: true,
  import: "default",
  query: "?url",
}) as Record<string, string>

function normalize(p: string) {
  return p.replace(/^@\//, "/src/").replace(/^\/+/, "/")
}

export function $require(url?: string): string {
  if (!url) return ""
  return assetUrlMap[normalize(url)] ?? ""
}
