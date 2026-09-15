package dev.hablock.app.data

/** Replays ordered foreground events while counting only the requested interval. */
internal class ForegroundUsageAccumulator(private val from: Long, private val to: Long) {
    private val completed = mutableMapOf<String, Long>()
    private val openedAt = mutableMapOf<String, Long>()
    private val seen = mutableSetOf<String>()

    fun open(packageName: String, at: Long) {
        seen.add(packageName)
        openedAt.putIfAbsent(packageName, at)
    }

    fun close(packageName: String, at: Long) {
        val start = openedAt.remove(packageName)
            ?: if (packageName !in seen) from else return
        seen.add(packageName)
        add(completed, packageName, start, at)
    }

    fun totals(): Map<String, Long> = completed.toMutableMap().also { result ->
        openedAt.forEach { (pkg, start) -> add(result, pkg, start, to) }
    }

    fun closeAll(at: Long) {
        openedAt.keys.toList().forEach { close(it, at) }
    }

    private fun add(result: MutableMap<String, Long>, pkg: String, start: Long, end: Long) {
        val duration = minOf(end, to) - maxOf(start, from)
        if (duration > 0) result[pkg] = (result[pkg] ?: 0L) + duration
    }
}
