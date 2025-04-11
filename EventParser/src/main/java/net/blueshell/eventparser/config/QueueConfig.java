package net.blueshell.eventparser.config;

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

    private static final String name = Communicators.EVENTPARSER_NAME;

    @Bean
    Queue queue() {
        return new Queue(name, true);
    }

    @Bean
    TopicExchange exchange() {
        return new TopicExchange(Constants.EXCHANGE);
    }

    @Bean
    Binding binding(Queue queue, TopicExchange exchange) {
        return BindingBuilder.bind(queue).to(exchange)
                .with( Constants.QUEUE_ROUTE_PREFIX + "." + name);
    }
}
