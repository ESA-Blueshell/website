/**
 * plugins/webfontloader.ts
 *
 * webfontloader documentation: https://github.com/typekit/webfontloader
 */

interface WebFontLoaderConfig {
  google?: {
    families: string[];
  };
}

interface WebFontLoader {
  load: (config: WebFontLoaderConfig) => void;
}

export async function loadFonts(): Promise<void> {
  const webFontLoader = await import(/* webpackChunkName: "webfontloader" */"webfontloader") as {
    default: WebFontLoader
  }

  webFontLoader.default.load({
    google: {
      families: ["Roboto:100,300,400,500,700,900&display=swap"],
    },
  })
}
