package com.accessicheck;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.net.URI;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class AnalyzeController {

    private final AnalyzerService service;
    private final ScanRepository repo;

    public AnalyzeController(AnalyzerService service, ScanRepository repo) {
        this.service = service;
        this.repo = repo;
    }

    @PostMapping("/analyze")
    public Map<String, Object> analyze(@RequestBody Map<String, String> body) {
        Map<String, Object> result = service.analyze(body.getOrDefault("html", ""));
        save(result, "Pasted HTML");
        return result;
    }

    @PostMapping("/analyze-url")
    public ResponseEntity<Map<String, Object>> analyzeUrl(@RequestBody Map<String, String> body) {
        String url = body.getOrDefault("url", "").trim();
        try {
            Map<String, Object> result = service.analyzeUrl(url);
            save(result, hostOf(url));
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            return error(400, e.getMessage());
        } catch (IOException e) {
            return error(502, "Could not load that website. Check the address and try again.");
        }
    }

    @GetMapping("/history")
    public List<ScanRecord> history() {
        return repo.findAllByOrderByIdAsc();
    }

    private void save(Map<String, Object> result, String source) {
        int score = (Integer) result.get("score");
        int total = (Integer) result.get("total");
        String date = LocalDate.now().format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH));
        repo.save(new ScanRecord(date, score, total, source));
    }

    private String hostOf(String url) {
        try {
            String host = URI.create(url).getHost();
            return host == null ? "Website" : host;
        } catch (IllegalArgumentException e) {
            return "Website";
        }
    }

    private ResponseEntity<Map<String, Object>> error(int status, String message) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("error", message);
        return ResponseEntity.status(status).body(m);
    }
}
