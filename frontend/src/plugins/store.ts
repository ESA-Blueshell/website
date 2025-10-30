import {deleteCookie, readJsonCookie, writeJsonCookie} from "@/plugins/cookies"
import {createStore, type Store} from "vuex"
import {type Guest, type Login, Role} from "@/services/api"

export interface State {
  login: Login | null;
  guestData: Guest | null;
  statusSnackbarMessage: string | null;
  loggedInSnackbar: boolean;
  xsrfToken: string | null;
}

export interface Mutations {
  setLogin(state: State, payload: Login): void;

  setLoginState(stage: State, payload: Login | null): void;

  logout(state: State): void;

  setRoles(state: State, roles: string[]): void;

  setStatusSnackbarMessage(state: State, message: string): void;

  saveGuestData(state: State, data: Record<string, unknown>): void;

  setXsrfToken(state: State, token: string | null): void;
}

export interface Actions {
  login(context: {
    commit: (type: keyof Mutations, payload?: Login) => void
  }, payload: Login): Promise<void>;

  logout(context: { commit: (type: keyof Mutations) => void }): Promise<void>;

  setRoles(context: { commit: (type: keyof Mutations, payload?: string[]) => void }, roles: string[]): Promise<void>;
}

export interface Getters {
  getLogin(state: State): Login | null;

  isLoggedIn(state: State): boolean;

  tokenExpired(state: State): boolean;

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
      login: readJsonCookie<Login>("login"),
      guestData: readJsonCookie("guestData"),
      statusSnackbarMessage: null,
      loggedInSnackbar: false,
      xsrfToken: null,
    }
  },
  mutations: {
    async setLogin(state: State, payload: Login) {
      state.login = payload
      writeJsonCookie("login", payload)
      state.statusSnackbarMessage = `Welcome back ${payload.username}!`
      localStorage.setItem("auth:ping", String(Date.now()))
    },
    setLoginState(state: State, payload: Login | null): void {
      state.login = payload
    },
    async logout(state: State) {
      state.login = null
      deleteCookie("login")
      state.statusSnackbarMessage = "You are now logged out."
      localStorage.setItem("auth:ping", String(Date.now()))
    },
    setRoles(state: State, roles: Role[]): void {
      if (state.login) {
        state.login = {...state.login, roles}
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
    saveGuestData(state: State, data: Guest): void {
      writeJsonCookie("guestData", data)
      state.guestData = data
    },
    setXsrfToken(state: State, token: string | null): void {
      state.xsrfToken = token
    },
  },
  getters: {
    getLogin(state: State): Login | null {
      return state.login
    },
    isLoggedIn(state: State): boolean {
      return !!state.login
    },
    tokenExpired(state: State): boolean {
      return !state.login || Date.now() > state.login.expiration
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
    getGuestData(state: State): Guest | null {
      return state.guestData
    },
    getXsrfToken(state: State): string | null {
      return state.xsrfToken
    },
  },
})

export default store as TypedStore
