package org.mmbs.tracker.data.repo

import org.mmbs.tracker.data.local.dao.AppUserDao
import org.mmbs.tracker.data.local.entity.AppUserEntity
import org.mmbs.tracker.domain.model.Role

class AppUsersRepo(private val dao: AppUserDao) {

    suspend fun getAll(): List<AppUserEntity> = dao.getAll()

    suspend fun role(email: String): Role? {
        val raw = dao.getRole(email.trim().lowercase()) ?: return null
        return Role.fromSheetValue(raw)
    }

    suspend fun isAllowed(email: String): Boolean =
        dao.get(email.trim().lowercase()) != null

    suspend fun upsertAll(entities: List<AppUserEntity>) = dao.upsertAll(entities)
    suspend fun clear() = dao.clear()
}
