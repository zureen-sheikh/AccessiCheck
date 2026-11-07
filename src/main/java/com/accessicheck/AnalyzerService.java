package com.accessicheck;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class AnalyzerService {

    private static final Set<String> VALID_ROLES = new HashSet<>(Arrays.asList(
            "alert", "alertdialog", "application", "article", "banner", "button", "cell", "checkbox",
            "columnheader", "combobox", "complementary", "contentinfo", "definition", "dialog",
            "directory", "document", "feed", "figure", "form", "grid", "gridcell", "group", "heading",
            "img", "link", "list", "listbox", "listitem", "log", "main", "marquee", "math", "menu",
            "menubar", "menuitem", "menuitemcheckbox", "menuitemradio", "navigation", "none", "note",
            "option", "presentation", "progressbar", "radio", "radiogroup", "region", "row", "rowgroup",
            "rowheader", "scrollbar", "search", "searchbox", "separator", "slider", "spinbutton",
            "status", "switch", "tab", "table", "tablist", "tabpanel", "term", "textbox", "timer",
            "toolbar", "tooltip", "tree", "treegrid", "treeitem"));

    /** Roles that behave like controls, so keyboard users must be able to reach them. */
    private static final Set<String> CONTROL_ROLES = new HashSet<>(Arrays.asList(
            "button", "link", "checkbox", "radio", "switch", "tab", "menuitem", "option",
            "slider", "textbox", "combobox"));

    /** Pasted HTML code. */
    public Map<String, Object> analyze(String html) {
        return build(Jsoup.parse(html));
    }

    /** A live web page. Throws IllegalArgumentException for bad or unsafe addresses. */
    public Map<String, Object> analyzeUrl(String raw) throws IOException {
        URI uri;
        try {
            uri = new URI(raw == null ? "" : raw.trim());
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("That does not look like a valid web address.");
        }
        String scheme = uri.getScheme();
        boolean webScheme = scheme != null
                && (scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"));
        if (!webScheme || uri.getHost() == null) {
            throw new IllegalArgumentException("Please enter a full address starting with http:// or https://");
        }
        InetAddress address = InetAddress.getByName(uri.getHost());
        if (address.isAnyLocalAddress() || address.isLoopbackAddress()
                || address.isSiteLocalAddress() || address.isLinkLocalAddress()) {
            throw new IllegalArgumentException("For safety, local and private network addresses cannot be scanned.");
        }
        Document doc = Jsoup.connect(uri.toString())
                .userAgent("Mozilla/5.0 (AccessiCheck)")
                .timeout(10000)
                .followRedirects(true)
                .get();
        return build(doc);
    }

    private Map<String, Object> build(Document doc) {
        doc.outputSettings().prettyPrint(false);
        List<Map<String, String>> issues = new ArrayList<>();

        checkLinks(doc, issues);
        checkImages(doc, issues);
        checkInputs(doc, issues);
        checkAria(doc, issues);
        checkContrast(doc, issues);

        int critical = 0;
        for (Map<String, String> i : issues) {
            if ("Critical".equals(i.get("p"))) critical++;
        }
        int medium = issues.size() - critical;
        int score = Math.max(0, 100 - 12 * critical - 7 * medium);

        List<Map<String, String>> shown = issues.size() > 200
                ? new ArrayList<>(issues.subList(0, 200)) : issues;

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("score", score);
        result.put("issues", shown);
        result.put("total", issues.size());
        result.put("fixedHtml", doc.body().html());
        return result;
    }

    // ---------- Rule 1: empty links ----------
    private void checkLinks(Document doc, List<Map<String, String>> issues) {
        for (Element a : doc.select("a")) {
            boolean hasText = !a.text().trim().isEmpty();
            boolean hasAria = a.hasAttr("aria-label") || a.hasAttr("aria-labelledby");
            boolean hasImg = !a.select("img").isEmpty();
            if (!hasText && !hasAria && !hasImg) {
                String before = shorten(a.outerHtml());
                a.text("Link text");
                issues.add(issue("Empty link", "Medium",
                        "A screen reader only says 'link', so the user cannot tell where it goes.",
                        before, shorten(a.outerHtml())));
            }
        }
    }

    // ---------- Rule 2: images without alt ----------
    private void checkImages(Document doc, List<Map<String, String>> issues) {
        for (Element img : doc.select("img:not([alt])")) {
            String before = shorten(img.outerHtml());
            img.attr("alt", "Describe the image");
            issues.add(issue("Missing alt text", "Critical",
                    "A screen reader cannot tell a blind user what the image shows.",
                    before, shorten(img.outerHtml())));
        }
    }

    // ---------- Rule 3: form fields without label ----------
    private void checkInputs(Document doc, List<Map<String, String>> issues) {
        for (Element in : doc.select("input")) {
            String type = in.attr("type").toLowerCase(Locale.ROOT);
            if (type.equals("hidden") || type.equals("submit") || type.equals("button")
                    || type.equals("reset") || type.equals("image")) {
                continue;
            }
            if (in.hasAttr("aria-label") || in.hasAttr("aria-labelledby") || in.closest("label") != null) {
                continue;
            }
            if (!in.id().isEmpty() && !doc.getElementsByAttributeValue("for", in.id()).isEmpty()) {
                continue;
            }
            String before = shorten(in.outerHtml());
            String id = !in.id().isEmpty() ? in.id() : (!in.attr("name").isEmpty() ? in.attr("name") : "field");
            in.attr("id", id);
            in.before("<label for=\"" + id + "\">" + id + "</label>");
            issues.add(issue("Form field without label", "Critical",
                    "Users do not know what to type, and screen readers cannot announce the field.",
                    before, "<label for=\"" + id + "\">" + id + "</label>\n" + shorten(in.outerHtml())));
        }
    }

    // ---------- Rule 4: ARIA roles ----------
    private void checkAria(Document doc, List<Map<String, String>> issues) {
        for (Element el : doc.select("[role]")) {
            String role = el.attr("role").trim().toLowerCase(Locale.ROOT);
            String first = role.isEmpty() ? "" : role.split("\\s+")[0];
            if (!VALID_ROLES.contains(first)) {
                String before = shorten(el.outerHtml());
                el.removeAttr("role");
                issues.add(issue("Invalid ARIA role", "Medium",
                        "The role \"" + role + "\" does not exist, so assistive technology ignores it or gets confused.",
                        before, shorten(el.outerHtml())));
            } else if (CONTROL_ROLES.contains(first) && !isNativeControl(el) && !el.hasAttr("tabindex")) {
                String before = shorten(el.outerHtml());
                el.attr("tabindex", "0");
                issues.add(issue("Custom control not reachable by keyboard", "Medium",
                        "A keyboard user cannot press Tab to reach this " + first + ".",
                        before, shorten(el.outerHtml())));
            }
        }
        for (Element el : doc.select("[aria-hidden=true]")) {
            if (isFocusable(el)) {
                String before = shorten(el.outerHtml());
                el.removeAttr("aria-hidden");
                issues.add(issue("Focusable element hidden from screen readers", "Critical",
                        "Keyboard users can reach it, but screen readers skip it, which is confusing.",
                        before, shorten(el.outerHtml())));
            }
        }
    }

    private boolean isNativeControl(Element el) {
        String tag = el.tagName().toLowerCase(Locale.ROOT);
        if (tag.equals("a")) return el.hasAttr("href");
        return tag.equals("button") || tag.equals("input") || tag.equals("select")
                || tag.equals("textarea") || tag.equals("summary");
    }

    private boolean isFocusable(Element el) {
        if (el.hasAttr("tabindex")) {
            return !el.attr("tabindex").trim().startsWith("-");
        }
        String tag = el.tagName().toLowerCase(Locale.ROOT);
        if (tag.equals("input")) return !el.attr("type").equalsIgnoreCase("hidden");
        return isNativeControl(el);
    }

    // ---------- Rule 5: colour contrast (inline styles) ----------
    private void checkContrast(Document doc, List<Map<String, String>> issues) {
        for (Element el : doc.select("[style]")) {
            if (el.ownText().trim().isEmpty()) continue;
            String style = el.attr("style").toLowerCase(Locale.ROOT);
            if (!style.contains("color") && !style.contains("background")) continue;

            int[] fg = inheritedColor(el, true);
            int[] bg = inheritedColor(el, false);
            if (fg == null) fg = new int[] {0, 0, 0};
            if (bg == null) bg = new int[] {255, 255, 255};

            double ratio = ColorUtil.contrast(fg, bg);
            double need = ColorUtil.isLargeText(ColorUtil.parseStyle(el.attr("style"))) ? 3.0 : 4.5;
            if (ratio >= need) continue;

            String before = shorten(el.outerHtml());
            int[] fixed = ColorUtil.fixColor(fg, bg, need);
            el.attr("style", ColorUtil.withStyle(el.attr("style"), "color", ColorUtil.toHex(fixed)));
            String level = ratio < 3.0 ? "Critical" : "Medium";
            issues.add(issue("Low colour contrast", level,
                    String.format(Locale.ROOT,
                            "People with low vision struggle to read this text. Contrast is %.1f:1 but needs %.1f:1.",
                            ratio, need),
                    before, shorten(el.outerHtml())));
        }
    }

    /** Looks at the element and its parents for an inline text colour or background colour. */
    private int[] inheritedColor(Element el, boolean text) {
        for (Element p = el; p != null; p = p.parent()) {
            Map<String, String> st = ColorUtil.parseStyle(p.attr("style"));
            String value;
            if (text) {
                value = st.get("color");
            } else {
                value = st.get("background-color");
                if (value == null) value = st.get("background");
            }
            if (value != null) {
                int[] c = ColorUtil.extractColor(value);
                if (c != null) return c;
            }
        }
        return null;
    }

    private String shorten(String s) {
        return s.length() > 300 ? s.substring(0, 300) + "..." : s;
    }

    private Map<String, String> issue(String title, String priority, String why, String before, String after) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("t", title);
        m.put("p", priority);
        m.put("w", why);
        m.put("b", before);
        m.put("a", after);
        return m;
    }
}
