package org.zsy.bysj.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.itextpdf.text.*;
import com.itextpdf.text.pdf.BaseFont;
import com.itextpdf.text.pdf.PdfWriter;
import org.apache.poi.xwpf.usermodel.UnderlinePatterns;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.jsoup.select.Elements;
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
     * 解析文档内容（支持HTML、JSON Delta格式、纯文本）
     */
    private String parseDocumentContent(String content) {
        if (content == null || content.trim().isEmpty()) {
            return "";
        }
        
        String trimmed = content.trim();
        
        // 1. 检查是否是HTML格式（包含HTML标签）
        if (trimmed.contains("<") && trimmed.contains(">")) {
            try {
                // 使用Jsoup解析HTML并提取纯文本
                org.jsoup.nodes.Document htmlDoc = Jsoup.parse(trimmed);
                // 提取所有文本内容，保留换行
                String text = htmlDoc.body().text();
                // 如果提取的文本为空，尝试获取原始文本（保留换行）
                if (text == null || text.trim().isEmpty()) {
                    text = htmlDoc.body().wholeText();
                }
                return text != null ? text : "";
            } catch (Exception e) {
                // HTML解析失败，继续尝试其他方法
            }
        }
        
        // 2. 检查是否是JSON格式（Delta格式）
        if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            try {
                JsonNode jsonNode = objectMapper.readTree(trimmed);
            
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
            } catch (Exception e) {
                System.out.println("JSON解析失败: " + e.getMessage());
                // JSON解析失败，继续尝试其他方法
            }
        }
        
        // 3. 纯文本格式，直接返回
        return content;
    }
    
    /**
     * 将HTML转换为Markdown格式
     */
    private String htmlToMarkdown(String html) {
        if (html == null || html.trim().isEmpty()) {
            return "";
        }
        
        try {
            org.jsoup.nodes.Document doc = Jsoup.parse(html);
            StringBuilder markdown = new StringBuilder();
            
            // 处理body下的所有直接子元素
            Element body = doc.body();
            if (body != null) {
                // 处理段落
                Elements paragraphs = body.select("p");
                if (!paragraphs.isEmpty()) {
                    for (Element p : paragraphs) {
                        String text = convertElementToMarkdown(p);
                        if (!text.trim().isEmpty()) {
                            markdown.append(text.trim()).append("\n\n");
                        }
                    }
                } else {
                    // 如果没有段落，处理所有文本节点
                    String bodyText = convertElementToMarkdown(body);
                    if (!bodyText.trim().isEmpty()) {
                        markdown.append(bodyText.trim());
                    }
                }
            }
            
            // 如果还是空的，直接提取纯文本
            if (markdown.length() == 0) {
                String plainText = doc.body().text();
                if (plainText != null && !plainText.trim().isEmpty()) {
                    markdown.append(plainText);
                }
            }
            
            return markdown.toString().trim();
        } catch (Exception e) {
            // 失败时返回纯文本
            return parseDocumentContent(html);
        }
    }
    
    /**
     * 将HTML元素转换为Markdown格式（递归处理）
     */
    private String convertElementToMarkdown(Element element) {
        if (element == null) {
            return "";
        }
        
        StringBuilder markdown = new StringBuilder();
        
        for (Node node : element.childNodes()) {
            if (node instanceof TextNode) {
                String text = ((TextNode) node).text();
                if (text != null) {
                    markdown.append(text);
                }
            } else if (node instanceof Element) {
                Element el = (Element) node;
                String tagName = el.tagName().toLowerCase();
                String innerText = convertElementToMarkdown(el);
                
                // 如果内部文本为空，跳过
                if (innerText == null || innerText.trim().isEmpty()) {
                    // 对于br标签，即使没有文本也要添加换行
                    if ("br".equals(tagName)) {
                        markdown.append("\n");
                    }
                    continue;
                }
                
                switch (tagName) {
                    case "strong":
                    case "b":
                        markdown.append("**").append(innerText).append("**");
                        break;
                    case "em":
                    case "i":
                        markdown.append("*").append(innerText).append("*");
                        break;
                    case "u":
                        markdown.append("<u>").append(innerText).append("</u>");
                        break;
                    case "s":
                    case "strike":
                    case "del":
                        markdown.append("~~").append(innerText).append("~~");
                        break;
                    case "h1":
                        markdown.append("# ").append(innerText.trim());
                        break;
                    case "h2":
                        markdown.append("## ").append(innerText.trim());
                        break;
                    case "h3":
                        markdown.append("### ").append(innerText.trim());
                        break;
                    case "h4":
                        markdown.append("#### ").append(innerText.trim());
                        break;
                    case "h5":
                        markdown.append("##### ").append(innerText.trim());
                        break;
                    case "h6":
                        markdown.append("###### ").append(innerText.trim());
                        break;
                    case "br":
                        markdown.append("\n");
                        break;
                    case "p":
                        // 段落内部内容，不添加额外格式
                        markdown.append(innerText);
                        break;
                    case "div":
                        // div内部内容，不添加额外格式
                        markdown.append(innerText);
                        break;
                    case "span":
                        // span内部内容，不添加额外格式（但保留内部格式）
                        markdown.append(innerText);
                        break;
                    default:
                        // 其他标签，只提取文本内容
                        markdown.append(innerText);
                        break;
                }
            }
        }
        
        return markdown.toString();
    }
    
    /**
     * 格式化文本片段（用于PDF和Word导出）
     */
    private static class FormattedText {
        String text;
        boolean bold;
        boolean italic;
        boolean underline;
        boolean strikethrough;
        String color; // RGB格式，如 "rgb(255,0,0)" 或 "#FF0000"
        String backgroundColor; // RGB格式
        
        FormattedText(String text) {
            this.text = text;
            this.bold = false;
            this.italic = false;
            this.underline = false;
            this.strikethrough = false;
            this.color = null;
            this.backgroundColor = null;
        }
    }
    
    /**
     * 解析HTML并提取格式化的文本片段
     */
    private java.util.List<FormattedText> parseHtmlToFormattedText(String html) {
        java.util.List<FormattedText> result = new java.util.ArrayList<>();
        if (html == null || html.trim().isEmpty()) {
            return result;
        }
        
        try {
            org.jsoup.nodes.Document doc = Jsoup.parse(html);
            Elements paragraphs = doc.select("p");
            boolean hasParagraph = !paragraphs.isEmpty();
            
            if (!hasParagraph) {
                // 如果没有段落，直接处理body
                processElement(doc.body(), result, false, false, false, false, null, null);
            } else {
                for (Element p : paragraphs) {
                    processElement(p, result, false, false, false, false, null, null);
                    // 段落之间添加换行
                    if (!result.isEmpty() && !result.get(result.size() - 1).text.endsWith("\n")) {
                        FormattedText newline = new FormattedText("\n");
                        result.add(newline);
                    }
                }
            }

            // 如果解析结果没有可见文本，回退到纯文本
            boolean hasVisibleText = result.stream().anyMatch(ft ->
                ft.text != null && ft.text.replace("\u00A0", " ").trim().length() > 0
            );

            if (!hasVisibleText) {
                String plainText = parseDocumentContent(html);
                if (plainText != null && !plainText.trim().isEmpty()) {
                    result.clear();
                    result.add(new FormattedText(plainText));
                }
            }
        } catch (Exception e) {
            // 失败时返回纯文本
            FormattedText plain = new FormattedText(parseDocumentContent(html));
            result.add(plain);
        }
        
        return result;
    }
    
    /**
     * 递归处理HTML元素，提取格式信息
     */
    private void processElement(Element element, java.util.List<FormattedText> result, 
                                boolean parentBold, boolean parentItalic, 
                                boolean parentUnderline, boolean parentStrikethrough,
                                String parentColor, String parentBackgroundColor) {
        for (Node node : element.childNodes()) {
            if (node instanceof TextNode) {
                TextNode textNode = (TextNode) node;
                String text = textNode.text();
                if (text != null && !text.isEmpty()) {
                    FormattedText formatted = new FormattedText(text);
                    formatted.bold = parentBold;
                    formatted.italic = parentItalic;
                    formatted.underline = parentUnderline;
                    formatted.strikethrough = parentStrikethrough;
                    formatted.color = parentColor;
                    formatted.backgroundColor = parentBackgroundColor;
                    result.add(formatted);
                }
            } else if (node instanceof Element) {
                Element el = (Element) node;
                String tagName = el.tagName().toLowerCase();
                
                if ("br".equals(tagName)) {
                    result.add(new FormattedText("\n"));
                    continue;
                }

                // 确定当前格式
                boolean currentBold = parentBold || "strong".equals(tagName) || "b".equals(tagName);
                boolean currentItalic = parentItalic || "em".equals(tagName) || "i".equals(tagName);
                boolean currentUnderline = parentUnderline || "u".equals(tagName);
                boolean currentStrikethrough = parentStrikethrough || "s".equals(tagName) || "strike".equals(tagName);
                
                // 提取颜色
                String currentColor = parentColor;
                String currentBackgroundColor = parentBackgroundColor;
                
                String style = el.attr("style");
                if (style != null && !style.isEmpty()) {
                    // 解析style属性
                    Pattern colorPattern = Pattern.compile("color:\\s*rgb\\((\\d+),\\s*(\\d+),\\s*(\\d+)\\)|color:\\s*#([0-9a-fA-F]{6})");
                    Matcher colorMatcher = colorPattern.matcher(style);
                    if (colorMatcher.find()) {
                        if (colorMatcher.group(1) != null) {
                            // RGB格式
                            int r = Integer.parseInt(colorMatcher.group(1));
                            int g = Integer.parseInt(colorMatcher.group(2));
                            int b = Integer.parseInt(colorMatcher.group(3));
                            currentColor = String.format("#%02X%02X%02X", r, g, b);
                        } else if (colorMatcher.group(4) != null) {
                            // 十六进制格式
                            currentColor = "#" + colorMatcher.group(4);
                        }
                    }
                    
                    Pattern bgColorPattern = Pattern.compile("background-color:\\s*rgb\\((\\d+),\\s*(\\d+),\\s*(\\d+)\\)|background-color:\\s*#([0-9a-fA-F]{6})");
                    Matcher bgColorMatcher = bgColorPattern.matcher(style);
                    if (bgColorMatcher.find()) {
                        if (bgColorMatcher.group(1) != null) {
                            int r = Integer.parseInt(bgColorMatcher.group(1));
                            int g = Integer.parseInt(bgColorMatcher.group(2));
                            int b = Integer.parseInt(bgColorMatcher.group(3));
                            currentBackgroundColor = String.format("#%02X%02X%02X", r, g, b);
                        } else if (bgColorMatcher.group(4) != null) {
                            currentBackgroundColor = "#" + bgColorMatcher.group(4);
                        }
                    }
                }
                
                // 递归处理子元素
                processElement(el, result, currentBold, currentItalic, currentUnderline, 
                             currentStrikethrough, currentColor, currentBackgroundColor);
            }
        }
    }
    
    /**
     * 将十六进制颜色转换为iText Color
     */
    private BaseColor hexToBaseColor(String hex) {
        if (hex == null || !hex.startsWith("#")) {
            return BaseColor.BLACK;
        }
        try {
            String hexColor = hex.substring(1);
            int r = Integer.parseInt(hexColor.substring(0, 2), 16);
            int g = Integer.parseInt(hexColor.substring(2, 4), 16);
            int b = Integer.parseInt(hexColor.substring(4, 6), 16);
            return new BaseColor(r, g, b);
        } catch (Exception e) {
            return BaseColor.BLACK;
        }
    }
    
    /**
     * 将十六进制颜色转换为POI颜色字符串（RRGGBB格式）
     */
    private String hexToPoiColorString(String hex) {
        if (hex == null || !hex.startsWith("#")) {
            return "000000"; // 黑色
        }
        try {
            return hex.substring(1).toUpperCase();
        } catch (Exception e) {
            return "000000";
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

            // 创建支持中文的字体（优先使用嵌入字体以确保跨平台兼容性）
            BaseFont baseFont = null;
                String osName = System.getProperty("os.name").toLowerCase();
            
            // 尝试多种字体方案，优先使用系统字体
            java.util.List<String> fontPaths = new java.util.ArrayList<>();
            
                if (osName.contains("windows")) {
                fontPaths.add("C:/Windows/Fonts/simsun.ttc,1");
                fontPaths.add("C:/Windows/Fonts/simhei.ttf");
                fontPaths.add("C:/Windows/Fonts/msyh.ttc,0");
            } else if (osName.contains("mac")) {
                fontPaths.add("/System/Library/Fonts/STHeiti Light.ttc,0");
                fontPaths.add("/System/Library/Fonts/PingFang.ttc,0");
                fontPaths.add("/Library/Fonts/Arial Unicode.ttf");
                fontPaths.add("/System/Library/Fonts/Supplemental/Arial Unicode.ttf");
                } else {
                // Linux系统
                fontPaths.add("/usr/share/fonts/truetype/wqy/wqy-microhei.ttc");
                fontPaths.add("/usr/share/fonts/truetype/arphic/uming.ttc");
            }
            
            // 尝试使用系统字体文件
            for (String fontPath : fontPaths) {
                try {
                    baseFont = BaseFont.createFont(fontPath, BaseFont.IDENTITY_H, BaseFont.EMBEDDED);
                    break;
                } catch (Exception e) {
                    // 继续尝试下一个字体
                }
            }
            
            // 如果系统字体都失败，尝试使用iText内置字体名称
            if (baseFont == null) {
                try {
                    baseFont = BaseFont.createFont("STSong-Light", "UniGB-UCS2-H", BaseFont.NOT_EMBEDDED);
                } catch (Exception e) {
                    try {
                        // 使用Identity-H编码（支持Unicode，但可能显示为方块如果字体不支持）
                        baseFont = BaseFont.createFont(BaseFont.HELVETICA, BaseFont.IDENTITY_H, BaseFont.NOT_EMBEDDED);
                    } catch (Exception e2) {
                        // 最后的备选方案
                        baseFont = BaseFont.createFont(BaseFont.HELVETICA, BaseFont.CP1252, BaseFont.NOT_EMBEDDED);
                    }
                }
            }

            // 添加标题
            Font titleFont = new Font(baseFont, 18, Font.BOLD);
            Paragraph title = new Paragraph(document.getTitle() != null ? document.getTitle() : "未命名文档", titleFont);
            title.setSpacingAfter(20);
            pdfDoc.add(title);

            // 解析HTML内容并保留格式
            String htmlContent = document.getContent();
            if (htmlContent == null || htmlContent.trim().isEmpty()) {
                Font contentFont = new Font(baseFont, 12, Font.NORMAL);
                Paragraph emptyPara = new Paragraph("（文档内容为空）", contentFont);
                pdfDoc.add(emptyPara);
            } else {
                // 检查是否是HTML格式
                if (htmlContent.trim().contains("<") && htmlContent.trim().contains(">")) {
                    // HTML格式，解析并保留格式
                    java.util.List<FormattedText> formattedTexts = parseHtmlToFormattedText(htmlContent);
                    
                    Paragraph contentPara = new Paragraph();
                    for (FormattedText ft : formattedTexts) {
                        if (ft.text == null) {
                            continue;
                        }

                        if ("\n".equals(ft.text)) {
                            contentPara.add(Chunk.NEWLINE);
                            continue;
                        }

                        if (ft.text.isEmpty()) {
                            continue;
                        }
                        
                        // 创建字体
                        int fontStyle = Font.NORMAL;
                        if (ft.bold && ft.italic) {
                            fontStyle = Font.BOLDITALIC;
                        } else if (ft.bold) {
                            fontStyle = Font.BOLD;
                        } else if (ft.italic) {
                            fontStyle = Font.ITALIC;
                        }
                        
                        BaseColor textColor = ft.color != null ? hexToBaseColor(ft.color) : BaseColor.BLACK;
                        Font chunkFont = new Font(baseFont, 12, fontStyle, textColor);
                        
                        // 创建Chunk
                        Chunk chunk = new Chunk(ft.text, chunkFont);
                        
                        // 添加下划线
                        if (ft.underline) {
                            chunk.setUnderline(0.1f, -2f);
                        }
                        
                        // 添加删除线（iText不支持直接删除线，使用下划线模拟）
                        if (ft.strikethrough) {
                            chunk.setUnderline(0.1f, 6f);
                        }
                        
                        // 添加背景色（使用高亮）
                        if (ft.backgroundColor != null) {
                            BaseColor bgColor = hexToBaseColor(ft.backgroundColor);
                            chunk.setBackground(bgColor);
                        }
                        
                        contentPara.add(chunk);
                    }
                    
                    if (contentPara.isEmpty()) {
                        Font contentFont = new Font(baseFont, 12, Font.NORMAL);
                        contentPara = new Paragraph("（文档内容为空）", contentFont);
                    }
                    
                    pdfDoc.add(contentPara);
                } else {
                    // 非HTML格式，使用纯文本
                    String content = parseDocumentContent(htmlContent);
            if (content == null || content.trim().isEmpty()) {
                content = "（文档内容为空）";
            }
            Font contentFont = new Font(baseFont, 12, Font.NORMAL);
            Paragraph contentPara = new Paragraph(content, contentFont);
            pdfDoc.add(contentPara);
                }
            }

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

            // 解析HTML内容并保留格式
            String htmlContent = document.getContent();
            if (htmlContent == null || htmlContent.trim().isEmpty()) {
                XWPFParagraph contentPara = doc.createParagraph();
                XWPFRun contentRun = contentPara.createRun();
                contentRun.setText("（文档内容为空）");
                contentRun.setFontSize(12);
                contentRun.setFontFamily("宋体", XWPFRun.FontCharRange.eastAsia);
                contentRun.setFontFamily("SimSun", XWPFRun.FontCharRange.ascii);
            } else {
                // 检查是否是HTML格式
                if (htmlContent.trim().contains("<") && htmlContent.trim().contains(">")) {
                    // HTML格式，解析并保留格式
                    java.util.List<FormattedText> formattedTexts = parseHtmlToFormattedText(htmlContent);
                    
                    XWPFParagraph contentPara = doc.createParagraph();
                    
                    for (FormattedText ft : formattedTexts) {
                        if (ft.text == null) {
                            continue;
                        }
                        
                        // 处理换行符
                        if (ft.text.equals("\n")) {
                            XWPFRun newlineRun = contentPara.createRun();
                            newlineRun.addBreak();
                            continue;
                        }
                        
                        if (ft.text.isEmpty()) {
                            continue;
                        }
                        
                        // 为每个格式化的文本片段创建新的Run
                        XWPFRun run = contentPara.createRun();
                        run.setText(ft.text);
                        run.setFontSize(12);
                        
                        // 设置字体
                        run.setFontFamily("宋体", XWPFRun.FontCharRange.eastAsia);
                        run.setFontFamily("SimSun", XWPFRun.FontCharRange.eastAsia);
                        run.setFontFamily("SimSun", XWPFRun.FontCharRange.ascii);
                        run.setFontFamily("SimSun", XWPFRun.FontCharRange.hAnsi);
                        run.setFontFamily("Times New Roman", XWPFRun.FontCharRange.cs);
                        
                        // 设置格式
                        run.setBold(ft.bold);
                        run.setItalic(ft.italic);
                        if (ft.underline) {
                            run.setUnderline(UnderlinePatterns.SINGLE);
                        }
                        if (ft.strikethrough) {
                            run.setStrikeThrough(true);
                        }
                        
                        // 设置颜色
                        if (ft.color != null) {
                            String colorStr = hexToPoiColorString(ft.color);
                            run.setColor(colorStr);
                        }
                        
                        // 设置背景色（POI通过高亮实现，但API较复杂，这里简化处理）
                        // 注意：POI的背景色设置需要使用CTR对象，这里先不实现
                        // 如果需要背景色，可以考虑使用shading
                    }
                    
                    // 如果没有内容，添加空内容提示
                    if (contentPara.getRuns().isEmpty()) {
                        XWPFRun emptyRun = contentPara.createRun();
                        emptyRun.setText("（文档内容为空）");
                        emptyRun.setFontSize(12);
                        emptyRun.setFontFamily("宋体", XWPFRun.FontCharRange.eastAsia);
                    }
                } else {
                    // 非HTML格式，使用纯文本
                    String content = parseDocumentContent(htmlContent);
            if (content == null || content.trim().isEmpty()) {
                content = "（文档内容为空）";
            }
            
            XWPFParagraph contentPara = doc.createParagraph();
            XWPFRun contentRun = contentPara.createRun();
            contentRun.setText(content);
            contentRun.setFontSize(12);
            contentRun.setFontFamily("宋体", XWPFRun.FontCharRange.eastAsia);
            contentRun.setFontFamily("SimSun", XWPFRun.FontCharRange.eastAsia);
            contentRun.setFontFamily("SimSun", XWPFRun.FontCharRange.ascii);
            contentRun.setFontFamily("SimSun", XWPFRun.FontCharRange.hAnsi);
            contentRun.setFontFamily("Times New Roman", XWPFRun.FontCharRange.cs);
                }
            }

            doc.write(outputStream);
            return outputStream;
        } catch (IOException e) {
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
    public String exportToMarkdown(org.zsy.bysj.model.Document document) {
        StringBuilder markdown = new StringBuilder();
        markdown.append("# ").append(document.getTitle() != null ? document.getTitle() : "未命名文档").append("\n\n");
        
        // 如果内容是HTML，转换为Markdown格式
        String content = document.getContent();
        if (content != null && content.trim().contains("<") && content.trim().contains(">")) {
            // HTML格式，转换为Markdown
            String markdownContent = htmlToMarkdown(content);
            markdown.append(markdownContent);
        } else {
            // 其他格式，解析为纯文本
            String textContent = parseDocumentContent(content);
            markdown.append(textContent);
        }
        
        return markdown.toString();
    }
}


