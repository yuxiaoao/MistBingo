package net.starryisle.mistbingo;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.inventory.ItemStack;

/** One 3x3 collection card. */
public final class Card {

    /** The eight winning lines on a 3x3 grid. */
    private static final int[][] LINES = {
            {0, 1, 2}, {3, 4, 5}, {6, 7, 8},
            {0, 3, 6}, {1, 4, 7}, {2, 5, 8},
            {0, 4, 8}, {2, 4, 6},
    };

    private final String date;
    private final List<ItemStack> items;
    private final List<Integer> amounts;
    private final List<Boolean> submitted;

    public Card(String date, List<ItemStack> items, List<Integer> amounts, List<Boolean> submitted) {
        this.date = date;
        this.items = new ArrayList<>(items);
        this.amounts = new ArrayList<>(amounts);
        this.submitted = new ArrayList<>(submitted);
    }

    public String date() {
        return date;
    }

    public List<ItemStack> items() {
        return items;
    }

    public int amount(int index) {
        return amounts.get(index);
    }

    public boolean isSubmitted(int index) {
        return submitted.get(index);
    }

    public boolean isFilled(int index) {
        return submitted.get(index);
    }

    public void markSubmitted(int index) {
        submitted.set(index, true);
    }

    public boolean completed() {
        for (int[] line : LINES) {
            if (submitted.get(line[0]) && submitted.get(line[1]) && submitted.get(line[2])) {
                return true;
            }
        }
        return false;
    }

    /** Number of cells already handed in, used in the chat header. */
    public int progress() {
        int count = 0;
        for (boolean value : submitted) {
            if (value) {
                count++;
            }
        }
        return count;
    }

    /** True when the given line index is complete, used for highlighting. */
    public boolean lineComplete(int lineIndex) {
        int[] line = LINES[lineIndex];
        return submitted.get(line[0]) && submitted.get(line[1]) && submitted.get(line[2]);
    }

    public static int lineCount() {
        return LINES.length;
    }

    /** Cell indices of one winning line. */
    public static int[] line(int index) {
        return LINES[index];
    }

    /** Human-readable line name, used in chat and logs. */
    public static String lineName(int index) {
        return switch (index) {
            case 0 -> "第一行";
            case 1 -> "第二行";
            case 2 -> "第三行";
            case 3 -> "第一列";
            case 4 -> "第二列";
            case 5 -> "第三列";
            case 6 -> "主对角线";
            case 7 -> "副对角线";
            default -> "线路" + index;
        };
    }
}
