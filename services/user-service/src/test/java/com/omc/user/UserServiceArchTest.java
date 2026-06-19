package com.omc.user;

import com.omc.arch.CommonArchRules;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

@AnalyzeClasses(packages = "com.omc.user", importOptions = ImportOption.DoNotIncludeTests.class)
public class UserServiceArchTest {

    @ArchTest
    static final ArchRule layer_dependency_rule = CommonArchRules.layerDependencyRule();

    @ArchTest
    static final ArchRule no_direct_order_service_import = CommonArchRules.noDirectServiceCrossImport("user", "order");

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
