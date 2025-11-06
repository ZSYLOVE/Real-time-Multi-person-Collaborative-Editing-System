package org.zsy.bysj.service;

import org.zsy.bysj.model.Document;
import java.io.ByteArrayOutputStream;

/**
 * 文档导出服务接口
 */
public interface ExportService {
    
    /**
     * 导出为PDF
     */
    ByteArrayOutputStream exportToPdf(Document document);
    
    /**
     * 导出为Word
     */
    ByteArrayOutputStream exportToWord(Document document);
    
    /**
     * 导出为Markdown
     */
    String exportToMarkdown(Document document);
}

