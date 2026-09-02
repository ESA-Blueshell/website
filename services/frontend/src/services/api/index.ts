export type {ClientOptions, Options} from "./blueshell"
// The Discord client is a published package now, not a generated tree in this
// repository. Only its types are used here — the public Discord banner reads
// widget.json directly, since that endpoint needs no authentication — but the
// package also exports `getGuildWidget` if a call site ever wants it.
export type {
  ClientOptions as DiscordClientOptions,
  Options as DiscordOptions,
} from "@esa-blueshell/discord-client"
export * from "./blueshell"
export * from "@esa-blueshell/discord-client"
export {apiUrl} from "./blueshell.runtime"
