package main.java.com.entri;

import java.time.LocalDateTime;

public final class RecentView {
    private final String type;
    private final long itemId;
    private final String name;
    private final LocalDateTime viewedAt;

    public RecentView(String type, long itemId, String name, LocalDateTime viewedAt) {
        this.type = type;
        this.itemId = itemId;
        this.name = name;
        this.viewedAt = viewedAt;
    }

    public String getType() { return type; }
    public long getItemId() { return itemId; }
    public String getName() { return name; }
    public LocalDateTime getViewedAt() { return viewedAt; }
}
