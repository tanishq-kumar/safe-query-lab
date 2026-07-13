package dev.querylab.api;

import dev.querylab.common.search.InvalidCriteriaException;
import dev.querylab.common.search.UnknownSortFieldException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.validation.BindException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/** Every rejected input becomes an RFC-7807 problem, not a stack trace. */
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler({UnknownSortFieldException.class, InvalidCriteriaException.class,
            UnknownEngineException.class})
    public ProblemDetail badRequest(RuntimeException e) {
        return problem(e.getMessage());
    }

    @ExceptionHandler({BindException.class, MethodArgumentTypeMismatchException.class})
    public ProblemDetail bindingFailure(Exception e) {
        // e.g. ?status=NOPE or an unparseable timestamp — surface a terse reason,
        // not the framework's multi-line binding report.
        return problem("Invalid request parameter: " + rootMessage(e));
    }

    private static ProblemDetail problem(String detail) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problem.setTitle("Invalid search request");
        problem.setDetail(detail);
        return problem;
    }

    private static String rootMessage(Throwable e) {
        Throwable root = e;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        return root.getMessage();
    }
}
