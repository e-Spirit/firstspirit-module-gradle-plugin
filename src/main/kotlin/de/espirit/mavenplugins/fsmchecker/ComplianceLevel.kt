package de.espirit.mavenplugins.fsmchecker

import de.espirit.mavenplugins.fsmchecker.Category.*
import java.util.*

enum class ComplianceLevel(vararg categories: Category) {

    /**
     * Asserts that
     *
     * - there is no use of implementation classes that are not available in the isolated runtime</li>
     * - no classes are shipped which are part of the ContentCreator</li>
     */
    MINIMAL(IMPL_USAGE, CONFLICT_WITH_CONTENT_CREATOR),

    /**
     * In addition to [ComplianceLevel.MINIMAL], the default level also prohibits the use of internal e-Spirit
     * classes, which are not part of the public API. These classes are available in the isolated runtime but are
     * subject to change without prior notice.
     */
    DEFAULT(NON_API_USAGE),

    /**
     * The highest compliance build requires that in addition to [ComplianceLevel.DEFAULT], there is no usage of
     * deprecated API.
     */
    HIGHEST(DEPRECATED_API_USAGE);

    /**
     * Not all but the directly defined categories of this compliance level.
     */
    val categories: MutableSet<Category>

    init {
        this.categories = EnumSet.noneOf(Category::class.java)
        this.categories.addAll(categories)
    }


    /**
     * Returns all (the directly defined and inherited) categories of this compliance level.
     */
    fun getAllCategories(): Set<Category>  {
        val allCategories = EnumSet.noneOf(Category::class.java)

        for (i in 0..ordinal) {
            allCategories.addAll(values()[i].categories)
        }

        return allCategories
    }

}