package org.mmbs.tracker.sync

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
import org.mmbs.tracker.data.local.SyncState
import org.mmbs.tracker.data.local.entity.AppUserEntity
import org.mmbs.tracker.data.local.entity.BankReconEntity
import org.mmbs.tracker.data.local.entity.ConfigKvEntity
import org.mmbs.tracker.data.local.entity.MemberEntity
import org.mmbs.tracker.data.local.entity.MembershipRowEntity
import org.mmbs.tracker.data.local.entity.TransactionEntity
import org.mmbs.tracker.domain.fy.FinancialYear.FyColumn
import org.mmbs.tracker.domain.model.FyCell

/**
 * Per-sheet row mappers. Each mapper owns:
 *
 *  - sheet-row -> entity        (for pull)
 *  - entity    -> sheet-row     (for push / append)
 *  - entity    -> snapshot JSON (for conflict detection; identical to
 *    "entity -> sheet-row" but expressed as a stable JSON map so future
 *    column rearrangements don't invalidate stored snapshots)
 *
 * Snapshots are intentionally cell-keyed: "A", "B", ... so comparing two
 * snapshots is just a `Map<String,String>` deep equals. Phase A stores them
 * on the entity in `lastSyncedSnapshot`.
 */
object RowMapper {

    private val JSON = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    // --- shared helpers ---

    /** Pad to [size] with empty strings; truncate if longer. */
    private fun List<String>.padTo(size: Int): List<String> =
        if (this.size >= size) this.take(size)
        else this + List(size - this.size) { "" }

    private fun List<String>.cell(idx: Int): String =
        if (idx in indices) this[idx] else ""

    /** Build the {"A":"..","B":".."} map from a row list and column count. */
    fun rowToSnapshot(row: List<String>, colCount: Int): String {
        val padded = row.padTo(colCount)
        val map = buildMap {
            padded.forEachIndexed { i, v ->
                put(columnLetter(i), v)
            }
        }
        return JSON.encodeToString(map)
    }

    /** "A", "Z", "AA" for 0-based index. Local copy to avoid a circular dep. */
    internal fun columnLetter(colIndex: Int): String {
        var n = colIndex
        val sb = StringBuilder()
        while (true) {
            val rem = n % 26
            sb.append('A' + rem)
            n = n / 26 - 1
            if (n < 0) break
        }
        return sb.reverse().toString()
    }

    fun snapshotToMap(snapshot: String): Map<String, String> {
        if (snapshot.isBlank()) return emptyMap()
        return try {
            val obj = JSON.parseToJsonElement(snapshot).jsonObject
            obj.mapValues { (_, v) -> v.asString() }
        } catch (_: Throwable) {
            emptyMap()
        }
    }

    private fun kotlinx.serialization.json.JsonElement.asString(): String =
        when (this) {
            is JsonPrimitive -> if (isString) content else content
            else -> toString()
        }

    // ===== MEMBERS (A..X) =====

    object Members {
        /**
         * Columns A..X — see MemberEntity for full mapping.
         */
        fun fromRow(row: List<String>, sheetRowIndex: Int): MemberEntity {
            val c = row.padTo(SheetSpec.MEMBERS_COL_COUNT)
            val memberId = c.cell(0).trim()
            return MemberEntity(
                memberId = memberId,
                regDate = c.cell(1),
                primaryName = c.cell(2),
                primaryMobile = c.cell(3),
                email = c.cell(4),
                fm2Name = c.cell(5),  fm2Rel = c.cell(6),  fm2Mobile = c.cell(7),  fm2WaGroup = c.cell(8),
                fm3Name = c.cell(9),  fm3Rel = c.cell(10), fm3Mobile = c.cell(11), fm3WaGroup = c.cell(12),
                fm4Name = c.cell(13), fm4Rel = c.cell(14), fm4Mobile = c.cell(15), fm4WaGroup = c.cell(16),
                address = c.cell(17),
                firstYear = c.cell(18),
                status = c.cell(19),
                totalFamilyMembers = c.cell(20),
                waGroupCount = c.cell(21),
                waValidation = c.cell(22),
                notes = c.cell(23),
                sheetRowIndex = sheetRowIndex,
                syncStatus = SyncState.SYNCED,
                lastLocalModifiedAt = 0L,
                lastSyncedSnapshot = rowToSnapshot(c, SheetSpec.MEMBERS_COL_COUNT),
                pushError = null,
            )
        }

        fun toRow(e: MemberEntity): List<String> = listOf(
            e.memberId, e.regDate, e.primaryName, e.primaryMobile, e.email,
            e.fm2Name, e.fm2Rel, e.fm2Mobile, e.fm2WaGroup,
            e.fm3Name, e.fm3Rel, e.fm3Mobile, e.fm3WaGroup,
            e.fm4Name, e.fm4Rel, e.fm4Mobile, e.fm4WaGroup,
            e.address, e.firstYear, e.status, e.totalFamilyMembers,
            e.waGroupCount, e.waValidation, e.notes,
        )

        fun snapshotOf(e: MemberEntity): String =
            rowToSnapshot(toRow(e), SheetSpec.MEMBERS_COL_COUNT)
    }

    // ===== TRANSACTIONS (A..O) =====

    object Transactions {
        fun fromRow(row: List<String>, sheetRowIndex: Int): TransactionEntity {
            val c = row.padTo(SheetSpec.TRANSACTIONS_COL_COUNT)
            return TransactionEntity(
                txnId = c.cell(0).trim(),
                date = c.cell(1),
                category = c.cell(2),
                subCategory = c.cell(3),
                description = c.cell(4),
                memberId = c.cell(5),
                accountCode = c.cell(6),
                receipt = c.cell(7),
                payment = c.cell(8),
                runningBalance = c.cell(9),
                paymentMode = c.cell(10),
                reference = c.cell(11),
                reconciled = c.cell(12),
                reconDate = c.cell(13),
                notes = c.cell(14),
                sheetRowIndex = sheetRowIndex,
                syncStatus = SyncState.SYNCED,
                lastLocalModifiedAt = 0L,
                lastSyncedSnapshot = rowToSnapshot(c, SheetSpec.TRANSACTIONS_COL_COUNT),
                pushError = null,
            )
        }

        fun toRow(e: TransactionEntity): List<String> = listOf(
            e.txnId, e.date, e.category, e.subCategory, e.description,
            e.memberId, e.accountCode, e.receipt, e.payment, e.runningBalance,
            e.paymentMode, e.reference, e.reconciled, e.reconDate, e.notes,
        )

        fun snapshotOf(e: TransactionEntity): String =
            rowToSnapshot(toRow(e), SheetSpec.TRANSACTIONS_COL_COUNT)
    }

    // ===== BANK RECON (A..M) =====

    object BankRecon {
        fun fromRow(row: List<String>, sheetRowIndex: Int): BankReconEntity {
            val c = row.padTo(SheetSpec.BANK_RECON_COL_COUNT)
            return BankReconEntity(
                reconId = c.cell(0).trim(),
                statementFrom = c.cell(1),
                statementTo = c.cell(2),
                openingBalance = c.cell(3),
                totalCredits = c.cell(4),
                totalDebits = c.cell(5),
                bankClosing = c.cell(6),
                appClosing = c.cell(7),
                depositsInTransit = c.cell(8),
                outstandingPayments = c.cell(9),
                reconciledBalance = c.cell(10),
                reconDate = c.cell(11),
                notes = c.cell(12),
                sheetRowIndex = sheetRowIndex,
                syncStatus = SyncState.SYNCED,
                lastLocalModifiedAt = 0L,
                lastSyncedSnapshot = rowToSnapshot(c, SheetSpec.BANK_RECON_COL_COUNT),
                pushError = null,
            )
        }

        fun toRow(e: BankReconEntity): List<String> = listOf(
            e.reconId, e.statementFrom, e.statementTo, e.openingBalance,
            e.totalCredits, e.totalDebits, e.bankClosing, e.appClosing,
            e.depositsInTransit, e.outstandingPayments, e.reconciledBalance,
            e.reconDate, e.notes,
        )

        fun snapshotOf(e: BankReconEntity): String =
            rowToSnapshot(toRow(e), SheetSpec.BANK_RECON_COL_COUNT)
    }

    // ===== APP USERS (A..B) =====

    object AppUsers {
        fun fromRow(row: List<String>): AppUserEntity? {
            val email = row.cell(0).trim().lowercase().ifBlank { return null }
            val role = row.cell(1).trim()
            return AppUserEntity(email = email, role = role)
        }

        fun toRow(e: AppUserEntity): List<String> = listOf(e.email, e.role)
    }

    // ===== CONFIG (dual-column value lists; see PRD §9.4) =====

    object Config {
        /**
         * Config sheet is wide and loose. We read row 1 as labels and row 2+
         * as values, producing flat key=column letter, value=first row value
         * pairs. Good enough for Phase A which only needs the labeled lookups
         * defined in ConfigKeys.
         *
         * Additionally, columns that are lists (Status, Category, etc) are
         * collected under keys like "LIST:G" with a JSON-encoded List<String>.
         */
        fun parse(rows: List<List<String>>): List<ConfigKvEntity> {
            if (rows.isEmpty()) return emptyList()
            val headers = rows[0]
            val out = mutableListOf<ConfigKvEntity>()

            headers.forEachIndexed { colIdx, header ->
                val letter = columnLetter(colIdx)
                val values = rows.drop(1).mapNotNull {
                    it.cell(colIdx).takeIf { cell -> cell.isNotBlank() }
                }
                // First-row value under column letter (for simple scalars like
                // Config!C4 = current FY).
                val firstVal = rows.getOrNull(1)?.cell(colIdx).orEmpty()
                out += ConfigKvEntity(key = "CELL:${letter}2", value = firstVal)

                if (header.isNotBlank()) {
                    out += ConfigKvEntity(key = "LABEL:${header.trim()}", value = JSON.encodeToString(values))
                }
            }
            return out
        }
    }

    // ===== MEMBERSHIP TRACKER (A..B + FY blocks) =====

    object Membership {
        /**
         * Row layout:
         *   A: memberId      B: primaryName
         *   Then repeated 4-column blocks per FY detected in the header row.
         */
        fun fromRow(
            row: List<String>,
            sheetRowIndex: Int,
            fyColumns: List<FyColumn>,
        ): MembershipRowEntity {
            val memberId = row.cell(0).trim()
            val primaryName = row.cell(1)

            val fees = mutableMapOf<String, FyCell>()
            fyColumns.forEach { fy ->
                val cell = FyCell(
                    status = row.cell(fy.startColumn),
                    amount = row.cell(fy.startColumn + 1),
                    date = row.cell(fy.startColumn + 2),
                    receipt = row.cell(fy.startColumn + 3),
                )
                if (!cell.isBlank) fees[fy.label] = cell
            }

            return MembershipRowEntity(
                memberId = memberId,
                primaryName = primaryName,
                feesJson = JSON.encodeToString(fees),
                sheetRowIndex = sheetRowIndex,
                syncStatus = SyncState.SYNCED,
                lastLocalModifiedAt = 0L,
                lastSyncedSnapshot = JSON.encodeToString(fees),
                pushError = null,
            )
        }

        /**
         * Build a full row for push. We overlay the entity's feesJson onto the
         * sheet's detected FY columns. Anything outside our known columns is
         * preserved from the previous sheet state in [preserveRow].
         */
        fun toRow(
            e: MembershipRowEntity,
            fyColumns: List<FyColumn>,
            preserveRow: List<String>,
        ): List<String> {
            val fees: Map<String, FyCell> = parseFees(e.feesJson)
            val maxCol = (fyColumns.maxOfOrNull { it.startColumn + 3 } ?: 1) + 1
            val out = MutableList(maxOf(maxCol, preserveRow.size)) { i ->
                preserveRow.cell(i)
            }
            out[0] = e.memberId
            out[1] = e.primaryName
            fyColumns.forEach { fy ->
                val cell = fees[fy.label] ?: FyCell()
                out[fy.startColumn]     = cell.status
                out[fy.startColumn + 1] = cell.amount
                out[fy.startColumn + 2] = cell.date
                out[fy.startColumn + 3] = cell.receipt
            }
            return out
        }

        fun parseFees(feesJson: String): Map<String, FyCell> {
            if (feesJson.isBlank()) return emptyMap()
            return try {
                JSON.decodeFromJsonElement<Map<String, FyCell>>(JSON.parseToJsonElement(feesJson))
            } catch (_: Throwable) {
                emptyMap()
            }
        }
    }
}
