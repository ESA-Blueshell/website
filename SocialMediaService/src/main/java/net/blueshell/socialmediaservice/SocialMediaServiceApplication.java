package net.blueshell.socialmediaservice;

import net.blueshell.common.Constants;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class SocialMediaServiceApplication {

	@Bean
	Queue queue() {
		return new Queue(Constants.SM_QUEUE_NAME, true);
	}

	@Bean
	TopicExchange exchange() {
		return new TopicExchange(Constants.SM_QUEUE_EXCHANGE_NAME);
	}

	@Bean
	Binding binding(Queue queue, TopicExchange exchange) {
		return BindingBuilder.bind(queue).to(exchange).with( Constants.SM_QUEUE_ROUTE_PREFIX + ".#");
	}

	public static void main(String[] args) {
		SpringApplication.run(SocialMediaServiceApplication.class, args);
	}

}
