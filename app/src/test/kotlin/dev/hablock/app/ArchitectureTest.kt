package dev.hablock.app

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

private const val ROOT = "dev.hablock.app"

/** Enforces the layer rules by scanning imports — no dependency needed, no discipline required. */
class ArchitectureTest {

    private val sourceRoot = sequenceOf(File("src/main/kotlin"), File("app/src/main/kotlin"))
        .firstOrNull { it.isDirectory }
        ?: fail("source root not found from ${File(".").absolutePath}")

    @Test
    fun `domain imports no framework and no outer layer`() = assertNoImports(
        inPackages = listOf("/domain/"),
        forbiddenPrefixes = listOf(
            "android.", "androidx.",
            "$ROOT.data.", "$ROOT.ui.", "$ROOT.system.", "$ROOT.enforcement.", "$ROOT.di.",
        ),
    )

    @Test
    fun `enforcement and system import no ui`() = assertNoImports(
        inPackages = listOf("/enforcement/", "/system/"),
        forbiddenPrefixes = listOf("$ROOT.ui."),
    )

    private fun assertNoImports(inPackages: List<String>, forbiddenPrefixes: List<String>) {
        val violations = sourceRoot.walkTopDown()
            .filter { it.extension == "kt" }
            .filter { file -> inPackages.any { file.invariantSeparatorsPath.contains(it) } }
            .filterNot { it.invariantSeparatorsPath.contains("/domain/") && "/domain/" !in inPackages }
            .flatMap { file ->
                file.readLines().mapIndexedNotNull { index, line ->
                    val import = line.trim().removePrefix("import ").takeIf { line.trim().startsWith("import ") }
                    import?.takeIf { imported -> forbiddenPrefixes.any { imported.startsWith(it) } }
                        ?.let { "${file.path}:${index + 1} imports $it" }
                }
            }
            .toList()
        assertTrue(violations.isEmpty(), "layer violations:\n" + violations.joinToString("\n"))
    }
}
