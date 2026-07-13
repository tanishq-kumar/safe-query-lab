package dev.querylab.engine.jpa;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

/** Minimal Boot app so the conformance test can bootstrap JPA against the shared container. */
@SpringBootApplication
@Import(JpaEngineConfiguration.class)
class JpaConformanceTestApp {
}
