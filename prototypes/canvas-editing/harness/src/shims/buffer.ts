/** Just enough of node's Buffer for the mocks: bytes from base64 or text. */
export class Buffer extends Uint8Array {
  static from(value: string, encoding?: string): Buffer {
    if (encoding === "base64") return Buffer.of(...Array.from(atob(value), one => one.charCodeAt(0))) as Buffer
    return Buffer.of(...new TextEncoder().encode(value)) as Buffer
  }
  includes(needle: string | number): boolean {
    if (typeof needle === "number") return super.includes(needle)
    return new TextDecoder().decode(this).includes(needle)
  }
  toString(): string {
    return new TextDecoder().decode(this)
  }
}
