package de.ebon.product;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.assertThat;

class ProductSeedMigrationTests {

    // Verifies the product seed migration provides unambiguous store-specific master data.
    @Test
    void migrationSeedsStoreSpecificCocaColaAndDmWaterRules() throws Exception {
        try (PostgreSQLContainer<?> database = new PostgreSQLContainer<>("postgres:18-alpine")
                .withDatabaseName("ebon")
                .withUsername("ebon")
                .withPassword("ebon_test_password")) {
            database.start();
            Flyway.configure()
                    .dataSource(database.getJdbcUrl(), database.getUsername(), database.getPassword())
                    .locations("classpath:db/migration")
                    .load()
                    .migrate();

            try (Connection connection = database.createConnection("")) {
                assertThat(productFamilyId(connection, "Coca-Cola Zero")).isNotNull();
                assertThat(productVariantId(connection, "Coca-Cola Zero", "0.33 l Einwegflasche")).isNotNull();
                assertThat(ruleCount(connection, "REWE", "CC Z 0,33L EW FL")).isEqualTo(1L);
                assertThat(defaultCategory(connection, "Denkmit Destilliertes Wasser")).isEqualTo("Haushalt");
                assertThat(productFamilyId(connection, "Toilettenpapier 3-lagig")).isNotNull();
                assertThat(ruleCount(connection, "REWE", "TOILETTENPAP.3LG")).isEqualTo(1L);
                assertThat(defaultCategory(connection, "Filetraeucherling")).isEqualTo("Fleisch und Wurst");
                assertThat(familyOnlyRuleCount(connection, "REWE", "FILETRAEUCHERL.")).isEqualTo(1L);
            }
        }
    }

    private Long productFamilyId(Connection connection, String familyName) throws Exception {
        return nullableLong(connection, "select id from product_family where name = ?", familyName);
    }

    private Long productVariantId(Connection connection, String familyName, String variantName) throws Exception {
        return nullableLong(
                connection,
                """
                        select variant.id
                        from product_variant variant
                        join product_family family on family.id = variant.product_family_id
                        where family.name = ? and variant.name = ?
                        """,
                familyName,
                variantName);
    }

    private Long ruleCount(Connection connection, String storeName, String matchValue) throws Exception {
        return nullableLong(
                connection,
                "select count(*) from product_rule where store_name = ? and match_value = ?",
                storeName,
                matchValue);
    }

    private Long familyOnlyRuleCount(Connection connection, String storeName, String matchValue) throws Exception {
        return nullableLong(
                connection,
                "select count(*) from product_rule where store_name = ? and match_value = ? and product_variant_id is null",
                storeName,
                matchValue);
    }

    private String defaultCategory(Connection connection, String familyName) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement("""
                select category.name
                from product_family family
                join category on category.id = family.default_category_id
                where family.name = ?
                """)) {
            statement.setString(1, familyName);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? resultSet.getString(1) : null;
            }
        }
    }

    private Long nullableLong(Connection connection, String sql, String... parameters) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int index = 0; index < parameters.length; index++) {
                statement.setString(index + 1, parameters[index]);
            }
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? resultSet.getLong(1) : null;
            }
        }
    }
}
