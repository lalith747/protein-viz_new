package com.proteinviz.model;

import java.util.List;

public class ProteinStructure {
    private String pdbId;
    private String title;
    private String organism;
    private String scientificName;
    private String taxId;
    private String geneName;
    private String resolution;
    private String experimentMethod;
    private String releaseDate;
    private String depositionDate;
    private String molecularWeight;
    private String spaceGroup;
    private String unitCell;
    private String functionDescription;
    private String macromoleculeType;
    private int chainCount;
    private int atomCount;
    private int residueCount;
    private boolean cached;
    private long fetchTimeMs;
    private List<String> chains;
    private List<String> keywords;
    private List<Publication> publications;
    private List<SequenceChain> sequenceChains;

    // --- Getters & Setters ---
    public String getPdbId() { return pdbId; }
    public void setPdbId(String v) { this.pdbId = v; }
    public String getTitle() { return title; }
    public void setTitle(String v) { this.title = v; }
    public String getOrganism() { return organism; }
    public void setOrganism(String v) { this.organism = v; }
    public String getScientificName() { return scientificName; }
    public void setScientificName(String v) { this.scientificName = v; }
    public String getTaxId() { return taxId; }
    public void setTaxId(String v) { this.taxId = v; }
    public String getGeneName() { return geneName; }
    public void setGeneName(String v) { this.geneName = v; }
    public String getResolution() { return resolution; }
    public void setResolution(String v) { this.resolution = v; }
    public String getExperimentMethod() { return experimentMethod; }
    public void setExperimentMethod(String v) { this.experimentMethod = v; }
    public String getReleaseDate() { return releaseDate; }
    public void setReleaseDate(String v) { this.releaseDate = v; }
    public String getDepositionDate() { return depositionDate; }
    public void setDepositionDate(String v) { this.depositionDate = v; }
    public String getMolecularWeight() { return molecularWeight; }
    public void setMolecularWeight(String v) { this.molecularWeight = v; }
    public String getSpaceGroup() { return spaceGroup; }
    public void setSpaceGroup(String v) { this.spaceGroup = v; }
    public String getUnitCell() { return unitCell; }
    public void setUnitCell(String v) { this.unitCell = v; }
    public String getFunctionDescription() { return functionDescription; }
    public void setFunctionDescription(String v) { this.functionDescription = v; }
    public String getMacromoleculeType() { return macromoleculeType; }
    public void setMacromoleculeType(String v) { this.macromoleculeType = v; }
    public int getChainCount() { return chainCount; }
    public void setChainCount(int v) { this.chainCount = v; }
    public int getAtomCount() { return atomCount; }
    public void setAtomCount(int v) { this.atomCount = v; }
    public int getResidueCount() { return residueCount; }
    public void setResidueCount(int v) { this.residueCount = v; }
    public boolean isCached() { return cached; }
    public void setCached(boolean v) { this.cached = v; }
    public long getFetchTimeMs() { return fetchTimeMs; }
    public void setFetchTimeMs(long v) { this.fetchTimeMs = v; }
    public List<String> getChains() { return chains; }
    public void setChains(List<String> v) { this.chains = v; }
    public List<String> getKeywords() { return keywords; }
    public void setKeywords(List<String> v) { this.keywords = v; }
    public List<Publication> getPublications() { return publications; }
    public void setPublications(List<Publication> v) { this.publications = v; }
    public List<SequenceChain> getSequenceChains() { return sequenceChains; }
    public void setSequenceChains(List<SequenceChain> v) { this.sequenceChains = v; }
}
