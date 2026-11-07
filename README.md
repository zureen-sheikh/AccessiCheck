# AccessiCheck – Web Accessibility Analyzer

AccessiCheck finds common web accessibility problems in HTML code or on a live web page. It explains why each problem matters in simple language, shows the code before and after the fix, applies the fixes automatically, and keeps a history of scores so you can watch your progress.

Built as an internship project with **Java Spring Boot**, **Jsoup** and an **H2 database**.

---

## Features

| # | Check | What it finds | Priority |
|---|-------|---------------|----------|
| 1 | Missing alt text | Images with no `alt` attribute | Critical |
| 2 | Unlabeled form fields | Inputs with no label, `aria-label` or `aria-labelledby` | Critical |
| 3 | Empty links | Links with no text, label or image | Medium |
| 4 | Colour contrast | Text that is hard to read on its background (WCAG ratio 4.5:1, or 3:1 for large text) | Critical or Medium |
| 5 | ARIA roles | Invalid roles, custom controls the keyboard cannot reach, focusable items hidden with `aria-hidden` | Critical or Medium |
| 6 | Live URL scan | Loads a public web page and runs all of the checks above | – |

Other features:

- One-click **Apply all fixes** and rescan
- **Before / After** code view for every issue
- Animated **score gauge** (confetti at 100)
- **Scan history** saved in a database, with a Source column (pasted HTML or website name)
- **Copy fixed code** and **Download report** (HTML)
- Landing page, dark mode, large text mode, keyboard-friendly controls
- Screen reader demo ("Hear what a screen reader hears")

### How the score works

```
score = 100 - (12 x Critical issues) - (7 x Medium issues)     (minimum 0)
```

---

## Tech stack

- Java 17, Spring Boot 3.3 (web, data-jpa)
- Jsoup 1.18 (HTML parsing and fixing)
- H2 database (file based, no installation)
- HTML, CSS and plain JavaScript for the pages (no frameworks)
- Maven

---

## Project structure

```
AccessiCheck
├── pom.xml
└── src/main
    ├── java/com/accessicheck
    │   ├── AccessiCheckApplication.java   (starts the app, adds sample history)
    │   ├── AnalyzeController.java         (REST API)
    │   ├── AnalyzerService.java           (all accessibility rules)
    │   ├── ColorUtil.java                 (colour and contrast maths)
    │   ├── ScanRecord.java                (database table)
    │   └── ScanRepository.java            (database access)
    └── resources
        ├── application.properties
        └── static
            ├── index.html                 (landing page)
            └── dashboard.html             (the tool)
```

---

## How it works

```
Browser (dashboard.html)
   |  POST /api/analyze  or  POST /api/analyze-url
   v
AnalyzeController  ->  AnalyzerService (Jsoup + ColorUtil)  ->  score, issues, fixed HTML
   |
   v
ScanRepository  ->  H2 database (date, score, issue count, source)
```

### REST API

| Method | URL | Body | Returns |
|--------|-----|------|---------|
| POST | `/api/analyze` | `{ "html": "<img src='a.jpg'>" }` | `score`, `issues`, `total`, `fixedHtml` |
| POST | `/api/analyze-url` | `{ "url": "https://example.com" }` | same as above, or `{ "error": "..." }` |
| GET | `/api/history` | – | list of saved scans |

Each issue has: `t` (title), `p` (priority), `w` (why it matters), `b` (before code), `a` (after code).

### Safety

- Only `http://` and `https://` addresses are accepted for URL scans.
- Local and private network addresses (localhost, 127.x, 10.x, 192.168.x and similar) are blocked to prevent server-side request forgery (SSRF).
- Only the scan date, score, issue count and source are stored. The scanned HTML is not saved.

---

## How to run

**Requirements:** Java 17 or newer, internet access for the first Maven download.

**In Eclipse:** right-click `AccessiCheckApplication.java` → Run As → Java Application.

**With Maven:**

```
mvn spring-boot:run
```

Then open:

- `http://localhost:8080` – landing page
- `http://localhost:8080/dashboard.html` – the tool
- `http://localhost:8080/h2-console` – database viewer
  (JDBC URL `jdbc:h2:file:./data/accessicheck`, user `sa`, empty password)

---

## Test cases

| Input | Expected score |
|-------|----------------|
| `<img src="a.png">` plus an image with `alt=""` and one with `alt="Logo"` | 88 (1 Critical) |
| Form with one unlabeled input and two empty links | 74 (1 Critical, 2 Medium) |
| `<p style="color:#aaa">Light grey</p>` | 88 (1 Critical) |
| `<div role="buttn">`, `<div role="button">`, `<a href="#" aria-hidden="true">` | 74 (1 Critical, 2 Medium) |
| Any scan after **Apply all fixes** | 100 |

---

## Limitations

- Colour contrast is checked for **inline styles** only, not for external CSS files.
- URL scan reads the HTML sent by the server. Content created later by JavaScript is not seen.
- Pages that need a login, or that block automated requests, cannot be scanned.
- There is no user login or multi-user support.
- An empty `alt=""` is treated as correct (decorative image).

## Future work

- Heading order and page language checks
- Contrast check for external and internal CSS
- Export history as PDF
- User accounts and project folders
- Browser extension

---

## Author

Zureen – BCA, St. Francis De Sales College, Nagpur
