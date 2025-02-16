package io.quarkus.mongodb;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;

import jakarta.inject.Inject;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.mongodb.client.MongoClient;

import de.flapdoodle.embed.mongo.commands.MongodArguments;
import de.flapdoodle.embed.mongo.transitions.ImmutableMongod;
import de.flapdoodle.reverse.transitions.Start;
import io.quarkus.mongodb.reactive.ReactiveMongoClient;
import io.quarkus.test.QuarkusUnitTest;
import io.smallrye.certs.CertificateGenerator;
import io.smallrye.certs.CertificateRequest;
import io.smallrye.certs.Format;

public class MongoTlsRegistryTest extends MongoTestBase {
    static final Path BASEDIR;
    static {
        try {
            CertificateRequest request = new CertificateRequest()
                    .withName("mongo-cert")
                    .withClientCertificate()
                    .withFormat(Format.PEM);
            BASEDIR = Files.createTempDirectory("mongo");
            new CertificateGenerator(BASEDIR, false).generate(request);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static final Path serverCertPath = BASEDIR.resolve("mongo-cert.crt");
    private static final Path serverKeyPath = BASEDIR.resolve("mongo-cert.key");
    private static final Path serverCaPath = BASEDIR.resolve("mongo-cert-server-ca.crt");
    private static final Path serverCertKeyPath = BASEDIR.resolve("mongo-certkey.pem");
    private static final Path clientCaPath = BASEDIR.resolve("mongo-cert-client-ca.crt");
    private static final Path clientKeyPath = BASEDIR.resolve("mongo-cert-client.key");
    private static final Path clientCertPath = BASEDIR.resolve("mongo-cert-client.crt");
    @RegisterExtension
    static final QuarkusUnitTest config = new QuarkusUnitTest()
            .withApplicationRoot((jar) -> jar.addClasses(MongoTestBase.class))
            .overrideConfigKey("quarkus.tls.mongo.trust-store.pem.certs", clientCaPath.toAbsolutePath().toString())
            .overrideConfigKey("quarkus.tls.mongo.key-store.pem.0.cert", clientCertPath.toAbsolutePath().toString())
            .overrideConfigKey("quarkus.tls.mongo.key-store.pem.0.key", clientKeyPath.toAbsolutePath().toString());

    @Inject
    MongoClient client;
    @Inject
    ReactiveMongoClient reactiveClient;

    @AfterEach
    void cleanup() {
        if (reactiveClient != null) {
            reactiveClient.close();
        }
        if (client != null) {
            client.close();
        }
    }

    @Override
    protected ImmutableMongod addExtraConfig(ImmutableMongod mongo) {

        try {
            try (var fos = Files.newOutputStream(serverCertKeyPath)) {
                Files.copy(serverCertPath, fos);
                Files.copy(serverKeyPath, fos);
            }
            return mongo.withMongodArguments(Start.to(mongo.mongodArguments().destination())
                    .initializedWith(MongodArguments.builder()
                            .putArgs("--tlsCertificateKeyFile",
                                    serverCertKeyPath.toAbsolutePath()
                                            .toString())
                            .putArgs("--tlsMode", "requireTLS")
                            .putArgs("--tlsCAFile",
                                    serverCaPath.toAbsolutePath()
                                            .toString())
                            .build()));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

    }

    @Test
    public void testClientWorksWithTls() {
        assertThat(client.listDatabaseNames().first()).isNotEmpty();
        assertThat(reactiveClient.listDatabases().collect().first().await().indefinitely()).isNotEmpty();
    }
}
