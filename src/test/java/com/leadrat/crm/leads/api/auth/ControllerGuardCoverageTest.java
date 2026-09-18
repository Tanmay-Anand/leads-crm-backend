package com.leadrat.crm.leads.api.auth;

import com.leadrat.crm.leads.api.auth.annotations.AuthenticatedOnly;
import com.leadrat.crm.leads.api.auth.annotations.PlatformAdminOnly;
import com.leadrat.crm.leads.api.auth.annotations.PlatformOnly;
import com.leadrat.crm.leads.api.auth.annotations.TenantAdminOnly;
import com.leadrat.crm.leads.api.auth.annotations.TenantOnly;
import com.leadrat.crm.leads.api.rbac.CrmPermission;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Reflects over every {@code @RestController} handler method in the app and asserts exactly one
 * guard - never two (the {@code AnnotationConfigurationException} trap PermissionService's class
 * doc explains: two {@code @PreAuthorize}-meta-annotated annotations on one method throws lazily,
 * on first invocation, surfacing as a 500 on that one endpoint rather than a startup failure) and
 * never zero (an endpoint nobody remembered to gate). Also parses every
 * {@code @permissionService.check('action', 'resource')} SpEL expression found and asserts the
 * pair is a real, catalogued permission - this is what would have caught a typo like
 * {@code 'channel-partner'} (singular) against the real {@code 'channel-partners'} resource key
 * at build time instead of as a silent, permanent 403.
 */
class ControllerGuardCoverageTest {

    private static final List<Class<? extends Annotation>> MAPPING_ANNOTATIONS = List.of(
            GetMapping.class, PostMapping.class, PutMapping.class, PatchMapping.class,
            DeleteMapping.class, RequestMapping.class);

    private static final List<Class<? extends Annotation>> GUARD_ANNOTATIONS = List.of(
            PreAuthorize.class, AuthenticatedOnly.class, TenantOnly.class,
            TenantAdminOnly.class, PlatformOnly.class, PlatformAdminOnly.class);

    private static final Pattern CHECK_CALL = Pattern.compile(
            "@permissionService\\.check\\(\\s*'([^']+)'\\s*,\\s*'([^']+)'\\s*\\)");

    @Test
    @DisplayName("every controller handler method has exactly one security guard")
    void everyHandlerHasExactlyOneGuard() {
        List<String> zeroGuards = new ArrayList<>();
        List<String> multipleGuards = new ArrayList<>();

        for (Class<?> controller : findControllers()) {
            for (Method method : controller.getDeclaredMethods()) {
                if (!isHandlerMethod(method)) {
                    continue;
                }
                long guardCount = GUARD_ANNOTATIONS.stream().filter(method::isAnnotationPresent).count();
                String label = controller.getSimpleName() + "." + method.getName();
                if (guardCount == 0) {
                    zeroGuards.add(label);
                } else if (guardCount > 1) {
                    multipleGuards.add(label);
                }
            }
        }

        assertThat(zeroGuards).as("handler methods with no security guard at all").isEmpty();
        assertThat(multipleGuards)
                .as("handler methods with more than one guard annotation - this throws "
                        + "AnnotationConfigurationException at request time, not at startup")
                .isEmpty();
    }

    @Test
    @DisplayName("every @permissionService.check(...) call names a real, catalogued permission")
    void everyPermissionCheckIsValid() {
        List<String> invalid = new ArrayList<>();

        for (Class<?> controller : findControllers()) {
            for (Method method : controller.getDeclaredMethods()) {
                PreAuthorize preAuthorize = method.getAnnotation(PreAuthorize.class);
                if (preAuthorize == null) {
                    continue;
                }
                Matcher matcher = CHECK_CALL.matcher(preAuthorize.value());
                if (!matcher.find()) {
                    continue;
                }
                String permission = CrmPermission.of(matcher.group(1), matcher.group(2));
                if (!CrmPermission.isValid(permission)) {
                    invalid.add(controller.getSimpleName() + "." + method.getName() + " -> " + permission);
                }
            }
        }

        assertThat(invalid).as("@PreAuthorize checks naming a permission absent from CrmPermission.ALL").isEmpty();
    }

    private boolean isHandlerMethod(Method method) {
        return MAPPING_ANNOTATIONS.stream().anyMatch(method::isAnnotationPresent);
    }

    private List<Class<?>> findControllers() {
        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));

        List<Class<?>> controllers = new ArrayList<>();
        scanner.findCandidateComponents("com.leadrat.crm.leads.api").forEach(beanDefinition -> {
            try {
                controllers.add(Class.forName(beanDefinition.getBeanClassName()));
            } catch (ClassNotFoundException e) {
                throw new IllegalStateException(e);
            }
        });
        return controllers;
    }
}
