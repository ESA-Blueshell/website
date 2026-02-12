package net.blueshell.api.domain.blog.web.mapping

import net.blueshell.api.domain.blog.command.CreateBlogCommand
import net.blueshell.api.domain.blog.command.UpdateBlogCommand
import net.blueshell.api.domain.blog.web.dto.CreateBlogRequest
import net.blueshell.api.domain.blog.web.dto.UpdateBlogRequest
import tech.mappie.api.ObjectMappie

object CreateBlogRequestToCommandMapper : ObjectMappie<CreateBlogRequest, CreateBlogCommand>() {
    override fun map(from: CreateBlogRequest) = mapping {
        CreateBlogCommand::title fromProperty { from.title!! }
        CreateBlogCommand::html fromProperty { from.html!! }
        CreateBlogCommand::publishedAt fromProperty { from.publishedAt!! }
    }
}

private data class UpdateBlogCommandRequest(
    val id: Long,
    val request: UpdateBlogRequest
)

object UpdateBlogCommandRequestToCommandMapper : ObjectMappie<UpdateBlogCommandRequest, UpdateBlogCommand>() {
    override fun map(from: UpdateBlogCommandRequest) = mapping {
        UpdateBlogCommand::id fromProperty from::id
        UpdateBlogCommand::title fromProperty { from.request.title!! }
        UpdateBlogCommand::html fromProperty { from.request.html!! }
        UpdateBlogCommand::publishedAt fromProperty { from.request.publishedAt!! }
        UpdateBlogCommand::version fromProperty { from.request.version }
    }
}

fun CreateBlogRequest.asCommand(): CreateBlogCommand = CreateBlogRequestToCommandMapper.map(this)

fun UpdateBlogRequest.asCommand(id: Long): UpdateBlogCommand =
    UpdateBlogCommandRequestToCommandMapper.map(UpdateBlogCommandRequest(id, this))
