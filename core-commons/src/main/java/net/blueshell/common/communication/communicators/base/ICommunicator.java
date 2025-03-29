package net.blueshell.common.communication.communicators.base;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

import java.util.HashMap;

public interface ICommunicator {
    <T, T1> ResponseEntity<T> sendSync(String url, HttpMethod type,
                                          T1 body, HashMap<String, Object> parameters,
                                          Class<T> responseType);
    <T> String sendAsync(String routeTarget, T body);
}
