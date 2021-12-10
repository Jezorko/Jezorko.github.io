package example

import com.amazonaws.auth.AWSStaticCredentialsProvider
import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.client.builder.AwsClientBuilder
import com.amazonaws.regions.Regions
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder
import com.amazonaws.services.dynamodbv2.document.DynamoDB
import org.testcontainers.containers.GenericContainer
import spock.lang.Specification

class RepositorySpecification extends Specification {

    private static GenericContainer dynamoDBLocalContainer
    private static DynamoDB dynamoDB

    def setupSpec() {
        final dynamoDbPort = 8000
        dynamoDBLocalContainer = new GenericContainer('amazon/dynamodb-local').withExposedPorts(dynamoDbPort)
        dynamoDBLocalContainer.start()

        // Important! Testcontainers map the exposed ports to random ports to avoid conflicts
        def endpoint = "http://localhost:${dynamoDBLocalContainer.getMappedPort dynamoDbPort}"
        def credentials = new AWSStaticCredentialsProvider(new BasicAWSCredentials('test', 'test'))
        def endpointConfiguration = new AwsClientBuilder.EndpointConfiguration(endpoint, Regions.US_WEST_2.name)

        dynamoDB = new DynamoDB(
                AmazonDynamoDBClientBuilder.standard()
                                           .withCredentials(credentials)
                                           .withEndpointConfiguration(endpointConfiguration)
                                           .build()
        )
    }

    protected static DynamoDB getDynamoDB() {
        dynamoDB ?: { throw new IllegalStateException('DynamoDB closed or not yet initialized') }()
    }

    void cleanupSpec() {
        dynamoDB?.shutdown()
        dynamoDBLocalContainer?.stop()
    }

}