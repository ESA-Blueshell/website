package net.blueshell.telemetry.data;

import net.blueshell.telemetry.model.MetricResponse;
import net.blueshell.telemetry.model.Shareable;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class MockDB {
    public final Map<String, Shareable> db = new ConcurrentHashMap<>();

    public Shareable findById(String id) {
        return db.get(id);
    }

    public void save(Shareable shareable) {
        db.put(shareable.getId(), shareable);
    }

    public void incrementVisit(String id, String type) {
        if (type.equals("facebook")) incrementFacebookCount(id);
        if (type.equals("x")) incrementXCount(id);
    }

    private void incrementFacebookCount(String id) {
        Shareable existing = db.get(id);
        if (existing != null) {
            int curr = existing.getFacebookVisits();
            existing.setFacebookVisits(curr + 1);
        }
    }

    private void incrementXCount(String id) {
        Shareable existing = db.get(id);
        if (existing != null) {
            int curr = existing.getXVisits();
            existing.setXVisits(curr + 1);
        }
    }
}