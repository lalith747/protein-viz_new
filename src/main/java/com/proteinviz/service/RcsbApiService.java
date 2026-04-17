package com.proteinviz.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.proteinviz.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.zip.GZIPInputStream;

/**
 * Complete RCSB PDB API service.
 * APIs: Search, Entry metadata, Polymer entity, FASTA sequences, Citations, PDB
 * file download.
 */
@Service
public class RcsbApiService {

    private static final Logger log = LoggerFactory.getLogger(RcsbApiService.class);

    private static final String SEARCH_URL = "https://search.rcsb.org/rcsbsearch/v2/query";
    private static final String DATA_URL = "https://data.rcsb.org/rest/v1/core/entry/";
    private static final String POLYMER_URL = "https://data.rcsb.org/rest/v1/core/polymer_entity/";
    // /view/ serves the file directly without CDN redirect (avoids gzip + Java header-strip issues)
    private static final String PDB_URL = "https://files.rcsb.org/view/";
    private static final String FASTA_URL = "https://www.rcsb.org/fasta/entry/";
    private static final Path CACHE_DIR = Path.of(System.getProperty("user.home"), ".proteinviz", "cache");
    private static final int TIMEOUT = 30;

    private final HttpClient http;
    private final HttpClient pdbHttp;
    private final ObjectMapper json = new ObjectMapper();

    public RcsbApiService() {
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(TIMEOUT))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        this.pdbHttp = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(60))
                .followRedirects(HttpClient.Redirect.ALWAYS)
                .build();
        try {
            Files.createDirectories(CACHE_DIR);
        } catch (IOException ignored) {
        }
    }

    // ── Resolve query → PDB ID ────────────────────────────────────────────────

    public String resolveQuery(String query) throws ProteinNotFoundException {
        if (query == null || query.isBlank())
            throw new ProteinNotFoundException("Empty query");
        String t = query.trim().toUpperCase();
        if (t.matches("^[A-Z0-9]{4}$"))
            return t;
        return searchByName(query.trim());
    }

    // ── Fetch full structure ──────────────────────────────────────────────────

    @Cacheable(value = "structures", key = "#pdbId.toUpperCase()")
    public ProteinStructure fetchStructure(String pdbId) throws Exception {
        String id = pdbId.toUpperCase().trim();
        long t0 = System.currentTimeMillis();
        ProteinStructure s = new ProteinStructure();
        s.setPdbId(id);

        fetchEntryMetadata(id, s);
        fetchPolymerEntityData(id, s);
        fetchSequences(id, s);
        fetchPublications(id, s);

        s.setFetchTimeMs(System.currentTimeMillis() - t0);
        return s;
    }

    // ── PDB file (disk-cached) ────────────────────────────────────────────────

    /**
     * Fetches the raw PDB file for a given ID.
     * Checks disk cache first. Downloads from RCSB with PDB-specific headers.
     *
     * Common failure causes fixed here:
     * 1. Wrong Accept header → RCSB returns HTML error page instead of PDB text
     * 2. Short timeout → large structures (spike protein, ribosomes) need 60s+
     * 3. Route conflict with metadata endpoint → caller must use
     * /api/protein/pdb/{id}
     */
    public String fetchPdbFile(String pdbId) throws Exception {
        String id = pdbId.toUpperCase().trim();
        Path cacheFile = CACHE_DIR.resolve(id + ".pdb");

        // Check disk cache first
        if (Files.exists(cacheFile)) {
            String cached = Files.readString(cacheFile);
            if (cached != null && (cached.startsWith("HEADER") || cached.contains("\nATOM  "))) {
                log.info("Disk cache HIT: {}", id);
                return cached;
            }
            // Corrupt cache file — delete and re-download
            Files.deleteIfExists(cacheFile);
            log.warn("Corrupt cache for {} — re-downloading", id);
        }

        // Download with PDB-specific headers
        String url = PDB_URL + id + ".pdb";
        log.info("Downloading PDB from RCSB: {}", url);
        String content = httpGetPdb(url);

        // Validate: real PDB files start with HEADER or contain ATOM/HETATM records
        // NOTE: do NOT use content.contains("404") — real PDB files can contain "404" as residue numbers!
        if (content == null || content.isBlank()) {
            throw new ProteinNotFoundException("Empty response for PDB ID: " + id);
        }
        if (content.contains("<!DOCTYPE") || content.contains("<html")) {
            throw new ProteinNotFoundException("PDB structure not available for: " + id +
                    " (RCSB may not have a legacy PDB file for this ID)");
        }
        if (!content.contains("ATOM  ") && !content.contains("HETATM") && !content.contains("HEADER")) {
            throw new ProteinNotFoundException("Invalid PDB content for: " + id);
        }

        // Save to disk cache
        try {
            Files.writeString(cacheFile, content);
            log.info("Cached PDB to disk: {} ({} KB)", id, content.length() / 1024);
        } catch (IOException e) {
            log.warn("Could not write disk cache for {}: {}", id, e.getMessage());
        }

        return content;
    }

    public boolean isCached(String pdbId) {
        return Files.exists(CACHE_DIR.resolve(pdbId.toUpperCase() + ".pdb"));
    }

    // ── Search ────────────────────────────────────────────────────────────────

    public List<SearchResult> searchProteins(String query, int max) throws IOException {
        String body = buildSearchPayload(query, max);
        String resp = httpPost(SEARCH_URL, body);
        List<SearchResult> out = new ArrayList<>();
        if (resp == null)
            return out;
        try {
            JsonNode rs = json.readTree(resp).path("result_set");
            if (rs.isArray())
                for (JsonNode n : rs) {
                    String id = n.path("identifier").asText().toUpperCase();
                    if (!id.isBlank())
                        out.add(new SearchResult(id, n.path("score").asDouble()));
                }
        } catch (Exception e) {
            log.error("Search parse: {}", e.getMessage());
        }
        return out;
    }

    // ── Private: metadata from /core/entry ───────────────────────────────────

    private void fetchEntryMetadata(String id, ProteinStructure s) {
        try {
            String resp = httpGet(DATA_URL + id);
            if (resp == null)
                return;
            JsonNode root = json.readTree(resp);

            s.setTitle(root.path("struct").path("title").asText("Unknown"));

            JsonNode exptl = root.path("exptl").path(0);
            s.setExperimentMethod(exptl.path("method").asText("Unknown"));

            JsonNode refine = root.path("refine").path(0);
            if (!refine.isMissingNode()) {
                String res = refine.path("ls_d_res_high").asText("");
                if (!res.isBlank())
                    s.setResolution(res + " Å");
            }
            if (s.getResolution() == null)
                s.setResolution("N/A");

            JsonNode acc = root.path("rcsb_accession_info");
            s.setReleaseDate(acc.path("initial_release_date").asText("N/A"));
            s.setDepositionDate(acc.path("deposit_date").asText("N/A"));

            JsonNode ei = root.path("rcsb_entry_info");
            s.setAtomCount(ei.path("deposited_atom_count").asInt(0));
            s.setResidueCount(ei.path("deposited_modeled_polymer_monomer_count").asInt(0));
            s.setChainCount(ei.path("polymer_entity_count").asInt(0));
            double mw = ei.path("molecular_weight").asDouble(0);
            if (mw > 0)
                s.setMolecularWeight(String.format("%.1f kDa", mw / 1000.0));
            s.setMacromoleculeType(ei.path("selected_polymer_entity_types").asText(""));

            // Organism (common name)
            JsonNode org = root.path("rcsb_entry_info").path("source_organism_commonname");
            if (!org.isMissingNode())
                s.setOrganism(org.asText());

            // Space group
            JsonNode sg = root.path("symmetry").path("space_group_name_hm");
            if (!sg.isMissingNode())
                s.setSpaceGroup(sg.asText());

            // Unit cell
            JsonNode cell = root.path("cell");
            if (!cell.isMissingNode()) {
                double a = cell.path("length_a").asDouble(0);
                if (a > 0)
                    s.setUnitCell(String.format("%.2f × %.2f × %.2f Å",
                            a, cell.path("length_b").asDouble(), cell.path("length_c").asDouble()));
            }

            // Keywords
            String kw = root.path("struct_keywords").path("pdbx_keywords").asText("");
            List<String> kws = new ArrayList<>();
            if (!kw.isBlank())
                for (String k : kw.split(","))
                    kws.add(k.trim());
            s.setKeywords(kws);

            // Citations
            JsonNode cites = root.path("citation");
            if (cites.isArray()) {
                List<Publication> pubs = new ArrayList<>();
                for (JsonNode c : cites) {
                    Publication p = new Publication();
                    p.setTitle(c.path("title").asText(null));
                    p.setJournal(c.path("journal_abbrev").asText(c.path("book_title").asText(null)));
                    String yr = c.path("year").asText("");
                    p.setYear(yr.equals("null") ? null : yr);
                    String doi = c.path("pdbx_database_id_doi").asText("");
                    p.setDoi(doi.equals("null") ? null : doi);
                    String pmid = c.path("pdbx_database_id_pub_med").asText("");
                    p.setPmid(pmid.equals("null") || pmid.equals("0") ? null : pmid);
                    List<String> authors = new ArrayList<>();
                    JsonNode al = c.path("rcsb_authors");
                    if (al.isArray())
                        for (JsonNode a : al) {
                            String nm = a.asText("").trim();
                            if (!nm.isEmpty())
                                authors.add(nm);
                        }
                    p.setAuthors(authors);
                    if (p.getTitle() != null || p.getDoi() != null)
                        pubs.add(p);
                }
                s.setPublications(pubs);
            }
        } catch (Exception e) {
            log.error("Entry metadata failed {}: {}", id, e.getMessage());
            if (s.getTitle() == null)
                s.setTitle("Structure " + id);
        }
    }

    // ── Private: polymer entity for gene/organism/function ───────────────────

    private void fetchPolymerEntityData(String id, ProteinStructure s) {
        try {
            String resp = httpGet(POLYMER_URL + id + "/1");
            if (resp == null)
                return;
            JsonNode root = json.readTree(resp);

            JsonNode srcOrg = root.path("rcsb_entity_source_organism").path(0);
            if (!srcOrg.isMissingNode()) {
                String sci = srcOrg.path("ncbi_scientific_name").asText("");
                if (!sci.isBlank())
                    s.setScientificName(sci);
                int tax = srcOrg.path("ncbi_taxonomy_id").asInt(0);
                if (tax > 0)
                    s.setTaxId(String.valueOf(tax));
                JsonNode genes = srcOrg.path("rcsb_gene_name");
                if (genes.isArray() && genes.size() > 0)
                    s.setGeneName(genes.get(0).path("value").asText(null));
            }

            String desc = root.path("rcsb_polymer_entity").path("pdbx_description").asText("");
            if (!desc.isBlank())
                s.setFunctionDescription(desc);

            // Chains from entity_poly
            String strandIds = root.path("entity_poly").path("pdbx_strand_id").asText("");
            if (!strandIds.isBlank()) {
                List<String> chains = new ArrayList<>();
                for (String c : strandIds.split(",")) {
                    String ch = c.trim();
                    if (!ch.isEmpty())
                        chains.add(ch);
                }
                s.setChains(chains);
            }
        } catch (Exception e) {
            log.warn("Polymer entity failed {}: {}", id, e.getMessage());
        }
    }

    // ── Private: FASTA sequences ──────────────────────────────────────────────

    private void fetchSequences(String id, ProteinStructure s) {
        try {
            String fasta = httpGet(FASTA_URL + id + "?download=false");
            if (fasta == null || fasta.isBlank())
                return;
            List<SequenceChain> chains = parseFasta(fasta);
            s.setSequenceChains(chains);
            // Backfill chains list if not set
            if (s.getChains() == null || s.getChains().isEmpty()) {
                List<String> ids = new ArrayList<>();
                for (SequenceChain sc : chains)
                    if (sc.getChainId() != null)
                        ids.add(sc.getChainId());
                s.setChains(ids);
            }
        } catch (Exception e) {
            log.warn("FASTA fetch failed {}: {}", id, e.getMessage());
        }
    }

    private void fetchPublications(String id, ProteinStructure s) {
        // Already fetched inside fetchEntryMetadata from the citation array
        // This is a no-op stub kept for clarity
    }

    // ── Private: parseFasta ───────────────────────────────────────────────────

    private List<SequenceChain> parseFasta(String fasta) {
        List<SequenceChain> out = new ArrayList<>();
        SequenceChain cur = null;
        StringBuilder seq = new StringBuilder();
        for (String line : fasta.split("\n")) {
            line = line.trim();
            if (line.startsWith(">")) {
                if (cur != null) {
                    cur.setSequence(seq.toString());
                    out.add(cur);
                }
                cur = new SequenceChain();
                seq = new StringBuilder();
                // Parse header: >XXXX_A|... or >XXXX_A ...
                String hdr = line.substring(1).trim();
                String[] pipes = hdr.split("\\|");
                String first = pipes[0].trim();
                int us = first.indexOf('_');
                if (us >= 0 && us < first.length() - 1) {
                    cur.setChainId(String.valueOf(first.charAt(us + 1)).toUpperCase());
                }
                if (pipes.length >= 3) {
                    String desc = pipes[pipes.length - 1].trim();
                    if (!desc.isBlank())
                        cur.setDescription(desc);
                }
            } else if (cur != null && !line.isEmpty()) {
                seq.append(line.toUpperCase());
            }
        }
        if (cur != null && seq.length() > 0) {
            cur.setSequence(seq.toString());
            out.add(cur);
        }
        return out;
    }

    // ── Private: search ───────────────────────────────────────────────────────

    private String searchByName(String query) throws ProteinNotFoundException {
        try {
            List<SearchResult> r = searchProteins(query, 10);
            if (r.isEmpty())
                throw new ProteinNotFoundException("No result for: " + query);
            return r.get(0).pdbId();
        } catch (IOException e) {
            throw new ProteinNotFoundException("Search failed: " + e.getMessage());
        }
    }

    private String buildSearchPayload(String query, int rows) {
        return """
                {"query":{"type":"terminal","service":"full_text","parameters":{"value":"%s"}},
                 "request_options":{"paginate":{"start":0,"rows":%d},"sort":[{"sort_by":"score","direction":"desc"}]},
                 "return_type":"entry"}
                """.formatted(query.replace("\"", "\\\""), rows);
    }

    // ── HTTP helpers ──────────────────────────────────────────────────────────

    /** Generic HTTP GET for JSON APIs. */
    private String httpGet(String url) throws IOException {
        try {
            HttpRequest req = HttpRequest.newBuilder().uri(URI.create(url))
                    .timeout(Duration.ofSeconds(TIMEOUT))
                    .header("Accept", "application/json, text/plain, */*")
                    .header("User-Agent", "ProteinViz/1.0").GET().build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200)
                return resp.body();
            if (resp.statusCode() == 404)
                throw new IOException("404: " + url);
            throw new IOException("HTTP " + resp.statusCode());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted");
        }
    }

    /**
     * Dedicated HTTP GET for PDB plain-text files.
     *
     * Differences from httpGet():
     * - Accept: text/plain (RCSB returns HTML if you send application/json)
     * - Timeout: 60s (large structures like ribosomes are 50MB+)
     * - Follows redirects (RCSB sometimes redirects to CDN)
     */
    private String httpGetPdb(String url) throws IOException {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(60))
                    .header("Accept", "text/plain, */*")
                    .header("User-Agent", "ProteinViz/1.0 (educational; contact info@proteinviz.dev)")
                    .GET().build();
            HttpResponse<byte[]> resp = pdbHttp.send(req, HttpResponse.BodyHandlers.ofByteArray());
            if (resp.statusCode() != 200) {
                if (resp.statusCode() == 404) throw new IOException("404 from RCSB for: " + url);
                throw new IOException("RCSB returned HTTP " + resp.statusCode() + " for: " + url);
            }
            byte[] body = resp.body();
            // Detect gzip magic bytes (0x1f 0x8b) and decompress if needed
            if (body.length >= 2 && (body[0] & 0xFF) == 0x1f && (body[1] & 0xFF) == 0x8b) {
                try (GZIPInputStream gz = new GZIPInputStream(new ByteArrayInputStream(body))) {
                    body = gz.readAllBytes();
                    log.info("Decompressed gzip PDB response for: {}", url);
                }
            }
            return new String(body, StandardCharsets.UTF_8);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("PDB download interrupted");
        }
    }

    private String httpPost(String url, String body) throws IOException {
        try {
            HttpRequest req = HttpRequest.newBuilder().uri(URI.create(url))
                    .timeout(Duration.ofSeconds(TIMEOUT))
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .header("User-Agent", "ProteinViz/1.0")
                    .POST(HttpRequest.BodyPublishers.ofString(body)).build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            return resp.statusCode() == 200 ? resp.body() : null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted");
        }
    }

    // ── Nested types ──────────────────────────────────────────────────────────

    public record SearchResult(String pdbId, double score) {
    }

    public static class ProteinNotFoundException extends Exception {
        public ProteinNotFoundException(String msg) {
            super(msg);
        }
    }
}
