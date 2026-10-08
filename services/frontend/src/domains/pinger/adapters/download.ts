/*
 * The desktop pinger download, a file the domain lets a page in through (frontend ADR-001/002).
 * The installer is the same binary for everyone: the app signs the member in on first run, so
 * nothing of theirs is baked in. The member gate lives on the api, which redirects a signed-in
 * member to the installer the latest release attached.
 */
import {apiUrl} from "@/services/api"

/** A platform the desktop pinger ships for. The api resolves an installer for each. */
export type AppOs = "macos" | "windows" | "linux"

/** Where a member fetches the installer for [os]. A browser follows the api redirect with the cookie. */
export const appDownloadUrl = (os: AppOs): string => apiUrl(`/pinger/app/download?os=${os}`)
