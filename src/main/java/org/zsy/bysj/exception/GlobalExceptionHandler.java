package org.zsy.bysj.exception;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageNotWritableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.zsy.bysj.dto.Result;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * 全局异常处理器
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * 处理序列化异常（LocalDateTime 序列化错误）
     */
    @ExceptionHandler(HttpMessageNotWritableException.class)
    public void handleHttpMessageNotWritableException(HttpMessageNotWritableException e, HttpServletResponse response) throws IOException {
        e.printStackTrace(); // 打印堆栈跟踪以便调试
        
        // 获取原始异常信息
        String message = e.getMessage();
        if (e.getCause() != null) {
            message = e.getCause().getMessage();
        }
        
        Result<Object> result = Result.error("序列化错误: " + message);
        response.setStatus(HttpStatus.INTERNAL_SERVER_ERROR.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        
        // 使用我们配置的 ObjectMapper 手动序列化
        objectMapper.writeValue(response.getWriter(), result);
    }

    @ExceptionHandler(RuntimeException.class)
    public void handleRuntimeException(RuntimeException e, HttpServletResponse response) throws IOException {
        e.printStackTrace(); // 打印堆栈跟踪以便调试
        
        Result<Object> result = Result.error(e.getMessage());
        response.setStatus(HttpStatus.BAD_REQUEST.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        
        // 使用我们配置的 ObjectMapper 手动序列化
        objectMapper.writeValue(response.getWriter(), result);
    }

    @ExceptionHandler(Exception.class)
    public void handleException(Exception e, HttpServletResponse response) throws IOException {
        e.printStackTrace(); // 打印堆栈跟踪以便调试
        
        Result<Object> result = Result.error("服务器内部错误: " + e.getMessage());
        response.setStatus(HttpStatus.INTERNAL_SERVER_ERROR.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        
        // 使用我们配置的 ObjectMapper 手动序列化
        objectMapper.writeValue(response.getWriter(), result);
    }
}

