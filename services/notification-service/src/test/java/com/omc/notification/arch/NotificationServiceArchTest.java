package com.omc.notification.arch;

import com.omc.arch.CommonArchRules;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

@AnalyzeClasses(packages = "com.omc.notification", importOptions = ImportOption.DoNotIncludeTests.class)
public class NotificationServiceArchTest {

    @ArchTest
    static final ArchRule layer_dependency_rule = layeredArchitecture()
            .consideringAllDependencies()
            .layer("Controllers").definedBy("..controller..")
            .layer("Services").definedBy("..service..")
            .layer("Repositories").definedBy("..repository..")
            .layer("Entities").definedBy("..entity..")
            .layer("DTOs").definedBy("..dto..")
            .layer("Consumers").definedBy("..event.consumer..")
            .layer("Schedulers").definedBy("..scheduler..")

            .whereLayer("Controllers").mayNotBeAccessedByAnyLayer()
            .whereLayer("Services").mayOnlyBeAccessedByLayers("Controllers", "Services", "Consumers", "Schedulers")
            .whereLayer("Repositories").mayOnlyBeAccessedByLayers("Services", "Schedulers")
            .whereLayer("Entities").mayOnlyBeAccessedByLayers("Controllers", "Services", "Repositories", "Entities", "DTOs", "Schedulers")
            .whereLayer("DTOs").mayOnlyBeAccessedByLayers("Controllers", "Services");

    @ArchTest
    static final ArchRule no_direct_user_service_import = CommonArchRules.noDirectServiceCrossImport("notification", "user");

    @ArchTest
    static final ArchRule no_direct_order_service_import = CommonArchRules.noDirectServiceCrossImport("notification", "order");

    @ArchTest
    static final ArchRule no_direct_coupon_service_import = CommonArchRules.noDirectServiceCrossImport("notification", "coupon");

    @ArchTest
    static final ArchRule controller_naming = CommonArchRules.controllerNamingRule();

    @ArchTest
    static final ArchRule service_naming = CommonArchRules.serviceNamingRule();

    @ArchTest
    static final ArchRule repository_naming = CommonArchRules.repositoryNamingRule();

    @ArchTest
    static final ArchRule controller_annotation = CommonArchRules.controllerAnnotationRule();

    @ArchTest
    static final ArchRule service_annotation = CommonArchRules.serviceAnnotationRule();

    @ArchTest
    static final ArchRule repository_annotation = CommonArchRules.repositoryAnnotationRule();

    @ArchTest
    static final ArchRule entity_annotation = CommonArchRules.entityAnnotationRule();
}
