package net.blueshell.apigateway.controllers;

import net.blueshell.common.communicator.ApiCommunicator;
import org.springframework.http.HttpMethod;
import org.springframework.util.Assert;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api")
public class ApiController {

    private final ApiCommunicator communicator;

    public ApiController(ApiCommunicator communicator) {
        this.communicator = communicator;
    }

    @RequestMapping(value = "/**",
            method = {RequestMethod.GET, RequestMethod.POST,
                    RequestMethod.PUT, RequestMethod.DELETE, RequestMethod.PATCH})
    public Object forwardRequest(HttpServletRequest request,
                                @RequestBody(required = false) Object body) {

        String forwardedPath = extractForwardedPath(request);
        HttpMethod method = HttpMethod.valueOf(request.getMethod().toUpperCase());
        HashMap<String, Object> parameters = extractQueryParameters(request);

        return communicator.sendSync(
                forwardedPath,
                method,
                body,
                parameters,
                Object.class
        );
    }

    private String extractForwardedPath(HttpServletRequest request) {
        String servletPath = request.getServletPath();
        return servletPath.replaceFirst("^/api", "");
    }

    private HashMap<String, Object> extractQueryParameters(HttpServletRequest request) {
        return new HashMap<String, Object>(request.getParameterMap().entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> entry.getValue().length > 0 ? entry.getValue()[0] : null
                )));
    }
}