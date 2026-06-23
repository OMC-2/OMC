package com.omc.arch;

import com.tngtech.archunit.lang.ArchRule;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

public class CommonArchRules {

    public static ArchRule layerDependencyRule() {
        return classes().that().resideInAPackage("..presentation..")
                .should().onlyDependOnClassesThat().resideInAnyPackage(
                        "..application..", "..domain..", "..dto..",
                        "java..", "org.springframework..",
                        "com.omc.common..",   // 공통 모듈 (ApiResponse 등)
                        "lombok..",            // Lombok 생성 코드
                        "jakarta.."            // Bean Validation, Persistence 어노테이션
                );
    }

    public static ArchRule noDirectServiceCrossImport(String serviceA, String serviceB) {
        return noClasses().that().resideInAPackage("..com.omc." + serviceA + "..")
                .should().dependOnClassesThat().resideInAPackage("..com.omc." + serviceB + "..");
    }

    public static ArchRule controllerNamingRule() {
        return classes().that().resideInAPackage("..presentation.controller..")
                .should().haveSimpleNameEndingWith("Controller");
    }

    public static ArchRule serviceNamingRule() {
        return classes().that().resideInAPackage("..application.service..")
                .should().haveSimpleNameEndingWith("Service");
    }

    public static ArchRule repositoryNamingRule() {
        return classes().that().resideInAPackage("..domain.repository..").or().resideInAPackage("..infrastructure.redis..")
                .should().haveSimpleNameEndingWith("Repository");
    }

    public static ArchRule controllerAnnotationRule() {
        return classes().that().resideInAPackage("..presentation.controller..")
                .should().beAnnotatedWith("org.springframework.web.bind.annotation.RestController");
    }

    public static ArchRule serviceAnnotationRule() {
        return classes().that().resideInAPackage("..application.service..")
                .should().beAnnotatedWith("org.springframework.stereotype.Service");
    }

    public static ArchRule repositoryAnnotationRule() {
        return classes().that().resideInAPackage("..infrastructure.repository..")
                .should().beAnnotatedWith("org.springframework.stereotype.Repository");
    }

    public static ArchRule entityAnnotationRule() {
        return classes().that().resideInAPackage("..domain.entity..")
                .and().areNotInterfaces()
                .and().areNotAnonymousClasses()
                .and().doNotHaveSimpleName("BaseTimeEntity")
                .and().doNotHaveSimpleName("RaffleResultStatus")
                .and().doNotHaveSimpleName("OutboxStatus")
                .and().doNotHaveSimpleName("RaffleStatus")
                .and().haveSimpleNameNotEndingWith("Builder")  // Lombok @Builder 내부 클래스 제외
                .should().beAnnotatedWith("jakarta.persistence.Entity");
    }
}
