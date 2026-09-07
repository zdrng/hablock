package dev.hablock.app.data.db

import dev.hablock.app.domain.model.BlockDayState
import dev.hablock.app.domain.model.Session
import dev.hablock.app.domain.repository.GateStateRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomGateStateRepository(
    private val dao: GateStateDao,
) : GateStateRepository {
    override val dayStates: Flow<Map<String, BlockDayState>> = dao.observeAll().map { rows ->
        rows.associate { row -> row.state.blockId to row.toDomain() }
    }

    override suspend fun get(blockId: String): BlockDayState? = dao.get(blockId)?.toDomain()

    override suspend fun save(state: BlockDayState) = dao.save(state.toAggregate())

    override suspend fun delete(blockId: String) = dao.delete(blockId)

    override suspend fun clearAll() = dao.clearAll()
}

private fun BlockDayState.toAggregate(): BlockDayStateAggregate = BlockDayStateAggregate(
    state = BlockDayStateEntity(
        blockId = blockId,
        dayKey = dayKey,
        sessionStartedAtMillis = activeSession?.startedAtMillis,
        sessionEndsAtMillis = activeSession?.endsAtMillis,
        unlockCount = unlockCount,
        emergencyUnlockUsed = emergencyUnlockUsed,
    ),
    requirements = requiredNow.map { (conditionId, required) ->
        RequirementEntity(blockId, conditionId, required)
    },
)

private fun BlockDayStateAggregate.toDomain(): BlockDayState {
    val started = state.sessionStartedAtMillis
    val ends = state.sessionEndsAtMillis
    check((started == null) == (ends == null)) { "Session timestamps must either both be null or both be set" }
    return BlockDayState(
        blockId = state.blockId,
        dayKey = state.dayKey,
        requiredNow = requirements.associate { it.conditionId to it.required },
        activeSession = if (started == null) null else Session(state.blockId, started, checkNotNull(ends)),
        unlockCount = state.unlockCount,
        emergencyUnlockUsed = state.emergencyUnlockUsed,
    )
}
