package net.blueshell.common.communicator;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
public class FileCommunicator extends BaseCommunicator {
    public FileCommunicator(
            @Value("${communicators.fileService.name}") String name,
            @Value("${communicators.fileService.port}") int port,
            RabbitTemplate rabbitTemplate,
            RestTemplate restTemplate) {
        super(name, port, rabbitTemplate, restTemplate);
    }
}
