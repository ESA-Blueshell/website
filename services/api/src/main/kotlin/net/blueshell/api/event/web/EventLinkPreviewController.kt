package net.blueshell.api.event.web

import io.swagger.v3.oas.annotations.Hidden
import io.swagger.v3.oas.annotations.tags.Tag
import net.blueshell.api.event.api.EventService
import net.blueshell.api.event.domain.linkPreview
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.MediaType
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController
import org.thymeleaf.TemplateEngine
import org.thymeleaf.context.Context

/** Head tags the frontend's nginx includes into an event page; hidden, so no client is generated. */
@Hidden
@Tag(name = "Events")
@RestController
class EventLinkPreviewController(
    private val service: EventService,
    private val templates: TemplateEngine,
    @param:Value($$"${frontend.url}") private val frontendUrl: String,
    @param:Value($$"${app.url}") private val apiUrl: String,
) {
    @GetMapping("/events/{id}/link-preview", produces = [MediaType.TEXT_HTML_VALUE])
    @PreAuthorize("hasPermission(#id, 'Event', 'read')")
    fun findLinkPreview(
        @PathVariable id: Long,
    ): String {
        val preview = service.findById(id).linkPreview(frontendUrl, apiUrl)
        return templates.process("link-previews/event", Context().apply { setVariable("preview", preview) })
    }
}
