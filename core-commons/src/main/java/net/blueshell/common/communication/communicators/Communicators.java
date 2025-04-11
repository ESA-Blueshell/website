package net.blueshell.common.communication.communicators;

import lombok.Getter;

@Getter
public enum Communicators {
    APIGATEWAY(80),
    BLOG(8080),
    EMAILPARSER(8080),
    EVENTPARSER(8080),
    SOCIALMEDIA(8080),
    TELEMETRY(8080);

    public static final String APIGATEWAY_NAME = "apigateway";
    public static final String BLOG_NAME = "blogservice";
    public static final String EMAILPARSER_NAME = "emailparser";
    public static final String EVENTPARSER_NAME = "eventparser";
    public static final String SOCIALMEDIA_NAME = "socialmediaservice";
    public static final String TELEMETRY_NAME = "telemetry";

    private final int port;

    Communicators(int port) {
        this.port = port;
    }

    public static final String APIGATEWAY_URL = "http://" + APIGATEWAY_NAME + ":" + APIGATEWAY.getPort();
    public static final String BLOG_URL = "http://" + BLOG_NAME + ":" + BLOG.getPort();
    public static final String EMAILPARSER_URL = "http://" + EMAILPARSER_NAME + ":" + EMAILPARSER.getPort();
    public static final String EVENTPARSER_URL = "http://" + EVENTPARSER_NAME + ":" + EVENTPARSER.getPort();
    public static final String SOCIALMEDIA_URL = "http://" + SOCIALMEDIA_NAME + ":" + SOCIALMEDIA.getPort();
    public static final String TELEMETRY_URL = "http://" + TELEMETRY_NAME + ":" + TELEMETRY.getPort();
}
