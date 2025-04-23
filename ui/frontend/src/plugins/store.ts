import {createStore} from 'vuex';

export type Login = { username: string; roles: string[]; expiration: number };

export type State = {
  login: { username: string; roles: string[]; expiration: number } | null;
  guestData: Record<string, unknown> | null;
  statusSnackbarMessage: string | null;
  loggedInSnackbar: boolean;
}


function getJsonCookie(cname: string): Login | null {
  const name = cname + "=";
  const decodedCookie = decodeURIComponent(document.cookie);
  const ca = decodedCookie.split(';');
  for (let i = 0; i < ca.length; i++) {
    let c = ca[i];
    while (c.charAt(0) === ' ') {
      c = c.substring(1);
    }
    if (c.indexOf(name) === 0) {
      return JSON.parse(c.substring(name.length, c.length)) as Login;
    }
  }
  return null;
}

const store = createStore<State>({
  state: {
    login: getJsonCookie('login') || null,
    guestData: getJsonCookie('guestData') || null,
    statusSnackbarMessage: null,
    loggedInSnackbar: false,
  },
  mutations: {
    setLogin(state, newLogin: { username: string; roles: string[]; expiration: number; userId: number }) {
      state.login = newLogin;
      document.cookie = `login=${JSON.stringify(state.login)};SameSite=strict;Secure;path=/`;
      state.statusSnackbarMessage = "Welcome back " + state.login.username + "!";

    },
    logout(state) {
      state.login = null;
      document.cookie = `login=${null};SameSite=strict;Secure;path=/`;

      state.statusSnackbarMessage = 'You are now logged out.';
    },
    setRoles(state, newRoles: string[]) {
      if (state.login) {
        state.login.roles = newRoles;
        document.cookie = `login=${JSON.stringify(state.login)};SameSite=strict;Secure;path=/`;
      }
    },
    setStatusSnackbarMessage(state, message: string) {
      state.statusSnackbarMessage = message;
    },
    saveGuestData(state, data: Record<string, unknown>) {
      document.cookie = `guestData=${JSON.stringify(data)};SameSite=strict;Secure;path=/`;
      state.guestData = data;
    }
  },
  actions: {},
  getters: {
    getGuestData: (state) => state.guestData,

    getLogin: (state) => state.login,
    isLoggedIn: (state) => !!state.login,
    tokenExpired: (state) => state.login == null || new Date().getTime() > state.login.expiration,

    isBoard: (state) => state.login?.roles.includes("BOARD"),
    isActive: (state) => state.login?.roles.includes("COMMITTEE"),
    isMember: (state) => state.login?.roles.includes("MEMBER"),
  },
});

export default store;
