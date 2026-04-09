package com.proteinviz.model;

import java.util.List;

public class Publication {
    private String title;
    private String journal;
    private String year;
    private String doi;
    private String pmid;
    private List<String> authors;

    public String getTitle() { return title; }
    public void setTitle(String v) { this.title = v; }
    public String getJournal() { return journal; }
    public void setJournal(String v) { this.journal = v; }
    public String getYear() { return year; }
    public void setYear(String v) { this.year = v; }
    public String getDoi() { return doi; }
    public void setDoi(String v) { this.doi = v; }
    public String getPmid() { return pmid; }
    public void setPmid(String v) { this.pmid = v; }
    public List<String> getAuthors() { return authors; }
    public void setAuthors(List<String> v) { this.authors = v; }

    public String getFormattedAuthors() {
        if (authors == null || authors.isEmpty()) return "Unknown authors";
        if (authors.size() <= 3) return String.join(", ", authors);
        return authors.get(0) + ", " + authors.get(1) + ", " + authors.get(2) + " et al.";
    }
}
