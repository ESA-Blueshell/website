package net.blueshell.common.communication;

import net.blueshell.common.communication.communicators.*;
import net.blueshell.common.communication.communicators.base.ICommunicator;

public class AsyncCommunicationService implements IAsyncCommunicationService {

    private final ICommunicator communicator;
    public AsyncCommunicationService(ICommunicator communicator) {
        this.communicator = communicator;
    }

    @Override
    public <T> String sendToAPIGateway(T body) {
        return communicator.sendAsync(Communicators.APIGATEWAY.getName(), body);
    }

    @Override
    public <T> String sendToBlogService(T body) {
        return communicator.sendAsync(Communicators.BLOG.getName(), body);
    }

    @Override
    public <T> String sendToEmailParserService(T body) {
        return communicator.sendAsync(Communicators.EMAILPARSER.getName(), body);
    }

    @Override
    public <T> String sendToEventParserService(T body) {
        return communicator.sendAsync(Communicators.EVENTPARSER.getName(), body);
    }

    @Override
    public <T> String sendToSocialMediaService(T body) {
        return communicator.sendAsync(Communicators.SOCIALMEDIA.getName(), body);
    }

    @Override
    public <T> String sendToTelemetryService(T body) {
        return communicator.sendAsync(Communicators.TELEMETRY.getName(), body);
    }
}
