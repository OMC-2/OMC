package com.omc.product.arch;

import com.omc.arch.CommonArchRules;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

@AnalyzeClasses(packages = "com.omc.product", importOptions = ImportOption.DoNotIncludeTests.class)
public class ProductServiceArchTest {

    // 레이어 의존성 규칙
    @ArchTest
    static final ArchRule layer_dependency_rule = CommonArchRules.layerDependencyRule();

    // MSA 직접 침범 금지 — 타 서비스 직접 import 금지
    @ArchTest
    static final ArchRule no_direct_user_import =
            CommonArchRules.noDirectServiceCrossImport("product", "user");

    @ArchTest
    static final ArchRule no_direct_drop_import =
            CommonArchRules.noDirectServiceCrossImport("product", "drop");

    @ArchTest
    static final ArchRule no_direct_raffle_import =
            CommonArchRules.noDirectServiceCrossImport("product", "raffle");

    @ArchTest
    static final ArchRule no_direct_order_import =
            CommonArchRules.noDirectServiceCrossImport("product", "order");

    @ArchTest
    static final ArchRule no_direct_payment_import =
            CommonArchRules.noDirectServiceCrossImport("product", "payment");

    @ArchTest
    static final ArchRule no_direct_coupon_import =
            CommonArchRules.noDirectServiceCrossImport("product", "coupon");

    @ArchTest
    static final ArchRule no_direct_notification_import =
            CommonArchRules.noDirectServiceCrossImport("product", "notification");

    // 네이밍 규칙
    @ArchTest
    static final ArchRule controller_naming = CommonArchRules.controllerNamingRule();

    @ArchTest
    static final ArchRule service_naming = CommonArchRules.serviceNamingRule();

    @ArchTest
    static final ArchRule repository_naming = CommonArchRules.repositoryNamingRule();

    @ArchTest
    static final ArchRule processor_naming = CommonArchRules.processorNamingRule();

    @ArchTest
    static final ArchRule request_dto_naming = CommonArchRules.requestDtoNamingRule();

    @ArchTest
    static final ArchRule response_dto_naming = CommonArchRules.responseDtoNamingRule();

    // 어노테이션 규칙
    @ArchTest
    static final ArchRule controller_annotation = CommonArchRules.controllerAnnotationRule();

    @ArchTest
    static final ArchRule service_annotation = CommonArchRules.serviceAnnotationRule();

    @ArchTest
    static final ArchRule processor_annotation = CommonArchRules.processorAnnotationRule();

    @ArchTest
    static final ArchRule repository_annotation = CommonArchRules.repositoryAnnotationRule();

    @ArchTest
    static final ArchRule entity_annotation = CommonArchRules.entityAnnotationRule();
}