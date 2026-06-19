package com.omc.arch;

import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.lang.ArchRule;
import org.springframework.stereotype.Controller;
import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RestController;
import jakarta.persistence.Entity;
import org.springframework.data.repository.RepositoryDefinition;
import org.springframework.data.jpa.repository.JpaRepository;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

public class CommonArchRules {

    // 1. 레이어 의존성 (Layered Architecture)
    public static ArchRule layerDependencyRule() {
        return layeredArchitecture()
                .consideringAllDependencies()
                .layer("Controllers").definedBy("..controller..")
                .layer("Services").definedBy("..service..")
                .layer("Repositories").definedBy("..repository..")
                .layer("Entities").definedBy("..entity..")
                .layer("DTOs").definedBy("..dto..")

                .whereLayer("Controllers").mayNotBeAccessedByAnyLayer()
                .whereLayer("Services").mayOnlyBeAccessedByLayers("Controllers", "Services")
                .whereLayer("Repositories").mayOnlyBeAccessedByLayers("Services", "Repositories")
                .whereLayer("Entities").mayOnlyBeAccessedByLayers("Controllers", "Services", "Repositories", "Entities", "DTOs")
                .whereLayer("DTOs").mayOnlyBeAccessedByLayers("Controllers", "Services", "Repositories", "Entities");
    }

    // 2. MSA 직접 침범 금지 (No direct import across domains)
    public static ArchRule noDirectServiceCrossImport(String sourceDomain, String forbiddenDomain) {
        return noClasses()
                .that().resideInAPackage("..com.omc." + sourceDomain + "..")
                .should().dependOnClassesThat().resideInAPackage("..com.omc." + forbiddenDomain + "..");
    }

    // 3. 네이밍 규칙 (Naming Rules)
    public static ArchRule controllerNamingRule() {
        return classes()
                .that().resideInAPackage("..controller..")
                .should().haveSimpleNameEndingWith("Controller");
    }

    public static ArchRule serviceNamingRule() {
        return classes()
                .that().resideInAPackage("..service..")
                .should().haveSimpleNameEndingWith("Service");
    }

    public static ArchRule repositoryNamingRule() {
        return classes()
                .that().resideInAPackage("..repository..")
                .should().haveSimpleNameEndingWith("Repository");
    }

    public static ArchRule requestDtoNamingRule() {
        return classes()
                .that().resideInAPackage("..dto.request..")
                .should().haveSimpleNameEndingWith("Request");
    }

    public static ArchRule responseDtoNamingRule() {
        return classes()
                .that().resideInAPackage("..dto.response..")
                .should().haveSimpleNameEndingWith("Response");
    }

    // 4. 어노테이션 규칙 (Annotation Rules)
    public static ArchRule controllerAnnotationRule() {
        return classes()
                .that().resideInAPackage("..controller..")
                .should().beAnnotatedWith(RestController.class)
                .orShould().beAnnotatedWith(Controller.class);
    }

    public static ArchRule serviceAnnotationRule() {
        return classes()
                .that().resideInAPackage("..service..")
                .and().areNotInterfaces()
                .should().beAnnotatedWith(Service.class);
    }

    public static ArchRule repositoryAnnotationRule() {
        return classes()
                .that().resideInAPackage("..repository..")
                .and().areInterfaces()
                .should().beAnnotatedWith(Repository.class)
                .orShould().beAssignableTo(org.springframework.data.repository.Repository.class);
    }

    public static ArchRule entityAnnotationRule() {
        return classes()
                .that().resideInAPackage("..entity..")
                .and().areTopLevelClasses()
                .and().doNotHaveModifier(JavaModifier.ABSTRACT)
                .should().beAnnotatedWith(Entity.class);
    }
}
