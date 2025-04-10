package net.blueshell.common.communication.communicators;

import lombok.Getter;

@Getter
public enum Communicators {
    APIGATEWAY("apigateway", 80),
    BLOG("blog", 8080),
    EMAILPARSER("emailparser", 8080),
    EVENTPARSER("emailparser", 8080),
    SOCIALMEDIA("socialmedia", 8080),
    TELEMETRY("telemetry", 8080);

    private final String name;
    private final int port;

    Communicators(String name, int port) {
        this.name = name;
        this.port = port;
    }

    public String getUrl(String path) {
        return "http://" + name + ":" + port + path;
    }
}
