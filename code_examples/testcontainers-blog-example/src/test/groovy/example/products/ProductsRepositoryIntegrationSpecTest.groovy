package example.products

import com.amazonaws.services.dynamodbv2.model.*
import example.RepositorySpecification
import spock.lang.Subject
import spock.lang.Unroll

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