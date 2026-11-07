package com.accessicheck;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Small helper for colour maths (WCAG contrast) and inline CSS parsing. */
public final class ColorUtil {

    private ColorUtil() {}

    private static final Map<String, int[]> NAMED = new HashMap<>();

    static {
        named("black", 0, 0, 0);
        named("white", 255, 255, 255);
        named("red", 255, 0, 0);
        named("green", 0, 128, 0);
        named("blue", 0, 0, 255);
        named("yellow", 255, 255, 0);
        named("orange", 255, 165, 0);
        named("purple", 128, 0, 128);
        named("gray", 128, 128, 128);
        named("grey", 128, 128, 128);
        named("silver", 192, 192, 192);
        named("lightgray", 211, 211, 211);
        named("lightgrey", 211, 211, 211);
        named("darkgray", 169, 169, 169);
        named("darkgrey", 169, 169, 169);
        named("navy", 0, 0, 128);
        named("maroon", 128, 0, 0);
        named("teal", 0, 128, 128);
        named("lime", 0, 255, 0);
        named("aqua", 0, 255, 255);
        named("cyan", 0, 255, 255);
        named("fuchsia", 255, 0, 255);
        named("magenta", 255, 0, 255);
        named("olive", 128, 128, 0);
        named("pink", 255, 192, 203);
        named("gold", 255, 215, 0);
        named("brown", 165, 42, 42);
        named("beige", 245, 245, 220);
        named("ivory", 255, 255, 240);
    }

    private static void named(String name, int r, int g, int b) {
        NAMED.put(name, new int[] {r, g, b});
    }

    private static final Pattern RGB =
            Pattern.compile("rgba?\\(\\s*(\\d{1,3})\\s*,\\s*(\\d{1,3})\\s*,\\s*(\\d{1,3})");
    private static final Pattern TOKEN =
            Pattern.compile("#[0-9a-fA-F]{3,8}\\b|rgba?\\([^)]*\\)|[a-zA-Z]+");
    private static final Pattern SIZE = Pattern.compile("([0-9]*\\.?[0-9]+)\\s*(px|pt|em|rem)");

    /** Parses one CSS colour (#rgb, #rrggbb, rgb(), or a common name). Returns null if unknown. */
    public static int[] parseColor(String value) {
        if (value == null) return null;
        String v = value.trim().toLowerCase(Locale.ROOT);
        if (v.isEmpty()) return null;
        if (v.startsWith("#")) {
            String hex = v.substring(1);
            if (hex.length() == 3 || hex.length() == 4) {
                hex = "" + hex.charAt(0) + hex.charAt(0) + hex.charAt(1) + hex.charAt(1)
                        + hex.charAt(2) + hex.charAt(2);
            } else if (hex.length() == 6 || hex.length() == 8) {
                hex = hex.substring(0, 6);
            } else {
                return null;
            }
            try {
                return new int[] {
                    Integer.parseInt(hex.substring(0, 2), 16),
                    Integer.parseInt(hex.substring(2, 4), 16),
                    Integer.parseInt(hex.substring(4, 6), 16)
                };
            } catch (NumberFormatException e) {
                return null;
            }
        }
        if (v.startsWith("rgb")) {
            Matcher m = RGB.matcher(v);
            if (m.find()) {
                return new int[] {
                    clamp(Integer.parseInt(m.group(1))),
                    clamp(Integer.parseInt(m.group(2))),
                    clamp(Integer.parseInt(m.group(3)))
                };
            }
            return null;
        }
        int[] c = NAMED.get(v);
        return c == null ? null : c.clone();
    }

    /** Finds the first colour inside a value such as "#fff url(a.png) no-repeat". */
    public static int[] extractColor(String value) {
        if (value == null) return null;
        Matcher m = TOKEN.matcher(value);
        while (m.find()) {
            int[] c = parseColor(m.group());
            if (c != null) return c;
        }
        return null;
    }

    private static int clamp(int n) {
        return Math.max(0, Math.min(255, n));
    }

    public static double luminance(int[] c) {
        double[] v = new double[3];
        for (int i = 0; i < 3; i++) {
            double s = c[i] / 255.0;
            v[i] = s <= 0.03928 ? s / 12.92 : Math.pow((s + 0.055) / 1.055, 2.4);
        }
        return 0.2126 * v[0] + 0.7152 * v[1] + 0.0722 * v[2];
    }

    /** WCAG contrast ratio, from 1 (no contrast) to 21 (black on white). */
    public static double contrast(int[] a, int[] b) {
        double l1 = luminance(a);
        double l2 = luminance(b);
        double hi = Math.max(l1, l2);
        double lo = Math.min(l1, l2);
        return (hi + 0.05) / (lo + 0.05);
    }

    public static String toHex(int[] c) {
        return String.format(Locale.ROOT, "#%02x%02x%02x", c[0], c[1], c[2]);
    }

    /** Moves the text colour towards black or white until the contrast is high enough. */
    public static int[] fixColor(int[] fg, int[] bg, double need) {
        int[] black = {0, 0, 0};
        int[] white = {255, 255, 255};
        int[] target = contrast(black, bg) >= contrast(white, bg) ? black : white;
        for (int step = 1; step <= 20; step++) {
            double t = step / 20.0;
            int[] c = new int[3];
            for (int k = 0; k < 3; k++) {
                c[k] = (int) Math.round(fg[k] + (target[k] - fg[k]) * t);
            }
            if (contrast(c, bg) >= need) return c;
        }
        return target;
    }

    /** Splits an inline style attribute into property to value (property names in lower case). */
    public static Map<String, String> parseStyle(String style) {
        Map<String, String> map = new LinkedHashMap<>();
        if (style == null) return map;
        for (String part : style.split(";")) {
            int i = part.indexOf(':');
            if (i > 0) {
                map.put(part.substring(0, i).trim().toLowerCase(Locale.ROOT), part.substring(i + 1).trim());
            }
        }
        return map;
    }

    /** Returns the style text with one property set to a new value. */
    public static String withStyle(String style, String prop, String value) {
        Map<String, String> map = parseStyle(style);
        map.put(prop, value);
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> e : map.entrySet()) {
            sb.append(e.getKey()).append(": ").append(e.getValue()).append("; ");
        }
        return sb.toString().trim();
    }

    /** WCAG large text: at least 24px, or 18.66px and bold. */
    public static boolean isLargeText(Map<String, String> style) {
        String size = style.get("font-size");
        if (size == null) return false;
        Matcher m = SIZE.matcher(size.toLowerCase(Locale.ROOT));
        if (!m.find()) return false;
        double px = Double.parseDouble(m.group(1));
        String unit = m.group(2);
        if (unit.equals("pt")) px = px * 4.0 / 3.0;
        if (unit.equals("em") || unit.equals("rem")) px = px * 16.0;
        boolean bold = false;
        String weight = style.get("font-weight");
        if (weight != null) {
            String w = weight.trim().toLowerCase(Locale.ROOT);
            if (w.equals("bold") || w.equals("bolder")) {
                bold = true;
            } else {
                try {
                    bold = Integer.parseInt(w) >= 700;
                } catch (NumberFormatException e) {
                    bold = false;
                }
            }
        }
        return px >= 24 || (bold && px >= 18.66);
    }
}
