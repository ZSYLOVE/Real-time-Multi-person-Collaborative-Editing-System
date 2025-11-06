package org.zsy.bysj.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.itextpdf.text.*;
import com.itextpdf.text.pdf.PdfWriter;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.zsy.bysj.model.Document;
import org.zsy.bysj.service.ExportService;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * 文档导出服务实现类
 */
@Service
public class ExportServiceImpl implements ExportService {

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * 解析文档内容（从JSON格式转换为纯文本）
     */
    private String parseDocumentContent(String jsonContent) {
        try {
            // 尝试解析JSON格式的内容
            JsonNode jsonNode = objectMapper.readTree(jsonContent);
            
            // 如果是数组，提取所有文本节点
            if (jsonNode.isArray()) {
                StringBuilder text = new StringBuilder();
                for (JsonNode node : jsonNode) {
                    if (node.isObject() && node.has("text")) {
                        text.append(node.get("text").asText());
                    } else if (node.isTextual()) {
                        text.append(node.asText());
                    }
                }
                return text.toString();
            } else if (jsonNode.isObject() && jsonNode.has("content")) {
                // 如果是对象，提取content字段
                return jsonNode.get("content").asText();
            } else if (jsonNode.isTextual()) {
                // 如果是纯文本
                return jsonNode.asText();
            }
            
            // 如果无法解析，返回原始内容
            return jsonContent;
        } catch (Exception e) {
            // JSON解析失败，返回原始内容
            return jsonContent;
        }
    }

    @Override
    public ByteArrayOutputStream exportToPdf(Document document) {
        try {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            com.itextpdf.text.Document pdfDoc = new com.itextpdf.text.Document(PageSize.A4);
            PdfWriter.getInstance(pdfDoc, outputStream);
            pdfDoc.open();

            // 添加标题
            Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18);
            Paragraph title = new Paragraph(document.getTitle(), titleFont);
            title.setSpacingAfter(20);
            pdfDoc.add(title);

            // 解析并添加内容（解析JSON格式的文档内容）
            String content = parseDocumentContent(document.getContent());
            Font contentFont = FontFactory.getFont(FontFactory.HELVETICA, 12);
            Paragraph contentPara = new Paragraph(content, contentFont);
            pdfDoc.add(contentPara);

            pdfDoc.close();
            return outputStream;
        } catch (Exception e) {
            throw new RuntimeException("PDF导出失败: " + e.getMessage());
        }
    }

    @Override
    public ByteArrayOutputStream exportToWord(Document document) {
        try {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            XWPFDocument doc = new XWPFDocument();

            // 添加标题
            XWPFParagraph titlePara = doc.createParagraph();
            XWPFRun titleRun = titlePara.createRun();
            titleRun.setText(document.getTitle());
            titleRun.setBold(true);
            titleRun.setFontSize(18);

            // 添加内容（解析JSON格式）
            String content = parseDocumentContent(document.getContent());
            XWPFParagraph contentPara = doc.createParagraph();
            XWPFRun contentRun = contentPara.createRun();
            contentRun.setText(content);

            doc.write(outputStream);
            doc.close();
            return outputStream;
        } catch (IOException e) {
            throw new RuntimeException("Word导出失败: " + e.getMessage());
        }
    }

    @Override
    public String exportToMarkdown(Document document) {
        StringBuilder markdown = new StringBuilder();
        markdown.append("# ").append(document.getTitle()).append("\n\n");
        
        // 解析JSON内容为Markdown格式
        String content = parseDocumentContent(document.getContent());
        markdown.append(content);
        
        return markdown.toString();
    }
}

