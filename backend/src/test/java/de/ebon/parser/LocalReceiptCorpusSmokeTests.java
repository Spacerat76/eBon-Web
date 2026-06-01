package de.ebon.parser;

import de.ebon.persistence.model.ParseStatus;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfSystemProperty(named = "ebon.localReceiptCorpus.enabled", matches = "true")
class LocalReceiptCorpusSmokeTests {

    private static final List<Path> LOCAL_CORPUS_DIRS = List.of(Path.of("../rewe"), Path.of("../dm"));

    private final ReceiptParserService parser = new ReceiptParserService(
            new RuleBasedReceiptParser(),
            rawText -> Optional.empty(),
            new AiReceiptJsonParser(new ObjectMapper()));

    @Test
    void localReceiptCorpusIsAvailable() throws IOException {
        assertThat(localReceipts().count())
                .as("local ignored folders ../rewe and ../dm should contain receipt txt files")
                .isPositive();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("localReceiptFiles")
    void parsesLocalReceiptCorpus(String label, Path receiptPath) throws IOException {
        ReceiptParseResult result = parser.parse(Files.readString(receiptPath));
        BigDecimal itemSum = itemSum(result);
        BigDecimal totalAmount = result.receipt() == null ? null : result.receipt().totalAmount();
        int itemCount = result.receipt() == null ? 0 : result.receipt().items().size();
        List<BigDecimal> itemAmounts = result.receipt() == null
                ? List.of()
                : result.receipt().items().stream().map(ParsedReceiptItem::totalPrice).toList();

        assertThat(result.parseStatus())
                .as("%s parse error=%s total=%s itemSum=%s itemCount=%d itemAmounts=%s",
                        label,
                        result.errorMessage(),
                        totalAmount,
                        itemSum,
                        itemCount,
                        itemAmounts)
                .isEqualTo(ParseStatus.PARSED);
        assertThat(result.receipt().items())
                .as("%s parsed items", label)
                .isNotEmpty();
    }

    private static Stream<Arguments> localReceiptFiles() throws IOException {
        return localReceipts()
                .map(path -> Arguments.of(path.getParent().getFileName() + "/" + path.getFileName(), path));
    }

    private static Stream<Path> localReceipts() throws IOException {
        Stream.Builder<Path> builder = Stream.builder();
        for (Path dir : LOCAL_CORPUS_DIRS) {
            if (!Files.isDirectory(dir)) {
                continue;
            }
            try (Stream<Path> files = Files.list(dir)) {
                files.filter(path -> path.getFileName().toString().endsWith(".txt"))
                        .sorted()
                        .forEach(builder);
            }
        }
        return builder.build();
    }

    private BigDecimal itemSum(ReceiptParseResult result) {
        if (result.receipt() == null) {
            return null;
        }
        return result.receipt().items().stream()
                .map(ParsedReceiptItem::totalPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
