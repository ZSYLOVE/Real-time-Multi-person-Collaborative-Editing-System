package org.zsy.bysj.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.itextpdf.text.*;
import com.itextpdf.text.pdf.BaseFont;
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
        if (jsonContent == null || jsonContent.trim().isEmpty()) {
            return "";
        }
        
        try {
            // 先尝试作为纯文本处理（如果不是JSON格式）
            if (!jsonContent.trim().startsWith("{") && !jsonContent.trim().startsWith("[")) {
                return jsonContent;
            }
            
            // 尝试解析JSON格式的内容
            JsonNode jsonNode = objectMapper.readTree(jsonContent);
            
            // 如果是数组，提取所有文本节点
            if (jsonNode.isArray()) {
                StringBuilder text = new StringBuilder();
                for (JsonNode node : jsonNode) {
                    if (node.isObject() && node.has("text")) {
                        String nodeText = node.get("text").asText();
                        if (nodeText != null) {
                            text.append(nodeText);
                        }
                    } else if (node.isObject() && node.has("content")) {
                        String nodeText = node.get("content").asText();
                        if (nodeText != null) {
                            text.append(nodeText);
                        }
                    } else if (node.isTextual()) {
                        text.append(node.asText());
                    }
                }
                return text.toString();
            } else if (jsonNode.isObject()) {
                // 如果是对象，尝试提取常见字段
                if (jsonNode.has("content")) {
                    return jsonNode.get("content").asText();
                } else if (jsonNode.has("text")) {
                    return jsonNode.get("text").asText();
                } else if (jsonNode.has("ops")) {
                    // 可能是 Quill Delta 格式
                    JsonNode ops = jsonNode.get("ops");
                    if (ops.isArray()) {
                        StringBuilder text = new StringBuilder();
                        for (JsonNode op : ops) {
                            if (op.has("insert") && op.get("insert").isTextual()) {
                                text.append(op.get("insert").asText());
                            }
                        }
                        return text.toString();
                    }
                }
            } else if (jsonNode.isTextual()) {
                // 如果是纯文本
                return jsonNode.asText();
            }
            
            // 如果无法解析，返回原始内容
            return jsonContent;
        } catch (Exception e) {
            // JSON解析失败，返回原始内容
            System.out.println("解析文档内容失败: " + e.getMessage());
            e.printStackTrace();
            return jsonContent;
        }
    }

    @Override
    public ByteArrayOutputStream exportToPdf(Document document) {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        com.itextpdf.text.Document pdfDoc = null;
        
        try {
            pdfDoc = new com.itextpdf.text.Document(PageSize.A4);
            PdfWriter.getInstance(pdfDoc, outputStream);
            pdfDoc.open();

            // 创建支持中文的字体
            BaseFont baseFont;
            try {
                // 优先尝试使用 Windows 系统的宋体
                String osName = System.getProperty("os.name").toLowerCase();
                if (osName.contains("windows")) {
                    // Windows 系统使用宋体
                    baseFont = BaseFont.createFont("C:/Windows/Fonts/simsun.ttc,1", BaseFont.IDENTITY_H, BaseFont.NOT_EMBEDDED);
                } else {
                    // Linux/Mac 系统尝试使用 STSong-Light
                    baseFont = BaseFont.createFont("STSong-Light", "UniGB-UCS2-H", BaseFont.NOT_EMBEDDED);
                }
            } catch (Exception e) {
                try {
                    // 如果失败，尝试使用 STSong-Light（中易宋体）
                    baseFont = BaseFont.createFont("STSong-Light", "UniGB-UCS2-H", BaseFont.NOT_EMBEDDED);
                } catch (Exception ex) {
                    // 最后使用 Identity-H 编码（支持Unicode，但可能显示为方块）
                    try {
                        baseFont = BaseFont.createFont(BaseFont.HELVETICA, BaseFont.IDENTITY_H, BaseFont.NOT_EMBEDDED);
                    } catch (Exception e2) {
                        // 最后的备选方案
                        baseFont = BaseFont.createFont(BaseFont.HELVETICA, BaseFont.CP1252, BaseFont.NOT_EMBEDDED);
                    }
                }
            }

            // 添加标题
            Font titleFont = new Font(baseFont, 18, Font.BOLD);
            Paragraph title = new Paragraph(document.getTitle(), titleFont);
            title.setSpacingAfter(20);
            pdfDoc.add(title);

            // 解析并添加内容（解析JSON格式的文档内容）
            String content = parseDocumentContent(document.getContent());
            if (content == null || content.trim().isEmpty()) {
                content = "（文档内容为空）";
            }
            
            Font contentFont = new Font(baseFont, 12, Font.NORMAL);
            Paragraph contentPara = new Paragraph(content, contentFont);
            pdfDoc.add(contentPara);

            pdfDoc.close();
            return outputStream;
        } catch (Exception e) {
            // 确保资源被正确关闭
            if (pdfDoc != null && pdfDoc.isOpen()) {
                pdfDoc.close();
            }
            e.printStackTrace();
            throw new RuntimeException("PDF导出失败: " + e.getMessage(), e);
        }
    }

    @Override
    public ByteArrayOutputStream exportToWord(Document document) {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        XWPFDocument doc = null;
        
        try {
            // 添加调试信息
            System.out.println("=== Word导出开始 ===");
            System.out.println("文档ID: " + document.getId());
            System.out.println("文档标题: " + document.getTitle());
            System.out.println("文档内容长度: " + (document.getContent() != null ? document.getContent().length() : 0));
            System.out.println("文档内容前100字符: " + (document.getContent() != null && document.getContent().length() > 100 
                ? document.getContent().substring(0, 100) : document.getContent()));
            
            doc = new XWPFDocument();

            // 添加标题
            XWPFParagraph titlePara = doc.createParagraph();
            XWPFRun titleRun = titlePara.createRun();
            String title = document.getTitle() != null ? document.getTitle() : "无标题";
            // 确保标题不为空且正确编码
            if (title == null || title.isEmpty()) {
                title = "无标题";
            }
            titleRun.setText(title);
            titleRun.setBold(true);
            titleRun.setFontSize(18);
            // 设置字体：同时设置 ASCII 字体和 East Asian 字体（中文）
            titleRun.setFontFamily("宋体", XWPFRun.FontCharRange.eastAsia);
            titleRun.setFontFamily("SimSun", XWPFRun.FontCharRange.eastAsia);
            titleRun.setFontFamily("SimSun", XWPFRun.FontCharRange.ascii);
            titleRun.setFontFamily("SimSun", XWPFRun.FontCharRange.hAnsi);
            titleRun.setFontFamily("Times New Roman", XWPFRun.FontCharRange.cs);

            // 添加内容（解析JSON格式）
            String content = parseDocumentContent(document.getContent());
            System.out.println("解析后的内容长度: " + (content != null ? content.length() : 0));
            System.out.println("解析后的内容前100字符: " + (content != null && content.length() > 100 
                ? content.substring(0, 100) : content));
            
            if (content == null || content.trim().isEmpty()) {
                content = "（文档内容为空）";
            }
            
            // 确保内容不为空
            if (content == null) {
                content = "";
            }
            
            // 添加内容段落
            XWPFParagraph contentPara = doc.createParagraph();
            XWPFRun contentRun = contentPara.createRun();
            // 直接设置完整内容，不分割
            contentRun.setText(content);
            // 设置字体大小
            contentRun.setFontSize(12);
            // 设置字体：同时设置 ASCII 字体和 East Asian 字体（中文）
            // 使用 "宋体" 作为中文字体名称（POI 会自动转换为 SimSun）
            contentRun.setFontFamily("宋体", XWPFRun.FontCharRange.eastAsia);
            contentRun.setFontFamily("SimSun", XWPFRun.FontCharRange.eastAsia);
            contentRun.setFontFamily("SimSun", XWPFRun.FontCharRange.ascii);
            contentRun.setFontFamily("SimSun", XWPFRun.FontCharRange.hAnsi);
            contentRun.setFontFamily("Times New Roman", XWPFRun.FontCharRange.cs);

            doc.write(outputStream);
            System.out.println("Word文档生成成功，大小: " + outputStream.size() + " 字节");
            System.out.println("=== Word导出结束 ===");
            return outputStream;
        } catch (IOException e) {
            System.out.println("Word导出失败: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("Word导出失败: " + e.getMessage(), e);
        } finally {
            // 确保资源被正确关闭
            if (doc != null) {
                try {
                    doc.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
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


