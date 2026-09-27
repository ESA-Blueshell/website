import {useRouter} from "vue-router"

/**
 * The page the reader came from, read once on arrival rather than gone back to blindly.
 *
 * `history.state.back` is the entry behind this one, which holds the season or the filters the
 * reader had open. Two answers are refused: the login page, which a reader bounced through on the
 * way here has behind them and is the one place saving must not land, and any address outside the
 * spa. Another edit page is refused too: one edit page saving into the next leaves the first behind
 * this one, and going back there would reopen a form already saved. Each falls back to [fallback].
 */
export function useReturnTo(fallback: string): string {
  const back = useRouter().options.history.state.back
  if (typeof back !== "string" || !back.startsWith("/") || back.startsWith("//")) return fallback
  if (back.startsWith("/login")) return fallback
  if (/\/(edit|new)(\?|$)/u.test(back)) return fallback
  return back
}
