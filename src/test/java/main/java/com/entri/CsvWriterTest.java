package main.java.com.entri;

import java.io.StringWriter;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

public final class CsvWriterTest {
    public static void main(String[] args) throws Exception {
        testFormattingAndExistingCsvSafety();
        testDecimalFormatting();
        testColumnValidation();
        testInvalidRowsDoNotModifyDestination();
        System.out.println("CsvWriterTest passed.");
    }

    private static void testFormattingAndExistingCsvSafety() throws Exception {
        StringWriter output = new StringWriter();
        CsvWriter.write(output, List.of("Description", "Amount", "Note", "Empty"), List.of(
                Arrays.asList("\t=SUM(A1:A2)", new BigDecimal("-12.5000"),
                        "said \"hello\"\r\nnext", null)));
        String expected = "\uFEFF\"Description\",\"Amount\",\"Note\",\"Empty\"\r\n"
                + "\"'\t=SUM(A1:A2)\",\"-12.50\",\"said \"\"hello\"\"\r\nnext\",\"\"\r\n";
        require(expected.equals(output.toString()),
                "CSV should preserve BOM, headers, quoting, escaping, formula safety, and empty fields.");

        StringWriter formulaPrefixes = new StringWriter();
        CsvWriter.write(formulaPrefixes, List.of("Formula"), List.of(
                List.of(" =SUM(A1:A2)"), List.of("\r@cmd"), List.of("-text"), List.of("+text")));
        require(formulaPrefixes.toString().contains(
                        "\"' =SUM(A1:A2)\"\r\n\"'\r@cmd\"\r\n\"'-text\"\r\n\"'+text\""),
                "Formula-like text after whitespace or control characters should be protected.");

        StringWriter validRow = new StringWriter();
        CsvWriter.write(validRow, List.of("Date", "Particulars", "Currency", "Debit", "Credit"),
                List.of(List.of("2026-11-02", "Office Supplies", "PHP",
                        new BigDecimal("150"), new BigDecimal("0"))));
        require(validRow.toString().contains(
                        "\"2026-11-02\",\"Office Supplies\",\"PHP\",\"150.00\",\"0.00\""),
                "A correctly sized row should be written with separate currency and numeric cells.");
    }

    private static void testDecimalFormatting() throws Exception {
        StringWriter output = new StringWriter();
        CsvWriter.write(output, List.of("Amount"), List.of(
                List.of(new BigDecimal("150")),
                List.of(new BigDecimal("12.5")),
                List.of(new BigDecimal("12.50")),
                List.of(new BigDecimal("-12.5000")),
                List.of(new BigDecimal("1.235")),
                List.of(new BigDecimal("-1.235")),
                List.of(BigDecimal.ZERO),
                List.of(new BigDecimal("123456789012345678901234567890.5")),
                List.of(new BigDecimal("1E+30"))));
        String expected = "\uFEFF\"Amount\"\r\n"
                + "\"150.00\"\r\n\"12.50\"\r\n\"12.50\"\r\n\"-12.50\"\r\n"
                + "\"1.24\"\r\n\"-1.24\"\r\n\"0.00\"\r\n"
                + "\"123456789012345678901234567890.50\"\r\n"
                + "\"1000000000000000000000000000000.00\"\r\n";
        require(expected.equals(output.toString()),
                "BigDecimal CSV values should have two decimals, HALF_UP rounding, and plain notation.");
    }

    private static void testColumnValidation() throws Exception {
        expectIllegalArgument(() -> CsvWriter.write(new StringWriter(), List.of(), List.of()));
        expectIllegalArgument(() -> CsvWriter.write(new StringWriter(), Arrays.asList("Date", null), List.of()));
        expectIllegalArgument(() -> CsvWriter.write(new StringWriter(), List.of("Date", " "), List.of()));

        StringWriter tooFew = new StringWriter();
        expectIllegalArgument(() -> CsvWriter.write(
                tooFew, List.of("Date", "Particulars"), List.of(List.of("2026-11-02"))));
        require(tooFew.toString().isEmpty(),
                "Rows with too few columns must be rejected before writing any CSV content.");

        StringWriter tooMany = new StringWriter();
        expectIllegalArgument(() -> CsvWriter.write(
                tooMany, List.of("Date", "Particulars"), List.of(List.of("date", "text", "extra"))));
        require(tooMany.toString().isEmpty(),
                "Rows with too many columns must be rejected before writing any CSV content.");

        StringWriter nullRow = new StringWriter();
        expectIllegalArgument(() -> CsvWriter.write(
                nullRow, List.of("Date"), Arrays.asList((List<?>) null)));
        require(nullRow.toString().isEmpty(), "Null rows must be rejected before writing.");
    }

    private static void testInvalidRowsDoNotModifyDestination() throws Exception {
        Path directory = Files.createTempDirectory("entri-csv-test-");
        Path existingFile = directory.resolve("existing.csv");
        Path newFile = directory.resolve("new.csv");
        byte[] original = "previous export".getBytes(StandardCharsets.UTF_8);
        Files.write(existingFile, original);
        try {
            expectIllegalArgument(() -> CsvWriter.write(existingFile,
                    List.of("Date", "Particulars"),
                    List.of(List.of("2026-11-02", "Supplies", "unexpected"))));
            require(Arrays.equals(original, Files.readAllBytes(existingFile)),
                    "Malformed export data must not replace an existing destination.");

            expectIllegalArgument(() -> CsvWriter.write(newFile,
                    List.of("Date", "Particulars"), List.of(List.of("2026-11-02"))));
            require(!Files.exists(newFile),
                    "Malformed export data must not create a partial destination file.");
        } finally {
            Files.deleteIfExists(existingFile);
            Files.deleteIfExists(newFile);
            Files.deleteIfExists(directory);
        }
    }

    private static void expectIllegalArgument(ThrowingAction action) throws Exception {
        try {
            action.run();
            throw new AssertionError("Invalid CSV data should be rejected.");
        } catch (IllegalArgumentException expected) {
            require(expected.getMessage() != null && !expected.getMessage().isBlank(),
                    "CSV validation errors should explain what is invalid.");
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    @FunctionalInterface
    private interface ThrowingAction {
        void run() throws Exception;
    }
}
