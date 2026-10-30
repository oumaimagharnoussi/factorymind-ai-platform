package com.factorymind.analytics;

import org.testcontainers.utility.DockerImageName;

/** Container images of the integration tests, the same as docker-compose.yml. */
public final class Images {

    public static final DockerImageName POSTGRES =
            DockerImageName.parse("pgvector/pgvector:0.8.1-pg17").asCompatibleSubstituteFor("postgres");

    private Images() {
    }
}
