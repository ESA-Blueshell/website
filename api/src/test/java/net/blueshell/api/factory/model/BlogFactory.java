package net.blueshell.api.factory.model;

import com.github.javafaker.Faker;
import lombok.RequiredArgsConstructor;
import net.blueshell.api.model.Blog;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Factory for Blog model test instances.
 */
@Component
@RequiredArgsConstructor
public class BlogFactory {

    private static final AtomicLong counter = new AtomicLong(1000);
    private final Faker faker;

    public Blog createBasic() {
        Blog blog = new Blog();
        blog.setId(generateId());
        blog.setTitle(faker.book().title());
        blog.setHtml("<p>" + faker.lorem().paragraph(10) + "</p>");
        blog.setPublishedAt(Instant.now());
        return blog;
    }

    public Blog createFull() {
        return createBasic();
    }

    public Blog createWithCustomizations(java.util.function.Consumer<Blog> customizer) {
        Blog blog = createFull();
        customizer.accept(blog);
        return blog;
    }

    private Long generateId() {
        return counter.incrementAndGet();
    }
}
