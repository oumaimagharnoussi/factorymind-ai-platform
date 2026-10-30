package com.factorymind.insights.knowledge;

import com.factorymind.insights.common.InvalidRequestException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.core.io.ByteArrayResource;

/**
 * Turns a manual into titled sections: Markdown by its headings, PDF page by page, plain text as one section.
 * Markdown may start with a front matter block that says what the document is:
 * <pre>
 * ---
 * title: Schuler HP-400 hydraulic press
 * kind: MANUAL
 * machines: HP-400
 * ---
 * </pre>
 */
public final class DocumentParser {

    private static final Pattern FRONT_MATTER = Pattern.compile("\\A---\\R(.*?)\\R---\\R", Pattern.DOTALL);
    private static final Pattern HEADING = Pattern.compile("^(#{1,3})\\s+(.+?)\\s*#*\\s*$");

    private DocumentParser() {
    }

    public record Parsed(String title, DocumentKind kind, List<String> machineModels, List<Section> sections) {

        /** Title, kind and machines given by the person who uploads the file win over the file's own. */
        Parsed withOverrides(String title, DocumentKind kind, List<String> machineModels) {
            return new Parsed(title == null || title.isBlank() ? this.title : title.strip(),
                    kind == null ? this.kind : kind,
                    machineModels == null || machineModels.isEmpty() ? this.machineModels : normalize(machineModels),
                    sections);
        }
    }

    /** @param heading "Troubleshooting › Oil overheats", or null for text before the first heading */
    public record Section(String heading, Integer page, String text) {
    }

    public static Parsed parse(String fileName, byte[] content) {
        String lower = fileName.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".pdf")) {
            return pdf(fileName, content);
        }
        String text = new String(content, java.nio.charset.StandardCharsets.UTF_8);
        if (lower.endsWith(".md") || lower.endsWith(".markdown")) {
            return markdown(fileName, text);
        }
        if (lower.endsWith(".txt")) {
            return new Parsed(baseName(fileName), DocumentKind.MANUAL, List.of(),
                    List.of(new Section(null, null, text.strip())));
        }
        throw new InvalidRequestException("Only Markdown (.md), text (.txt) and PDF (.pdf) files can be added.");
    }

    public static Parsed markdown(String fileName, String text) {
        String title = null;
        DocumentKind kind = DocumentKind.MANUAL;
        List<String> machines = List.of();
        String body = text.replace("\r\n", "\n");
        Matcher front = FRONT_MATTER.matcher(body);
        if (front.find()) {
            for (String line : front.group(1).split("\n")) {
                int colon = line.indexOf(':');
                if (colon < 0) {
                    continue;
                }
                String key = line.substring(0, colon).strip().toLowerCase(Locale.ROOT);
                String value = line.substring(colon + 1).strip();
                switch (key) {
                    case "title" -> title = value;
                    case "kind" -> kind = kind(value);
                    case "machines" -> machines = normalize(Arrays.asList(value.split(",")));
                    default -> { }
                }
            }
            body = body.substring(front.end());
        }

        List<Section> sections = new ArrayList<>();
        String h2 = null;
        String h3 = null;
        StringBuilder current = new StringBuilder();
        for (String line : body.split("\n", -1)) {
            Matcher heading = HEADING.matcher(line);
            if (heading.matches()) {
                add(sections, h3 == null ? h2 : h2 + " › " + h3, current);
                int level = heading.group(1).length();
                String name = heading.group(2);
                if (level == 1) {
                    if (title == null) {
                        title = name;
                    }
                    h2 = null;
                    h3 = null;
                } else if (level == 2) {
                    h2 = name;
                    h3 = null;
                } else {
                    h3 = name;
                }
            } else {
                current.append(line).append('\n');
            }
        }
        add(sections, h3 == null ? h2 : h2 + " › " + h3, current);
        if (sections.isEmpty()) {
            throw new InvalidRequestException("The document has no text.");
        }
        return new Parsed(title == null ? baseName(fileName) : title, kind, machines, sections);
    }

    static Parsed pdf(String fileName, byte[] content) {
        List<Document> pages;
        try {
            pages = new PagePdfDocumentReader(new ByteArrayResource(content) {
                @Override
                public String getFilename() {
                    return fileName;
                }
            }).get();
        } catch (RuntimeException e) {
            throw new InvalidRequestException("The PDF could not be read: " + e.getMessage());
        }
        List<Section> sections = new ArrayList<>();
        for (Document page : pages) {
            Object number = page.getMetadata().get(PagePdfDocumentReader.METADATA_START_PAGE_NUMBER);
            String text = page.getText();
            if (text != null && !text.isBlank()) {
                Integer pageNumber = number instanceof Number n ? n.intValue() : null;
                sections.add(new Section(pageNumber == null ? null : "Page " + pageNumber, pageNumber, text.strip()));
            }
        }
        if (sections.isEmpty()) {
            throw new InvalidRequestException("The PDF has no text layer (a scan?); only text PDFs can be searched.");
        }
        return new Parsed(baseName(fileName), DocumentKind.MANUAL, List.of(), sections);
    }

    /** Models are compared case-insensitively with the model field of asset-service ("HP-400"). */
    static List<String> normalize(List<String> models) {
        return models.stream().map(String::strip).filter(m -> !m.isEmpty())
                .map(m -> m.toLowerCase(Locale.ROOT)).distinct().toList();
    }

    private static DocumentKind kind(String value) {
        try {
            return DocumentKind.valueOf(value.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new InvalidRequestException("kind must be MANUAL or PROCEDURE, not " + value);
        }
    }

    private static void add(List<Section> sections, String heading, StringBuilder text) {
        String content = text.toString().strip();
        text.setLength(0);
        if (!content.isEmpty()) {
            sections.add(new Section(heading, null, content));
        }
    }

    private static String baseName(String fileName) {
        int slash = Math.max(fileName.lastIndexOf('/'), fileName.lastIndexOf('\\'));
        String name = fileName.substring(slash + 1);
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }
}
