---
layout: post
title:  "Testcontainers: a guide to hassle-free integration testing"
date:   2018-10-08 12:00:00 +0000
categories: Kotlin, Spock, Docker, Testing, Testcontainers
---

## There are times when “mocking the world” is just not enough.

Database queries, third-party software or some API calls just have to be tested against the real thing.
This is especially true when we are working with Cloud, as it provides tools as services with which our applications need to integrate.
So what do we do when that happens? We spin up Docker, of course!

Docker is a neat tool that makes life a lot easier in many ways.
Recently, I worked on a project that reminded me that it can be simplified even further - thanks to Testcontainers.
As a huge TDD fan, I started with writing tests and I thought I'd share a quick, worked example of how Testcontainers can remove some of the hassle of integration testing.

## Say Hello to Testcontainers

So what exactly is Testcontainers?

> Testcontainers is a Java 8 library that supports JUnit tests, providing lightweight, throwaway instances of common databases, Selenium web browsers, or anything else that can run in a Docker container.

—[Testcontainers GitHub](https://github.com/testcontainers/testcontainers-java)

What that means is that it is possible (and extremely easy) to spin up a Docker container straight from your integration tests.
No scripting required - everything is handled by the test code.

## A Worked Example

Imagine you want to write some code that integrates with AWS DynamoDB.
There are quite a few ways to run this service - or an equivalent - on your machine:

 * Download and run [DynamoDB jar file](https://docs.aws.amazon.com/amazondynamodb/latest/developerguide/DynamoDBLocal.DownloadingAndRunning.html).
 * Download and run [dynalite](https://github.com/mhart/dynalite).
 * Run [embedded DynamoDB](https://docs.aws.amazon.com/amazondynamodb/latest/developerguide/DynamoDBLocal.Maven.html) straight from your Maven project.
 * Spin up a [Docker container](https://hub.docker.com/r/amazon/dynamodb-local/).

Unfortunately, most of these solutions require additional dependencies, like SQLite. On top of that, unlike Docker, they are [platform-dependent](https://stackoverflow.com/questions/34137043/amazon-dynamodb-local-unknown-error-exception-or-failure).

In my example, I will be using the [amazon/dynamodb-local](https://hub.docker.com/r/amazon/dynamodb-local/) Docker image.

### Application Setup

To make it clean and easy, our sample application will consist of only two classes.
The code is in Kotlin, but could be in Java or Scala.
Same thing applies to the tests - I have chosen Spock, but Testcontainers can be employed by anything that runs on JVM.

#### Product and Products Repository

Our application's purpose is to save `Product` objects:

{% highlight kotlin %}
internal data class Product(val serialId: UUID,
                            val name: String,
                            val price: BigDecimal) {

    internal constructor(serialId: String, name: String, price: BigDecimal)
            : this(UUID.fromString(serialId), name, price)

}
{% endhighlight %}

To a DynamoDB database:

{% highlight kotlin %}
internal class ProductsRepository(dynamoDB: DynamoDB, productsTableName: String) {

    private val productsTable = dynamoDB.getTable(productsTableName)

    fun add(product: Product): PutItemOutcome? = Item()
            .withString("serialId", product.serialId.toString())
            .withString("name", product.name)
            .withNumber("price", product.price)
            .let(productsTable::putItem)

}
{% endhighlight %}

Simple enough!

#### The test

To run the test, we need to add the following Maven dependency:

{% highlight xml %}
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>testcontainers</artifactId>
    <version>1.16.2</version>
    <scope>test</scope>
</dependency>
{% endhighlight %}

When testing database queries, and other things that require an expensive setup, I tend to put the setup code in a common base class.
Try not to add any more code to the base class than the minimum necessary for setup and cleanup.

Inheritance in tests can be a [code smell](https://www.petrikainulainen.net/programming/unit-testing/3-reasons-why-we-should-not-use-inheritance-in-our-tests/).

{% highlight groovy %}
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
{% endhighlight %}

And finally, our very simple test case:

{% highlight groovy %}
class ProductsRepositoryIntegrationSpecTest extends RepositorySpecification {

    private final static String TEST_PRODUCTS_TABLE_NAME = 'products'
    private final static String PRODUCTS_HASH_KEY_NAME = 'serialId'
    private final static ScalarAttributeType PRODUCTS_HASH_KEY_TYPE = ScalarAttributeType.S

    def setup() {
        getDynamoDB().createTable(
                new CreateTableRequest().withTableName(TEST_PRODUCTS_TABLE_NAME)
                                        .withProvisionedThroughput(new ProvisionedThroughput(10L, 10L))
                                        .withKeySchema([new KeySchemaElement(PRODUCTS_HASH_KEY_NAME, KeyType.HASH)])
                                        .withAttributeDefinitions([new AttributeDefinition(PRODUCTS_HASH_KEY_NAME,
                                                PRODUCTS_HASH_KEY_TYPE)])
        )
    }

    @Subject
    def productsRepository = new ProductsRepository(getDynamoDB(), TEST_PRODUCTS_TABLE_NAME)

    @Unroll
    'should add #givenProduct to the database'() {
        when: 'the product is added in the repository'
          def addingResult = productsRepository.add(givenProduct)

        and: 'the product is then fetched from the database'
          def productFromDatabase = getDynamoDB().getTable(TEST_PRODUCTS_TABLE_NAME)
                                                 .getItem(PRODUCTS_HASH_KEY_NAME, givenProduct.serialId.toString())

        then: 'the request to the database was successful'
          addingResult.putItemResult.sdkHttpMetadata.httpStatusCode == 200

        and: 'product information matches'
          givenProduct.serialId == UUID.fromString(productFromDatabase.getString(PRODUCTS_HASH_KEY_NAME))
          givenProduct.name == productFromDatabase.getString('name')
          givenProduct.price == productFromDatabase.getNumber('price')

        where:
          givenProduct = new Product(UUID.randomUUID(), 'Test', 10.5G)
    }

    def cleanup() {
        getDynamoDB().listTables().each { it.delete() }
    }

}
{% endhighlight %}

Notice how I am always creating the table and then deleting it after each test case.
This is not really necessary with a single test case, but when more features are tested the database will eventually get polluted.
It is better to keep it clean so no accidental dependencies are introduced between the test cases.

Also, keep in mind that the entire container, along with the DynamoDB instance, exists only for the duration of tests and will be discarded afterwards.
Consecutive test runs should not depend on each other!

Complete project example can be found [here](https://github.com/Jezorko/Jezorko.github.io/tree/master/code_examples/testcontainers-blog-example).

## Troubleshooting

Obviously, the Docker daemon needs to be started before you run your tests.
If you forgot to start it, the following exception will be thrown:

> java.lang.IllegalStateException: Could not find a valid Docker environment. Please see logs and check configuration

To fix it, simply start Docker.

## Key takeaways

 * Testcontainers provide a degree of flexibility for integration tests that's often overlooked - make the most of them for more hassle-free integration testing.
 * You can start one or multiple containers before each test case or only once per tests execution.
 * The library is meant to be used for testing (hence the name). If you are looking for a better way to manage your containers, use orchestration tools such as [Kubernetes](https://kubernetes.io/).
 * Be mindful of the environment you’re planning to run your tests in. If it is already dockerized, Testcontainers will not be feasible. Using Docker in Docker is usually a [bad idea](https://jpetazzo.github.io/2015/09/03/do-not-use-docker-in-docker-for-ci/).

## Footnote

_Originally published at [AND Digital's Engineering Blog](https://www.and.digital/blog/how-to-use-testcontainers-for-hassle-free-integration-testing) on 2018-10-08._