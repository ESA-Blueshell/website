package net.blueshell.api.domain.blog.web.mapping

import net.blueshell.api.domain.blog.command.CreateBlogCommand
import net.blueshell.api.domain.blog.command.UpdateBlogCommand
import net.blueshell.api.domain.blog.web.dto.request.CreateBlogRequest
import net.blueshell.api.domain.blog.web.dto.request.UpdateBlogRequest
import tech.mappie.api.ObjectMappie

object CreateBlogRequestToCommandMapper : ObjectMappie<CreateBlogRequest, CreateBlogCommand>() {
    override fun map(from: CreateBlogRequest) = mapping {
        CreateBlogCommand::title fromValue from.title!!
        CreateBlogCommand::html fromValue from.html!!
        CreateBlogCommand::publishedAt fromValue from.publishedAt!!
    }
}

internal data class UpdateBlogCommandRequest(
    val id: Long,
    val request: UpdateBlogRequest
)

internal object UpdateBlogCommandRequestToCommandMapper : ObjectMappie<UpdateBlogCommandRequest, UpdateBlogCommand>() {
    override fun map(from: UpdateBlogCommandRequest) = mapping {
        UpdateBlogCommand::id fromProperty from::id
        UpdateBlogCommand::title fromValue from.request.title!!
        UpdateBlogCommand::html fromValue from.request.html!!
        UpdateBlogCommand::publishedAt fromValue from.request.publishedAt!!
        UpdateBlogCommand::version fromValue from.request.version
    }
}

fun CreateBlogRequest.asCommand(): CreateBlogCommand = CreateBlogRequestToCommandMapper.map(this)

fun UpdateBlogRequest.asCommand(id: Long): UpdateBlogCommand =
    UpdateBlogCommandRequestToCommandMapper.map(UpdateBlogCommandRequest(id, this))
