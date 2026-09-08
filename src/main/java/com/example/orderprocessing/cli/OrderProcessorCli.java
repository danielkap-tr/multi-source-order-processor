package com.example.orderprocessing.cli;

import com.example.orderprocessing.delivery.DeadLetterSink;
import com.example.orderprocessing.delivery.InMemoryDeadLetterSink;
import com.example.orderprocessing.delivery.InMemoryTargetSystemClient;
import com.example.orderprocessing.enrichment.CountryReferenceService;
import com.example.orderprocessing.io.JsonMapperFactory;
import com.example.orderprocessing.processing.BatchResult;
import com.example.orderprocessing.processing.CanonicalOrderValidator;
import com.example.orderprocessing.processing.OrderProcessor;
import com.example.orderprocessing.processing.TargetOrderMapper;
import com.example.orderprocessing.source.SourceRouter;
import com.example.orderprocessing.source.SourceSystem;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Batch command-line entry point. Reads order JSON from files/directories, runs the
 * processing pipeline, writes the delivered target-system orders to one file and the
 * dead-lettered orders to another, and prints a summary.
 *
 * <pre>
 * Usage:
 *   order-processor --input &lt;file-or-dir&gt; [--input ...] --output &lt;file&gt;
 *                   [--errors &lt;file&gt;] [--source A|B|auto]
 * </pre>
 */
public final class OrderProcessorCli {

    public static void main(String[] args) {
        configureLogging();
        try {
            Args parsed = Args.parse(args);
            int exitCode = new OrderProcessorCli().run(parsed);
            System.exit(exitCode);
        } catch (IllegalArgumentException e) {
            System.err.println("Error: " + e.getMessage());
            System.err.println();
            System.err.println(Args.usage());
            System.exit(2);
        }
    }

    /**
     * Give java.util.logging a compact, locale-stable one-line format
     * ("2026-09-09 02:11:24 WARNING message") instead of its two-line, locale-dependent
     * default. Must run before the first log record is emitted.
     */
    private static void configureLogging() {
        if (System.getProperty("java.util.logging.SimpleFormatter.format") == null) {
            System.setProperty("java.util.logging.SimpleFormatter.format",
                    "%1$tF %1$tT %4$-7s %5$s%6$s%n");
        }
    }

    int run(Args args) {
        ObjectMapper json = JsonMapperFactory.create();

        // In-memory sinks: the CLI reads them back after the run to write the result files.
        // A production deployment would inject an HTTP/queue client and a real dead-letter queue.
        InMemoryTargetSystemClient target = new InMemoryTargetSystemClient();
        InMemoryDeadLetterSink deadLetters = new InMemoryDeadLetterSink();
        OrderProcessor processor = new OrderProcessor(
                SourceRouter.withDefaults(),
                new CanonicalOrderValidator(),
                new TargetOrderMapper(CountryReferenceService.withAssignmentDefaults()),
                target,
                deadLetters
        );

        List<JsonNode> rawOrders = loadOrders(json, args.inputs());
        System.out.printf("Loaded %d order(s) from %d input path(s)%n", rawOrders.size(), args.inputs().size());

        BatchResult result = processor.processBatch(rawOrders, args.source());

        writeArray(json, args.output(), target.delivered());
        Path errorsPath = args.errors();
        if (errorsPath != null) {
            writeArray(json, errorsPath, deadLetters.failures());
        }

        System.out.println();
        System.out.println("Processing summary");
        System.out.println("  " + result);
        System.out.println("  delivered -> " + args.output().toAbsolutePath());
        if (errorsPath != null) {
            System.out.println("  dead-letters -> " + errorsPath.toAbsolutePath());
        }
        for (DeadLetterSink.FailedOrder failure : deadLetters.failures()) {
            System.out.println("  ! [" + failure.source() + "] " + failure.reason());
        }

        return result.failed() == 0 ? 0 : 1;
    }

    private static List<JsonNode> loadOrders(ObjectMapper json, List<Path> inputs) {
        List<JsonNode> orders = new ArrayList<>();
        for (Path input : inputs) {
            for (Path file : expand(input)) {
                orders.addAll(readOrdersFromFile(json, file));
            }
        }
        return orders;
    }

    private static List<Path> expand(Path input) {
        if (Files.isDirectory(input)) {
            try (Stream<Path> files = Files.list(input)) {
                return files.filter(p -> p.toString().toLowerCase().endsWith(".json"))
                        .sorted()
                        .toList();
            } catch (IOException e) {
                throw new UncheckedIOException("Cannot list directory " + input, e);
            }
        }
        if (!Files.isRegularFile(input)) {
            throw new IllegalArgumentException("Input path does not exist: " + input);
        }
        return List.of(input);
    }

    private static List<JsonNode> readOrdersFromFile(ObjectMapper json, Path file) {
        try {
            JsonNode root = json.readTree(Files.readString(file));
            if (root.isArray()) {
                List<JsonNode> list = new ArrayList<>();
                root.forEach(list::add);
                return list;
            }
            return List.of(root);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read order file " + file, e);
        }
    }

    private static void writeArray(ObjectMapper json, Path path, List<?> items) {
        try {
            if (path.getParent() != null) {
                Files.createDirectories(path.getParent());
            }
            // Serialize the values directly (no JsonNode round-trip) so BigDecimal money
            // keeps its 2-decimal scale.
            Files.writeString(path, json.writerWithDefaultPrettyPrinter().writeValueAsString(items));
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot write output file " + path, e);
        }
    }

    /** Parsed command-line arguments. */
    record Args(List<Path> inputs, Path output, Path errors, SourceSystem source) {

        static Args parse(String[] argv) {
            List<Path> inputs = new ArrayList<>();
            Path output = null;
            Path errors = null;
            SourceSystem source = null;

            for (int i = 0; i < argv.length; i++) {
                String arg = argv[i];
                switch (arg) {
                    case "--input", "-i" -> inputs.add(Path.of(requireValue(argv, ++i, arg)));
                    case "--output", "-o" -> output = Path.of(requireValue(argv, ++i, arg));
                    case "--errors", "-e" -> errors = Path.of(requireValue(argv, ++i, arg));
                    case "--source", "-s" -> source = parseSource(requireValue(argv, ++i, arg));
                    case "--help", "-h" -> {
                        System.out.println(usage());
                        System.exit(0);
                    }
                    default -> throw new IllegalArgumentException("Unknown argument: " + arg);
                }
            }

            if (inputs.isEmpty()) {
                throw new IllegalArgumentException("At least one --input is required");
            }
            if (output == null) {
                throw new IllegalArgumentException("--output is required");
            }
            return new Args(List.copyOf(inputs), output, errors, source);
        }

        private static String requireValue(String[] argv, int index, String flag) {
            if (index >= argv.length) {
                throw new IllegalArgumentException("Missing value for " + flag);
            }
            return argv[index];
        }

        private static SourceSystem parseSource(String value) {
            return switch (value.toUpperCase()) {
                case "A", "SOURCE_A" -> SourceSystem.SOURCE_A;
                case "B", "SOURCE_B" -> SourceSystem.SOURCE_B;
                case "AUTO" -> null;
                default -> throw new IllegalArgumentException("--source must be A, B or auto");
            };
        }

        static String usage() {
            return """
                   Multi-Source Order Processor

                   Usage:
                     order-processor --input <file-or-dir> [--input ...] --output <file> [--errors <file>] [--source A|B|auto]

                   Options:
                     -i, --input    Order JSON file or a directory of *.json files (repeatable).
                                    Each file may hold a single order object or an array of orders.
                     -o, --output   File to write the delivered target-system orders (JSON array).
                     -e, --errors   File to write dead-lettered orders with failure reasons (JSON array).
                     -s, --source   Declare the source system for all inputs. Default: auto-detect per order.
                     -h, --help     Show this help.

                   Exit codes:
                     0  all orders delivered
                     1  completed, but one or more orders were dead-lettered
                     2  invalid command-line arguments
                   """;
        }
    }
}
