package dev.monitorcidadao.buildlogic

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.util.zip.ZipFile

/**
 * Falha se algum APK em [apkDirectory] levar o servidor de fixtures: arquivos em `assets/fixtures/`
 * ou classes cujos descritores de tipo (`Ldev/monitorcidadao/...;`) aparecem no `classes*.dex`.
 * O `:app` liga esta tarefa ao `assembleRelease`.
 */
abstract class VerifyNoFixturesTask : DefaultTask() {
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val apkDirectory: DirectoryProperty

    /** Prefixos de pacote proibidos, no formato de descritor do dex (`dev/monitorcidadao/core/network/fixture/`). */
    @get:Input
    abstract val forbiddenPackages: ListProperty<String>

    @get:OutputFile
    abstract val report: RegularFileProperty

    @TaskAction
    fun verify() {
        val apks = apkDirectory.get().asFile.walkTopDown().filter { it.extension == "apk" }.toList()
        if (apks.isEmpty()) throw GradleException("Nenhum APK encontrado em ${apkDirectory.get().asFile}")

        val descriptors = forbiddenPackages.get().map { "L$it".toByteArray(Charsets.US_ASCII) }
        val problems = mutableListOf<String>()
        apks.forEach { apk ->
            ZipFile(apk).use { zip ->
                zip.entries().asSequence().forEach { entry ->
                    when {
                        entry.name.startsWith("assets/fixtures/") -> problems += "${apk.name}: ${entry.name}"
                        entry.name.matches(Regex("classes\\d*\\.dex")) -> {
                            val bytes = zip.getInputStream(entry).use { it.readBytes() }
                            descriptors.filter { bytes.contains(it) }.forEach {
                                problems += "${apk.name}/${entry.name}: ${String(it, Charsets.US_ASCII)}"
                            }
                        }
                    }
                }
            }
        }
        if (problems.isNotEmpty()) {
            throw GradleException("O build Release leva fixtures de Debug:\n" + problems.joinToString("\n"))
        }
        report.get().asFile.writeText("OK: ${apks.joinToString { it.name }} sem fixtures\n")
    }

    private fun ByteArray.contains(needle: ByteArray): Boolean {
        if (needle.isEmpty() || needle.size > size) return false
        outer@ for (i in 0..size - needle.size) {
            for (j in needle.indices) if (this[i + j] != needle[j]) continue@outer
            return true
        }
        return false
    }
}
