package com.modpack.linktablet.client.screen;

import com.modpack.linktablet.client.UISounds;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

/**
 * The calculator's model (extracted from CalculatorScreen for the
 * 1.10.0 program-aware overlay): classic immediate-execution
 * four-function arithmetic on BigDecimal, with session-static state so
 * the full screen, the pinned overlay pad, and the kiosk display all
 * show the SAME tape. Client-session scratch — never persisted, never
 * synced; a placed tablet's calculator face shows the viewer's own
 * numbers.
 */
public final class CalcEngine {

    /** Pad legend, row-major; "DEL" is backspace, '±' negate. The "="
     * cell spans the last two columns of the final row. */
    static final String[][] PAD = {
            {"C", "DEL", "±", "÷"},
            {"7", "8", "9", "×"},
            {"4", "5", "6", "-"},
            {"1", "2", "3", "+"},
            {"0", ".", "=", "="},
    };

    private static final MathContext MATH = new MathContext(12, RoundingMode.HALF_UP);
    static final int MAX_ENTRY = 12;

    private static BigDecimal accumulator = null;
    private static char pendingOp = 0;
    private static String entry = "0";
    /** Next digit replaces the entry (after = or an operator). */
    private static boolean fresh = true;
    private static boolean error = false;

    public static boolean error() {
        return error;
    }

    /** The display line ("Error" handled by callers via {@link #error}). */
    public static String entry() {
        return entry;
    }

    /** Pending operator as its pad glyph, or "" when none. */
    public static String pendingOpGlyph() {
        return switch (pendingOp) {
            case '/' -> "÷";
            case '*' -> "×";
            case '-' -> "-";
            case '+' -> "+";
            default -> "";
        };
    }

    private static String format(BigDecimal value) {
        BigDecimal stripped = value.stripTrailingZeros();
        // Past 12 significant digits fall to scientific form; otherwise
        // plain (no "1E+1" for ten)
        String plain = stripped.toPlainString();
        return plain.length() <= MAX_ENTRY + 2 ? plain : stripped.round(MATH).toString();
    }

    private static void pressDigit(char digit) {
        if (error) clearAll();
        if (fresh) {
            entry = digit == '.' ? "0." : String.valueOf(digit);
            fresh = false;
            return;
        }
        if (digit == '.' && entry.contains(".")) return;
        if (entry.replace("-", "").replace(".", "").length() >= MAX_ENTRY) return;
        entry = entry.equals("0") && digit != '.' ? String.valueOf(digit) : entry + digit;
    }

    private static void backspace() {
        if (error) {
            clearAll();
            return;
        }
        if (fresh) return;
        entry = entry.length() <= 1 || (entry.length() == 2 && entry.startsWith("-"))
                ? "0" : entry.substring(0, entry.length() - 1);
    }

    private static void negate() {
        if (error) return;
        if (entry.equals("0")) return;
        entry = entry.startsWith("-") ? entry.substring(1) : "-" + entry;
    }

    private static void clearAll() {
        accumulator = null;
        pendingOp = 0;
        entry = "0";
        fresh = true;
        error = false;
    }

    /** Applies the pending op to (accumulator, entry) → the new entry. */
    private static void applyPending() {
        if (error) return;
        BigDecimal current = new BigDecimal(entry);
        if (accumulator != null && pendingOp != 0 && !fresh) {
            try {
                current = switch (pendingOp) {
                    case '+' -> accumulator.add(current, MATH);
                    case '-' -> accumulator.subtract(current, MATH);
                    case '*' -> accumulator.multiply(current, MATH);
                    case '/' -> accumulator.divide(current, MATH);
                    default -> current;
                };
                entry = format(current);
            } catch (ArithmeticException e) {
                error = true;
                accumulator = null;
                pendingOp = 0;
                fresh = true;
                return;
            }
        }
        accumulator = new BigDecimal(entry);
    }

    private static void pressOp(char op) {
        if (error) return;
        applyPending();
        if (error) return;
        pendingOp = op;
        fresh = true;
    }

    private static void pressEquals() {
        if (error) return;
        applyPending();
        pendingOp = 0;
        fresh = true;
    }

    /** One pad key by its legend, UI click sound included. */
    static void pressKey(String key) {
        switch (key) {
            case "C" -> {
                clearAll();
                UISounds.tick(0.8F);
            }
            case "DEL" -> {
                backspace();
                UISounds.tick(1.0F);
            }
            case "±" -> {
                negate();
                UISounds.tick(1.1F);
            }
            case "÷" -> {
                pressOp('/');
                UISounds.tick(1.3F);
            }
            case "×" -> {
                pressOp('*');
                UISounds.tick(1.3F);
            }
            case "-" -> {
                pressOp('-');
                UISounds.tick(1.3F);
            }
            case "+" -> {
                pressOp('+');
                UISounds.tick(1.3F);
            }
            case "=" -> {
                pressEquals();
                UISounds.confirm();
            }
            default -> {
                pressDigit(key.charAt(0));
                UISounds.tick(1.5F);
            }
        }
    }

    private CalcEngine() {
    }
}
