/**
 * The application layer: use cases that orchestrate domain objects, plus the outbound ports they
 * depend on.
 *
 * <p>Use cases are plain classes with constructor dependencies and no annotations, which keeps them
 * unit testable without a Spring context. Wiring lives in
 * {@code com.physicsfactory.infrastructure.config.BootstrapConfiguration}.
 */
package com.physicsfactory.application;
