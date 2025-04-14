package net.blueshell.common.communication.communicators;

import net.blueshell.common.communication.communicators.base.CommunicatorBase;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

import java.util.HashMap;

public class BlogCommunicator extends CommunicatorBase {

    public static final String name = "blogservice";
    private final String blogUrl = formatUrl(name, 8080);

    public <T, T1> ResponseEntity<T> sendSync(String url, HttpMethod method,
                                          T1 body, HashMap<String, Object> parameters,
                                          Class<T> responseType) {
        return super.sendSync(blogUrl + url, method, body, parameters, responseType);
    }

}
