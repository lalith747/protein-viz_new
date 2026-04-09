package com.proteinviz.controller;

import com.proteinviz.model.*;
import com.proteinviz.service.RcsbApiService;
import com.proteinviz.service.RcsbApiService.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class ProteinController {

    private static final Logger log = LoggerFactory.getLogger(ProteinController.class);
    private final RcsbApiService api;

    public ProteinController(RcsbApiService api) { this.api = api; }

    /**
     * Load full protein metadata + sequence + publications.
     * Mapped to /api/protein/load/{query} to avoid route conflicts with /pdb sub-path.
     */
    @GetMapping("/protein/load/{query}")
    public ResponseEntity<ApiResponse<ProteinStructure>> getProtein(@PathVariable String query) {
        log.info("Load: {}", query);
        try {
            String pdbId = api.resolveQuery(query);
            ProteinStructure s = api.fetchStructure(pdbId);
            return ResponseEntity.ok(ApiResponse.success(s, "Loaded " + pdbId));
        } catch (ProteinNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error(e.getMessage(), "NOT_FOUND"));
        } catch (Exception e) {
            log.error("Load error for '{}': {}", query, e.getMessage());
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(ApiResponse.error("Error: " + e.getMessage(), "ERROR"));
        }
    }

    /**
     * Get raw PDB file for a given PDB ID.
     * Uses disk cache; falls back to direct RCSB proxy streaming.
     * Mapped to /api/protein/pdb/{pdbId} — completely separate from /load/.
     */
    @GetMapping(value = "/protein/pdb/{pdbId}", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> getPdb(@PathVariable String pdbId) {
        String id = pdbId.toUpperCase().trim();
        log.info("PDB file request: {}", id);
        try {
            String pdb = api.fetchPdbFile(id);
            return ResponseEntity.ok()
                    .header("Content-Disposition", "attachment; filename=\"" + id + ".pdb\"")
                    .header("Cache-Control", "public, max-age=86400")
                    .header("Content-Type", "text/plain; charset=UTF-8")
                    .body(pdb);
        } catch (ProteinNotFoundException e) {
            log.warn("PDB not found: {}", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("PDB not found: " + id);
        } catch (Exception e) {
            log.error("PDB fetch error for {}: {}", id, e.getMessage());
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body("Error fetching PDB: " + e.getMessage());
        }
    }

    /** Search proteins by name */
    @GetMapping("/search")
    public ResponseEntity<ApiResponse<List<SearchResult>>> search(
            @RequestParam("q") String query,
            @RequestParam(value = "limit", defaultValue = "10") int limit) {
        try {
            List<SearchResult> results = api.searchProteins(query, Math.min(limit, 20));
            return ResponseEntity.ok(ApiResponse.success(results, results.size() + " results"));
        } catch (Exception e) {
            return ResponseEntity.status(503).body(ApiResponse.error("Search failed: " + e.getMessage()));
        }
    }

    /** Health check */
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of("status", "UP", "service", "ProteinViz 3D"));
    }
}

