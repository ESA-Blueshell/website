package net.blueshell.common.communicator;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
public class BlogCommunicator extends BaseCommunicator {
    public BlogCommunicator(
            @Value("${communicators.blogService.name}") String name,
            @Value("${communicators.blogService.port}") int port,
            RabbitTemplate rabbitTemplate,
            RestTemplate restTemplate) {
        super(name, port, rabbitTemplate, restTemplate);
    }
}