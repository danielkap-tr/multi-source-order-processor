package com.example.orderprocessing.source;

/**
 * Identifies which external source system an order originated from.
 * Carried through the pipeline for observability, metrics and dead-letter routing.
 */
public enum SourceSystem {
    SOURCE_A,
    SOURCE_B
}
