package com.thedavelopers.eventqr.features.monitor;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AssignableTypeFilter;
import org.springframework.data.repository.Repository;

import com.thedavelopers.eventqr.features.monitor.aspect.RepositoryCrudAspect;
import com.thedavelopers.eventqr.features.monitor.aspect.RepositoryCrudAspect.Kind;

/** Guards the aspect's classification rules against the application's real repository interfaces. */
class RepositoryClassificationTest {

    private static final String[] INTENTIONALLY_IGNORED = { "save", "delete", "getreference", "getbyid", "getone", "flush" };

    private static List<Class<?>> applicationRepositories() throws ClassNotFoundException {
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false) {
            @Override
            protected boolean isCandidateComponent(AnnotatedBeanDefinition definition) {
                return definition.getMetadata().isInterface();
            }
        };
        scanner.addIncludeFilter(new AssignableTypeFilter(Repository.class));
        List<Class<?>> repositories = new ArrayList<>();
        for (BeanDefinition definition : scanner.findCandidateComponents("com.thedavelopers.eventqr")) {
            repositories.add(Class.forName(definition.getBeanClassName()));
        }
        return repositories;
    }

    @Test
    @DisplayName("every method declared by an application repository is recorded or intentionally ignored")
    void everyDeclaredRepositoryMethodIsClassified() throws Exception {
        List<Class<?>> repositories = applicationRepositories();
        assertThat(repositories).hasSizeGreaterThan(10);

        List<String> unclassified = new ArrayList<>();
        int reads = 0;
        int modifying = 0;
        for (Class<?> repository : repositories) {
            for (Method method : repository.getMethods()) {
                if (!method.getDeclaringClass().getName().startsWith("com.thedavelopers.eventqr")
                        || method.isDefault()) {
                    continue;
                }
                Kind kind = RepositoryCrudAspect.classify(method);
                String name = method.getName().toLowerCase(Locale.ROOT);
                if (kind == Kind.IGNORE) {
                    boolean intentional = false;
                    for (String prefix : INTENTIONALLY_IGNORED) {
                        intentional |= name.startsWith(prefix);
                    }
                    if (!intentional) {
                        unclassified.add(repository.getSimpleName() + "." + method.getName());
                    }
                } else if (kind == Kind.READ) {
                    reads++;
                } else {
                    modifying++;
                }
            }
        }
        assertThat(reads).isGreaterThan(20);
        assertThat(modifying).isGreaterThan(5);
        assertThat(unclassified).as("repository methods neither recorded nor intentionally ignored").isEmpty();
    }
}
