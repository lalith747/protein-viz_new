<div align="center">

<img width="100%" src="https://capsule-render.vercel.app/api?type=waving&color=0:03060f,50:0a2a4a,100:00d4ff&height=220&section=header&text=ProteinViz%203D&fontSize=72&fontColor=00d4ff&fontAlignY=40&desc=Molecular%20Structure%20Explorer&descAlignY=62&descColor=90e0ef&animation=fadeIn" />

<br/>

### 🌐 [**protein-viz-new.onrender.com**](https://protein-viz-new.onrender.com)
**Live, interactive 3D protein structures — straight from RCSB PDB, right in your browser.**

<br/>

![Java](https://img.shields.io/badge/Java%2017-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot%203.2-6DB33F?style=for-the-badge&logo=springboot&logoColor=white)
![3Dmol.js](https://img.shields.io/badge/3Dmol.js-WebGL-00b4d8?style=for-the-badge)
![Docker](https://img.shields.io/badge/Docker-2496ED?style=for-the-badge&logo=docker&logoColor=white)


</div>

---

## What is ProteinViz 3D?

Type a protein name (`hemoglobin`, `spike protein`) or a PDB ID (`1HHO`, `6VXX`) — ProteinViz fetches the structure live from the **RCSB Protein Data Bank**, renders it in interactive 3D inside your browser, and shows you full metadata, color-coded amino acid sequences, and linked publications.

No files to download. No account needed. No preprocessing. Just search and explore.

---

## Features

### 🔬 Interactive 3D Viewer

Powered by **3Dmol.js** with WebGL rendering.

| Rendering Style | Description |
|---|---|
| **Ribbon** | Cartoon secondary structure |
| **Stick** | Bond-level atomic detail |
| **Sphere** | Space-filling van der Waals model |
| **Surface** | Solvent-accessible molecular surface |

| Coloring Scheme | Description |
|---|---|
| **By Chain** | Each chain gets a distinct color |
| **Secondary Structure** | Helix / sheet / coil colored separately |
| **Hydrophobicity** | Gradient from polar to hydrophobic |
| **B-factor** | Crystallographic temperature factor heatmap |

- Click any chain tag to isolate and highlight it
- Auto-spin toggle for continuous rotation
- One-click **PDB file download**

---

### 🧬 Sequence Viewer

Full amino acid sequence for every chain, color-coded by chemistry:

- 🟣 **Hydrophobic** — `A V L I M F W P`
- 🔵 **Polar** — `G S T C Y N Q`
- 🟢 **Positively charged** — `K R H`
- 🔴 **Negatively charged** — `D E`

Copy any chain as FASTA with one click.

---

### 🗂️ Metadata Panel

For every loaded structure:

- Title, organism (common + scientific name), gene, taxonomy ID
- Experiment method — X-ray crystallography, cryo-EM, NMR, etc.
- Resolution, deposition & release dates, molecular weight
- Chain count, atom count, residue count
- Space group, unit cell dimensions, function description, structural keywords

---

### 📚 Publications

Full citation list pulled from RCSB — title, authors, journal, year, with DOI and PubMed links.

---

## Tech Stack

### Backend

| Component | Technology |
|---|---|
| Language | Java 17 |
| Framework | Spring Boot 3.2 |
| Web layer | Spring MVC — REST endpoints + static file serving |
| In-memory cache | Caffeine via Spring Cache · 100 entries · 24h TTL |
| Disk cache | `~/.proteinviz/cache/` — PDB files, validated on every read |
| HTTP client | Java `HttpClient` (JDK built-in) |
| JSON parsing | Jackson Databind |
| Build | Maven + Spring Boot Maven Plugin |
| Container | Docker — multi-stage `maven:3.9-alpine` → `eclipse-temurin:17-jre-alpine` |
| Deployment | Railway — Dockerfile-based, health check at `/api/health` |

### Frontend

| Component | Technology |
|---|---|
| Architecture | Single-page app — one `index.html`, no framework, no bundler |
| 3D Rendering | [3Dmol.js](https://3dmol.csb.pitt.edu/) — WebGL molecular viewer |
| Fonts | Space Grotesk (UI) · JetBrains Mono (sequences) |
| Theme | *"Deep Space Biopunk"* — full dark theme, `#03060f` base, custom CSS vars |
| Language | Vanilla JavaScript (ES2020+) |
| Styling | Pure CSS — no Tailwind, no Bootstrap |

### External APIs

> All calls are made **server-side**. The browser never contacts RCSB directly.

| Endpoint | Purpose |
|---|---|
| `data.rcsb.org/rest/v1/core/entry/{id}` | Title, resolution, MW, dates, citations |
| `data.rcsb.org/rest/v1/core/polymer_entity/{id}/1` | Organism, gene, chains, function |
| `www.rcsb.org/fasta/entry/{id}` | Full amino acid sequences |
| `files.rcsb.org/view/{id}.pdb` | Raw PDB coordinates (disk-cached) |
| `search.rcsb.org/rcsbsearch/v2/query` | Full-text name search |

---

## How It Works

```
User types "hemoglobin"
        ↓
Spring Boot checks Caffeine cache
        ↓ (miss)
Fetches from RCSB PDB:
  ├─ Core Entry API    →  title, resolution, MW, keywords, citations
  ├─ Polymer Entity    →  organism, gene, chains, function
  └─ FASTA endpoint    →  amino acid sequences

PDB file (separate path):
  ├─ Check disk cache (~/.proteinviz/cache/*.pdb)
  │     hit  →  validate content → serve
  └─ miss  →  download from files.rcsb.org
              → gzip detection + decompression
              → validate (ATOM / HETATM / HEADER check)
              → write to disk → serve

Browser:
  ├─ 3Dmol.js renders PDB text → WebGL 3D model
  ├─ Metadata panel populated from JSON
  ├─ Sequence viewer renders color-coded residues
  └─ Publication list rendered from citation array
```

---

## Project Structure

```
protein-viz_new/
├── Dockerfile                              Multi-stage Docker build
├── railway.toml                            Railway deployment config
├── pom.xml                                 Maven dependencies
└── src/main/
    ├── java/com/proteinviz/
    │   ├── ProteinVizApplication.java      Entry point
    │   ├── config/
    │   │   └── AppConfig.java              Caffeine cache + static resources
    │   ├── controller/
    │   │   └── ProteinController.java      All REST endpoints
    │   ├── model/
    │   │   ├── ProteinStructure.java       Main data model
    │   │   ├── SequenceChain.java          Per-chain sequence + metadata
    │   │   ├── Publication.java            Citation model
    │   │   └── ApiResponse.java            Generic JSON wrapper
    │   └── service/
    │       └── RcsbApiService.java         RCSB API calls + caching
    └── resources/
        ├── application.properties
        └── static/
            └── index.html                  Complete single-page frontend
```

---

## API Endpoints

```
GET  /api/protein/load/{query}          Load by PDB ID or name
                                        → ProteinStructure JSON

GET  /api/protein/pdb/{pdbId}           Raw PDB file (served from disk cache)
                                        → text/plain

GET  /api/search?q={query}&limit={n}    Full-text protein name search
                                        → [{pdbId, score}, ...]

GET  /api/health                        → {"status": "UP", "service": "ProteinViz 3D"}
```

---

## Caching

| Layer | Location | TTL | Max Size |
|---|---|---|---|
| Caffeine (structures) | In-memory, per process | 24 hours | 100 entries |
| Disk (PDB files) | `~/.proteinviz/cache/*.pdb` | Permanent | Unlimited |

PDB files are validated on every read — must contain `ATOM`, `HETATM`, or `HEADER` records. Corrupt files are deleted and re-downloaded automatically.

---

## Running Locally

**Requirements:** Java 17+, Maven (or use the `./mvnw` wrapper)

```bash
# Clone
git clone https://github.com/lalith747/protein-viz_new.git
cd protein-viz_new

# Run
./mvnw spring-boot:run

# Open
http://localhost:8080
```

Port conflict? Override it:

```bash
./mvnw spring-boot:run -Dspring-boot.run.arguments="--server.port=9090"
```

---

## Docker

```bash
docker build -t proteinviz .
docker run -p 8080:8080 proteinviz
```

---

## Deploy on Railway

1. Push this repo to GitHub
2. New Railway project → **Deploy from GitHub repo**
3. Railway auto-detects `Dockerfile` and `railway.toml`
4. No environment variables needed — `${PORT}` is wired automatically
5. Health check runs on `/api/health` with a 60s timeout

---

<div align="center">

**Try it now → [protein-viz-new.onrender.com](https://protein-viz-new.onrender.com)**

Start with: `1HHO` · `6VXX` · `insulin` · `spike protein` · `collagen`

<br/>

Built by **[Lalith Krishna T](https://github.com/lalith747)**

<img width="100%" src="https://capsule-render.vercel.app/api?type=waving&color=0:00d4ff,50:0a2a4a,100:03060f&height=120&section=footer" />

</div>
