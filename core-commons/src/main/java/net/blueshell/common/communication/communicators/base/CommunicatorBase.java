package net.blueshell.common.communication.communicators.base;

import net.blueshell.common.communication.communicators.serializers.ISerializer;
import net.blueshell.common.communication.communicators.serializers.JsonSerializer;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.http.*;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import net.blueshell.common.Constants;
import org.springframework.web.client.RestTemplate;

public class CommunicatorBase implements ICommunicator {

    public static final String name = null;
    protected final String urlFormat = "http://%s:%s";

    private static final Logger logger = Logger.getLogger(CommunicatorBase.class.getName());
    private final ISerializer serializer;
    private final RestTemplate restTemplate;
    private final RabbitTemplate rabbitTemplate;

    public CommunicatorBase() {
        this.serializer = new JsonSerializer();
        this.rabbitTemplate = null;
        this.restTemplate = new RestTemplate();
    }

    public CommunicatorBase(RabbitTemplate rabbitTemplate,
                            ISerializer serializer) {
        this.serializer = serializer;
        this.rabbitTemplate = rabbitTemplate;
        this.restTemplate = new RestTemplate();
    }

    @Override
    public <T, T1> ResponseEntity<T> sendSync(String url, HttpMethod method,
                                          T1 body, HashMap<String, Object> parameters,
                                          Class<T> responseType) {
        try {
            // Convert request object to JSON string
            String jsonRequest = serializer.serialize(body);

            // Set headers
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            // Create HttpEntity
            HttpEntity<String> entity = new HttpEntity<>(jsonRequest, headers);

            // Add parameters to the URL
            UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(url);
            if(parameters != null) {
                for (Map.Entry<String, Object> entry : parameters.entrySet()) {
                    builder.queryParam(entry.getKey(), entry.getValue());
                }
            }
            String finalUrl = builder.toUriString();

            // Send request and receive response
            ResponseEntity<String> response = restTemplate.exchange(finalUrl, method, entity, String.class);
            String responseBody = response.getBody();

            return getResponseObject(responseBody, responseType, response, serializer);

        } catch (Exception e) {
            logger.log(Level.SEVERE, e.getMessage());
            return null;
        }
    }

    @Override
    public <T> String sendAsync(String targetName, T body) {

        try {
            assert rabbitTemplate != null;
            String serializedBody = new JsonSerializer().serialize(body);
            rabbitTemplate.convertAndSend(Constants.EXCHANGE,
                    Constants.QUEUE_ROUTE_PREFIX + "." + targetName,
                    serializedBody);
        }
        catch (Exception e) {
            logger.log(Level.SEVERE, e.getMessage());
            return "Could not send message to " + targetName;
        }

        return "Message sent to " + targetName;
    }

    protected String formatUrl(String name, int port) {
        return String.format(urlFormat, name, port);
    }

    private <T, T1> ResponseEntity<T> getResponseObject(String responseBody, Class<T> responseType,
                                                        ResponseEntity<T1> response, ISerializer serializer)
    {
        try {
            // Convert response JSON string to response object
            return new ResponseEntity<>(serializer.deserialize(responseBody, responseType),
                    response.getStatusCode());
        } catch (Exception e) {
            logger.log(Level.SEVERE, e.getMessage());
            return new ResponseEntity<>(response.getStatusCode());
        }
    }
}
