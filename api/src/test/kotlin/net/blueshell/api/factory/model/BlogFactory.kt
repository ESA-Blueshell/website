package net.blueshell.api.factory.model

import com.github.javafaker.Faker
import net.blueshell.api.feature.blog.model.Blog
import org.springframework.stereotype.Component
import java.time.Instant
import java.util.concurrent.atomic.AtomicLong
import java.util.function.Consumer

/**
 * Factory for Blog model test instances.
 */
@Component
class BlogFactory(
    private val faker: Faker
) {

    fun createBasic(): Blog {
        val blog = Blog()
        blog.title = faker.book().title()
        blog.html = "<p>${faker.lorem().paragraph(10)}</p>"
        blog.publishedAt = Instant.now()
        return blog
    }

    fun createFull(): Blog = createBasic()

    fun createWithCustomizations(customizer: Consumer<Blog>): Blog {
        val blog = createFull()
        customizer.accept(blog)
        return blog
    }

    private fun generateId(): Long = COUNTER.incrementAndGet()

    private companion object {
        val COUNTER = AtomicLong(1000)
    }
}
