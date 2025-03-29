package net.blueshell.common.communication;

public interface IAsyncCommunicationService {
    <T> String sendToAPIGateway(T body);
    <T> String sendToBlogService(T body);
    <T> String sendToEmailParserService(T body);
    <T> String sendToEventParserService(T body);
    <T> String sendToSocialMediaService(T body);
    <T> String sendToTelemetryService(T body);
}
