package net.blueshell.common.communication.communicators.base;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final RabbitTemplate rabbitTemplate;

    public CommunicatorBase() {
        this.rabbitTemplate = null;
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
    }

    public CommunicatorBase(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public <T> ResponseEntity<T> sendSync(String url, MessageType type,
                                          T body, HashMap<String, Object> parameters,
                                          Class<T> responseType) {
        try {
            // Convert request object to JSON string
            String jsonRequest = objectMapper.writeValueAsString(body);

            // Set headers
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            // Create HttpEntity
            HttpEntity<String> entity = new HttpEntity<>(jsonRequest, headers);

            // Add parameters to the URL
            UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(url);
            for (Map.Entry<String, Object> entry : parameters.entrySet()) {
                builder.queryParam(entry.getKey(), entry.getValue());
            }
            String finalUrl = builder.toUriString();

            // Send request and receive response
            ResponseEntity<String> response = restTemplate.exchange(finalUrl, HttpMethod.POST, entity, String.class);

            // Convert response JSON string to response object
            return new ResponseEntity<>(objectMapper.readValue(response.getBody(), responseType), response.getStatusCode());

        } catch (Exception e) {
            logger.log(Level.SEVERE, e.getMessage());
            return null;
        }
    }

    @Override
    public <T> String sendAsync(String targetName, T body) {

        try {
            assert rabbitTemplate != null;
            rabbitTemplate.convertAndSend(Constants.EXCHANGE, Constants.QUEUE_ROUTE_PREFIX + "." + targetName, body);
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
}
