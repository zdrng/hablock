package dev.hablock.app.domain.enforcement

import dev.hablock.app.domain.model.Block
import dev.hablock.app.domain.model.GateState
import dev.hablock.app.domain.model.OverlapPolicy

data class EnforcementPlan(
    val blockedByPackage: Map<String, String>,
) {
    val blockedPackages: Set<String> get() = blockedByPackage.keys

    fun blockingBlockId(packageName: String): String? = blockedByPackage[packageName]
}

/** Resolves overlap policy once so every enforcement backend acts on the same package set. */
fun resolveEnforcementPlan(
    blocks: List<Block>,
    states: Map<String, GateState>,
    policy: OverlapPolicy,
): EnforcementPlan {
    val enabled = blocks
        .filter { it.enabled && states[it.id] !is GateState.Inactive }
        .sortedBy { it.id }
    val packages = enabled.flatMapTo(sortedSetOf()) { it.blockedPackages }
    val blockedByPackage = buildMap {
        for (packageName in packages) {
            val matching = enabled.filter { packageName in it.blockedPackages }
            val activeCount = matching.count { states[it.id] is GateState.SessionActive }
            val released = when (policy) {
                OverlapPolicy.ALL_BLOCKS -> activeCount == matching.size
                OverlapPolicy.ANY_BLOCK -> activeCount > 0
            }
            if (released) continue

            val unresolved = matching.filterNot { states[it.id] is GateState.SessionActive }
            val candidates = if (policy == OverlapPolicy.ANY_BLOCK) {
                unresolved.filter { states[it.id] is GateState.Open }.ifEmpty { unresolved }
            } else {
                unresolved
            }
            candidates.firstOrNull()?.let { put(packageName, it.id) }
        }
    }
    return EnforcementPlan(blockedByPackage)
}

interface EnforcementBackend {
    suspend fun applyState(plan: EnforcementPlan)
    suspend fun showBlocked(packageName: String, blockId: String)

    /** True when first use of an unlocked app will reach GateEngine.onAppForegrounded. */
    fun reportsForegroundUse(): Boolean
}

interface DeviceOwnerController {
    fun isDeviceOwner(): Boolean

    /** Returns the packages whose suspension state could not be changed. */
    fun setPackagesSuspended(packages: Set<String>, suspended: Boolean): Set<String>
    fun applyRestrictions()
    fun relinquishOwnership(): Boolean
}

/** Presents the blocked interstitial; implemented in ui/ so enforcement/ needs no ui import. */
fun interface BlockedScreenLauncher {
    fun show(packageName: String, blockId: String)
}

/** Persisted record of what Hablock has suspended, so orphans are released after process death. */
interface SuspensionStore {
    suspend fun suspended(): Set<String>
    suspend fun setSuspended(packages: Set<String>)
}

/** Routes to the device-owner backend when ownership is granted, otherwise to accessibility. */
class EnforcementCoordinator(
    private val accessibilityBackend: EnforcementBackend,
    private val deviceOwnerBackend: EnforcementBackend,
    private val deviceOwner: DeviceOwnerController,
) : EnforcementBackend {

    private fun active(): EnforcementBackend =
        if (deviceOwner.isDeviceOwner()) deviceOwnerBackend else accessibilityBackend

    override suspend fun applyState(plan: EnforcementPlan) = active().applyState(plan)

    override suspend fun showBlocked(packageName: String, blockId: String) =
        accessibilityBackend.showBlocked(packageName, blockId)

    // The foreground sensor is the accessibility service, which runs regardless of which backend enforces.
    override fun reportsForegroundUse(): Boolean = accessibilityBackend.reportsForegroundUse()
}
