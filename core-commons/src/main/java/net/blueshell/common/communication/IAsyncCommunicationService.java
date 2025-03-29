package net.blueshell.common.communication;

import net.blueshell.common.communication.communicators.*;

public interface IAsyncCommunicationService {
    public <T> String sendToAPIGateway(T body);
    public <T> String sendToBlogService(T body);
    public <T> String sendToEmailParserService(T body);
    public <T> String sendToEventParserService(T body);
    public <T> String sendToSocialMediaService(T body);
    public <T> String sendToTelemetryService(T body);
}
