package com.rookie.framework.handle;

import cn.hutool.core.exceptions.ExceptionUtil;
import com.rookie.common.enums.ErrorSourceType;
import com.rookie.common.enums.ResultEnum;
import com.rookie.common.exception.ServiceException;
import com.rookie.common.pojo.Result;
import com.rookie.common.util.ServletUtil;
import com.rookie.common.pojo.entity.SysErrorLog;
import com.rookie.framework.security.pojo.UserInfo;
import com.rookie.framework.service.ErrorLogService;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AccountExpiredException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.CredentialsExpiredException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Date;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** 切面在失败路径同步写操作日志后，把 oper_id 塞进此 request attribute，供此处关联错误日志 */
    public static final String OPER_LOG_ID_ATTR = "operLogId";

    @Autowired
    private ErrorLogService errorLogService;

    @ExceptionHandler(ServiceException.class)
    public Result serviceExceptionHandle(ServiceException e) {
        if (e.getErrorMsg() != null && !e.getErrorMsg().isEmpty()) {
            log.error("业务异常详情 >>> {}", e.getErrorMsg());
        }
        log.error("系统发生了一个业务错误，请求路径:{} >>> {}", ServletUtil.getRequestCompleteURL(), e.getMessage());
        recordErrorLog(e, ServletUtil.getRequestCompleteURL());
        return Result.error(e.getCode() != null ? e.getCode() : 500, e.getMessage());
    }

    /**
     * 认证异常处理：登录阶段 {@link org.springframework.security.authentication.AuthenticationManager#authenticate}
     * 抛出的 AuthenticationException（BadCredentialsException / LockedException / DisabledException / 等）原先被
     * {@link #exceptionHandle(Exception)} 兜底，统一返回 "请求失败"，前端无法看到真实原因。
     * 这里按具体子类映射成可读消息，沿用业务错误码（500）——不返回 401，避免前端 http 拦截器把它当"登录态失效"
     * 触发 redirectToLogin 重载登录页、清空已输入的账号密码。
     * 日志（log + recordErrorLog）保持与通用分支一致，确保错误日志仍正常落库。
     */
    @ExceptionHandler(AuthenticationException.class)
    public Result authenticationExceptionHandle(AuthenticationException e) {
        String msg = resolveAuthMsg(e);
        log.error("认证失败，请求路径:{} >>> {} | {}", ServletUtil.getRequestCompleteURL(), e.getClass().getSimpleName(), msg);
        printExceptionLocation(e);
        recordErrorLog(e, ServletUtil.getRequestCompleteURL());
        return Result.error(ResultEnum.COMMON_ERROR.getCode(), msg);
    }

    private String resolveAuthMsg(AuthenticationException e) {
        // 按子类给出面向用户的具体原因；其它 AuthenticationException 退回通用"用户名或密码错误"，避免泄露内部细节
        if (e instanceof BadCredentialsException) {
            return "用户名或密码错误";
        }
        if (e instanceof LockedException) {
            return "账号已锁定，请联系管理员";
        }
        if (e instanceof DisabledException) {
            return "账号已禁用，请联系管理员";
        }
        if (e instanceof AccountExpiredException) {
            return "账号已过期，请联系管理员";
        }
        if (e instanceof CredentialsExpiredException) {
            return "密码已过期，请重置密码";
        }
        return "用户名或密码错误";
    }

    @ExceptionHandler(Exception.class)
    public Result exceptionHandle(Exception e) {
        log.error("系统发生了一个未知错误，请求路径:{} >>> {}", ServletUtil.getRequestCompleteURL(), e.getMessage());
        printExceptionLocation(e);
        recordErrorLog(e, ServletUtil.getRequestCompleteURL());
        return Result.error();
    }

    private void printExceptionLocation(Exception e) {
        StackTraceElement[] stackTrace = e.getStackTrace();
        StackTraceElement firstFrame = stackTrace[0];
        log.error("异常的位置 >>> {}:{}", firstFrame.getFileName(), firstFrame.getLineNumber());
    }

    /**
     * 记录错误日志（REQUEST 来源）
     * - title：请求路径简述
     * - oper_name：从 SecurityContext 取，无登录态置空
     * - oper_log_id：从 request attribute 取（仅接口带 @Log 且失败时由切面塞入），无则空
     * - 异常三件套：类型/消息/完整堆栈
     * 异步落库，不阻塞响应
     */
    private void recordErrorLog(Throwable e, String title) {
        try {
            SysErrorLog errorLog = new SysErrorLog();
            errorLog.setSourceType(ErrorSourceType.REQUEST.getCode());
            errorLog.setTitle(title);
            errorLog.setExceptionType(e.getClass().getName());
            errorLog.setExceptionMsg(ExceptionUtil.getMessage(e));
            errorLog.setExceptionStack(ExceptionUtil.stacktraceToString(e));
            errorLog.setErrorTime(new Date());

            // 操作人：有登录态才填，匿名/未认证置空
            String operName = currentOperName();
            if (operName != null && !operName.isEmpty()) {
                errorLog.setOperName(operName);
            }

            // 关联操作日志主键：切面失败路径塞入的 request attribute
            HttpServletRequest request = ServletUtil.getRequest();
            Object operLogId = request.getAttribute(OPER_LOG_ID_ATTR);
            if (operLogId instanceof Long id) {
                errorLog.setOperLogId(id);
            }

            errorLogService.saveAsync(errorLog);
        } catch (Exception ignore) {
            // 日志采集本身绝不能再抛异常，否则会干扰正常响应
        }
    }

    /**
     * 从 SecurityContext 取当前操作人用户名，无登录态或异常时返回 null
     */
    private String currentOperName() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth == null || !auth.isAuthenticated()) {
                return null;
            }
            Object principal = auth.getPrincipal();
            if (principal instanceof UserInfo userInfo) {
                return userInfo.getUsername();
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }
}
