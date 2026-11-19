package com.henrique.dashboard.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import com.henrique.dashboard.api.AuthController;
import com.henrique.dashboard.api.CollectionController;
import com.henrique.dashboard.api.ItemController;
import com.henrique.dashboard.api.UserAdminController;
import com.henrique.dashboard.domain.CollectedItem;
import com.henrique.dashboard.domain.CollectionRun;
import com.henrique.dashboard.domain.ItemRemoval;
import com.henrique.dashboard.domain.UserAccount;
import com.henrique.dashboard.service.CollectionRunService;
import com.henrique.dashboard.service.ItemQueryService;
import com.henrique.dashboard.service.ItemRemovalService;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CleanArchitectureTest {

    private final List<Class<?>> controllerClasses = List.of(
            AuthController.class,
            CollectionController.class,
            ItemController.class,
            UserAdminController.class
    );

    private final List<Class<?>> domainClasses = List.of(
            CollectedItem.class,
            CollectionRun.class,
            ItemRemoval.class,
            UserAccount.class
    );

    private final List<Class<?>> serviceClasses = List.of(
            CollectionRunService.class,
            ItemQueryService.class,
            ItemRemovalService.class
    );

    @Test
    @DisplayName("AC-01: Controllers must not access repositories directly")
    void controllersMustNotAccessRepositoriesDirectly() {
        for (Class<?> controller : controllerClasses) {
            for (Field field : controller.getDeclaredFields()) {
                String fieldTypeName = field.getType().getName();
                assertThat(fieldTypeName)
                        .withFailMessage("Controller %s directly accesses repository %s", controller.getSimpleName(), fieldTypeName)
                        .doesNotContain("com.henrique.dashboard.repository");
            }
        }
    }

    @Test
    @DisplayName("AC-01: Domain classes must be free of web and presentation dependencies")
    void domainMustBeFreeOfWebDependencies() {
        for (Class<?> domain : domainClasses) {
            for (Field field : domain.getDeclaredFields()) {
                String fieldTypeName = field.getType().getName();
                assertThat(fieldTypeName)
                        .withFailMessage("Domain %s references presentation layer %s", domain.getSimpleName(), fieldTypeName)
                        .doesNotContain("com.henrique.dashboard.api")
                        .doesNotContain("org.springframework.web");
            }
        }
    }

    @Test
    @DisplayName("AC-01: Services must not depend on API/controller layer")
    void servicesMustNotDependOnApiLayer() {
        for (Class<?> service : serviceClasses) {
            for (Field field : service.getDeclaredFields()) {
                String fieldTypeName = field.getType().getName();
                assertThat(fieldTypeName)
                        .withFailMessage("Service %s references API layer %s", service.getSimpleName(), fieldTypeName)
                        .doesNotContain("com.henrique.dashboard.api");
            }
            for (Method method : service.getDeclaredMethods()) {
                for (Class<?> paramType : method.getParameterTypes()) {
                    assertThat(paramType.getName())
                            .withFailMessage("Service %s method %s references API layer parameter %s",
                                    service.getSimpleName(), method.getName(), paramType.getName())
                            .doesNotContain("com.henrique.dashboard.api");
                }
            }
        }
    }
}
