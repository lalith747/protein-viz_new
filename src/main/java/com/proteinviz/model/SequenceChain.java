package com.proteinviz.model;

public class SequenceChain {
    private String chainId;
    private String entityType;
    private String macromoleculeType;
    private String description;
    private String geneName;
    private String scientificName;
    private String taxId;
    private String sequence;
    private int length;

    public String getChainId() { return chainId; }
    public void setChainId(String v) { this.chainId = v; }
    public String getEntityType() { return entityType; }
    public void setEntityType(String v) { this.entityType = v; }
    public String getMacromoleculeType() { return macromoleculeType; }
    public void setMacromoleculeType(String v) { this.macromoleculeType = v; }
    public String getDescription() { return description; }
    public void setDescription(String v) { this.description = v; }
    public String getGeneName() { return geneName; }
    public void setGeneName(String v) { this.geneName = v; }
    public String getScientificName() { return scientificName; }
    public void setScientificName(String v) { this.scientificName = v; }
    public String getTaxId() { return taxId; }
    public void setTaxId(String v) { this.taxId = v; }
    public String getSequence() { return sequence; }
    public void setSequence(String v) { this.sequence = v; this.length = v != null ? v.length() : 0; }
    public int getLength() { return length; }

    public String toFasta(String pdbId) {
        if (sequence == null || sequence.isBlank()) return "";
        StringBuilder sb = new StringBuilder();
        sb.append(">").append(pdbId).append("_").append(chainId);
        if (description != null && !description.isBlank()) sb.append("  ").append(description);
        sb.append("\n");
        for (int i = 0; i < sequence.length(); i += 60) {
            sb.append(sequence, i, Math.min(i + 60, sequence.length())).append("\n");
        }
        return sb.toString();
    }
}
