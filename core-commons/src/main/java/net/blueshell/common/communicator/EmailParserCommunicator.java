package net.blueshell.common.communicator;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
public class EmailParserCommunicator extends BaseCommunicator {
    public EmailParserCommunicator(
            @Value("${communicators.emailParser.name}") String name,
            @Value("${communicators.emailParser.port}") int port,
            RabbitTemplate rabbitTemplate,
            RestTemplate restTemplate) {
        super(name, port, rabbitTemplate, restTemplate);
    }
}
