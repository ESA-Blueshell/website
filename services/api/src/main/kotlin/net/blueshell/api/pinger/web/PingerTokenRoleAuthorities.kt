package net.blueshell.api.pinger.web

import net.blueshell.api.shared.enums.Role
import org.springframework.core.convert.converter.Converter
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.oauth2.jwt.Jwt

/**
 * Reads a member bearer token's `roles` claim into authorities, each role expanded through the
 * roles it inherits, so a token carrying only `ADMIN` still answers a `MEMBER` check. A claimed
 * role the enum does not know is dropped rather than trusted.
 */
class PingerTokenRoleAuthorities : Converter<Jwt, Collection<GrantedAuthority>> {
    override fun convert(source: Jwt): Collection<GrantedAuthority> =
        source
            .getClaimAsStringList("roles")
            .orEmpty()
            .mapNotNull { name -> runCatching { Role.valueOf(name) }.getOrNull() }
            .flatMap { it.allInheritedRoles }
            .map { SimpleGrantedAuthority(it.reprString) }
            .toSet()
}
