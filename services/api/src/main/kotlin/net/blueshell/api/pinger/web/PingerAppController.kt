package net.blueshell.api.pinger.web

import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import net.blueshell.api.pinger.api.PingerAppDownloads
import net.blueshell.api.pinger.api.PingerPlatform
import net.blueshell.api.security.MemberOnly
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import java.net.URI

/**
 * The member-gated download for the desktop pinger.
 *
 * A signed-in member is redirected to the installer the latest release attached for their platform;
 * a signed-out visitor is refused by the member gate (api ADR-030, cookie-only). The redirect, not a
 * proxy stream, is deliberate: the binary carries no secret and the release asset is public, so
 * streaming the bytes through the api would only add load without gating anything the link does not.
 */
@RestController
@RequestMapping("/pinger/app")
@Tag(name = "Pinger app", description = "The member download for the desktop pinger")
class PingerAppController(
    private val downloads: PingerAppDownloads,
) {
    @MemberOnly
    @GetMapping("/download")
    @ApiResponses(
        ApiResponse(responseCode = "302", description = "Redirect to the installer for the requested OS"),
        ApiResponse(responseCode = "404", description = "The OS is unknown or the latest release has no installer for it"),
    )
    fun downloadApp(
        @Parameter(description = "The platform to download for: macos, linux or windows")
        @RequestParam os: String,
    ): ResponseEntity<Void> {
        val platform =
            PingerPlatform.of(os)
                ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "No pinger download for OS '$os'")
        val url =
            downloads.installerUrl(platform)
                ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "The latest release has no ${platform.assetName}")
        return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(url)).build()
    }
}
