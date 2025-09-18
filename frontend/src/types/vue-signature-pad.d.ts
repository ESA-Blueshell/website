declare module 'vue-signature-pad' {
  import {Plugin} from 'vue'

  interface SignaturePadOptions {
    dotSize?: number
    minWidth?: number
    maxWidth?: number
    throttle?: number
    minDistance?: number
    backgroundColor?: string
    penColor?: string
    velocityFilterWeight?: number
  }

  interface VueSignaturePadInstance {
    isEmpty(): boolean

    clear(): void

    save(type?: string): string

    fromData(data: string): void

    addImages(images: string[]): void

    mergeImageAndSignature(signature: string): string
  }

  const VueSignaturePad: Plugin

  export default VueSignaturePad
  export {SignaturePadOptions, VueSignaturePadInstance}
}
