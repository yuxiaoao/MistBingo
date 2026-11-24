package net.starryisle.mistbingo;

import java.util.ArrayList;
import java.util.List;

/** Everything the plugin keeps per player: the active card plus unfinished ones. */
public final class PlayerState {

    private String lastRollDate = "";
    private long lastSeen;
    private int streak;
    private final List<Card> cards = new ArrayList<>();

    public String lastRollDate() {
        return lastRollDate;
    }

    public void lastRollDate(String date) {
        this.lastRollDate = date;
    }

    /** Last time the player was seen, used to prune old data. */
    public long lastSeen() {
        return lastSeen;
    }

    public void lastSeen(long value) {
        this.lastSeen = value;
    }

    public int streak() {
        return streak;
    }

    public void streak(int value) {
        this.streak = value;
    }

    /** Newest first; index 0 is the card most recently rolled. */
    public List<Card> cards() {
        return cards;
    }
}
