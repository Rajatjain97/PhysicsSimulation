/**
 * The infrastructure layer: everything that knows about Spring Boot, Logback, the filesystem and the
 * host operating system.
 *
 * <p>Dependencies point inwards only - infrastructure may depend on
 * {@code com.physicsfactory.application} and {@code com.physicsfactory.domain}, never the reverse.
 */
package com.physicsfactory.infrastructure;
