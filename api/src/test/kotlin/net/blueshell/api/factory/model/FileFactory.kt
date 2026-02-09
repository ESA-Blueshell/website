package net.blueshell.api.factory.model

import com.github.javafaker.Faker
import net.blueshell.api.shared.enums.FileType
import net.blueshell.api.file.persistence.File
import org.springframework.stereotype.Component
import java.util.concurrent.atomic.AtomicLong
import java.util.function.Consumer

/**
 * Factory for File model test instances.
 */
@Component
class FileFactory(
    private val faker: Faker
) {

    fun createBasic(): File {
        val file = File()
        file.name = faker.file().fileName()
        file.path = "/uploads/${faker.file().fileName()}"
        file.mediaType = faker.options().option("image/jpeg", "image/png", "application/pdf")
        file.size = faker.number().numberBetween(1024L, 10485760L)
        file.type = faker.options().option(FileType::class.java)

        return file
    }

    fun createFull(): File = createBasic()

    fun createWithCustomizations(customizer: Consumer<File>): File {
        val file = createFull()
        customizer.accept(file)
        return file
    }

    fun createImage(): File {
        return createWithCustomizations { file ->
            file.mediaType = faker.options().option("image/jpeg", "image/png")
            file.type = FileType.EVENT_BANNER
        }
    }

    fun createDocument(): File {
        return createWithCustomizations { file ->
            file.mediaType = "application/pdf"
            file.type = FileType.DOCUMENT
        }
    }

    private fun generateId(): Long = COUNTER.incrementAndGet()

    private companion object {
        val COUNTER = AtomicLong(1000)
    }
}
