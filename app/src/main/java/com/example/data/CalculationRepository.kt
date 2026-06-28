package com.example.data

import kotlinx.coroutines.flow.Flow

class CalculationRepository(private val dao: CalculationDao) {
    val allHistory: Flow<List<CalculationEntity>> = dao.getAllHistory()

    suspend fun insert(entity: CalculationEntity) {
        dao.insertCalculation(entity)
    }

    suspend fun deleteById(id: Int) {
        dao.deleteById(id)
    }

    suspend fun clearHistory() {
        dao.clearAllHistory()
    }

    suspend fun toggleFavorite(id: Int, currentFav: Boolean) {
        dao.updateFavorite(id, !currentFav)
    }
}
