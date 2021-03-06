package com.apollographql.android.compiler

import com.google.common.truth.Truth.assertAbout
import com.google.common.truth.Truth.assertThat
import com.google.testing.compile.JavaFileObjects
import com.google.testing.compile.JavaSourcesSubjectFactory.javaSources
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.io.File
import java.nio.file.*
import java.nio.file.attribute.BasicFileAttributes
import java.util.*
import javax.tools.JavaFileObject

@RunWith(Parameterized::class)
class CodeGenTest(val testDir: File, val pkgName: String, val customScalarTypeMap: Map<String, String>,
    val useOptional: Boolean, val useGuava: Boolean) {
  private val testDirPath = testDir.toPath()
  private val expectedFileMatcher = FileSystems.getDefault().getPathMatcher("glob:**.java")

  private val compiler = GraphQLCompiler()
  private val outputDir = GraphQLCompiler.Companion.OUTPUT_DIRECTORY.fold(File("build"), ::File)
  private val sourceFileObjects: MutableList<JavaFileObject> = ArrayList()

  @Test
  fun generateExpectedClasses() {
    val irFile = File(testDir, "TestQuery.json")
    compiler.write(GraphQLCompiler.Arguments(irFile, outputDir, customScalarTypeMap, useOptional, useGuava))

    Files.walkFileTree(testDirPath, object : SimpleFileVisitor<Path>() {
      override fun visitFile(expectedFile: Path, attrs: BasicFileAttributes): FileVisitResult {
        if (expectedFileMatcher.matches(expectedFile)) {
          val expected = expectedFile.toFile()

          val actualClassName = actualClassName(expectedFile)
          val actual = findActual(actualClassName)

          if (!actual.isFile) {
            throw AssertionError("Couldn't find actual file: $actual")
          }

          assertThat(actual.readText()).isEqualTo(expected.readText())
          sourceFileObjects.add(JavaFileObjects.forSourceLines("com.example.$pkgName.$actualClassName",
              actual.readLines()))
        }
        return FileVisitResult.CONTINUE
      }
    })
    assertAbout(javaSources()).that(sourceFileObjects).compilesWithoutError()
  }

  private fun actualClassName(expectedFile: Path): String {
    return expectedFile.fileName.toString().replace("Expected", "").replace(".java", "")
  }

  private fun findActual(className: String): File {
    val possiblePaths = arrayOf("$className.java", "type/$className.java", "fragment/$className.java")
    possiblePaths
        .map { outputDir.toPath().resolve("com/example/$pkgName/$it").toFile() }
        .filter { it.isFile }
        .forEach { return it }
    throw AssertionError("Couldn't find actual file: $className")
  }

  companion object {
    @JvmStatic
    @Parameterized.Parameters(name = "{1}")
    fun data(): Collection<Array<Any>> {
      return File("src/test/graphql/com/example/").listFiles()
          .filter { it.isDirectory }
          .map {
            if (it.name == "custom_scalar_type") {
              arrayOf(it, it.name, mapOf("Date" to "java.util.Date"), true, false)
            } else if (it.name == "hero_details_guava") {
              arrayOf(it, it.name, emptyMap<String, String>(), true, true)
            } else if (it.name == "hero_details_nullable") {
              arrayOf(it, it.name, emptyMap<String, String>(), false, false)
            } else {
              arrayOf(it, it.name, emptyMap<String, String>(), true, false)
            }
          }
    }
  }
}
