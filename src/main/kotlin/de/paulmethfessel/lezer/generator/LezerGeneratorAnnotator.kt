package de.paulmethfessel.lezer.generator

import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.execution.ExecutionException
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.ExternalAnnotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.util.PsiTreeUtil
import de.paulmethfessel.lezer.psi.LezerFile
import de.paulmethfessel.lezer.psi.impl.LezerReferenceElementImpl

/** Shows the errors and warnings of `@lezer/generator` while typing. */
class LezerGeneratorAnnotator : ExternalAnnotator<LezerGeneratorAnnotator.Input, GeneratorReport>(), DumbAware {
    class Input(val project: Project, val file: VirtualFile?, val text: String)

    override fun collectInformation(file: PsiFile, editor: Editor, hasErrors: Boolean): Input? = collectInformation(file)

    override fun collectInformation(file: PsiFile): Input? {
        if (file !is LezerFile) return null
        if (!LezerSettings.getInstance(file.project).state.liveErrors) return null
        // Syntax errors are already reported by the plugin's own parser
        if (PsiTreeUtil.hasErrorElements(file)) return null
        return Input(file.project, file.virtualFile, file.text)
    }

    override fun doAnnotate(input: Input): GeneratorReport? {
        val node = NodeLocator.find(input.project) ?: return null
        val generator = GeneratorLocator.resolve(input.project, input.file) ?: return null
        val indicator = ProgressManager.getInstance().progressIndicator ?: EmptyProgressIndicator()
        return try {
            GeneratorCheck.run(node, generator, input.text, indicator)
        } catch (e: ExecutionException) {
            LOG.info("Could not run lezer-generator", e)
            null
        }
    }

    override fun apply(file: PsiFile, report: GeneratorReport, holder: AnnotationHolder) {
        val document = PsiDocumentManager.getInstance(file.project).getDocument(file) ?: return
        report.errors.forEach { annotate(file, document, GeneratorMessage.parse(it), HighlightSeverity.ERROR, holder) }
        report.warnings.forEach { annotate(file, document, GeneratorMessage.parse(it), HighlightSeverity.WARNING, holder) }
        report.failure?.let {
            holder.newAnnotation(HighlightSeverity.WARNING, "lezer-generator failed: ${it.lineSequence().first()}")
                .tooltip(pre(it))
                .fileLevel()
                .create()
        }
    }

    private fun annotate(
        file: PsiFile,
        document: Document,
        message: GeneratorMessage,
        severity: HighlightSeverity,
        holder: AnnotationHolder,
    ) {
        val range = message.offset(document)?.let { range(file, it) }
        if (range == null) {
            // Conflicts have no position, but their productions can be found in the grammar
            val conflict = ConflictLocator.locate(file, message.text)
            if (conflict != null) {
                for (conflictRange in conflict.ranges) {
                    holder.newAnnotation(severity, conflict.description).tooltip(pre(message.text)).range(conflictRange).create()
                }
            } else {
                holder.newAnnotation(severity, message.summary).tooltip(pre(message.text)).fileLevel().create()
            }
            return
        }
        val builder = holder.newAnnotation(severity, message.summary).tooltip(pre(message.text))
        builder.range(range)
        if (message.isUnusedRule) builder.highlightType(ProblemHighlightType.LIKE_UNUSED_SYMBOL)
        builder.create()
    }

    /** The name or token at the offset. */
    private fun range(file: PsiFile, offset: Int): TextRange? {
        val leaf = file.findElementAt(offset) ?: file.findElementAt(offset - 1) ?: return null
        if (leaf is PsiWhiteSpace) return TextRange(offset, offset + 1).takeIf { it.endOffset <= file.textLength }
        return (leaf.parent as? LezerReferenceElementImpl ?: leaf).textRange
    }

    private fun pre(text: String) = "<html><pre>${StringUtil.escapeXmlEntities(text)}</pre></html>"

    private companion object {
        val LOG = logger<LezerGeneratorAnnotator>()
    }
}
