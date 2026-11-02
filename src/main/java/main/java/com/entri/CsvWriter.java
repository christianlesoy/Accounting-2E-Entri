package main.java.com.entri;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.Writer;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.List;

final class CsvWriter {
    private CsvWriter() {}

    static void write(Writer writer, List<String> headers, List<? extends List<?>> rows)
            throws IOException {
        if (writer == null) throw new IllegalArgumentException("A CSV writer is required.");
        validate(headers, rows);
        writeValidated(writer, headers, rows);
    }

    static void write(Path destination, List<String> headers, List<? extends List<?>> rows)
            throws IOException {
        validate(headers, rows);
        if (destination == null) throw new IllegalArgumentException("A CSV destination is required.");
        Path absoluteDestination = destination.toAbsolutePath();
        Path temporaryFile = Files.createTempFile(absoluteDestination.getParent(),
                "." + absoluteDestination.getFileName() + ".", ".tmp");
        try {
            try (BufferedWriter writer = Files.newBufferedWriter(temporaryFile,
                    StandardCharsets.UTF_8, StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE)) {
                writeValidated(writer, headers, rows);
            }
            try {
                Files.move(temporaryFile, absoluteDestination, StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ex) {
                Files.move(temporaryFile, absoluteDestination, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException | SecurityException ex) {
            try {
                Files.deleteIfExists(temporaryFile);
            } catch (IOException | SecurityException cleanupError) {
                ex.addSuppressed(cleanupError);
            }
            throw ex;
        }
    }

    private static void writeValidated(Writer writer, List<String> headers,
                                       List<? extends List<?>> rows) throws IOException {
        writer.write('\uFEFF');
        writeRow(writer, headers);
        for (List<?> row : rows) {
            writeRow(writer, row);
        }
    }

    static void validate(List<String> headers, List<? extends List<?>> rows) {
        if (headers == null || headers.isEmpty()) {
            throw new IllegalArgumentException("CSV headers are required.");
        }
        if (rows == null) {
            throw new IllegalArgumentException("CSV rows are required.");
        }
        for (int column = 0; column < headers.size(); column++) {
            String header = headers.get(column);
            if (header == null || header.isBlank()) {
                throw new IllegalArgumentException(
                        "CSV header " + (column + 1) + " must not be blank.");
            }
        }
        for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
            List<?> row = rows.get(rowIndex);
            if (row == null) {
                throw new IllegalArgumentException(
                        "CSV row " + (rowIndex + 1) + " must not be null.");
            }
            if (row.size() != headers.size()) {
                throw new IllegalArgumentException("CSV row " + (rowIndex + 1)
                        + " has " + row.size() + " columns; expected " + headers.size() + ".");
            }
        }
    }

    private static void writeRow(Writer writer, List<?> values) throws IOException {
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) writer.write(',');
            Object value = values.get(i);
            String text = value instanceof BigDecimal
                    ? ((BigDecimal) value).setScale(2, RoundingMode.HALF_UP).toPlainString()
                    : value == null ? "" : value.toString();
            if (value instanceof String && startsWithFormula(text)) {
                text = "'" + text;
            }
            writer.write('"');
            writer.write(text.replace("\"", "\"\""));
            writer.write('"');
        }
        writer.write("\r\n");
    }

    private static boolean startsWithFormula(String text) {
        for (int i = 0; i < text.length(); i++) {
            char current = text.charAt(i);
            if (Character.isWhitespace(current) || Character.isISOControl(current)) {
                continue;
            }
            return current == '=' || current == '+' || current == '-' || current == '@';
        }
        return false;
    }
}
