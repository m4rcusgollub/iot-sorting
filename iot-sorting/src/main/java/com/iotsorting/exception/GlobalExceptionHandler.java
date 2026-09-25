package com.iotsorting.exception;

import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.iotsorting.dto.ErroResponse;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Tratamento global de excecoes da API.
 *
 * <p>Garante que qualquer erro gere um JSON padronizado ({@link ErroResponse}),
 * sem expor stack trace ou detalhes internos ao cliente. Os erros do proprio
 * Spring MVC (404, 405, 415, ...) tambem sao convertidos para o mesmo formato
 * atraves de {@link ResponseEntityExceptionHandler}.</p>
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(RecursoNaoEncontradoException.class)
    public ResponseEntity<Object> tratarRecursoNaoEncontrado(RecursoNaoEncontradoException excecao) {
        return construir(HttpStatus.NOT_FOUND, excecao.getMessage(), Map.of());
    }

    @ExceptionHandler({DadosInvalidosException.class, ConstraintViolationException.class})
    public ResponseEntity<Object> tratarDadosInvalidos(RuntimeException excecao) {
        return construir(HttpStatus.BAD_REQUEST, excecao.getMessage(), Map.of());
    }

    @ExceptionHandler(ConflitoException.class)
    public ResponseEntity<Object> tratarConflito(ConflitoException excecao) {
        return construir(HttpStatus.CONFLICT, excecao.getMessage(), Map.of());
    }

    /** Rede de seguranca para violacoes de restricao do banco (ex.: codigo duplicado). */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Object> tratarViolacaoDeIntegridade(DataIntegrityViolationException excecao) {
        log.warn("Violação de integridade no banco de dados: {}", excecao.getMostSpecificCause().getMessage());
        return construir(HttpStatus.CONFLICT,
                "Não foi possível concluir a operação: os dados informados violam uma regra do banco de dados.",
                Map.of());
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException excecao,
                                                                  HttpHeaders headers,
                                                                  HttpStatusCode status,
                                                                  WebRequest request) {
        Map<String, String> campos = new LinkedHashMap<>();
        excecao.getBindingResult().getFieldErrors()
                .forEach(erro -> campos.putIfAbsent(erro.getField(), erro.getDefaultMessage()));
        excecao.getBindingResult().getGlobalErrors()
                .forEach(erro -> campos.putIfAbsent(erro.getObjectName(), erro.getDefaultMessage()));

        String mensagem = campos.values().stream()
                .findFirst()
                .orElse("Dados inválidos na requisição");

        return construir(HttpStatus.BAD_REQUEST, mensagem, campos);
    }

    /** Corpo JSON ausente, malformado ou com valor invalido para enum/data. */
    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException excecao,
                                                                  HttpHeaders headers,
                                                                  HttpStatusCode status,
                                                                  WebRequest request) {
        if (excecao.getCause() instanceof InvalidFormatException formatoInvalido
                && formatoInvalido.getTargetType() != null
                && formatoInvalido.getTargetType().isEnum()) {
            String campo = formatoInvalido.getPath().isEmpty()
                    ? "valor"
                    : formatoInvalido.getPath().get(0).getFieldName();
            String aceitos = Arrays.stream(formatoInvalido.getTargetType().getEnumConstants())
                    .map(String::valueOf)
                    .collect(Collectors.joining(", "));
            return construir(HttpStatus.BAD_REQUEST,
                    "Valor inválido para o campo '" + campo + "'. Valores aceitos: " + aceitos,
                    Map.of());
        }

        log.warn("Corpo da requisição inválido: {}", excecao.getMessage());
        return construir(HttpStatus.BAD_REQUEST,
                "Corpo da requisição inválido. Verifique se o JSON está correto e se os campos possuem os tipos esperados.",
                Map.of());
    }

    /** Converte os erros tratados pelo Spring MVC (404, 405, 415, ...) para o formato padronizado. */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception excecao,
                                                             Object body,
                                                             HttpHeaders headers,
                                                             HttpStatusCode status,
                                                             WebRequest request) {
        return construir(HttpStatusCode.valueOf(status.value()), mensagemPadrao(status), Map.of(), headers);
    }

    /** Qualquer erro nao previsto: registra no log e devolve 500 sem detalhes tecnicos. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> tratarErroInesperado(Exception excecao) {
        log.error("Erro inesperado ao processar a requisição", excecao);
        return construir(HttpStatus.INTERNAL_SERVER_ERROR,
                "Erro interno do servidor. Consulte os logs da aplicação para mais detalhes.",
                Map.of());
    }

    private String mensagemPadrao(HttpStatusCode status) {
        return switch (status.value()) {
            case 400 -> "Requisição inválida";
            case 404 -> "Recurso não encontrado";
            case 405 -> "Método HTTP não permitido para este recurso";
            case 406 -> "Formato de resposta não suportado pelo cliente";
            case 415 -> "Tipo de conteúdo (Content-Type) não suportado. Utilize application/json";
            case 500 -> "Erro interno do servidor";
            default -> "Não foi possível processar a requisição (HTTP " + status.value() + ")";
        };
    }

    private ResponseEntity<Object> construir(HttpStatusCode status, String mensagem, Map<String, String> campos) {
        return construir(status, mensagem, campos, new HttpHeaders());
    }

    private ResponseEntity<Object> construir(HttpStatusCode status,
                                             String mensagem,
                                             Map<String, String> campos,
                                             HttpHeaders headers) {
        ErroResponse corpo = ErroResponse.de(status.value(), mensagem, campos);
        return new ResponseEntity<>(corpo, headers, status);
    }

}
