import {deleteCookie, readJsonCookie, writeJsonCookie} from "@/plugins/cookies"
import {createStore, type Store} from "vuex"
import {type GuestResponse, type LoginResponse, Role} from "@/services/api"
import {emitAuthChanged} from "@/plugins/authSync"

export interface State {
  login: LoginResponse | null;
  guestData: GuestResponse | null;
  statusSnackbarMessage: string | null;
  loggedInSnackbar: boolean;
  xsrfToken: string | null;
}

export interface Mutations {
  setLogin(state: State, payload: LoginResponse): void;

  setLoginState(stage: State, payload: LoginResponse | null): void;

  logout(state: State): void;

  setRoles(state: State, roles: string[]): void;

  setAddressId(state: State, addressId: number): void;

  setStatusSnackbarMessage(state: State, message: string): void;

  saveGuestData(state: State, data: Record<string, unknown>): void;

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
  getLogin(state: State): LoginResponse | null;

  isLoggedIn(state: State): boolean;

  tokenExpired(state: State): boolean;

  isAdmin(state: State): boolean;

  isBoard(state: State): boolean;

  isActive(state: State): boolean;

  isMember(state: State): boolean;

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

const store = createStore<State>({
  state(): State {
      return {
      login: readJsonCookie<LoginResponse>("login"),
      guestData: readJsonCookie("guestData"),
      statusSnackbarMessage: null,
      loggedInSnackbar: false,
      xsrfToken: null,
    }
  },
  mutations: {
    async setLogin(state: State, payload: LoginResponse) {
      state.login = payload
      writeJsonCookie("login", payload)
      state.statusSnackbarMessage = `Welcome back ${payload.username}!`
      emitAuthChanged()
    },
    setLoginState(state: State, payload: LoginResponse | null): void {
      state.login = payload
    },
    async logout(state: State) {
      state.login = null
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
      }
    },
    saveGuestData(state: State, data: GuestResponse): void {
      writeJsonCookie("guestData", data)
      state.guestData = data
    },
    setXsrfToken(state: State, token: string | null): void {
      state.xsrfToken = token
    },
  },
  getters: {
    getLogin(state: State): LoginResponse | null {
      return state.login
    },
    isLoggedIn(state: State): boolean {
      return !!state.login
    },
    tokenExpired(state: State): boolean {
      return !state.login || Date.now() > state.login.expiration
    },
    isAdmin(state: State): boolean {
      const roles = state.login?.roles ?? []
      return roles.some(r => `${r}` === `${Role.ADMIN}`)
    },
    isBoard(state: State): boolean {
      const roles = state.login?.roles ?? []
      return roles.some(r => `${r}` === `${Role.BOARD}`)
    },
    isActive(state: State): boolean {
      const roles = state.login?.roles ?? []
      return roles.some(r => `${r}` === `${Role.COMMITTEE}`)
    },
    isMember(state: State): boolean {
      const roles = state.login?.roles ?? []
      return roles.some(r => `${r}` === `${Role.MEMBER}`)
    },
    getGuestData(state: State): GuestResponse | null {
      return state.guestData
    },
    getXsrfToken(state: State): string | null {
      return state.xsrfToken
    },
  },
})

export default store as TypedStore
