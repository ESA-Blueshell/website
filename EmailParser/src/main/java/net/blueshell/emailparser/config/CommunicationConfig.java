package net.blueshell.emailparser.config;

import net.blueshell.common.communication.AsyncCommunicationService;
import net.blueshell.common.communication.CommunicationService;
import net.blueshell.common.communication.IAsyncCommunicationService;
import net.blueshell.common.communication.ICommunicationService;
import net.blueshell.common.communication.communicators.base.CommunicatorBase;
import net.blueshell.common.communication.communicators.base.ICommunicator;
import net.blueshell.common.communication.communicators.serializers.ISerializer;
import net.blueshell.common.communication.communicators.serializers.JsonSerializer;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CommunicationConfig {

    @Bean
    public ICommunicationService communicationService(ICommunicator communicator) {
        return new CommunicationService(communicator);
    }

    @Bean
    public ISerializer serializer() {
        return new JsonSerializer();
    }

    @Bean
    public ICommunicator baseCommunicator(RabbitTemplate rabbitTemplate,
                                          ISerializer serializer) {

        return new CommunicatorBase(rabbitTemplate, serializer);
    }

    @Bean
    public IAsyncCommunicationService asyncCommunicationService(ICommunicator communicator) {
        return new AsyncCommunicationService(communicator);
    }
}
