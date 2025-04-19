export function writeJsonCookie(
  name: string,
  value: unknown,
  {
    maxAge = 60 * 60 * 24 * 30,
    sameSite = 'Lax',
    secure   = window.location.protocol === 'https:',
    path     = '/',
  } = {}
) {
  const encoded = encodeURIComponent(JSON.stringify(value));
  document.cookie =
    `${name}=${encoded};Max-Age=${maxAge};SameSite=${sameSite};${secure ? 'Secure;' : ''}path=${path}`;
}

export function readJsonCookie<T = unknown>(name: string): T | null {
  const match = document.cookie.match(new RegExp(`(?:^|; )${name}=([^;]*)`));
  return match ? (JSON.parse(decodeURIComponent(match[1])) as T) : null;
}

export function deleteCookie(name: string, path = '/') {
  document.cookie = `${name}=;expires=Thu, 01 Jan 1970 00:00:00 GMT;path=${path}`;
}
