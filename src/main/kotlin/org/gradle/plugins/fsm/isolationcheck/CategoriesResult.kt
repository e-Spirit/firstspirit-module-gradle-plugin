package org.gradle.plugins.fsm.isolationcheck

import de.espirit.mavenplugins.fsmchecker.Category
import org.json.JSONArray
import org.json.JSONObject

class CategoriesResult(categoriesResponse: String) {

    private val categoriesCount: Map<String, CategoryDto>

    init {
        val categories = JSONArray(categoriesResponse)
        categoriesCount = initializeCategoriesCount(categories)
    }


    private fun initializeCategoriesCount(categories: JSONArray): Map<String, CategoryDto> {
        return categories
            .map { CategoryDto.of(it as JSONObject) }
            .associateBy({ it.category }, { it })
    }


    fun violationCountFor(category: Category): Int {
        return categoriesCount[category.name]?.count ?: 0
    }


    fun descriptionFor(category: Category): String {
        return categoriesCount[category.name]?.description ?: ""
    }

}