package com.savemon.identity.infrastructure.persistence;

import com.savemon.identity.application.SubjectStatusQuery;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class JdbcSubjectStatusQueryContextTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(SubjectStatusScan.class)
            .withConfiguration(AutoConfigurations.of(JdbcTemplateAutoConfiguration.class))
            .withBean(DataSource.class, () -> mock(DataSource.class));

    @Test
    void componentScanRegistersSubjectStatusQueryWithAutoConfiguredJdbcTemplate() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(SubjectStatusQuery.class);
            assertThat(context.getBean(SubjectStatusQuery.class))
                    .isInstanceOf(JdbcSubjectStatusQuery.class);
        });
    }

    @Configuration(proxyBeanMethods = false)
    @ComponentScan(
            basePackageClasses = JdbcSubjectStatusQuery.class,
            useDefaultFilters = false,
            includeFilters = @ComponentScan.Filter(
                    type = FilterType.ASSIGNABLE_TYPE,
                    classes = JdbcSubjectStatusQuery.class))
    static class SubjectStatusScan {
    }
}
