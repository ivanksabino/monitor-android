package dev.monitorcidadao.buildlogic

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import javax.inject.Inject

/**
 * Copia `contracts/fixtures` (fonte única, compartilhada com o iOS) para `<saída>/fixtures/`.
 * Usada pelo `:app` (assets só do build Debug) e pelo `:core:testing` (recursos de teste).
 * Nada é duplicado à mão no repositório.
 */
abstract class CopyFixturesTask : DefaultTask() {
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val fixturesDirectory: DirectoryProperty

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @get:Inject
    abstract val fileSystem: FileSystemOperations

    @TaskAction
    fun copy() {
        fileSystem.sync {
            from(fixturesDirectory) { include("*.json") }
            into(outputDirectory.dir("fixtures"))
        }
    }
}
