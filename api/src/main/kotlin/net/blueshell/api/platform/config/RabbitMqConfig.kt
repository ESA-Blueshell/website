package net.blueshell.api.platform.config

import org.springframework.amqp.core.Binding
import org.springframework.amqp.core.BindingBuilder
import org.springframework.amqp.core.DirectExchange
import org.springframework.amqp.core.Queue
import org.springframework.amqp.rabbit.annotation.EnableRabbit
import org.springframework.amqp.rabbit.connection.ConnectionFactory
import org.springframework.amqp.rabbit.core.RabbitTemplate
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
@EnableRabbit
@EnableConfigurationProperties(JobQueueProperties::class)
class RabbitMqConfig(
    private val properties: JobQueueProperties
) {
    @Bean
    fun jobQueue(): Queue = Queue(properties.queueName, true)

    @Bean
    fun jobExchange(): DirectExchange = DirectExchange(properties.exchangeName, true, false)

    @Bean
    fun jobBinding(): Binding = BindingBuilder.bind(jobQueue()).to(jobExchange()).with(properties.routingKey)

    @Bean
    fun jobMessageConverter(): Jackson2JsonMessageConverter = Jackson2JsonMessageConverter()

    @Bean
    fun rabbitTemplate(
        connectionFactory: ConnectionFactory,
        jobMessageConverter: Jackson2JsonMessageConverter
    ): RabbitTemplate {
        val template = RabbitTemplate(connectionFactory)
        template.messageConverter = jobMessageConverter
        return template
    }
}
