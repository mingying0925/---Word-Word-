package com.skillbridge.controller;

import com.skillbridge.service.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSource;
import org.springframework.ui.Model;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.http.converter.HttpMessageNotReadableException;

import java.util.stream.Collectors;

/**
 * 全局异常处理。将业务异常与未捕获异常导向 error 页面。
 * <p>
 * 安全策略：
 * <ul>
 *   <li>业务异常 {@link BusinessException}：展示用户友好消息（业务语义明确），HTTP 400。</li>
 *   <li>Bean Validation 校验异常：展示字段级错误提示，HTTP 400。</li>
 *   <li>系统异常：统一展示"系统繁忙，请稍后重试"，详细堆栈仅写入日志，HTTP 500。</li>
 * </ul>
 */
@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final MessageSource messageSource;

    public GlobalExceptionHandler(MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    @ExceptionHandler(BusinessException.class)
    public String handleBusiness(BusinessException e, Model model, HttpServletResponse response) {
        model.addAttribute("error", e.getMessage());
        response.setStatus(e.getStatusCode());
        return "common/error";
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public String handleValidation(MethodArgumentNotValidException e, HttpServletRequest request, Model model, HttpServletResponse response) {
        String errors = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("；"));
        model.addAttribute("error", messageSource.getMessage("error.validation.failed", new Object[]{errors}, request.getLocale()));
        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        return "common/error";
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public String handleConstraintViolation(ConstraintViolationException e, HttpServletRequest request, Model model, HttpServletResponse response) {
        String errors = e.getConstraintViolations().stream()
                .map(v -> v.getMessage())
                .collect(Collectors.joining("；"));
        model.addAttribute("error", messageSource.getMessage("error.validation.failed", new Object[]{errors}, request.getLocale()));
        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        return "common/error";
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public String handleMissingParam(MissingServletRequestParameterException e, HttpServletRequest request, Model model, HttpServletResponse response) {
        model.addAttribute("error", messageSource.getMessage("error.missing.param", new Object[]{e.getParameterName()}, request.getLocale()));
        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        return "common/error";
    }

    @ExceptionHandler(NoHandlerFoundException.class)
    public String handleNotFound(NoHandlerFoundException e, HttpServletRequest request, Model model, HttpServletResponse response) {
        model.addAttribute("error", messageSource.getMessage("error.resource.notfound", null, request.getLocale()));
        response.setStatus(HttpServletResponse.SC_NOT_FOUND);
        return "common/error";
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public String handleMethodNotAllowed(HttpRequestMethodNotSupportedException e, HttpServletRequest request, Model model, HttpServletResponse response) {
        model.addAttribute("error", messageSource.getMessage("error.method.notallowed", new Object[]{e.getMethod()}, request.getLocale()));
        response.setStatus(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
        return "common/error";
    }

    /**
     * 上传文件超过大小限制。
     * Spring 在 multipart 解析阶段抛出，无法被常规 @ExceptionHandler 捕获，
     * 但 Tomcat 的 MultipartConfig 会将其包装为该异常，此处兜底处理。
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public String handleMaxUploadSize(MaxUploadSizeExceededException e, HttpServletRequest request, Model model, HttpServletResponse response) {
        model.addAttribute("error", messageSource.getMessage("error.upload.tooLarge", null, request.getLocale()));
        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        return "common/error";
    }

    /**
     * 路径变量类型不匹配（如 /teacher/activity/abc/submissions 中 abc 无法转为 Long）。
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public String handleTypeMismatch(MethodArgumentTypeMismatchException e, HttpServletRequest request, Model model, HttpServletResponse response) {
        model.addAttribute("error", messageSource.getMessage("error.param.invalid", new Object[]{e.getName()}, request.getLocale()));
        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        return "common/error";
    }

    /**
     * 请求体 JSON 格式错误或不可读。
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public String handleNotReadable(HttpMessageNotReadableException e, HttpServletRequest request, Model model, HttpServletResponse response) {
        model.addAttribute("error", messageSource.getMessage("error.request.body.invalid", null, request.getLocale()));
        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        return "common/error";
    }

    @ExceptionHandler(Exception.class)
    public String handleAll(Exception e, HttpServletRequest request, Model model, HttpServletResponse response) {
        log.error("未捕获的系统异常", e);
        model.addAttribute("error", messageSource.getMessage("error.system.busy", null, request.getLocale()));
        response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        return "common/error";
    }
}
