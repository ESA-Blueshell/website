package net.blueshell.emailparser.config;

import net.blueshell.common.Constants;
import net.blueshell.common.communication.communicators.Communicators;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class QueueConfig {

    @Bean
    Queue queue() {
        return new Queue(Communicators.EMAILPARSER_NAME, true);
    }

    @Bean
    TopicExchange exchange() {
        return new TopicExchange(Constants.EXCHANGE);
    }

    @Bean
    Binding binding(Queue queue, TopicExchange exchange) {
        return BindingBuilder.bind(queue).to(exchange)
                .with( Constants.QUEUE_ROUTE_PREFIX + "." + Communicators.EMAILPARSER_NAME);
    }
}
