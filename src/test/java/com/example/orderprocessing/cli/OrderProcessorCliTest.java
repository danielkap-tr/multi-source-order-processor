package com.example.orderprocessing.cli;

import com.example.orderprocessing.TestJson;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class OrderProcessorCliTest {

    private static final String SOURCE_A = """
            [{"orderId":"ORD-10001","customerId":"CUST-501","customerName":"John Smith","country":"US",
              "orderDate":"2026-09-01T10:30:00","productCode":"P100","quantity":2,"unitPrice":125.50}]
            """;
    private static final String SOURCE_B = """
            [{"order_number":"ORD-20001","customer":{"id":"CUST-842","first_name":"Jane","last_name":"Miller",
              "country_code":"DE"},"created_at":"2026-09-01T11:15:00","item":{"sku":"P200","units":3,"price":80.00}},
             {"order_number":"ORD-20099","customer":{"id":"C","first_name":"No","last_name":"Country",
              "country_code":"ZZ"},"created_at":"2026-09-01T11:15:00","item":{"sku":"P","units":1,"price":1.00}}]
            """;

    @Test
    void processes_both_sources_and_writes_delivered_and_error_files(@TempDir Path dir) throws Exception {
        Path aFile = Files.writeString(dir.resolve("a.json"), SOURCE_A);
        Path bFile = Files.writeString(dir.resolve("b.json"), SOURCE_B);
        Path output = dir.resolve("delivered.json");
        Path errors = dir.resolve("errors.json");

        int exit = new OrderProcessorCli().run(new OrderProcessorCli.Args(
                java.util.List.of(aFile, bFile), output, errors, null));

        assertThat(exit).isEqualTo(1); // one order (ZZ country) was dead-lettered

        JsonNode delivered = TestJson.parse(Files.readString(output));
        assertThat(delivered).hasSize(2);
        assertThat(delivered).extracting(n -> n.get("orderReference").asText())
                .containsExactlyInAnyOrder("ORD-10001", "ORD-20001");

        JsonNode deadLettered = TestJson.parse(Files.readString(errors));
        assertThat(deadLettered).hasSize(1);
        assertThat(deadLettered.get(0).get("reason").asText()).contains("ZZ");
    }

    @Test
    void reads_every_json_file_in_a_directory(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("a.json"), SOURCE_A);
        Path output = dir.resolve("out.json");

        int exit = new OrderProcessorCli().run(new OrderProcessorCli.Args(
                java.util.List.of(dir), output, null, null));

        assertThat(exit).isZero();
        assertThat(TestJson.parse(Files.readString(output))).hasSize(1);
    }
}
