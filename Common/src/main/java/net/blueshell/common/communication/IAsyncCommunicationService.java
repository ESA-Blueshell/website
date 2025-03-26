package net.blueshell.common.communication;

import net.blueshell.common.communication.communicators.base.CommunicatorBase;

public interface IAsyncCommunicationService {
    String sendToAPIGateway(CommunicatorBase source, String body);
    String sendToBlogService(CommunicatorBase source, String body);
    String sendToEmailParserService(CommunicatorBase source, String body);
    String sendToEventParserService(CommunicatorBase source, String body);
    String sendToSocialMediaService(CommunicatorBase source, String body);
    String sendToTelemetryService(CommunicatorBase source, String body);
}
