package com.usermanagement.audit;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import tools.jackson.databind.json.JsonMapper;

/** Turns the arguments of an audited endpoint into a details map, redacting secrets. */
@Component
class AuditDetails {

    private static final String REDACTED = "***";

    private final JsonMapper jsonMapper;
    private final Set<String> redactedFields;

    AuditDetails(JsonMapper jsonMapper, AuditProperties properties) {
        this.jsonMapper = jsonMapper;
        this.redactedFields = properties.redactedFields().stream()
                .map(field -> field.toLowerCase(Locale.ROOT))
                .collect(Collectors.toUnmodifiableSet());
    }

    Map<String, Object> capture(Method method, Object[] args) {
        Map<String, Object> details = new LinkedHashMap<>();
        Parameter[] parameters = method.getParameters();
        for (int i = 0; i < parameters.length; i++) {
            Parameter parameter = parameters[i];
            Object value = args[i];
            if (value == null) {
                continue;
            }
            if (parameter.isAnnotationPresent(RequestBody.class)) {
                Object body = jsonMapper.convertValue(value, Object.class);
                if (body instanceof Map<?, ?> fields) {
                    fields.forEach((key, fieldValue) -> {
                        if (fieldValue != null) {
                            details.put(String.valueOf(key), fieldValue);
                        }
                    });
                } else {
                    details.put("body", body);
                }
            } else if (parameter.isAnnotationPresent(RequestParam.class)) {
                RequestParam annotation = parameter.getAnnotation(RequestParam.class);
                details.put(name(annotation.name(), annotation.value(), parameter), value);
            } else if (parameter.isAnnotationPresent(PathVariable.class)) {
                PathVariable annotation = parameter.getAnnotation(PathVariable.class);
                details.put(name(annotation.name(), annotation.value(), parameter), String.valueOf(value));
            } else if (value instanceof Pageable pageable && pageable.isPaged()) {
                details.put("page", pageable.getPageNumber());
                details.put("size", pageable.getPageSize());
                if (pageable.getSort().isSorted()) {
                    details.put("sort", pageable.getSort().toString());
                }
            }
        }
        return redact(details);
    }

    /** Returns a copy in which values of sensitive keys are replaced, at any depth. */
    @SuppressWarnings("unchecked")
    Map<String, Object> redact(Map<String, ?> source) {
        Map<String, Object> copy = new LinkedHashMap<>();
        source.forEach((key, value) -> copy.put(key,
                redactedFields.contains(key.toLowerCase(Locale.ROOT)) ? REDACTED : redactValue(value)));
        return copy;
    }

    @SuppressWarnings("unchecked")
    private Object redactValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            return redact((Map<String, ?>) map);
        }
        if (value instanceof Collection<?> collection) {
            List<Object> copy = new ArrayList<>(collection.size());
            collection.forEach(item -> copy.add(redactValue(item)));
            return copy;
        }
        return value;
    }

    private static String name(String name, String value, Parameter parameter) {
        if (!name.isEmpty()) {
            return name;
        }
        return value.isEmpty() ? parameter.getName() : value;
    }
}
