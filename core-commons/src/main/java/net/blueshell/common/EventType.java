package net.blueshell.common;

public enum EventType {
    NEWSLETTER_RECEIVED(0),
    EVENT_CREATED(1),
    BLOG_PUBLISHED(2),
    ;

    private final int value;

    EventType(final int val) {
        value = val;
    }

    public int getValue() { return value; }
}
