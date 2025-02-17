package io.quarkus.liquibase.mongodb.runtime;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mongodb.ConnectionString;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;

import io.quarkus.arc.Arc;
import io.quarkus.runtime.annotations.RegisterForReflection;
import liquibase.exception.DatabaseException;
import liquibase.ext.mongodb.database.MongoClientDriver;

@RegisterForReflection
public class QuarkusMongoClientDriver extends MongoClientDriver {
    private static final Logger log = LoggerFactory.getLogger(QuarkusMongoClientDriver.class);

    public MongoClient connect(ConnectionString connectionString) throws DatabaseException {
        try {
            Optional<MongoClient> first = Arc.container().select(MongoClient.class).stream().findFirst();
            if (first.isPresent()) {
                log.error("TCA CLIENT " + first.get());
            }
            MongoClient client = MongoClients.create(connectionString);
            return client;
        } catch (Exception e) {
            throw new DatabaseException(
                    "Connection could not be established to: " + connectionString.getConnectionString(), e);
        }
    }
}
