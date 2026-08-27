package net.blueshell.api.platform.oidc

import org.springframework.modulith.ApplicationModule
import org.springframework.modulith.PackageInfo

/**
 * The API acting as an OIDC provider for the admin tooling: authorization server wiring, the
 * registered clients (Headlamp with PKCE, Vault as a confidential client) and the Vault Transit
 * JWK source that signs the tokens.
 *
 * The Traefik forward-auth check and the MyApps catalogue belong to this module too, but sit in
 * `platform/web/oidc` until the packages flatten.
 */
@PackageInfo
@ApplicationModule(id = "oidc")
class ModuleMetadata
