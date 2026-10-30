package com.factorymind.insights.knowledge;

import com.factorymind.insights.common.InvalidRequestException;
import java.io.IOException;
import java.security.Principal;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/insights/knowledge")
public class KnowledgeController {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeController.class);

    private final KnowledgeBase knowledge;

    public KnowledgeController(KnowledgeBase knowledge) {
        this.knowledge = knowledge;
    }

    @GetMapping("/documents")
    public List<KnowledgeDocument> documents() {
        return knowledge.documents();
    }

    /** The document cut into passages, as the copilot reads it. */
    @GetMapping("/documents/{id}")
    public KnowledgeDocument.Detail document(@PathVariable UUID id) {
        return knowledge.document(id);
    }

    /**
     * Adds a manual: Markdown (front matter optional), plain text or a PDF with a text layer.
     *
     * @param machines comma-separated machine models from asset-service ("HP-400"); empty = every machine
     */
    @PostMapping(path = "/documents", consumes = "multipart/form-data")
    @ResponseStatus(HttpStatus.CREATED)
    public KnowledgeDocument.Detail upload(@RequestParam("file") MultipartFile file,
                                           @RequestParam(required = false) String title,
                                           @RequestParam(required = false) DocumentKind kind,
                                           @RequestParam(required = false) String machines,
                                           Principal principal) throws IOException {
        if (file.isEmpty() || file.getOriginalFilename() == null) {
            throw new InvalidRequestException("Choose a non-empty file.");
        }
        String fileName = file.getOriginalFilename();
        byte[] content = file.getBytes();
        DocumentParser.Parsed parsed = DocumentParser.parse(fileName, content).withOverrides(title, kind,
                machines == null ? null : Arrays.asList(machines.split(",")));
        UUID id = knowledge.save("upload:" + UUID.randomUUID(), parsed, KnowledgeDocument.Origin.UPLOADED, fileName,
                content, username(principal));
        try {
            // Usually a second or two with a local model; the background backfill catches up if not.
            knowledge.embedPending(500);
        } catch (RuntimeException e) {
            log.warn("Uploaded document not embedded yet: {}", e.getMessage());
        }
        return knowledge.document(id);
    }

    @DeleteMapping("/documents/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        knowledge.delete(id);
    }

    private static String username(Principal principal) {
        if (principal instanceof JwtAuthenticationToken jwt) {
            String name = jwt.getToken().getClaimAsString("preferred_username");
            if (name != null) {
                return name;
            }
        }
        return principal == null ? null : principal.getName();
    }
}
