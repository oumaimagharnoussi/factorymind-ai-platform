package com.factorymind.insights;

import org.testcontainers.utility.DockerImageName;

/** Container images of the integration tests, the same as docker-compose.yml. */
public final class Images {

    /** Postgres 17 with the pgvector extension, for the copilot's knowledge base. */
    public static final DockerImageName POSTGRES =
            DockerImageName.parse("pgvector/pgvector:0.8.1-pg17").asCompatibleSubstituteFor("postgres");

    private Images() {
    }
}
