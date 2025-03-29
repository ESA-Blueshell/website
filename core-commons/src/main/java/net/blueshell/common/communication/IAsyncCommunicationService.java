package net.blueshell.common.communication;

import net.blueshell.common.communication.communicators.base.ICommunicator;

public interface IAsyncCommunicationService {
    String sendToAPIGateway(ICommunicator source, String body);
    String sendToBlogService(ICommunicator source, String body);
    String sendToEmailParserService(ICommunicator source, String body);
    String sendToEventParserService(ICommunicator source, String body);
    String sendToSocialMediaService(ICommunicator source, String body);
    String sendToTelemetryService(ICommunicator source, String body);
}
