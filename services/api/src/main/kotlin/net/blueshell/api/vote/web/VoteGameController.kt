package net.blueshell.api.vote.web

import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import jakarta.validation.constraints.Positive
import jakarta.validation.constraints.Size
import net.blueshell.api.security.BoardOnly
import net.blueshell.api.security.MemberOnly
import net.blueshell.api.shared.security.CurrentUserProvider
import net.blueshell.api.vote.domain.VoteGames
import net.blueshell.api.vote.persistence.VoteKind
import org.springframework.http.CacheControl
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import java.time.Duration

private const val QUERY_MAX = 100

// IGDB never changes the picture behind an image id, so a browser may keep a cover for a while.
private val COVER_CACHE: CacheControl = CacheControl.maxAge(Duration.ofDays(7)).cachePrivate()

/** Finding games and tracks to pick, storing them, and the board merging duplicate tracks. */
@RestController
@Validated
@Tag(name = "Annual vote")
@MemberOnly
class VoteGameController(
    private val games: VoteGames,
    private val currentUser: CurrentUserProvider,
) {
    @GetMapping("/vote-games")
    fun searchVoteGames(
        @RequestParam @Size(max = QUERY_MAX) query: String,
        @RequestParam kind: VoteKind,
        @RequestParam year: Int,
    ): List<CatalogueGameResponse> = games.search(query, kind, year).map { it.asResponse() }

    /** A search result's cover, fetched by the api so the browser never asks IGDB. */
    @GetMapping("/vote-games/covers/{imageId}", produces = [MediaType.IMAGE_JPEG_VALUE, MediaType.IMAGE_PNG_VALUE, "image/webp"])
    fun voteGameCover(
        @PathVariable imageId: String,
    ): ResponseEntity<ByteArray> {
        val cover = games.coverPreview(imageId)
        return ResponseEntity
            .ok()
            .cacheControl(COVER_CACHE)
            .contentType(MediaType.parseMediaType(cover.mediaType))
            .body(cover.bytes)
    }

    @PostMapping("/vote-games")
    fun storeVoteGame(
        @Valid @RequestBody request: StoreVoteGameRequest,
    ): VoteGameResponse = games.game(request.igdbId).asResponse()

    @GetMapping("/vote-games/{igdbId}/tracks")
    fun voteGameTracks(
        @PathVariable @Positive igdbId: Long,
    ): List<VoteTrackResponse> = games.tracksOf(igdbId).map { it.asResponse() }

    @PostMapping("/vote-tracks")
    @ResponseStatus(HttpStatus.CREATED)
    fun addVoteTrack(
        @Valid @RequestBody request: AddVoteTrackRequest,
    ): VoteTrackResponse {
        val adder = currentUser.currentUser()?.id ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED)
        return games.addTrack(request.igdbId, request.url, adder).asResponse()
    }

    @BoardOnly
    @PostMapping("/vote-tracks/{id}/merge")
    fun mergeVoteTrack(
        @PathVariable @Positive id: Long,
        @Valid @RequestBody request: MergeVoteTrackRequest,
    ): VoteTrackResponse = games.merge(id, request.intoId).asResponse()
}
