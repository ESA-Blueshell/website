/**
 * Discord live socket adapter: the api's push of the band's server, sent on connect and again
 * whenever it changes. OpenAPI describes no sockets, so this is the one call here that is not a
 * generated SDK function; the messages are the SDK's own `DiscordLiveResponse`.
 */
import {apiUrl, type DiscordLiveResponse} from "@/services/api"

/**
 * Opens the socket, handing each server to `onLive`. `onGone` hears once that the socket closed or
 * never opened: no bot (the api closes with 1013), no api, or a proxy in the way. The returned call
 * closes it without `onGone` hearing.
 */
export function openLiveSocket(onLive: (live: DiscordLiveResponse) => void, onGone: () => void): () => void {
  const socket = new WebSocket(apiUrl("/discord/live/socket").replace(/^http/u, "ws"))
  let over = false
  const gone = () => {
    if (over) return
    over = true
    onGone()
  }
  socket.onmessage = (message: MessageEvent<string>) => {
    try {
      onLive(JSON.parse(message.data) as DiscordLiveResponse)
    } catch {
      // A message that is not a server says nothing; the next one will.
    }
  }
  socket.onerror = gone
  socket.onclose = gone
  return () => {
    over = true
    socket.close()
  }
}
