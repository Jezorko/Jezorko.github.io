package example.products

import com.amazonaws.services.dynamodbv2.document.DynamoDB
import com.amazonaws.services.dynamodbv2.document.Item
import com.amazonaws.services.dynamodbv2.document.PutItemOutcome

internal class ProductsRepository(dynamoDB: DynamoDB, productsTableName: String) {

    private val productsTable = dynamoDB.getTable(productsTableName)

    fun add(product: Product): PutItemOutcome? = Item()
            .withString("serialId", product.serialId.toString())
            .withString("name", product.name)
            .withNumber("price", product.price)
            .let(productsTable::putItem)

}