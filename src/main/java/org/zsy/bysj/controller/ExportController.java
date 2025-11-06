package org.zsy.bysj.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.zsy.bysj.model.Document;
import org.zsy.bysj.service.DocumentService;
import org.zsy.bysj.service.ExportService;

import java.io.ByteArrayOutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * 文档导出控制器
 */
@RestController
@RequestMapping("/api/export")
public class ExportController {

    @Autowired
    private DocumentService documentService;

    @Autowired
    private ExportService exportService;

    /**
     * 导出为PDF
     */
    @GetMapping("/pdf/{documentId}")
    public ResponseEntity<byte[]> exportToPdf(@PathVariable Long documentId) {
        Document document = documentService.getDocumentById(documentId);
        if (document == null) {
            return ResponseEntity.notFound().build();
        }

        ByteArrayOutputStream pdfStream = exportService.exportToPdf(document);
        byte[] pdfBytes = pdfStream.toByteArray();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDispositionFormData("attachment", 
            URLEncoder.encode(document.getTitle() + ".pdf", StandardCharsets.UTF_8));

        return ResponseEntity.ok()
                .headers(headers)
                .body(pdfBytes);
    }

    /**
     * 导出为Word
     */
    @GetMapping("/word/{documentId}")
    public ResponseEntity<byte[]> exportToWord(@PathVariable Long documentId) {
        Document document = documentService.getDocumentById(documentId);
        if (document == null) {
            return ResponseEntity.notFound().build();
        }

        ByteArrayOutputStream wordStream = exportService.exportToWord(document);
        byte[] wordBytes = wordStream.toByteArray();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        headers.setContentDispositionFormData("attachment", 
            URLEncoder.encode(document.getTitle() + ".docx", StandardCharsets.UTF_8));

        return ResponseEntity.ok()
                .headers(headers)
                .body(wordBytes);
    }

    /**
     * 导出为Markdown
     */
    @GetMapping("/markdown/{documentId}")
    public ResponseEntity<String> exportToMarkdown(@PathVariable Long documentId) {
        Document document = documentService.getDocumentById(documentId);
        if (document == null) {
            return ResponseEntity.notFound().build();
        }

        String markdown = exportService.exportToMarkdown(document);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.TEXT_PLAIN);
        headers.setContentDispositionFormData("attachment", 
            URLEncoder.encode(document.getTitle() + ".md", StandardCharsets.UTF_8));

        return ResponseEntity.ok()
                .headers(headers)
                .body(markdown);
    }
}

