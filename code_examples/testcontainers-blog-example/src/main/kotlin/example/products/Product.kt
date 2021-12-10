package example.products

import java.math.BigDecimal
import java.util.*

internal data class Product(val serialId: UUID,
                            val name: String,
                            val price: BigDecimal) {

    internal constructor(serialId: String, name: String, price: BigDecimal)
            : this(UUID.fromString(serialId), name, price)

}