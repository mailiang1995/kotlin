/*
 * Copyright 2010-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.idea.filters

import com.intellij.execution.filters.FileHyperlinkInfo
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.testFramework.LightProjectDescriptor
import org.jetbrains.kotlin.idea.refactoring.toVirtualFile
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase
import org.junit.Assert
import java.io.File

private data class SuffixOption(val suffix: String, val expectedLine: Int, val expectedColumn: Int)

class KotlinExceptionFilterTest : KotlinLightCodeInsightFixtureTestCase() {
    private var myFiles = HashMap<String, VirtualFile>()

    private var myExceptionLine: String = ""

    private val mySuffixOptions = listOf(
        SuffixOption(":10:1)\n", 10, 1),
        SuffixOption(":14:11)\n", 14, 11),
        SuffixOption(":<unknown>)\n", 0, 0)
    )

    private fun errorMessage(detail: String) = "Failed to parse Kotlin Native exception '$myExceptionLine': $detail"

    override fun getProjectDescriptor() = LightProjectDescriptor.EMPTY_PROJECT_DESCRIPTOR

    override fun getTestDataPath() = ""

    override fun setUp() {
        super.setUp()
        val rootDir = File("idea/testData/debugger/exceptionFilter/kt29871/")
        rootDir.listFiles().forEach {
            val virtualFile = it.toVirtualFile()
            if (virtualFile != null) {
                myFiles[it.absolutePath] = virtualFile
            }
        }
    }

    fun testDifferentLocations() {
        val prefix = "\tat 1   main.kexe\t\t 0x000000010d7cdb4c kfun:package.function(kotlin.Int) + 108 ("
        for (suffixOption in mySuffixOptions) {
            doTest(prefix, suffixOption.suffix, suffixOption.expectedLine, suffixOption.expectedColumn)
        }
    }

    fun doTest(prefix: String, suffix: String, expectedLine: Int, expectedColumn: Int) {
        val filter = KotlinExceptionFilterFactory().create(GlobalSearchScope.allScope(project))

        for ((absolutePath, virtualFile) in myFiles) {
            myExceptionLine = "$prefix$absolutePath$suffix"
            val filterResult = filter.applyFilter(myExceptionLine, myExceptionLine.length)
            Assert.assertNotNull(errorMessage("filename is not found by parser"), filterResult)

            val fileHyperlinkInfo = filterResult?.firstHyperlinkInfo as FileHyperlinkInfo
            val descriptor = fileHyperlinkInfo.descriptor!!

            val document = FileDocumentManager.getInstance().getDocument(virtualFile)
            Assert.assertNotNull(errorMessage("test file $absolutePath could not be found in repository"), document)
            val expectedOffset = document!!.getLineStartOffset(expectedLine) + expectedColumn

            Assert.assertEquals(errorMessage("different filename parsed"), virtualFile.canonicalPath, descriptor.file.canonicalPath)
            Assert.assertEquals(errorMessage("different offset parsed"), expectedOffset, descriptor.offset)
        }
    }
}