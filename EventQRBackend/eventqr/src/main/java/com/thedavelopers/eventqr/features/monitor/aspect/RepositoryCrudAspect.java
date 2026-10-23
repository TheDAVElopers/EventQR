package com.thedavelopers.eventqr.features.monitor.aspect;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.core.annotation.Order;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.core.support.AbstractRepositoryMetadata;
import org.springframework.stereotype.Component;

import com.thedavelopers.eventqr.features.monitor.config.MonitorEnabled;
import com.thedavelopers.eventqr.features.monitor.model.CrudOperation;
import com.thedavelopers.eventqr.features.monitor.service.CrudEventCapture;
import com.thedavelopers.eventqr.features.monitor.service.TableNames;

/**
 * Reports Spring Data repository reads (find/get/read/query/count/exists methods) and {@code @Modifying} queries
 * to the CRUD monitor. Entity {@code save*}/{@code delete*} calls are intentionally NOT recorded here: they reach
 * the database through Hibernate and are reported (once) by the Hibernate listeners, so recording them here would
 * double count. Native SQL outside repository methods is not observable.
 *
 * <p>A method is a read if it is not {@code @Modifying} and either carries {@code @Query} or is named
 * find/get/read/query/count/exists. {@code @Modifying} queries are CREATE (insert), DELETE or UPDATE.
 *
 * <p>The advice never changes behaviour: the repository result or exception is returned/rethrown unchanged, and
 * every piece of monitoring code is wrapped so it cannot throw ({@code Error}s are not intercepted). Arguments are never inspected or recorded.
 */
@Aspect
@Component
@MonitorEnabled
@Order(Integer.MAX_VALUE)
public class RepositoryCrudAspect {

    private static final Logger log = LoggerFactory.getLogger(RepositoryCrudAspect.class);

    /** How a repository method is reported; {@code IGNORE} means "not recorded by this aspect". */
    public enum Kind { READ, MODIFY_UPDATE, MODIFY_INSERT, MODIFY_DELETE, BATCH_DELETE, IGNORE }

    private final CrudEventCapture capture;
    private final Map<Method, Kind> kinds = new ConcurrentHashMap<>();
    private final Map<Class<?>, Optional<String>> tables = new ConcurrentHashMap<>();

    public RepositoryCrudAspect(CrudEventCapture capture) {
        this.capture = capture;
    }

    @Around("execution(* org.springframework.data.repository.Repository+.*(..))")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        Kind kind = Kind.IGNORE;
        String table = null;
        Method method = null;
        try {
            if (capture.isCapturing()) {
                method = ((MethodSignature) joinPoint.getSignature()).getMethod();
                kind = kinds.computeIfAbsent(method, RepositoryCrudAspect::classify);
                if (kind != Kind.IGNORE) {
                    table = tableOf(joinPoint.getTarget());
                    if (table == null) {
                        kind = Kind.IGNORE;
                    }
                }
            }
        } catch (Exception e) {
            log.debug("monitor repository pre-check failed", e);
            kind = Kind.IGNORE;
        }
        if (kind == Kind.IGNORE) {
            return joinPoint.proceed();
        }

        Object result;
        try {
            result = joinPoint.proceed();
        } catch (Exception failure) {
            report(kind, table, method, null, false);
            throw failure;
        }
        report(kind, table, method, result, true);
        return result;
    }

    private void report(Kind kind, String table, Method method, Object result, boolean success) {
        try {
            String name = method.getName();
            switch (kind) {
                case READ -> capture.recordRead(table, "SELECT " + table + " via " + name,
                        success ? rowCountOfRead(name, result) : null, success);
                case MODIFY_UPDATE -> write(CrudOperation.UPDATE, "UPDATE ", table, name, result, success);
                case MODIFY_INSERT -> write(CrudOperation.CREATE, "INSERT ", table, name, result, success);
                case MODIFY_DELETE, BATCH_DELETE -> write(CrudOperation.DELETE, "DELETE ", table, name, result, success);
                default -> { }
            }
        } catch (Exception e) {
            log.debug("monitor repository capture failed", e);
        }
    }

    private void write(CrudOperation operation, String verb, String table, String methodName, Object result,
                       boolean success) {
        String description = verb + table + " via " + methodName;
        if (success) {
            Integer rows = result instanceof Number n ? Integer.valueOf((int) Math.min(n.longValue(), Integer.MAX_VALUE)) : null;
            capture.recordWrite(operation, table, null, description, rows);
        } else {
            capture.recordWriteFailure(operation, table, description);
        }
    }

    private String tableOf(Object target) {
        if (target == null) {
            return null;
        }
        return tables.computeIfAbsent(target.getClass(), type -> {
            for (Class<?> candidate : type.getInterfaces()) {
                if (Repository.class.isAssignableFrom(candidate) && !candidate.getName().startsWith("org.springframework.data.")) {
                    try {
                        Class<?> domain = AbstractRepositoryMetadata.getMetadata(candidate).getDomainType();
                        return TableNames.of(domain);
                    } catch (Exception e) {
                        // Cached as unresolved below, so an unresolvable repository is not retried on every call.
                        log.debug("monitor could not resolve the entity of {}", candidate.getName(), e);
                        return Optional.<String>empty();
                    }
                }
            }
            return Optional.empty();
        }).orElse(null);
    }

    public static Kind classify(Method method) {
        String name = method.getName().toLowerCase(Locale.ROOT);
        if (AnnotatedElementUtils.hasAnnotation(method, Modifying.class)) {
            Query query = AnnotationUtils.findAnnotation(method, Query.class);
            String jpql = query == null ? "" : query.value().stripLeading().toLowerCase(Locale.ROOT);
            if (name.startsWith("delete") || jpql.startsWith("delete")) {
                return Kind.MODIFY_DELETE;
            }
            if (jpql.startsWith("insert")) {
                return Kind.MODIFY_INSERT;
            }
            return Kind.MODIFY_UPDATE;
        }
        // Bulk deletes that bypass the persistence context (and so the Hibernate listeners).
        if (name.startsWith("delete") && name.endsWith("inbatch")) {
            return Kind.BATCH_DELETE;
        }
        if (name.startsWith("save") || name.startsWith("delete") || name.startsWith("getreference")
                || name.equals("getbyid") || name.equals("getone")) {
            return Kind.IGNORE;
        }
        // Any other @Query method that is not @Modifying is a read, whatever it is called.
        if (AnnotatedElementUtils.hasAnnotation(method, Query.class)) {
            return Kind.READ;
        }
        if (name.startsWith("find") || name.startsWith("get") || name.startsWith("read") || name.startsWith("query")
                || name.startsWith("count") || name.startsWith("exists")) {
            return Kind.READ;
        }
        return Kind.IGNORE;
    }

    private static Integer rowCountOfRead(String methodName, Object result) {
        if (result == null) {
            return 0;
        }
        if (result instanceof Optional<?> optional) {
            return optional.isPresent() ? 1 : 0;
        }
        if (result instanceof Slice<?> slice) {
            return slice.getNumberOfElements();
        }
        if (result instanceof Collection<?> collection) {
            return collection.size();
        }
        if (result instanceof Map<?, ?> map) {
            return map.size();
        }
        if (result instanceof Boolean flag) {
            return flag ? 1 : 0;
        }
        if (result instanceof Number number) {
            return methodName.toLowerCase(Locale.ROOT).startsWith("count")
                    ? (Integer) (int) Math.min(number.longValue(), Integer.MAX_VALUE)
                    : 1;
        }
        if (result instanceof Iterable<?> || result instanceof java.util.stream.BaseStream<?, ?>) {
            return null;
        }
        if (result.getClass().isArray()) {
            return java.lang.reflect.Array.getLength(result);
        }
        return 1;
    }
}
