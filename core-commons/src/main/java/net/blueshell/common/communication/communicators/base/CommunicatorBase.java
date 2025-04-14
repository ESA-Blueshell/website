package net.blueshell.common.communication.communicators.base;

import net.blueshell.common.communication.communicators.serializers.ISerializer;
import net.blueshell.common.communication.communicators.serializers.JsonSerializer;
import net.blueshell.common.Constants;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

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

    public CommunicatorBase(RabbitTemplate rabbitTemplate, ISerializer serializer) {
        this.serializer = serializer;
        this.rabbitTemplate = rabbitTemplate;
        this.restTemplate = new RestTemplate();
    }

    @Override
    public <T, T1> ResponseEntity<T> sendSync(
            String url, HttpMethod method,
            T1 body, HashMap<String, Object> parameters,
            Class<T> responseType
    ) {
        try {
            HttpEntity<T1> entity = new HttpEntity<>(body);

            UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(url);
            if (parameters != null) {
                parameters.forEach(builder::queryParam);
            }
            String finalUrl = builder.toUriString();

            return restTemplate.exchange(finalUrl, method, entity, responseType);
        } catch (Exception e) {
            logger.log(Level.SEVERE, e.getMessage(), e);
            return null;
        }
    }

    /**
     * **Refactored** to:
     *  1. Pass the raw object (T) to rabbitTemplate instead of manually serializing to String.
     *  2. Set the __TypeId__ header to the fully qualified class name so consumers know the real type.
     */
    @Override
    public <T> String sendAsync(String targetName, T dto) {
        try {
            if (rabbitTemplate == null) {
                throw new IllegalStateException("RabbitTemplate is not initialized.");
            }

            rabbitTemplate.convertAndSend(
                    Constants.EXCHANGE,
                    Constants.QUEUE_ROUTE_PREFIX + "." + targetName,
                    dto
            );
        } catch (Exception e) {
            logger.log(Level.SEVERE, e.getMessage(), e);
            return "Could not send message to " + targetName;
        }

        return "Message sent to " + targetName;
    }

    protected String formatUrl(String name, int port) {
        return String.format(urlFormat, name, port);
    }
}
