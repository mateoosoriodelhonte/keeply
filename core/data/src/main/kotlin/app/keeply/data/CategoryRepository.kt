package app.keeply.data

import app.keeply.data.sql.KeeplyDatabase
import app.keeply.domain.Category
import app.keeply.domain.CategoryId

/** Categories, including the starter set Keeply installs on first run. */
public class CategoryRepository internal constructor(private val database: KeeplyDatabase) {
    private val queries get() = database.categoryQueries

    public fun all(): List<Category> = queries.selectAll().executeAsList().map(Mappers::toCategory)

    public fun get(id: CategoryId): Category? = queries.selectById(id.value).executeAsOneOrNull()?.let(Mappers::toCategory)

    public fun save(category: Category) {
        val (kind, value) = Mappers.suggestedWarrantyColumns(category.suggestedWarranty)
        queries.upsert(
            id = category.id.value,
            name = category.name,
            icon = category.icon,
            is_built_in = if (category.isBuiltIn) 1L else 0L,
            sort_order = category.sortOrder.toLong(),
            suggested_warranty_kind = kind,
            suggested_warranty_value = value,
        )
    }

    /** Built-in categories cannot be deleted, only renamed, so a purchase never loses its category. */
    public fun delete(id: CategoryId) {
        queries.deleteById(id.value)
    }

    /** Installs the starter categories if none exist. Safe to call on every launch. */
    public fun installDefaultsIfEmpty() {
        database.transaction {
            if (queries.countAll().executeAsOne() == 0L) {
                Category.defaults().forEach(::save)
            }
        }
    }
}
