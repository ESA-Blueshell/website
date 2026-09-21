import {deleteCookie, readJsonCookie, writeJsonCookie} from "@/plugins/cookies"
import {createStore, type Store} from "vuex"
import {type GuestResponse, type LoginResponse, Role} from "@/services/api"
import {emitAuthChanged} from "@/plugins/authSync"
import {hasAuthority} from "@/utils/roleAuthority"

export type GuestSessionData = GuestResponse & {
  accessToken: string;
}

/**
 * What a sign-in leaves behind in the browser: who the reader is, never a credential and never a
 * clock.
 *
 * The token lives in memory alone, so a cookie cannot hand it to anything that can read cookies.
 * `expiration` is dropped for a different reason — it was reported once at sign-in and the api
 * re-issues the token while the sign-in is in use, so a stored copy is wrong by the end of the
 * first day. Keeping a number that is both wrong and unread is an invitation to read it.
 */
export type StoredLogin = Omit<LoginResponse, "token" | "expiration">

export interface SnackbarAction {
  label: string;
  to: string;
}

export interface State {
  login: StoredLogin | null;
  authToken: string | null;
  guestData: GuestSessionData | null;
  statusSnackbarMessage: string | null;
  statusSnackbarAction: SnackbarAction | null;
  loggedInSnackbar: boolean;
  xsrfToken: string | null;
}

export interface Mutations {
  setLogin(state: State, payload: LoginResponse): void;

  setLoginState(stage: State, payload: StoredLogin | null): void;

  logout(state: State): void;

  setRoles(state: State, roles: string[]): void;

  setAddressId(state: State, addressId: number): void;

  setStatusSnackbarMessage(state: State, message: string): void;

  setStatusSnackbarAction(state: State, action: SnackbarAction | null): void;

  clearStatusSnackbar(state: State): void;

  saveGuestData(state: State, data: GuestSessionData): void;

  setXsrfToken(state: State, token: string | null): void;
}

export interface Actions {
  login(context: {
    commit: (type: keyof Mutations, payload?: LoginResponse) => void
  }, payload: LoginResponse): Promise<void>;

  logout(context: { commit: (type: keyof Mutations) => void }): Promise<void>;

  setRoles(context: { commit: (type: keyof Mutations, payload?: string[]) => void }, roles: string[]): Promise<void>;

  setAddressId(context: {
    commit: (type: keyof Mutations, payload?: number) => void
  }, addressId: number): Promise<void>;
}

export interface Getters {
  getLogin(state: State): StoredLogin | null;

  isLoggedIn(state: State): boolean;

  isAdmin(state: State): boolean;

  isBoard(state: State): boolean;

  hasBoardAuthority(state: State): boolean;

  isActive(state: State): boolean;

  isMember(state: State): boolean;

  getGuestData(state: State): GuestSessionData | null;

  getAuthToken(state: State): string | null;

  getXsrfToken(state: State): string | null;
}

export type TypedStore = Store<State> & {
  commit<K extends keyof Mutations>(
    key: K,
    payload?: Parameters<Mutations[K]>[1],
  ): ReturnType<Mutations[K]>;
  dispatch<K extends keyof Actions>(
    key: K,
    payload?: Parameters<Actions[K]>[1],
  ): ReturnType<Actions[K]>;
  getters: {
    [K in keyof Getters]: ReturnType<Getters[K]>;
  };
};

/** The sign-in response, reduced to what is kept. */
export function sanitizeLoginPayload(payload: LoginResponse | null): StoredLogin | null {
  if (!payload) return null
  // Named rather than spread-and-delete, so a field added to the response is not stored by accident.
  const {addressId, roles, userId, username} = payload
  return {addressId, roles, userId, username}
}

/**
 * A cookie written before this shape existed carries `token` and `expiration`; both are dropped on
 * the way in, so an old cookie reads the same as a new one rather than differing from it forever.
 * A cookie still carrying a real token is refused outright — that one predates the token leaving
 * the browser's storage, and is a credential nothing should go on using.
 */
function sanitizePersistedLoginState(payload: (LoginResponse & Partial<StoredLogin>) | null): StoredLogin | null {
  if (!payload) return null
  if ((payload.token ?? "").length > 0) return null
  return sanitizeLoginPayload(payload)
}

const store = createStore<State>({
  state(): State {
      return {
      login: sanitizePersistedLoginState(readJsonCookie<LoginResponse>("login")),
      authToken: null,
      guestData: readJsonCookie<GuestSessionData>("guestData"),
      statusSnackbarMessage: null,
      statusSnackbarAction: null,
      loggedInSnackbar: false,
      xsrfToken: null,
    }
  },
  mutations: {
    async setLogin(state: State, payload: LoginResponse) {
      const sanitized = sanitizeLoginPayload(payload)
      if (!sanitized) return
      state.login = sanitized
      state.authToken = payload.token || null
      writeJsonCookie("login", sanitized)
      state.statusSnackbarMessage = `Welcome back ${sanitized.username}!`
      emitAuthChanged()
    },
    setLoginState(state: State, payload: StoredLogin | null): void {
      state.login = payload
      state.authToken = null
    },
    async logout(state: State) {
      state.login = null
      state.authToken = null
      deleteCookie("login")
      state.statusSnackbarMessage = "You are now logged out."
      emitAuthChanged()
    },
    setRoles(state: State, roles: Role[]): void {
      if (state.login) {
        state.login = {...state.login, roles}
        writeJsonCookie("login", state.login)
      }
    },
    setAddressId(state: State, addressId: number): void {
      if (state.login) {
        state.login = {...state.login, addressId}
        writeJsonCookie("login", state.login)
      }
    },
    setStatusSnackbarMessage(state: State, message: string): void {
      if (message) {
        state.statusSnackbarMessage = message
      } else {
        state.statusSnackbarMessage = null
        state.statusSnackbarAction = null
      }
    },
    setStatusSnackbarAction(state: State, action: SnackbarAction | null): void {
      state.statusSnackbarAction = action
    },
    clearStatusSnackbar(state: State): void {
      state.statusSnackbarMessage = null
      state.statusSnackbarAction = null
    },
    saveGuestData(state: State, data: GuestSessionData): void {
      writeJsonCookie("guestData", data)
      state.guestData = data
    },
    setXsrfToken(state: State, token: string | null): void {
      state.xsrfToken = token
    },
  },
  getters: {
    getLogin(state: State): StoredLogin | null {
      return state.login
    },
    isLoggedIn(state: State): boolean {
      return !!state.login
    },
    isAdmin(state: State): boolean {
      const roles = state.login?.roles ?? []
      return roles.some(r => `${r}` === `${Role.ADMIN}`)
    },
    isBoard(state: State): boolean {
      const roles = state.login?.roles ?? []
      return roles.some(r => `${r}` === `${Role.BOARD}`)
    },
    // Unlike isBoard above, this walks the role chain the api walks, so an admin who was never
    // separately granted board still answers true.
    hasBoardAuthority(state: State): boolean {
      return hasAuthority(state.login?.roles, Role.BOARD)
    },
    isActive(state: State): boolean {
      const roles = state.login?.roles ?? []
      return roles.some(r => `${r}` === `${Role.COMMITTEE}`)
    },
    isMember(state: State): boolean {
      const roles = state.login?.roles ?? []
      return roles.some(r => `${r}` === `${Role.MEMBER}`)
    },
    getGuestData(state: State): GuestSessionData | null {
      return state.guestData
    },
    getAuthToken(state: State): string | null {
      return state.authToken
    },
    getXsrfToken(state: State): string | null {
      return state.xsrfToken
    },
  },
})

export default store as TypedStore
