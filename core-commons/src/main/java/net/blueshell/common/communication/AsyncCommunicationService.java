package net.blueshell.common.communication;

import net.blueshell.common.communication.communicators.*;
import net.blueshell.common.communication.communicators.base.CommunicatorBase;
import net.blueshell.common.communication.communicators.base.ICommunicator;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

public class AsyncCommunicationService implements IAsyncCommunicationService {

    private final CommunicatorBase communicator;

    public AsyncCommunicationService(RabbitTemplate rabbitTemplate) {
        communicator = new CommunicatorBase(rabbitTemplate);
    }

    @Override
    public <T> String sendToAPIGateway(T body) {
        return communicator.sendAsync(ApiGatewayCommunicator.name, body);
    }

    @Override
    public <T> String sendToBlogService(T body) {
        return communicator.sendAsync(BlogCommunicator.name, body);
    }

    @Override
    public <T> String sendToEmailParserService(T body) {
        return communicator.sendAsync(EmailParserCommunicator.name, body);
    }

    @Override
    public <T> String sendToEventParserService(T body) {
        return communicator.sendAsync(EventParserCommunicator.name, body);
    }

    @Override
    public <T> String sendToSocialMediaService(T body) {
        return communicator.sendAsync(SocialMediaCommunicator.name, body);
    }

    @Override
    public <T> String sendToTelemetryService(T body) {
        return communicator.sendAsync(TelemetryCommunicator.name, body);
    }
}
