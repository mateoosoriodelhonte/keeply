package app.keeply.domain

/**
 * Categories a person can sort purchases by.
 *
 * Keeply ships a starter set because an empty category list is a chore, not a
 * feature. All of them can be renamed, hidden or added to.
 */
public data class Category(
    val id: CategoryId,
    val name: String,
    /** An emoji shown on cards. Plain text, never rendered as markup. */
    val icon: String,
    val isBuiltIn: Boolean,
    val sortOrder: Int,
    /** A typical warranty for this kind of thing, only ever offered as a suggestion. */
    val suggestedWarranty: WarrantyTerm = WarrantyTerm.Unknown,
) {
    init {
        require(name.isNotBlank()) { "A category needs a name" }
    }

    public companion object {
        public fun defaults(): List<Category> = listOf(
            builtIn("electronics", "Electronics", "📱", 0, WarrantyTerm.Months(12)),
            builtIn("clothing", "Clothing", "👕", 1),
            builtIn("appliances", "Appliances", "🧺", 2, WarrantyTerm.Months(24)),
            builtIn("furniture", "Furniture", "🛋", 3),
            builtIn("home", "Home", "🏠", 4),
            builtIn("automotive", "Automotive", "🚗", 5),
            builtIn("school", "School", "🎒", 6),
            builtIn("health", "Health", "💊", 7),
            builtIn("other", "Other", "📦", 8),
        )

        private fun builtIn(id: String, name: String, icon: String, order: Int, warranty: WarrantyTerm = WarrantyTerm.Unknown) =
            Category(CategoryId(id), name, icon, isBuiltIn = true, sortOrder = order, suggestedWarranty = warranty)
    }
}
