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
import java.nio.charset.Charset;

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
     * 构建 Content-Disposition 头，支持中文文件名
     */
    private String buildContentDisposition(String fileName) {
        try {
            // 检查文件名是否包含非ASCII字符
            boolean hasNonAscii = !StandardCharsets.US_ASCII.newEncoder().canEncode(fileName);
            
            if (hasNonAscii) {
                // 包含中文等非ASCII字符
                // filename* 使用 RFC 5987 格式（UTF-8''编码）
                String utf8Encoded = URLEncoder.encode(fileName, StandardCharsets.UTF_8.toString())
                        .replace("+", "%20");
                
                // filename 参数：对于非ASCII字符，使用 URL 编码
                // 现代浏览器会优先使用 filename* 参数
                return String.format("attachment; filename=\"%s\"; filename*=UTF-8''%s", 
                        utf8Encoded, utf8Encoded);
            } else {
                // 只包含ASCII字符，直接使用
                return String.format("attachment; filename=\"%s\"", fileName);
            }
        } catch (Exception e) {
            // 如果编码失败，使用简单的格式
            return String.format("attachment; filename=\"%s\"", fileName);
        }
    }

    /**
     * 清理文件名，移除非法字符
     */
    private String sanitizeFileName(String fileName) {
        if (fileName == null || fileName.trim().isEmpty()) {
            return "未命名文档";
        }
        // 移除文件名中的非法字符：/ \ : * ? " < > |
        String sanitized = fileName.replaceAll("[\\\\/:*?\"<>|]", "_");
        // 移除首尾空格和点
        sanitized = sanitized.trim().replaceAll("^\\.+|\\.+$", "");
        // 如果清理后为空，使用默认名称
        if (sanitized.isEmpty()) {
            return "未命名文档";
        }
        return sanitized;
    }

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

        String fileName = sanitizeFileName(document.getTitle()) + ".pdf";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.add("Content-Disposition", buildContentDisposition(fileName));

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

        String fileName = sanitizeFileName(document.getTitle()) + ".docx";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.wordprocessingml.document"));
        headers.add("Content-Disposition", buildContentDisposition(fileName));

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

        String fileName = sanitizeFileName(document.getTitle()) + ".md";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("text/plain;charset=UTF-8"));
        headers.add("Content-Disposition", buildContentDisposition(fileName));

        return ResponseEntity.ok()
                .headers(headers)
                .body(markdown);
    }
}

