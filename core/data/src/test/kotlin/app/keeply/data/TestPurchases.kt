package app.keeply.data

import app.keeply.domain.CurrencyCode
import app.keeply.domain.DocumentId
import app.keeply.domain.Money
import app.keeply.domain.Purchase
import app.keeply.domain.PurchaseId
import app.keeply.domain.ReturnPolicy
import app.keeply.domain.ReturnPolicySource
import app.keeply.domain.ReturnWindowCalculator
import app.keeply.domain.Warranty
import app.keeply.domain.WarrantyProvenance
import app.keeply.domain.WarrantyTerm
import java.time.Instant
import java.time.LocalDate

internal object TestPurchases {
    val NOW: Instant = Instant.parse("2026-09-12T10:00:00Z")
    val BOUGHT: LocalDate = LocalDate.of(2026, 9, 12)

    fun purchase(
        name: String = "Sony Headphones",
        merchantName: String? = "Best Buy",
        price: Money? = Money.of("249.99", CurrencyCode.USD),
        date: LocalDate? = BOUGHT,
        tags: List<String> = emptyList(),
        receiptId: DocumentId? = null,
        archived: Boolean = false,
        demo: Boolean = false,
        notes: String? = null,
        receiptNumber: String? = null,
    ): Purchase = Purchase(
        id = PurchaseId.new(),
        productName = name,
        merchantId = null,
        merchantName = merchantName,
        purchaseDate = date,
        price = price,
        tax = price?.let { Money(it.amountMinor / 10, it.currency) },
        categoryId = null,
        receiptDocumentId = receiptId,
        receiptNumber = receiptNumber,
        returnWindow = ReturnWindowCalculator.resolve(
            date,
            ReturnPolicy.Days(30),
            ReturnPolicySource.SAVED_MERCHANT_RULE,
        ),
        warranty = Warranty(
            term = WarrantyTerm.Months(12),
            provenance = WarrantyProvenance.DOCUMENTED,
            startDate = date,
            endDate = date?.plusMonths(12),
        ),
        notes = notes,
        tags = tags,
        isArchived = archived,
        isDemoData = demo,
        createdAt = NOW,
        updatedAt = NOW,
    )
}
