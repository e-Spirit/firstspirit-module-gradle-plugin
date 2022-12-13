package org.gradle.plugins.fsm.isolationcheck

import org.json.JSONObject

data class CategoryDto(val category: String, val description: String, val count: Int) {

    companion object {
        fun of(jsonObject: JSONObject): CategoryDto {
            return CategoryDto(
                jsonObject.getString("category"),
                jsonObject.getString("description"),
                jsonObject.getInt("count")
            )
        }
    }

}
