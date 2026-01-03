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
        String fontFamily; // 字体名称
        String fontSize; // 字体大小: small, normal, large, huge
        String align; // 对齐方式: left, center, right, justify
        int indent; // 缩进级别 (0-8)
        String script; // 上标/下标: sub, super, null
        boolean isHeader; // 是否为标题
        int headerLevel; // 标题级别 (1-6)
        boolean isCodeBlock; // 是否为代码块
        boolean isBlockquote; // 是否为引用块
        boolean isListItem; // 是否为列表项
        String listType; // 列表类型: ordered, bullet, null
        int listLevel; // 列表嵌套级别
        String imageUrl; // 图片URL或base64
        String linkUrl; // 链接URL
        String linkText; // 链接文本
        String videoUrl; // 视频URL
        boolean isImage; // 是否为图片
        boolean isLink; // 是否为链接
        boolean isVideo; // 是否为视频
        String direction; // 文字方向: ltr, rtl, null
        
        FormattedText(String text) {
            this.text = text;
            this.bold = false;
            this.italic = false;
            this.underline = false;
            this.strikethrough = false;
            this.color = null;
            this.backgroundColor = null;
            this.fontFamily = null;
            this.fontSize = "normal";
            this.align = "left";
            this.indent = 0;
            this.script = null;
            this.isHeader = false;
            this.headerLevel = 0;
            this.isCodeBlock = false;
            this.isBlockquote = false;
            this.isListItem = false;
            this.listType = null;
            this.listLevel = 0;
            this.imageUrl = null;
            this.linkUrl = null;
            this.linkText = null;
            this.videoUrl = null;
            this.isImage = false;
            this.isLink = false;
            this.isVideo = false;
            this.direction = "ltr"; // 默认从左到右
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
            
            // 处理列表（ul, ol）
            Elements lists = doc.select("ul, ol");
            if (!lists.isEmpty()) {
                for (Element list : lists) {
                    processList(list, result, 0);
                }
            }
            
            Elements paragraphs = doc.select("p");
            boolean hasParagraph = !paragraphs.isEmpty();
            
            if (!hasParagraph && lists.isEmpty()) {
                // 如果没有段落和列表，直接处理body
                processElement(doc.body(), result, false, false, false, false, null, null,
                              null, "normal", "left", 0, null, false, 0, false, false, false, null, 0, null, null, null, null, false, false, false, "ltr");
            } else {
                for (Element p : paragraphs) {
                    // 从段落标签提取对齐和缩进信息
                    String pClassName = p.className();
                    String pStyle = p.attr("style");
                    String paragraphAlign = "left";
                    int paragraphIndent = 0;
                    
                    // 解析对齐方式（从类名或样式）
                    if (pClassName != null && pClassName.contains("ql-align-")) {
                        Pattern alignPattern = Pattern.compile("ql-align-([\\w]+)");
                        Matcher alignMatcher = alignPattern.matcher(pClassName);
                        if (alignMatcher.find()) {
                            paragraphAlign = alignMatcher.group(1);
                        }
                    } else if (pStyle != null && pStyle.contains("text-align")) {
                        Pattern textAlignPattern = Pattern.compile("text-align:\\s*([^;]+)");
                        Matcher textAlignMatcher = textAlignPattern.matcher(pStyle);
                        if (textAlignMatcher.find()) {
                            paragraphAlign = textAlignMatcher.group(1).trim();
                        }
                    }
                    
                    // 解析缩进（从类名）
                    if (pClassName != null && pClassName.contains("ql-indent-")) {
                        Pattern indentPattern = Pattern.compile("ql-indent-(\\d+)");
                        Matcher indentMatcher = indentPattern.matcher(pClassName);
                        if (indentMatcher.find()) {
                            try {
                                paragraphIndent = Integer.parseInt(indentMatcher.group(1));
                            } catch (NumberFormatException e) {
                                // 忽略
                            }
                        }
                    }
                    
                    // 检查是否是代码块或引用块
                    boolean isCodeBlock = pClassName != null && (pClassName.contains("ql-code-block") || pClassName.contains("ql-syntax"));
                    boolean isBlockquote = "blockquote".equals(p.tagName().toLowerCase());
                    
                    // 检查文字方向
                    String paragraphDirection = "ltr";
                    String pDir = p.attr("dir");
                    if (pDir != null && !pDir.isEmpty()) {
                        paragraphDirection = pDir.toLowerCase();
                    } else if (pClassName != null && pClassName.contains("ql-direction-rtl")) {
                        paragraphDirection = "rtl";
                    }
                    
                    processElement(p, result, false, false, false, false, null, null,
                                  null, "normal", paragraphAlign, paragraphIndent, null, false, 0, isCodeBlock, isBlockquote,
                                  false, null, 0, null, null, null, null, false, false, false, paragraphDirection);
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
     * 处理列表（ul, ol）
     */
    private void processList(Element listElement, java.util.List<FormattedText> result, int level) {
        String listTag = listElement.tagName().toLowerCase();
        String listType = "ordered".equals(listTag) ? "ordered" : "bullet";
        
        Elements listItems = listElement.select("> li");
        for (Element li : listItems) {
            FormattedText listItem = new FormattedText("");
            listItem.isListItem = true;
            listItem.listType = listType;
            listItem.listLevel = level;
            
            // 处理列表项内容
            java.util.List<FormattedText> itemContent = new java.util.ArrayList<>();
            processElement(li, itemContent, false, false, false, false, null, null,
                          null, "normal", "left", 0, null, false, 0, false, false,
                          true, listType, level, null, null, null, null, false, false, false, "ltr");
            
            // 合并列表项内容
            if (!itemContent.isEmpty()) {
                StringBuilder itemText = new StringBuilder();
                for (FormattedText ft : itemContent) {
                    if (ft.text != null) {
                        itemText.append(ft.text);
                    }
                }
                listItem.text = itemText.toString();
                // 复制第一个内容的格式信息
                FormattedText firstContent = itemContent.get(0);
                listItem.bold = firstContent.bold;
                listItem.italic = firstContent.italic;
                listItem.underline = firstContent.underline;
                listItem.strikethrough = firstContent.strikethrough;
                listItem.color = firstContent.color;
                listItem.backgroundColor = firstContent.backgroundColor;
                listItem.fontFamily = firstContent.fontFamily;
                listItem.fontSize = firstContent.fontSize;
            }
            
            result.add(listItem);
            
            // 处理嵌套列表
            Elements nestedLists = li.select("> ul, > ol");
            for (Element nestedList : nestedLists) {
                processList(nestedList, result, level + 1);
            }
        }
    }
    
    /**
     * 递归处理HTML元素，提取格式信息
     */
    private void processElement(Element element, java.util.List<FormattedText> result, 
                                boolean parentBold, boolean parentItalic, 
                                boolean parentUnderline, boolean parentStrikethrough,
                                String parentColor, String parentBackgroundColor,
                                String parentFontFamily, String parentFontSize,
                                String parentAlign, int parentIndent, String parentScript,
                                boolean parentIsHeader, int parentHeaderLevel,
                                boolean parentIsCodeBlock, boolean parentIsBlockquote,
                                boolean parentIsListItem, String parentListType, int parentListLevel,
                                String parentImageUrl, String parentLinkUrl, String parentLinkText, String parentVideoUrl,
                                boolean parentIsImage, boolean parentIsLink, boolean parentIsVideo,
                                String parentDirection) {
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
                    formatted.fontFamily = parentFontFamily;
                    formatted.fontSize = parentFontSize;
                    formatted.align = parentAlign;
                    formatted.indent = parentIndent;
                    formatted.script = parentScript;
                    formatted.isHeader = parentIsHeader;
                    formatted.headerLevel = parentHeaderLevel;
                    formatted.isCodeBlock = parentIsCodeBlock;
                    formatted.isBlockquote = parentIsBlockquote;
                    formatted.isListItem = parentIsListItem;
                    formatted.listType = parentListType;
                    formatted.listLevel = parentListLevel;
                    formatted.imageUrl = parentImageUrl;
                    formatted.linkUrl = parentLinkUrl;
                    formatted.linkText = parentLinkText;
                    formatted.videoUrl = parentVideoUrl;
                    formatted.isImage = parentIsImage;
                    formatted.isLink = parentIsLink;
                    formatted.isVideo = parentIsVideo;
                    formatted.direction = parentDirection;
                    result.add(formatted);
                }
            } else if (node instanceof Element) {
                Element el = (Element) node;
                String tagName = el.tagName().toLowerCase();
                String className = el.className();
                String dirAttr = el.attr("dir"); // 获取 dir 属性
                
                if ("br".equals(tagName)) {
                    result.add(new FormattedText("\n"));
                    continue;
                }
                
                // 处理图片
                if ("img".equals(tagName)) {
                    String src = el.attr("src");
                    String alt = el.attr("alt");
                    if (src != null && !src.isEmpty()) {
                        FormattedText image = new FormattedText(alt != null ? alt : "[图片]");
                        image.isImage = true;
                        image.imageUrl = src;
                        // 继承父元素的格式（对齐、缩进等）
                        image.align = parentAlign;
                        image.indent = parentIndent;
                        image.direction = parentDirection;
                        // 检查图片元素本身是否有样式
                        String imgStyle = el.attr("style");
                        if (imgStyle != null && !imgStyle.isEmpty()) {
                            // 解析图片元素的背景色（如果有）
                            Pattern bgColorPattern = Pattern.compile("background-color:\\s*rgba?\\((\\d+),\\s*(\\d+),\\s*(\\d+)(?:,\\s*[\\d.]+)?\\)", Pattern.CASE_INSENSITIVE);
                            Matcher bgColorMatcher = bgColorPattern.matcher(imgStyle);
                            if (bgColorMatcher.find()) {
                                int r = Integer.parseInt(bgColorMatcher.group(1));
                                int g = Integer.parseInt(bgColorMatcher.group(2));
                                int b = Integer.parseInt(bgColorMatcher.group(3));
                                image.backgroundColor = String.format("#%02X%02X%02X", r, g, b);
                            }
                        }
                        result.add(image);
                    }
                    continue;
                }
                
                // 处理链接
                if ("a".equals(tagName)) {
                    String href = el.attr("href");
                    String linkText = el.text();
                    if (href != null && !href.isEmpty()) {
                        // 处理链接内的内容
                        java.util.List<FormattedText> linkContent = new java.util.ArrayList<>();
                        processElement(el, linkContent, parentBold, parentItalic, parentUnderline, parentStrikethrough,
                                      parentColor, parentBackgroundColor, parentFontFamily, parentFontSize,
                                      parentAlign, parentIndent, parentScript, parentIsHeader, parentHeaderLevel,
                                      parentIsCodeBlock, parentIsBlockquote, parentIsListItem, parentListType, parentListLevel,
                                      parentImageUrl, href, linkText, parentVideoUrl, false, true, false, parentDirection);
                        
                        // 标记所有链接内容为链接
                        for (FormattedText ft : linkContent) {
                            ft.isLink = true;
                            ft.linkUrl = href;
                            ft.linkText = linkText;
                        }
                        result.addAll(linkContent);
                    } else {
                        // 没有href，正常处理
                        processElement(el, result, parentBold, parentItalic, parentUnderline, parentStrikethrough,
                                      parentColor, parentBackgroundColor, parentFontFamily, parentFontSize,
                                      parentAlign, parentIndent, parentScript, parentIsHeader, parentHeaderLevel,
                                      parentIsCodeBlock, parentIsBlockquote, parentIsListItem, parentListType, parentListLevel,
                                      parentImageUrl, parentLinkUrl, parentLinkText, parentVideoUrl,
                                      parentIsImage, parentIsLink, parentIsVideo, parentDirection);
                    }
                    continue;
                }
                
                // 处理视频（通常是 iframe 或 video 标签）
                if ("video".equals(tagName) || ("iframe".equals(tagName) && el.attr("src").contains("youtube") || el.attr("src").contains("video"))) {
                    String src = el.attr("src");
                    if (src == null || src.isEmpty()) {
                        src = el.attr("data-src");
                    }
                    if (src != null && !src.isEmpty()) {
                        FormattedText video = new FormattedText("[视频]");
                        video.isVideo = true;
                        video.videoUrl = src;
                        result.add(video);
                    }
                    continue;
                }
                
                // 跳过列表标签（已在 processList 中处理）
                if ("ul".equals(tagName) || "ol".equals(tagName) || "li".equals(tagName)) {
                    // 如果是在列表项内部，继续处理
                    if (parentIsListItem) {
                        processElement(el, result, parentBold, parentItalic, parentUnderline, parentStrikethrough,
                                      parentColor, parentBackgroundColor, parentFontFamily, parentFontSize,
                                      parentAlign, parentIndent, parentScript, parentIsHeader, parentHeaderLevel,
                                      parentIsCodeBlock, parentIsBlockquote, parentIsListItem, parentListType, parentListLevel,
                                      parentImageUrl, parentLinkUrl, parentLinkText, parentVideoUrl,
                                      parentIsImage, parentIsLink, parentIsVideo, parentDirection);
                    }
                    continue;
                }

                // 确定当前格式
                boolean currentBold = parentBold || "strong".equals(tagName) || "b".equals(tagName);
                boolean currentItalic = parentItalic || "em".equals(tagName) || "i".equals(tagName);
                boolean currentUnderline = parentUnderline || "u".equals(tagName);
                boolean currentStrikethrough = parentStrikethrough || "s".equals(tagName) || "strike".equals(tagName);
                
                // 提取颜色（初始化为父元素的值，后续可能被覆盖）
                String currentColor = parentColor;
                String currentBackgroundColor = parentBackgroundColor;
                
                // 提取字体信息（初始化为父元素的值，后续可能被覆盖）
                String currentFontFamily = parentFontFamily;
                String currentFontSize = parentFontSize;
                String currentAlign = parentAlign;
                int currentIndent = parentIndent;
                String currentScript = parentScript;
                boolean currentIsHeader = parentIsHeader;
                int currentHeaderLevel = parentHeaderLevel;
                boolean currentIsCodeBlock = parentIsCodeBlock;
                boolean currentIsBlockquote = parentIsBlockquote;
                String currentDirection = parentDirection;
                
                // 检查文字方向（RTL）
                if (dirAttr != null && !dirAttr.isEmpty()) {
                    currentDirection = dirAttr.toLowerCase(); // rtl 或 ltr
                } else if (className != null && className.contains("ql-direction-rtl")) {
                    currentDirection = "rtl";
                } else if (className != null && className.contains("ql-direction-ltr")) {
                    currentDirection = "ltr";
                }
                
                // 检查标题标签
                if (tagName.matches("h[1-6]")) {
                    currentIsHeader = true;
                    currentHeaderLevel = Integer.parseInt(tagName.substring(1));
                }
                
                // 检查代码块
                if ("pre".equals(tagName) || "code".equals(tagName)) {
                    currentIsCodeBlock = true;
                }
                
                // 检查引用块
                if ("blockquote".equals(tagName)) {
                    currentIsBlockquote = true;
                }
                
                // 解析 Quill 格式类（主要从内联元素如 span 中提取）
                if (className != null && !className.isEmpty()) {
                    // 字体类 (ql-font-*) - 通常在 span 标签上
                    if (className.contains("ql-font-")) {
                        Pattern fontPattern = Pattern.compile("ql-font-([\\w-]+)");
                        Matcher fontMatcher = fontPattern.matcher(className);
                        if (fontMatcher.find()) {
                            String fontName = fontMatcher.group(1);
                            // 映射 Quill 字体名到实际字体名
                            currentFontFamily = mapQuillFontToRealFont(fontName);
                        }
                    }
                    
                    // 字体大小类 (ql-size-*) - 通常在 span 标签上
                    if (className.contains("ql-size-")) {
                        Pattern sizePattern = Pattern.compile("ql-size-([\\w]+)");
                        Matcher sizeMatcher = sizePattern.matcher(className);
                        if (sizeMatcher.find()) {
                            currentFontSize = sizeMatcher.group(1);
                        }
                    }
                    
                    // 对齐类 (ql-align-*) - 通常在 p 标签上，这里不处理（已在段落级别处理）
                    // 缩进类 (ql-indent-*) - 通常在 p 标签上，这里不处理（已在段落级别处理）
                    
                    // 上标/下标类 (ql-script-*)
                    if (className.contains("ql-script-")) {
                        Pattern scriptPattern = Pattern.compile("ql-script-([\\w]+)");
                        Matcher scriptMatcher = scriptPattern.matcher(className);
                        if (scriptMatcher.find()) {
                            currentScript = scriptMatcher.group(1);
                        }
                    }
                }
                
                // 解析 style 属性
                String style = el.attr("style");
                if (style != null && !style.isEmpty()) {
                    // 解析颜色（支持多种格式：rgb, rgba, #rrggbb, #rgb）
                    // 先尝试 rgb/rgba 格式（注意：rgb 和 rgba 格式可能有空格）
                    Pattern colorPattern = Pattern.compile("color:\\s*rgba?\\s*\\(\\s*(\\d+)\\s*,\\s*(\\d+)\\s*,\\s*(\\d+)(?:\\s*,\\s*[\\d.]+)?\\s*\\)", Pattern.CASE_INSENSITIVE);
                    Matcher colorMatcher = colorPattern.matcher(style);
                    if (colorMatcher.find()) {
                        try {
                            int r = Integer.parseInt(colorMatcher.group(1).trim());
                            int g = Integer.parseInt(colorMatcher.group(2).trim());
                            int b = Integer.parseInt(colorMatcher.group(3).trim());
                            currentColor = String.format("#%02X%02X%02X", r, g, b);
                        } catch (NumberFormatException e) {
                            // 解析失败，保持父元素的颜色
                        }
                    } else {
                        // 尝试十六进制格式（#rrggbb 或 #rgb）
                        Pattern hexColorPattern = Pattern.compile("color:\\s*#([0-9a-fA-F]{6}|[0-9a-fA-F]{3})", Pattern.CASE_INSENSITIVE);
                        Matcher hexColorMatcher = hexColorPattern.matcher(style);
                        if (hexColorMatcher.find()) {
                            String hex = hexColorMatcher.group(1);
                            if (hex.length() == 3) {
                                // 短格式 #rgb 转换为 #rrggbb
                                currentColor = "#" + hex.charAt(0) + hex.charAt(0) + 
                                             hex.charAt(1) + hex.charAt(1) + 
                                             hex.charAt(2) + hex.charAt(2);
                            } else {
                                currentColor = "#" + hex;
                            }
                        } else {
                            // 尝试命名颜色（如 red, blue 等，转换为十六进制）
                            Pattern namedColorPattern = Pattern.compile("color:\\s*([a-zA-Z]+)", Pattern.CASE_INSENSITIVE);
                            Matcher namedColorMatcher = namedColorPattern.matcher(style);
                            if (namedColorMatcher.find()) {
                                String colorName = namedColorMatcher.group(1).toLowerCase();
                                currentColor = mapColorNameToHex(colorName);
                            }
                        }
                    }
                    
                    // 解析背景色（支持多种格式：rgb, rgba, #rrggbb, #rgb）
                    // 先尝试 rgb/rgba 格式（注意：rgb 和 rgba 格式可能有空格）
                    Pattern bgColorPattern = Pattern.compile("background-color:\\s*rgba?\\s*\\(\\s*(\\d+)\\s*,\\s*(\\d+)\\s*,\\s*(\\d+)(?:\\s*,\\s*[\\d.]+)?\\s*\\)", Pattern.CASE_INSENSITIVE);
                    Matcher bgColorMatcher = bgColorPattern.matcher(style);
                    if (bgColorMatcher.find()) {
                        try {
                            int r = Integer.parseInt(bgColorMatcher.group(1).trim());
                            int g = Integer.parseInt(bgColorMatcher.group(2).trim());
                            int b = Integer.parseInt(bgColorMatcher.group(3).trim());
                            currentBackgroundColor = String.format("#%02X%02X%02X", r, g, b);
                        } catch (NumberFormatException e) {
                            // 解析失败，保持父元素的背景色
                        }
                    } else {
                        // 尝试十六进制格式（#rrggbb 或 #rgb）
                        Pattern hexBgColorPattern = Pattern.compile("background-color:\\s*#([0-9a-fA-F]{6}|[0-9a-fA-F]{3})", Pattern.CASE_INSENSITIVE);
                        Matcher hexBgColorMatcher = hexBgColorPattern.matcher(style);
                        if (hexBgColorMatcher.find()) {
                            String hex = hexBgColorMatcher.group(1);
                            if (hex.length() == 3) {
                                // 短格式 #rgb 转换为 #rrggbb
                                currentBackgroundColor = "#" + hex.charAt(0) + hex.charAt(0) + 
                                                        hex.charAt(1) + hex.charAt(1) + 
                                                        hex.charAt(2) + hex.charAt(2);
                            } else {
                                currentBackgroundColor = "#" + hex;
                            }
                        } else {
                            // 尝试命名颜色
                            Pattern namedBgColorPattern = Pattern.compile("background-color:\\s*([a-zA-Z]+)", Pattern.CASE_INSENSITIVE);
                            Matcher namedBgColorMatcher = namedBgColorPattern.matcher(style);
                            if (namedBgColorMatcher.find()) {
                                String colorName = namedBgColorMatcher.group(1).toLowerCase();
                                String mappedColor = mapColorNameToHex(colorName);
                                if (mappedColor != null) {
                                    currentBackgroundColor = mappedColor;
                                }
                            }
                        }
                    }
                    
                    // 解析字体族（从 style 属性，优先使用style中的值）
                    Pattern fontFamilyPattern = Pattern.compile("font-family:\\s*([^;]+)");
                    Matcher fontFamilyMatcher = fontFamilyPattern.matcher(style);
                    if (fontFamilyMatcher.find()) {
                        String fontFamily = fontFamilyMatcher.group(1).trim();
                        // 移除引号和多余空格
                        fontFamily = fontFamily.replaceAll("^['\"]|['\"]$", "").trim();
                        // 提取第一个字体名（可能有多个，用逗号分隔）
                        if (fontFamily.contains(",")) {
                            fontFamily = fontFamily.split(",")[0].trim();
                        }
                        // 如果style中有字体，优先使用style中的字体（覆盖类名中的字体）
                        if (fontFamily != null && !fontFamily.isEmpty()) {
                            currentFontFamily = fontFamily;
                        }
                    }
                    
                    // 解析字体大小（从 style 属性，优先使用style中的值）
                    Pattern fontSizePattern = Pattern.compile("font-size:\\s*([^;]+)");
                    Matcher fontSizeMatcher = fontSizePattern.matcher(style);
                    if (fontSizeMatcher.find()) {
                        String fontSize = fontSizeMatcher.group(1).trim();
                        // 尝试映射到 Quill 大小
                        if (fontSize.contains("0.75em") || fontSize.contains("9px") || fontSize.contains("10px") || fontSize.contains("0.75")) {
                            currentFontSize = "small";
                        } else if (fontSize.contains("1.5em") || fontSize.contains("18px") || fontSize.contains("20px") || fontSize.contains("1.5")) {
                            currentFontSize = "large";
                        } else if (fontSize.contains("2.5em") || fontSize.contains("30px") || fontSize.contains("36px") || fontSize.contains("2.5")) {
                            currentFontSize = "huge";
                        } else if (fontSize.contains("1em") || fontSize.contains("12px") || fontSize.contains("14px") || fontSize.contains("16px")) {
                            currentFontSize = "normal";
                        }
                    }
                    
                    // 解析文本对齐（从 style 属性，但通常已在段落级别处理）
                    // 这里只处理内联元素的对齐（如果有的话）
                }
                
                // 递归处理子元素
                processElement(el, result, currentBold, currentItalic, currentUnderline, 
                             currentStrikethrough, currentColor, currentBackgroundColor,
                             currentFontFamily, currentFontSize, currentAlign, currentIndent,
                             currentScript, currentIsHeader, currentHeaderLevel,
                             currentIsCodeBlock, currentIsBlockquote,
                             parentIsListItem, parentListType, parentListLevel,
                             parentImageUrl, parentLinkUrl, parentLinkText, parentVideoUrl,
                             parentIsImage, parentIsLink, parentIsVideo,
                             currentDirection);
            }
        }
    }
    
    /**
     * 设置 Word 段落格式（对齐和缩进）
     */
    /**
     * 将颜色名称映射为十六进制颜色值
     */
    private String mapColorNameToHex(String colorName) {
        if (colorName == null) return null;
        colorName = colorName.toLowerCase().trim();
        switch (colorName) {
            case "black": return "#000000";
            case "white": return "#FFFFFF";
            case "red": return "#FF0000";
            case "green": return "#00FF00";
            case "blue": return "#0000FF";
            case "yellow": return "#FFFF00";
            case "cyan": return "#00FFFF";
            case "magenta": return "#FF00FF";
            case "gray": case "grey": return "#808080";
            case "orange": return "#FFA500";
            case "pink": return "#FFC0CB";
            case "purple": return "#800080";
            case "brown": return "#A52A2A";
            default: return null;
        }
    }
    
    private void setParagraphFormat(XWPFParagraph para, String align, int indent) {
        // 设置对齐方式
        switch (align) {
            case "center":
                para.setAlignment(org.apache.poi.xwpf.usermodel.ParagraphAlignment.CENTER);
                break;
            case "right":
                para.setAlignment(org.apache.poi.xwpf.usermodel.ParagraphAlignment.RIGHT);
                break;
            case "justify":
                para.setAlignment(org.apache.poi.xwpf.usermodel.ParagraphAlignment.BOTH);
                break;
            default:
                para.setAlignment(org.apache.poi.xwpf.usermodel.ParagraphAlignment.LEFT);
        }
        // 设置缩进
        if (indent > 0) {
            para.setIndentationLeft(indent * 400); // 缩进单位：400 twips
        }
    }
    
    /**
     * 映射 Quill 字体名到实际字体名
     */
    private String mapQuillFontToRealFont(String quillFontName) {
        switch (quillFontName) {
            case "arial":
                return "Arial";
            case "comic-sans":
                return "Comic Sans MS";
            case "courier-new":
                return "Courier New";
            case "georgia":
                return "Georgia";
            case "helvetica":
                return "Helvetica";
            case "lucida":
                return "Lucida Console";
            case "times-new-roman":
                return "Times New Roman";
            case "verdana":
                return "Verdana";
            case "simsun":
                return "SimSun";
            case "simhei":
                return "SimHei";
            case "kaiti":
                return "KaiTi";
            case "fangsong":
                return "FangSong";
            case "microsoft-yahei":
                return "Microsoft YaHei";
            default:
                return quillFontName;
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
                    
                    Paragraph currentPara = null;
                    int currentAlignment = com.itextpdf.text.Element.ALIGN_LEFT;
                    float currentIndent = 0;
                    String currentAlign = "left";
                    int currentIndentLevel = 0;
                    
                    for (int i = 0; i < formattedTexts.size(); i++) {
                        FormattedText ft = formattedTexts.get(i);
                        if (ft.text == null) {
                            continue;
                        }

                        // 检查是否需要创建新段落（标题、代码块、引用块）
                        boolean needNewParagraph = ft.isHeader || ft.isCodeBlock || ft.isBlockquote;
                        if (needNewParagraph && currentPara != null && !currentPara.isEmpty()) {
                            // 设置段落对齐和缩进
                            currentPara.setAlignment(currentAlignment);
                            currentPara.setIndentationLeft(currentIndent);
                            pdfDoc.add(currentPara);
                            currentPara = null;
                        }
                        
                        // 处理换行符 - 结束当前段落
                        if (ft.text != null && "\n".equals(ft.text)) {
                            if (currentPara != null && !currentPara.isEmpty()) {
                                currentPara.setAlignment(currentAlignment);
                                currentPara.setIndentationLeft(currentIndent);
                                pdfDoc.add(currentPara);
                                currentPara = null;
                            }
                            // 重置格式信息，下一个文本将开始新段落
                            currentAlign = "left";
                            currentIndentLevel = 0;
                            currentAlignment = com.itextpdf.text.Element.ALIGN_LEFT;
                            currentIndent = 0;
                            continue;
                        }

                        // 处理图片（在检查文本之前，因为图片可能没有文本或文本为空）
                        if (ft.isImage && ft.imageUrl != null) {
                            try {
                                com.itextpdf.text.Image image = null;
                                // 处理 base64 图片
                                if (ft.imageUrl.startsWith("data:image")) {
                                    String base64Data = ft.imageUrl.substring(ft.imageUrl.indexOf(",") + 1);
                                    byte[] imageBytes = java.util.Base64.getDecoder().decode(base64Data);
                                    image = com.itextpdf.text.Image.getInstance(imageBytes);
                                } else if (ft.imageUrl.startsWith("http://") || ft.imageUrl.startsWith("https://")) {
                                    // 处理 URL 图片（需要网络连接）
                                    image = com.itextpdf.text.Image.getInstance(new java.net.URL(ft.imageUrl));
                                } else {
                                    // 处理本地文件路径
                                    image = com.itextpdf.text.Image.getInstance(ft.imageUrl);
                                }
                                
                                if (image != null) {
                                    image.scaleToFit(500, 500); // 限制图片大小
                                    image.setAlignment(com.itextpdf.text.Image.MIDDLE);
                                    Paragraph imagePara = new Paragraph();
                                    imagePara.add(image);
                                    imagePara.setAlignment(com.itextpdf.text.Element.ALIGN_CENTER);
                                    pdfDoc.add(imagePara);
                                }
                            } catch (Exception e) {
                                // 图片加载失败，添加文本说明
                                Font imageFont = new Font(baseFont, 10, Font.ITALIC);
                                Paragraph imagePara = new Paragraph("[图片: " + (ft.text != null ? ft.text : "加载失败") + "]", imageFont);
                                pdfDoc.add(imagePara);
                            }
                            continue;
                        }
                        
                        if (ft.text == null || ft.text.isEmpty()) {
                            continue;
                        }
                        
                        // 处理标题
                        if (ft.isHeader && ft.headerLevel > 0) {
                            int headerSize = 24 - (ft.headerLevel * 2); // H1=24, H2=22, H3=20...
                            
                            // 选择字体
                            BaseFont headerBaseFont = baseFont;
                            if (ft.fontFamily != null && !ft.fontFamily.isEmpty()) {
                                try {
                                    headerBaseFont = BaseFont.createFont(ft.fontFamily, BaseFont.IDENTITY_H, BaseFont.NOT_EMBEDDED);
                                } catch (Exception e) {
                                    headerBaseFont = baseFont;
                                }
                            }
                            
                            // 确定字体样式
                            int headerFontStyle = Font.BOLD;
                            if (ft.italic) {
                                headerFontStyle = Font.BOLDITALIC;
                            }
                            
                            BaseColor headerColor = ft.color != null ? hexToBaseColor(ft.color) : BaseColor.BLACK;
                            Font headerFont = new Font(headerBaseFont, headerSize, headerFontStyle, headerColor);
                            
                            Paragraph headerPara = new Paragraph();
                            Chunk headerChunk = new Chunk(ft.text, headerFont);
                            
                            // 应用格式
                            if (ft.underline) {
                                headerChunk.setUnderline(0.1f, -2f);
                            }
                            if (ft.strikethrough) {
                                headerChunk.setUnderline(0.1f, 6f);
                            }
                            if (ft.backgroundColor != null) {
                                BaseColor bgColor = hexToBaseColor(ft.backgroundColor);
                                headerChunk.setBackground(bgColor);
                            }
                            
                            headerPara.add(headerChunk);
                            
                            // 设置对齐方式
                            switch (ft.align) {
                                case "center":
                                    headerPara.setAlignment(com.itextpdf.text.Element.ALIGN_CENTER);
                                    break;
                                case "right":
                                    headerPara.setAlignment(com.itextpdf.text.Element.ALIGN_RIGHT);
                                    break;
                                case "justify":
                                    headerPara.setAlignment(com.itextpdf.text.Element.ALIGN_JUSTIFIED);
                                    break;
                                default:
                                    headerPara.setAlignment(com.itextpdf.text.Element.ALIGN_LEFT);
                            }
                            
                            // 处理 RTL 方向
                            if ("rtl".equals(ft.direction) && headerPara.getAlignment() == com.itextpdf.text.Element.ALIGN_LEFT) {
                                headerPara.setAlignment(com.itextpdf.text.Element.ALIGN_RIGHT);
                            }
                            
                            headerPara.setSpacingAfter(10);
                            headerPara.setIndentationLeft(ft.indent * 20);
                            pdfDoc.add(headerPara);
                            continue;
                        }
                        
                        // 处理代码块
                        if (ft.isCodeBlock) {
                            BaseFont courierFont = BaseFont.createFont(BaseFont.COURIER, BaseFont.IDENTITY_H, BaseFont.NOT_EMBEDDED);
                            
                            // 确定字体大小
                            float codeFontSize = 10;
                            switch (ft.fontSize) {
                                case "small":
                                    codeFontSize = 8;
                                    break;
                                case "large":
                                    codeFontSize = 14;
                                    break;
                                case "huge":
                                    codeFontSize = 20;
                                    break;
                                default:
                                    codeFontSize = 10;
                            }
                            
                            BaseColor codeColor = ft.color != null ? hexToBaseColor(ft.color) : BaseColor.BLACK;
                            Font codeFont = new Font(courierFont, codeFontSize, Font.NORMAL, codeColor);
                            
                            Paragraph codePara = new Paragraph();
                            Chunk codeChunk = new Chunk(ft.text, codeFont);
                            
                            // 应用背景色（代码块默认使用浅灰色背景）
                            BaseColor bgColor = ft.backgroundColor != null ? hexToBaseColor(ft.backgroundColor) : BaseColor.LIGHT_GRAY;
                            codeChunk.setBackground(bgColor);
                            
                            codePara.add(codeChunk);
                            
                            // 设置对齐方式
                            switch (ft.align) {
                                case "center":
                                    codePara.setAlignment(com.itextpdf.text.Element.ALIGN_CENTER);
                                    break;
                                case "right":
                                    codePara.setAlignment(com.itextpdf.text.Element.ALIGN_RIGHT);
                                    break;
                                default:
                                    codePara.setAlignment(com.itextpdf.text.Element.ALIGN_LEFT);
                            }
                            
                            // 处理 RTL 方向
                            if ("rtl".equals(ft.direction) && codePara.getAlignment() == com.itextpdf.text.Element.ALIGN_LEFT) {
                                codePara.setAlignment(com.itextpdf.text.Element.ALIGN_RIGHT);
                            }
                            
                            codePara.setIndentationLeft(20 + ft.indent * 20);
                            pdfDoc.add(codePara);
                            continue;
                        }
                        
                        // 处理引用块
                        if (ft.isBlockquote) {
                            // 确定字体大小
                            float quoteFontSize = 12;
                            switch (ft.fontSize) {
                                case "small":
                                    quoteFontSize = 9;
                                    break;
                                case "large":
                                    quoteFontSize = 18;
                                    break;
                                case "huge":
                                    quoteFontSize = 30;
                                    break;
                                default:
                                    quoteFontSize = 12;
                            }
                            
                            // 选择字体
                            BaseFont quoteBaseFont = baseFont;
                            if (ft.fontFamily != null && !ft.fontFamily.isEmpty()) {
                                try {
                                    quoteBaseFont = BaseFont.createFont(ft.fontFamily, BaseFont.IDENTITY_H, BaseFont.NOT_EMBEDDED);
                                } catch (Exception e) {
                                    quoteBaseFont = baseFont;
                                }
                            }
                            
                            // 确定字体样式
                            int quoteFontStyle = Font.ITALIC;
                            if (ft.bold) {
                                quoteFontStyle = Font.BOLDITALIC;
                            }
                            
                            BaseColor quoteColor = ft.color != null ? hexToBaseColor(ft.color) : BaseColor.BLACK;
                            Font quoteFont = new Font(quoteBaseFont, quoteFontSize, quoteFontStyle, quoteColor);
                            
                            Paragraph quotePara = new Paragraph();
                            Chunk quoteChunk = new Chunk(ft.text, quoteFont);
                            
                            // 应用格式
                            if (ft.underline) {
                                quoteChunk.setUnderline(0.1f, -2f);
                            }
                            if (ft.strikethrough) {
                                quoteChunk.setUnderline(0.1f, 6f);
                            }
                            if (ft.backgroundColor != null) {
                                BaseColor bgColor = hexToBaseColor(ft.backgroundColor);
                                quoteChunk.setBackground(bgColor);
                            }
                            
                            quotePara.add(quoteChunk);
                            
                            // 设置对齐方式
                            switch (ft.align) {
                                case "center":
                                    quotePara.setAlignment(com.itextpdf.text.Element.ALIGN_CENTER);
                                    break;
                                case "right":
                                    quotePara.setAlignment(com.itextpdf.text.Element.ALIGN_RIGHT);
                                    break;
                                case "justify":
                                    quotePara.setAlignment(com.itextpdf.text.Element.ALIGN_JUSTIFIED);
                                    break;
                                default:
                                    quotePara.setAlignment(com.itextpdf.text.Element.ALIGN_LEFT);
                            }
                            
                            // 处理 RTL 方向
                            if ("rtl".equals(ft.direction) && quotePara.getAlignment() == com.itextpdf.text.Element.ALIGN_LEFT) {
                                quotePara.setAlignment(com.itextpdf.text.Element.ALIGN_RIGHT);
                            }
                            
                            quotePara.setIndentationLeft(20 + ft.indent * 20);
                            quotePara.setIndentationRight(20);
                            pdfDoc.add(quotePara);
                            continue;
                        }
                        
                        // 处理视频
                        if (ft.isVideo && ft.videoUrl != null) {
                            Font videoFont = new Font(baseFont, 10, Font.ITALIC);
                            Paragraph videoPara = new Paragraph("[视频: " + ft.videoUrl + "]", videoFont);
                            videoPara.setAlignment(com.itextpdf.text.Element.ALIGN_LEFT);
                            pdfDoc.add(videoPara);
                            continue;
                        }
                        
                        // 处理列表项
                        if (ft.isListItem) {
                            // 计算列表序号（需要统计前面的列表项）
                            int listIndex = 1;
                            for (int j = i - 1; j >= 0; j--) {
                                FormattedText prevFt = formattedTexts.get(j);
                                if (prevFt.isListItem && prevFt.listType.equals(ft.listType) && prevFt.listLevel == ft.listLevel) {
                                    listIndex++;
                                } else if (!prevFt.isListItem || prevFt.listLevel != ft.listLevel) {
                                    break;
                                }
                            }
                            
                            String listPrefix = "";
                            if ("ordered".equals(ft.listType)) {
                                listPrefix = listIndex + ". "; // 有序列表序号
                            } else {
                                listPrefix = "• "; // 无序列表圆点
                            }
                            
                            // 创建列表项段落
                            Paragraph listPara = new Paragraph();
                            
                            // 确定字体大小
                            float listFontSize = 12;
                            switch (ft.fontSize) {
                                case "small":
                                    listFontSize = 9;
                                    break;
                                case "large":
                                    listFontSize = 18;
                                    break;
                                case "huge":
                                    listFontSize = 30;
                                    break;
                                default:
                                    listFontSize = 12;
                            }
                            
                            // 确定字体样式
                            int listFontStyle = Font.NORMAL;
                            if (ft.bold && ft.italic) {
                                listFontStyle = Font.BOLDITALIC;
                            } else if (ft.bold) {
                                listFontStyle = Font.BOLD;
                            } else if (ft.italic) {
                                listFontStyle = Font.ITALIC;
                            }
                            
                            // 选择字体
                            BaseFont listBaseFont = baseFont;
                            if (ft.fontFamily != null && !ft.fontFamily.isEmpty()) {
                                try {
                                    listBaseFont = BaseFont.createFont(ft.fontFamily, BaseFont.IDENTITY_H, BaseFont.NOT_EMBEDDED);
                                } catch (Exception e) {
                                    listBaseFont = baseFont;
                                }
                            }
                            
                            BaseColor listTextColor = ft.color != null ? hexToBaseColor(ft.color) : BaseColor.BLACK;
                            Font listFont = new Font(listBaseFont, listFontSize, listFontStyle, listTextColor);
                            
                            // 添加列表前缀
                            Chunk prefixChunk = new Chunk(listPrefix, listFont);
                            listPara.add(prefixChunk);
                            
                            // 添加列表项文本（应用所有格式）
                            Font itemFont = new Font(listBaseFont, listFontSize, listFontStyle, listTextColor);
                            Chunk itemChunk = new Chunk(ft.text, itemFont);
                            
                            // 应用上标/下标
                            if ("super".equals(ft.script)) {
                                itemChunk.setTextRise(6);
                                itemFont = new Font(listBaseFont, listFontSize * 0.7f, listFontStyle, listTextColor);
                                itemChunk = new Chunk(ft.text, itemFont);
                                itemChunk.setTextRise(6);
                            } else if ("sub".equals(ft.script)) {
                                itemChunk.setTextRise(-4);
                                itemFont = new Font(listBaseFont, listFontSize * 0.7f, listFontStyle, listTextColor);
                                itemChunk = new Chunk(ft.text, itemFont);
                                itemChunk.setTextRise(-4);
                            }
                            
                            if (ft.underline) itemChunk.setUnderline(0.1f, -2f);
                            if (ft.strikethrough) itemChunk.setUnderline(0.1f, 6f);
                            
                            // 应用背景色
                            if (ft.backgroundColor != null) {
                                BaseColor bgColor = hexToBaseColor(ft.backgroundColor);
                                itemChunk.setBackground(bgColor);
                            }
                            
                            listPara.add(itemChunk);
                            
                            // 设置缩进和对齐
                            listPara.setIndentationLeft(ft.listLevel * 30 + 20); // 列表缩进
                            // 处理 RTL 方向
                            if ("rtl".equals(ft.direction)) {
                                listPara.setAlignment(com.itextpdf.text.Element.ALIGN_RIGHT);
                            } else {
                                listPara.setAlignment(com.itextpdf.text.Element.ALIGN_LEFT);
                            }
                            
                            pdfDoc.add(listPara);
                            continue;
                        }
                        
                        // 检查是否需要创建新段落（格式变化：对齐或缩进）
                        boolean formatChanged = !ft.align.equals(currentAlign) || ft.indent != currentIndentLevel;
                        if (currentPara == null || formatChanged) {
                            // 如果格式变化，先保存当前段落
                            if (currentPara != null && !currentPara.isEmpty()) {
                                currentPara.setAlignment(currentAlignment);
                                currentPara.setIndentationLeft(currentIndent);
                                pdfDoc.add(currentPara);
                            }
                            // 创建新段落
                            currentPara = new Paragraph();
                            // 更新格式信息
                            currentAlign = ft.align;
                            currentIndentLevel = ft.indent;
                            // 设置对齐方式
                            switch (ft.align) {
                                case "center":
                                    currentAlignment = com.itextpdf.text.Element.ALIGN_CENTER;
                                    break;
                                case "right":
                                    currentAlignment = com.itextpdf.text.Element.ALIGN_RIGHT;
                                    break;
                                case "justify":
                                    currentAlignment = com.itextpdf.text.Element.ALIGN_JUSTIFIED;
                                    break;
                                default:
                                    currentAlignment = com.itextpdf.text.Element.ALIGN_LEFT;
                            }
                            currentIndent = ft.indent * 20; // 缩进单位：20点
                            
                            // 处理 RTL 方向（从右到左）
                            if ("rtl".equals(ft.direction)) {
                                // 对于 RTL 文本，调整对齐方式
                                if (currentAlignment == com.itextpdf.text.Element.ALIGN_LEFT) {
                                    currentAlignment = com.itextpdf.text.Element.ALIGN_RIGHT;
                                } else if (currentAlignment == com.itextpdf.text.Element.ALIGN_RIGHT) {
                                    currentAlignment = com.itextpdf.text.Element.ALIGN_LEFT;
                                }
                            }
                        }
                        
                        // 确定字体大小
                        float fontSize = 12;
                        switch (ft.fontSize) {
                            case "small":
                                fontSize = 9;
                                break;
                            case "large":
                                fontSize = 18;
                                break;
                            case "huge":
                                fontSize = 30;
                                break;
                            default:
                                fontSize = 12;
                        }
                        
                        // 确定字体样式
                        int fontStyle = Font.NORMAL;
                        if (ft.bold && ft.italic) {
                            fontStyle = Font.BOLDITALIC;
                        } else if (ft.bold) {
                            fontStyle = Font.BOLD;
                        } else if (ft.italic) {
                            fontStyle = Font.ITALIC;
                        }
                        
                        // 选择字体（如果有指定字体族，尝试使用；否则使用默认字体）
                        BaseFont textBaseFont = baseFont;
                        if (ft.fontFamily != null && !ft.fontFamily.isEmpty()) {
                            try {
                                // 尝试创建指定字体
                                textBaseFont = BaseFont.createFont(ft.fontFamily, BaseFont.IDENTITY_H, BaseFont.NOT_EMBEDDED);
                            } catch (Exception e) {
                                // 如果失败，使用默认字体
                                textBaseFont = baseFont;
                            }
                        }
                        
                        BaseColor textColor = ft.color != null ? hexToBaseColor(ft.color) : BaseColor.BLACK;
                        Font chunkFont = new Font(textBaseFont, fontSize, fontStyle, textColor);
                        
                        // 创建Chunk
                        Chunk chunk = new Chunk(ft.text, chunkFont);
                        
                        // 处理链接（在设置颜色之后，避免覆盖原有颜色）
                        if (ft.isLink && ft.linkUrl != null) {
                            // 在 PDF 中，链接使用 Anchor 或 Chunk 的 setAnchor
                            chunk.setAnchor(ft.linkUrl);
                            // 添加下划线表示链接（如果还没有下划线）
                            if (!ft.underline) {
                                chunk.setUnderline(0.1f, -2f);
                            }
                            // 如果链接没有指定颜色，使用蓝色；否则保持原有颜色
                            if (ft.color == null) {
                                chunkFont = new Font(textBaseFont, fontSize, fontStyle, BaseColor.BLUE);
                                chunk = new Chunk(ft.text, chunkFont);
                                chunk.setAnchor(ft.linkUrl);
                                if (!ft.underline) {
                                    chunk.setUnderline(0.1f, -2f);
                                }
                            }
                        }
                        
                        // 添加上标/下标
                        if ("super".equals(ft.script)) {
                            chunk.setTextRise(6); // 上标
                            chunkFont = new Font(textBaseFont, fontSize * 0.7f, fontStyle, textColor);
                            chunk = new Chunk(ft.text, chunkFont);
                            chunk.setTextRise(6);
                        } else if ("sub".equals(ft.script)) {
                            chunk.setTextRise(-4); // 下标
                            chunkFont = new Font(textBaseFont, fontSize * 0.7f, fontStyle, textColor);
                            chunk = new Chunk(ft.text, chunkFont);
                            chunk.setTextRise(-4);
                        }
                        
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
                        
                        // 处理 RTL 方向（从右到左）
                        // 注意：iText 对 RTL 的支持有限，主要通过调整对齐方式实现
                        // 如果需要更精确的 RTL 支持，可以使用 PdfChunk 的 setTextRise 或其他方法
                        
                        currentPara.add(chunk);
                    }
                    
                    // 添加最后一个段落
                    if (currentPara != null && !currentPara.isEmpty()) {
                        currentPara.setAlignment(currentAlignment);
                        currentPara.setIndentationLeft(currentIndent);
                        pdfDoc.add(currentPara);
                    } else if (formattedTexts.isEmpty()) {
                        Font contentFont = new Font(baseFont, 12, Font.NORMAL);
                        Paragraph emptyPara = new Paragraph("（文档内容为空）", contentFont);
                        pdfDoc.add(emptyPara);
                    }
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
                    
                    XWPFParagraph currentPara = null;
                    String currentAlign = "left";
                    int currentIndentLevel = 0;
                    
                    for (int i = 0; i < formattedTexts.size(); i++) {
                        FormattedText ft = formattedTexts.get(i);
                        if (ft.text == null) {
                            continue;
                        }
                        
                        // 检查是否需要创建新段落（标题、代码块、引用块）
                        boolean needNewParagraph = ft.isHeader || ft.isCodeBlock || ft.isBlockquote;
                        if (needNewParagraph && currentPara != null) {
                            // 设置段落格式
                            setParagraphFormat(currentPara, currentAlign, currentIndentLevel);
                            currentPara = null;
                        }
                        
                        // 处理换行符 - 结束当前段落
                        if (ft.text.equals("\n")) {
                            if (currentPara != null) {
                                setParagraphFormat(currentPara, currentAlign, currentIndentLevel);
                                currentPara = null;
                            }
                            // 重置格式信息
                            currentAlign = "left";
                            currentIndentLevel = 0;
                            continue;
                        }
                        
                        if (ft.text.isEmpty()) {
                            continue;
                        }
                        
                        // 处理标题
                        if (ft.isHeader && ft.headerLevel > 0) {
                            XWPFParagraph headerPara = doc.createParagraph();
                            XWPFRun headerRun = headerPara.createRun();
                            headerRun.setText(ft.text);
                            int headerSize = 24 - (ft.headerLevel * 2); // H1=24, H2=22, H3=20...
                            headerRun.setFontSize(headerSize);
                            headerRun.setBold(true);
                            headerRun.setItalic(ft.italic);
                            
                            // 设置格式
                            if (ft.underline) {
                                headerRun.setUnderline(UnderlinePatterns.SINGLE);
                            }
                            if (ft.strikethrough) {
                                headerRun.setStrikeThrough(true);
                            }
                            
                            // 设置颜色
                            if (ft.color != null) {
                                headerRun.setColor(hexToPoiColorString(ft.color));
                            }
                            
                            // 设置背景色
                            if (ft.backgroundColor != null) {
                                try {
                                    String bgColor = ft.backgroundColor.toUpperCase().replace("#", "");
                                    org.openxmlformats.schemas.wordprocessingml.x2006.main.STHighlightColor.Enum highlightColor = 
                                        org.openxmlformats.schemas.wordprocessingml.x2006.main.STHighlightColor.YELLOW;
                                    
                                    if (bgColor.equals("FFFF00") || bgColor.equals("FFEB3B") || bgColor.equals("FFC107")) {
                                        highlightColor = org.openxmlformats.schemas.wordprocessingml.x2006.main.STHighlightColor.YELLOW;
                                    } else if (bgColor.equals("FF0000") || bgColor.equals("F44336") || bgColor.equals("E91E63")) {
                                        highlightColor = org.openxmlformats.schemas.wordprocessingml.x2006.main.STHighlightColor.RED;
                                    } else if (bgColor.equals("00FF00") || bgColor.equals("4CAF50") || bgColor.equals("8BC34A")) {
                                        highlightColor = org.openxmlformats.schemas.wordprocessingml.x2006.main.STHighlightColor.GREEN;
                                    } else if (bgColor.equals("0000FF") || bgColor.equals("2196F3") || bgColor.equals("3F51B5")) {
                                        highlightColor = org.openxmlformats.schemas.wordprocessingml.x2006.main.STHighlightColor.BLUE;
                                    }
                                    
                                    headerRun.getCTR().addNewRPr().addNewHighlight().setVal(highlightColor);
                                } catch (Exception e) {
                                    // 背景色设置失败，忽略
                                }
                            }
                            
                            // 设置字体
                            if (ft.fontFamily != null && !ft.fontFamily.isEmpty()) {
                                headerRun.setFontFamily(ft.fontFamily, XWPFRun.FontCharRange.eastAsia);
                                headerRun.setFontFamily(ft.fontFamily, XWPFRun.FontCharRange.ascii);
                                headerRun.setFontFamily(ft.fontFamily, XWPFRun.FontCharRange.hAnsi);
                            } else {
                                headerRun.setFontFamily("宋体", XWPFRun.FontCharRange.eastAsia);
                                headerRun.setFontFamily("SimSun", XWPFRun.FontCharRange.ascii);
                                headerRun.setFontFamily("SimSun", XWPFRun.FontCharRange.hAnsi);
                            }
                            headerRun.setFontFamily("Times New Roman", XWPFRun.FontCharRange.cs);
                            
                            // 设置对齐方式
                            switch (ft.align) {
                                case "center":
                                    headerPara.setAlignment(org.apache.poi.xwpf.usermodel.ParagraphAlignment.CENTER);
                                    break;
                                case "right":
                                    headerPara.setAlignment(org.apache.poi.xwpf.usermodel.ParagraphAlignment.RIGHT);
                                    break;
                                case "justify":
                                    headerPara.setAlignment(org.apache.poi.xwpf.usermodel.ParagraphAlignment.BOTH);
                                    break;
                                default:
                                    headerPara.setAlignment(org.apache.poi.xwpf.usermodel.ParagraphAlignment.LEFT);
                            }
                            
                            // 处理 RTL 方向
                            if ("rtl".equals(ft.direction)) {
                                try {
                                    headerPara.getCTP().getPPr().addNewBidi().setVal(true);
                                } catch (Exception e) {
                                    // RTL 设置失败，忽略
                                }
                            }
                            
                            headerPara.setIndentationLeft(ft.indent * 400);
                            headerPara.setSpacingAfter(200); // 段落间距
                            continue;
                        }
                        
                        // 处理代码块
                        if (ft.isCodeBlock) {
                            XWPFParagraph codePara = doc.createParagraph();
                            XWPFRun codeRun = codePara.createRun();
                            codeRun.setText(ft.text);
                            
                            // 确定字体大小
                            int codeFontSize = 10;
                            switch (ft.fontSize) {
                                case "small":
                                    codeFontSize = 8;
                                    break;
                                case "large":
                                    codeFontSize = 14;
                                    break;
                                case "huge":
                                    codeFontSize = 20;
                                    break;
                                default:
                                    codeFontSize = 10;
                            }
                            codeRun.setFontSize(codeFontSize);
                            
                            // 设置格式
                            codeRun.setBold(ft.bold);
                            codeRun.setItalic(ft.italic);
                            if (ft.underline) {
                                codeRun.setUnderline(UnderlinePatterns.SINGLE);
                            }
                            if (ft.strikethrough) {
                                codeRun.setStrikeThrough(true);
                            }
                            
                            // 设置颜色
                            if (ft.color != null) {
                                codeRun.setColor(hexToPoiColorString(ft.color));
                            }
                            
                            // 设置背景色
                            if (ft.backgroundColor != null) {
                                try {
                                    String bgColor = ft.backgroundColor.toUpperCase().replace("#", "");
                                    org.openxmlformats.schemas.wordprocessingml.x2006.main.STHighlightColor.Enum highlightColor = 
                                        org.openxmlformats.schemas.wordprocessingml.x2006.main.STHighlightColor.YELLOW;
                                    
                                    if (bgColor.equals("FFFF00") || bgColor.equals("FFEB3B") || bgColor.equals("FFC107")) {
                                        highlightColor = org.openxmlformats.schemas.wordprocessingml.x2006.main.STHighlightColor.YELLOW;
                                    } else if (bgColor.equals("FF0000") || bgColor.equals("F44336") || bgColor.equals("E91E63")) {
                                        highlightColor = org.openxmlformats.schemas.wordprocessingml.x2006.main.STHighlightColor.RED;
                                    } else if (bgColor.equals("00FF00") || bgColor.equals("4CAF50") || bgColor.equals("8BC34A")) {
                                        highlightColor = org.openxmlformats.schemas.wordprocessingml.x2006.main.STHighlightColor.GREEN;
                                    } else if (bgColor.equals("0000FF") || bgColor.equals("2196F3") || bgColor.equals("3F51B5")) {
                                        highlightColor = org.openxmlformats.schemas.wordprocessingml.x2006.main.STHighlightColor.BLUE;
                                    }
                                    
                                    codeRun.getCTR().addNewRPr().addNewHighlight().setVal(highlightColor);
                                } catch (Exception e) {
                                    // 背景色设置失败，使用默认黄色
                                    try {
                                        codeRun.getCTR().addNewRPr().addNewHighlight().setVal(
                                            org.openxmlformats.schemas.wordprocessingml.x2006.main.STHighlightColor.YELLOW);
                                    } catch (Exception e2) {
                                        // 忽略
                                    }
                                }
                            } else {
                                // 代码块默认使用黄色背景（作为高亮）
                                try {
                                    codeRun.getCTR().addNewRPr().addNewHighlight().setVal(
                                        org.openxmlformats.schemas.wordprocessingml.x2006.main.STHighlightColor.YELLOW);
                                } catch (Exception e) {
                                    // 忽略
                                }
                            }
                            
                            codeRun.setFontFamily("Courier New", XWPFRun.FontCharRange.eastAsia);
                            codeRun.setFontFamily("Courier New", XWPFRun.FontCharRange.ascii);
                            codeRun.setFontFamily("Courier New", XWPFRun.FontCharRange.hAnsi);
                            
                            // 设置对齐方式
                            switch (ft.align) {
                                case "center":
                                    codePara.setAlignment(org.apache.poi.xwpf.usermodel.ParagraphAlignment.CENTER);
                                    break;
                                case "right":
                                    codePara.setAlignment(org.apache.poi.xwpf.usermodel.ParagraphAlignment.RIGHT);
                                    break;
                                default:
                                    codePara.setAlignment(org.apache.poi.xwpf.usermodel.ParagraphAlignment.LEFT);
                            }
                            
                            // 处理 RTL 方向
                            if ("rtl".equals(ft.direction)) {
                                try {
                                    codePara.getCTP().getPPr().addNewBidi().setVal(true);
                                } catch (Exception e) {
                                    // RTL 设置失败，忽略
                                }
                            }
                            
                            codePara.setIndentationLeft(400 + ft.indent * 400); // 缩进
                            continue;
                        }
                        
                        // 处理引用块
                        if (ft.isBlockquote) {
                            XWPFParagraph quotePara = doc.createParagraph();
                            XWPFRun quoteRun = quotePara.createRun();
                            quoteRun.setText(ft.text);
                            
                            // 确定字体大小
                            int quoteFontSize = 12;
                            switch (ft.fontSize) {
                                case "small":
                                    quoteFontSize = 9;
                                    break;
                                case "large":
                                    quoteFontSize = 18;
                                    break;
                                case "huge":
                                    quoteFontSize = 30;
                                    break;
                                default:
                                    quoteFontSize = 12;
                            }
                            quoteRun.setFontSize(quoteFontSize);
                            
                            // 设置格式
                            quoteRun.setBold(ft.bold);
                            quoteRun.setItalic(true); // 引用块默认斜体
                            if (ft.underline) {
                                quoteRun.setUnderline(UnderlinePatterns.SINGLE);
                            }
                            if (ft.strikethrough) {
                                quoteRun.setStrikeThrough(true);
                            }
                            
                            // 设置颜色
                            if (ft.color != null) {
                                quoteRun.setColor(hexToPoiColorString(ft.color));
                            }
                            
                            // 设置背景色
                            if (ft.backgroundColor != null) {
                                try {
                                    String bgColor = ft.backgroundColor.toUpperCase().replace("#", "");
                                    org.openxmlformats.schemas.wordprocessingml.x2006.main.STHighlightColor.Enum highlightColor = 
                                        org.openxmlformats.schemas.wordprocessingml.x2006.main.STHighlightColor.YELLOW;
                                    
                                    if (bgColor.equals("FFFF00") || bgColor.equals("FFEB3B") || bgColor.equals("FFC107")) {
                                        highlightColor = org.openxmlformats.schemas.wordprocessingml.x2006.main.STHighlightColor.YELLOW;
                                    } else if (bgColor.equals("FF0000") || bgColor.equals("F44336") || bgColor.equals("E91E63")) {
                                        highlightColor = org.openxmlformats.schemas.wordprocessingml.x2006.main.STHighlightColor.RED;
                                    } else if (bgColor.equals("00FF00") || bgColor.equals("4CAF50") || bgColor.equals("8BC34A")) {
                                        highlightColor = org.openxmlformats.schemas.wordprocessingml.x2006.main.STHighlightColor.GREEN;
                                    } else if (bgColor.equals("0000FF") || bgColor.equals("2196F3") || bgColor.equals("3F51B5")) {
                                        highlightColor = org.openxmlformats.schemas.wordprocessingml.x2006.main.STHighlightColor.BLUE;
                                    }
                                    
                                    quoteRun.getCTR().addNewRPr().addNewHighlight().setVal(highlightColor);
                                } catch (Exception e) {
                                    // 背景色设置失败，忽略
                                }
                            }
                            
                            // 设置字体
                            if (ft.fontFamily != null && !ft.fontFamily.isEmpty()) {
                                quoteRun.setFontFamily(ft.fontFamily, XWPFRun.FontCharRange.eastAsia);
                                quoteRun.setFontFamily(ft.fontFamily, XWPFRun.FontCharRange.ascii);
                                quoteRun.setFontFamily(ft.fontFamily, XWPFRun.FontCharRange.hAnsi);
                            } else {
                                quoteRun.setFontFamily("宋体", XWPFRun.FontCharRange.eastAsia);
                                quoteRun.setFontFamily("SimSun", XWPFRun.FontCharRange.ascii);
                            }
                            quoteRun.setFontFamily("Times New Roman", XWPFRun.FontCharRange.cs);
                            
                            // 设置对齐方式
                            switch (ft.align) {
                                case "center":
                                    quotePara.setAlignment(org.apache.poi.xwpf.usermodel.ParagraphAlignment.CENTER);
                                    break;
                                case "right":
                                    quotePara.setAlignment(org.apache.poi.xwpf.usermodel.ParagraphAlignment.RIGHT);
                                    break;
                                case "justify":
                                    quotePara.setAlignment(org.apache.poi.xwpf.usermodel.ParagraphAlignment.BOTH);
                                    break;
                                default:
                                    quotePara.setAlignment(org.apache.poi.xwpf.usermodel.ParagraphAlignment.LEFT);
                            }
                            
                            // 处理 RTL 方向
                            if ("rtl".equals(ft.direction)) {
                                try {
                                    quotePara.getCTP().getPPr().addNewBidi().setVal(true);
                                } catch (Exception e) {
                                    // RTL 设置失败，忽略
                                }
                            }
                            
                            quotePara.setIndentationLeft(400 + ft.indent * 400);
                            quotePara.setIndentationRight(400);
                            continue;
                        }
                        
                        // 处理视频
                        if (ft.isVideo && ft.videoUrl != null) {
                            XWPFParagraph videoPara = doc.createParagraph();
                            XWPFRun videoRun = videoPara.createRun();
                            videoRun.setText("[视频: " + ft.videoUrl + "]");
                            videoRun.setItalic(true);
                            videoRun.setFontSize(10);
                            continue;
                        }
                        
                        // 处理列表项
                        if (ft.isListItem) {
                            // 计算列表序号（需要统计前面的列表项）
                            int listIndex = 1;
                            for (int j = i - 1; j >= 0; j--) {
                                FormattedText prevFt = formattedTexts.get(j);
                                if (prevFt.isListItem && prevFt.listType.equals(ft.listType) && prevFt.listLevel == ft.listLevel) {
                                    listIndex++;
                                } else if (!prevFt.isListItem || prevFt.listLevel != ft.listLevel) {
                                    break;
                                }
                            }
                            
                            XWPFParagraph listPara = doc.createParagraph();
                            
                            // 确定字体大小
                            int listFontSize = 12;
                            switch (ft.fontSize) {
                                case "small":
                                    listFontSize = 9;
                                    break;
                                case "large":
                                    listFontSize = 18;
                                    break;
                                case "huge":
                                    listFontSize = 30;
                                    break;
                                default:
                                    listFontSize = 12;
                            }
                            
                            String listPrefix = "";
                            if ("ordered".equals(ft.listType)) {
                                listPrefix = listIndex + ". "; // 有序列表序号
                            } else {
                                listPrefix = "• "; // 无序列表圆点
                            }
                            
                            // 创建前缀 Run
                            XWPFRun prefixRun = listPara.createRun();
                            prefixRun.setText(listPrefix);
                            prefixRun.setFontSize(listFontSize);
                            
                            // 创建列表项文本 Run（应用所有格式）
                            XWPFRun listRun = listPara.createRun();
                            listRun.setText(ft.text);
                            listRun.setFontSize(listFontSize);
                            
                            // 设置格式
                            listRun.setBold(ft.bold);
                            listRun.setItalic(ft.italic);
                            if (ft.underline) {
                                listRun.setUnderline(UnderlinePatterns.SINGLE);
                            }
                            if (ft.strikethrough) {
                                listRun.setStrikeThrough(true);
                            }
                            
                            // 设置上标/下标
                            if ("super".equals(ft.script)) {
                                listRun.setTextPosition(6);
                                listRun.setFontSize((int)(listFontSize * 0.7));
                            } else if ("sub".equals(ft.script)) {
                                listRun.setTextPosition(-4);
                                listRun.setFontSize((int)(listFontSize * 0.7));
                            }
                            
                            // 设置颜色
                            if (ft.color != null) {
                                listRun.setColor(hexToPoiColorString(ft.color));
                            }
                            
                            // 设置背景色
                            if (ft.backgroundColor != null) {
                                try {
                                    String bgColor = ft.backgroundColor.toUpperCase().replace("#", "");
                                    org.openxmlformats.schemas.wordprocessingml.x2006.main.STHighlightColor.Enum highlightColor = 
                                        org.openxmlformats.schemas.wordprocessingml.x2006.main.STHighlightColor.YELLOW;
                                    
                                    if (bgColor.equals("FFFF00") || bgColor.equals("FFEB3B") || bgColor.equals("FFC107")) {
                                        highlightColor = org.openxmlformats.schemas.wordprocessingml.x2006.main.STHighlightColor.YELLOW;
                                    } else if (bgColor.equals("FF0000") || bgColor.equals("F44336") || bgColor.equals("E91E63")) {
                                        highlightColor = org.openxmlformats.schemas.wordprocessingml.x2006.main.STHighlightColor.RED;
                                    } else if (bgColor.equals("00FF00") || bgColor.equals("4CAF50") || bgColor.equals("8BC34A")) {
                                        highlightColor = org.openxmlformats.schemas.wordprocessingml.x2006.main.STHighlightColor.GREEN;
                                    } else if (bgColor.equals("0000FF") || bgColor.equals("2196F3") || bgColor.equals("3F51B5")) {
                                        highlightColor = org.openxmlformats.schemas.wordprocessingml.x2006.main.STHighlightColor.BLUE;
                                    }
                                    
                                    listRun.getCTR().addNewRPr().addNewHighlight().setVal(highlightColor);
                                } catch (Exception e) {
                                    // 背景色设置失败，忽略
                                }
                            }
                            
                            // 设置字体
                            if (ft.fontFamily != null && !ft.fontFamily.isEmpty()) {
                                listRun.setFontFamily(ft.fontFamily, XWPFRun.FontCharRange.eastAsia);
                                listRun.setFontFamily(ft.fontFamily, XWPFRun.FontCharRange.ascii);
                                listRun.setFontFamily(ft.fontFamily, XWPFRun.FontCharRange.hAnsi);
                            } else {
                                listRun.setFontFamily("宋体", XWPFRun.FontCharRange.eastAsia);
                                listRun.setFontFamily("SimSun", XWPFRun.FontCharRange.ascii);
                            }
                            listRun.setFontFamily("Times New Roman", XWPFRun.FontCharRange.cs);
                            
                            // 设置缩进
                            listPara.setIndentationLeft(ft.listLevel * 400 + 400); // 列表缩进
                            
                            // 处理 RTL 方向
                            if ("rtl".equals(ft.direction)) {
                                try {
                                    listPara.getCTP().getPPr().addNewBidi().setVal(true);
                                } catch (Exception e) {
                                    // RTL 设置失败，忽略
                                }
                            }
                            
                            continue;
                        }
                        
                        // 检查是否需要创建新段落（格式变化：对齐、缩进或方向）
                        boolean formatChanged = !ft.align.equals(currentAlign) || ft.indent != currentIndentLevel;
                        if (currentPara == null || formatChanged) {
                            // 如果格式变化，先保存当前段落
                            if (currentPara != null) {
                                setParagraphFormat(currentPara, currentAlign, currentIndentLevel);
                            }
                            // 创建新段落
                            currentPara = doc.createParagraph();
                            // 更新格式信息
                            currentAlign = ft.align;
                            currentIndentLevel = ft.indent;
                            
                            // 处理 RTL 方向（从右到左）
                            if ("rtl".equals(ft.direction)) {
                                // 设置段落方向为 RTL
                                try {
                                    currentPara.getCTP().getPPr().addNewBidi().setVal(true);
                                } catch (Exception e) {
                                    // RTL 设置失败，忽略
                                }
                            }
                        }
                        
                        // 为每个格式化的文本片段创建新的Run
                        XWPFRun run = currentPara.createRun();
                        run.setText(ft.text);
                        
                        // 确定字体大小
                        int fontSize = 12;
                        switch (ft.fontSize) {
                            case "small":
                                fontSize = 9;
                                break;
                            case "large":
                                fontSize = 18;
                                break;
                            case "huge":
                                fontSize = 30;
                                break;
                            default:
                                fontSize = 12;
                        }
                        run.setFontSize(fontSize);
                        
                        // 设置字体
                        if (ft.fontFamily != null && !ft.fontFamily.isEmpty()) {
                            run.setFontFamily(ft.fontFamily, XWPFRun.FontCharRange.eastAsia);
                            run.setFontFamily(ft.fontFamily, XWPFRun.FontCharRange.ascii);
                            run.setFontFamily(ft.fontFamily, XWPFRun.FontCharRange.hAnsi);
                        } else {
                        run.setFontFamily("宋体", XWPFRun.FontCharRange.eastAsia);
                        run.setFontFamily("SimSun", XWPFRun.FontCharRange.eastAsia);
                        run.setFontFamily("SimSun", XWPFRun.FontCharRange.ascii);
                        run.setFontFamily("SimSun", XWPFRun.FontCharRange.hAnsi);
                        }
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
                        
                        // 设置上标/下标
                        if ("super".equals(ft.script)) {
                            run.setTextPosition(6); // 上标，向上偏移
                            run.setFontSize((int)(fontSize * 0.7)); // 缩小字体
                        } else if ("sub".equals(ft.script)) {
                            run.setTextPosition(-4); // 下标，向下偏移
                            run.setFontSize((int)(fontSize * 0.7)); // 缩小字体
                        }
                        
                        // 设置颜色（在链接处理之前，确保颜色正确应用）
                        if (ft.color != null) {
                            String colorStr = hexToPoiColorString(ft.color);
                            run.setColor(colorStr);
                        }
                        
                        // 处理链接（在设置颜色之后，避免覆盖原有颜色）
                        if (ft.isLink && ft.linkUrl != null) {
                            // 在 Word 中设置超链接
                            // 如果链接没有指定颜色，使用蓝色；否则保持原有颜色
                            if (ft.color == null) {
                                run.setColor("0000FF"); // 蓝色
                            }
                            // 添加下划线表示链接（如果还没有下划线）
                            if (!ft.underline) {
                                run.setUnderline(UnderlinePatterns.SINGLE);
                            }
                            // 注意：POI 的超链接设置需要使用 CTR 对象，这里简化处理
                            // 实际应用中可以使用 XWPFHyperlinkRun 或手动设置超链接
                        }
                        
                        // 设置背景色（Word中使用高亮）
                        if (ft.backgroundColor != null) {
                            try {
                                // POI 的背景色设置需要使用 CTShd，这里使用高亮方式
                                // 将十六进制颜色转换为高亮颜色
                                String bgColor = ft.backgroundColor.toUpperCase().replace("#", "");
                                // POI 支持的高亮颜色有限，这里使用黄色作为默认值
                                // 如果需要更精确的颜色，需要使用 CTShd 对象
                                org.openxmlformats.schemas.wordprocessingml.x2006.main.STHighlightColor.Enum highlightColor = 
                                    org.openxmlformats.schemas.wordprocessingml.x2006.main.STHighlightColor.YELLOW;
                                
                                // 尝试根据颜色值选择合适的高亮颜色
                                if (bgColor.equals("FFFF00") || bgColor.equals("FFEB3B") || bgColor.equals("FFC107")) {
                                    highlightColor = org.openxmlformats.schemas.wordprocessingml.x2006.main.STHighlightColor.YELLOW;
                                } else if (bgColor.equals("FF0000") || bgColor.equals("F44336") || bgColor.equals("E91E63")) {
                                    highlightColor = org.openxmlformats.schemas.wordprocessingml.x2006.main.STHighlightColor.RED;
                                } else if (bgColor.equals("00FF00") || bgColor.equals("4CAF50") || bgColor.equals("8BC34A")) {
                                    highlightColor = org.openxmlformats.schemas.wordprocessingml.x2006.main.STHighlightColor.GREEN;
                                } else if (bgColor.equals("0000FF") || bgColor.equals("2196F3") || bgColor.equals("3F51B5")) {
                                    highlightColor = org.openxmlformats.schemas.wordprocessingml.x2006.main.STHighlightColor.BLUE;
                                } else if (bgColor.equals("FFFFFF") || bgColor.equals("F5F5F5")) {
                                    highlightColor = org.openxmlformats.schemas.wordprocessingml.x2006.main.STHighlightColor.NONE;
                                }
                                
                                run.getCTR().addNewRPr().addNewHighlight().setVal(highlightColor);
                            } catch (Exception e) {
                                // 背景色设置失败，忽略（POI 的背景色设置较复杂）
                                // 可以尝试使用其他方法，如设置段落背景色
                            }
                        }
                        
                        // 设置背景色（POI通过高亮实现，但API较复杂，这里简化处理）
                        // 注意：POI的背景色设置需要使用CTR对象，这里先不实现
                        // 如果需要背景色，可以考虑使用shading
                    }
                    
                    // 添加最后一个段落
                    if (currentPara != null) {
                        setParagraphFormat(currentPara, currentAlign, currentIndentLevel);
                    } else if (formattedTexts.isEmpty()) {
                        XWPFParagraph emptyPara = doc.createParagraph();
                        XWPFRun emptyRun = emptyPara.createRun();
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


