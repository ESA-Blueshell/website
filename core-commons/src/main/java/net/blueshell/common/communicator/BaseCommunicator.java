package net.blueshell.common.communicator;

import net.blueshell.common.Constants;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.HashMap;
import java.util.logging.Level;
import java.util.logging.Logger;


public class BaseCommunicator {

    private static final Logger logger = Logger.getLogger(BaseCommunicator.class.getName());
    private final String baseUrl;
    private final String name;
    private final RabbitTemplate rabbitTemplate;
    private final RestTemplate restTemplate;

    public BaseCommunicator(String name, int port, RabbitTemplate rabbitTemplate, RestTemplate restTemplate) {
        this.name = name;
        this.baseUrl = "http://" + name + ":" + port;
        this.rabbitTemplate = rabbitTemplate;
        this.restTemplate = restTemplate;
    }

    public <T, T1> T sendSync(
            String url, HttpMethod method,
            T1 body, HashMap<String, Object> parameters,
            Class<T> responseType
    ) {
        try {
            String formattedUrl = this.baseUrl + url;
            HttpEntity<T1> entity = new HttpEntity<>(body);

            UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(formattedUrl);
            if (parameters != null) {
                parameters.forEach(builder::queryParam);
            }
            String finalUrl = builder.toUriString();

            return restTemplate.exchange(finalUrl, method, entity, responseType).getBody();
        } catch (Exception e) {
            logger.log(Level.SEVERE, e.getMessage(), e);
            return null;
        }
    }

    public <T> T sendSync(String url, HttpMethod method, Class<T> responseType) {
        return sendSync(url, method, null, null, responseType);
    }

    public <T, T1> T sendSync(String url, HttpMethod method, T1 body, Class<T> responseType) {
        return sendSync(url, method, body, null, responseType);
    }

    public <T> T sendSync(String url, HttpMethod method, HashMap<String, Object> parameters, Class<T> responseType) {
        return sendSync(url, method, null, parameters, responseType);
    }


    public <T> String sendAsync(T body) {
        try {
            if (rabbitTemplate == null) {
                throw new IllegalStateException("RabbitTemplate is not initialized.");
            }

            rabbitTemplate.convertAndSend(
                    Constants.EXCHANGE,
                    Constants.QUEUE_ROUTE_PREFIX + "." + this.name,
                    body
            );
        } catch (Exception e) {
            logger.log(Level.SEVERE, e.getMessage(), e);
            return "Could not send message to " + this.name;
        }

        return "Message sent to " + this.name;
    }
}
