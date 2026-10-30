package com.factorymind.insights.knowledge;

import java.util.ArrayList;
import java.util.List;

/**
 * Cuts sections into passages of about {@code targetSize} characters, never across a section: a passage
 * always belongs to one heading, so the copilot can cite "HP-400 manual › Troubleshooting". Paragraphs stay
 * whole when they fit; a paragraph too long for one passage is cut between sentences.
 */
public final class Chunker {

    private final int targetSize;

    public Chunker(int targetSize) {
        this.targetSize = targetSize;
    }

    public record Chunk(int index, String section, Integer page, String content) {
    }

    public List<Chunk> chunk(List<DocumentParser.Section> sections) {
        List<Chunk> chunks = new ArrayList<>();
        for (DocumentParser.Section section : sections) {
            for (String piece : pack(pieces(section.text()))) {
                chunks.add(new Chunk(chunks.size(), section.heading(), section.page(), piece));
            }
        }
        return chunks;
    }

    /** Paragraphs, with the ones longer than a passage cut between sentences (or anywhere, as a last resort). */
    private List<String> pieces(String text) {
        List<String> pieces = new ArrayList<>();
        for (String paragraph : text.split("\\n\\s*\\n")) {
            String p = paragraph.strip();
            if (p.isEmpty()) {
                continue;
            }
            if (p.length() <= targetSize) {
                pieces.add(p);
                continue;
            }
            for (String sentence : p.split("(?<=[.!?])\\s+")) {
                for (int start = 0; start < sentence.length(); start += targetSize) {
                    pieces.add(sentence.substring(start, Math.min(sentence.length(), start + targetSize)));
                }
            }
        }
        return pieces;
    }

    private List<String> pack(List<String> pieces) {
        List<String> passages = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String piece : pieces) {
            if (!current.isEmpty() && current.length() + piece.length() + 2 > targetSize) {
                passages.add(current.toString());
                current.setLength(0);
            }
            if (!current.isEmpty()) {
                current.append("\n\n");
            }
            current.append(piece);
        }
        if (!current.isEmpty()) {
            passages.add(current.toString());
        }
        return passages;
    }
}
