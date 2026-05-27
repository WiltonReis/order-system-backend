package com.ordersystem.exception;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.apache.catalina.connector.ClientAbortException;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MultipartException;

import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    public ProblemDetail handleResourceNotFound(ResourceNotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, "Recurso não encontrado", ex.getMessage());
    }

    @ExceptionHandler(BusinessException.class)
    public ProblemDetail handleBusiness(BusinessException ex) {
        return problem(HttpStatus.UNPROCESSABLE_ENTITY, "Regra de negócio violada", ex.getMessage());
    }

    @ExceptionHandler(ConflictException.class)
    public ProblemDetail handleConflict(ConflictException ex) {
        ProblemDetail pd = problem(HttpStatus.CONFLICT, "Conflito de dados", ex.getMessage());
        if (ex.getField() != null) {
            pd.setProperty("errors", Map.of(ex.getField(), ex.getMessage()));
        }
        return pd;
    }

    @ExceptionHandler(ForbiddenOperationException.class)
    public ProblemDetail handleForbiddenOperation(ForbiddenOperationException ex) {
        return problem(HttpStatus.FORBIDDEN, "Operação não permitida", ex.getMessage());
    }

    @ExceptionHandler(InvalidFileException.class)
    public ProblemDetail handleInvalidFile(InvalidFileException ex) {
        return problem(HttpStatus.BAD_REQUEST, "Arquivo inválido", ex.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleIllegalArgument(IllegalArgumentException ex) {
        log.warn("Argumento inválido: {}", ex.getMessage());
        return problem(HttpStatus.BAD_REQUEST, "Requisição inválida",
                "Algum dos dados enviados está inválido. Verifique e tente novamente.");
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ProblemDetail handleAccessDenied(AccessDeniedException ex) {
        return problem(HttpStatus.FORBIDDEN, "Acesso negado",
                "Você não tem permissão para executar esta ação.");
    }

    // hideUserNotFoundExceptions=false permite diferenciar email errado de senha errada (deliberado)
    @ExceptionHandler(UsernameNotFoundException.class)
    public ProblemDetail handleUsernameNotFound(UsernameNotFoundException ex) {
        ProblemDetail pd = problem(HttpStatus.UNAUTHORIZED, "Credenciais inválidas",
                "Usuário não encontrado. Verifique o e-mail e tente novamente.");
        pd.setProperty("errors", Map.of("email", "Usuário não encontrado"));
        return pd;
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ProblemDetail handleBadCredentials(BadCredentialsException ex) {
        ProblemDetail pd = problem(HttpStatus.UNAUTHORIZED, "Credenciais inválidas",
                "Senha incorreta. Verifique sua senha e tente novamente.");
        pd.setProperty("errors", Map.of("password", "Senha incorreta"));
        return pd;
    }

    @ExceptionHandler(TooManyRequestsException.class)
    public ProblemDetail handleTooManyRequests(TooManyRequestsException ex) {
        return problem(HttpStatus.TOO_MANY_REQUESTS, "Limite de requisições excedido", ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.toMap(
                        FieldError::getField,
                        fe -> fe.getDefaultMessage() == null ? "" : fe.getDefaultMessage(),
                        (a, b) -> a,
                        LinkedHashMap::new));
        String detail = String.join(", ", fieldErrors.values());
        ProblemDetail pd = problem(HttpStatus.BAD_REQUEST, "Erro de validação", detail);
        pd.setProperty("errors", fieldErrors);
        return pd;
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ProblemDetail handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        log.warn("Tipo de parâmetro inválido em '{}': {}", ex.getName(), ex.getMessage());
        return problem(HttpStatus.BAD_REQUEST, "Parâmetro inválido",
                "Um dos campos enviados está com formato inválido. Verifique e tente novamente.");
    }

    @ExceptionHandler(DateTimeParseException.class)
    public ProblemDetail handleDateTimeParse(DateTimeParseException ex) {
        return problem(HttpStatus.BAD_REQUEST, "Data inválida",
                "A data informada está em formato inválido.");
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail handleDataIntegrity(DataIntegrityViolationException ex) {
        log.warn("Violação de integridade de dados: {}", ex.getMostSpecificCause().getMessage());
        return problem(HttpStatus.CONFLICT, "Conflito de dados",
                "Não foi possível concluir a operação por conflito com dados já existentes.");
    }

    @ExceptionHandler(MultipartException.class)
    public ProblemDetail handleMultipart(MultipartException ex) {
        log.warn("Erro em upload multipart: {}", ex.getMessage());
        return problem(HttpStatus.PAYLOAD_TOO_LARGE, "Upload inválido",
                "O arquivo enviado está muito grande ou corrompido. Envie um arquivo menor ou em outro formato.");
    }

    // Cliente desconectou (Prometheus scraping, SSE, downloads) — sem body, sem stacktrace
    @ExceptionHandler({ClientAbortException.class, AsyncRequestNotUsableException.class})
    public void handleClientAbort(Exception ex, HttpServletRequest request) {
        log.debug("Conexão encerrada pelo cliente [{}]: {} uri={}",
                ex.getClass().getSimpleName(), ex.getMessage(), request.getRequestURI());
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleGeneric(Exception ex) {
        if (isBrokenPipe(ex)) {
            log.warn("Broken pipe — conexão encerrada: {}", ex.getMessage());
            return problem(HttpStatus.SERVICE_UNAVAILABLE, "Conexão encerrada", "");
        }
        log.error("Exceção não tratada: {}", ex.getMessage(), ex);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Erro interno",
                "Ocorreu um problema inesperado. Tente novamente em instantes.");
    }

    private boolean isBrokenPipe(Throwable ex) {
        Throwable t = ex;
        while (t != null) {
            if (t.getMessage() != null && t.getMessage().toLowerCase().contains("broken pipe")) {
                return true;
            }
            t = t.getCause();
        }
        return false;
    }

    private ProblemDetail problem(HttpStatus status, String title, String detail) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, detail);
        pd.setTitle(title);
        String requestId = MDC.get("requestId");
        if (requestId != null) {
            pd.setProperty("requestId", requestId);
        }
        return pd;
    }
}
