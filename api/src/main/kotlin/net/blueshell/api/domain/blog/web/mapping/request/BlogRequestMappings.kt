package net.blueshell.api.domain.blog.web.mapping.request

import net.blueshell.api.domain.blog.command.CreateBlogCommand
import net.blueshell.api.domain.blog.command.UpdateBlogCommand
import net.blueshell.api.domain.blog.web.dto.request.CreateBlogRequest
import net.blueshell.api.domain.blog.web.dto.request.UpdateBlogRequest

fun CreateBlogRequest.asCommand(): CreateBlogCommand =
    CreateBlogCommand(
        title = this.title!!,
        html = this.html!!,
        publishedAt = this.publishedAt!!,
    )

fun UpdateBlogRequest.asCommand(id: Long): UpdateBlogCommand =
    UpdateBlogCommand(
        id = id,
        title = this.title!!,
        html = this.html!!,
        publishedAt = this.publishedAt!!,
        version = this.version!!,
    )
