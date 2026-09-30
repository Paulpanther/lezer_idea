package de.paulmethfessel.lezer.javascript

import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import de.paulmethfessel.lezer.psi.LezerFile
import de.paulmethfessel.lezer.psi.LezerNamedElement
import de.paulmethfessel.lezer.resolve.LezerResolver

/**
 * Finds the grammar that JavaScript code refers to. Code and grammar belong together if they are in the same npm
 * package (the closest directory with a package.json), or the same content root if there is none.
 */
object LezerGrammars {
    fun declarations(project: Project, name: String, codeFile: VirtualFile): List<LezerNamedElement> {
        val root = packageRoot(project, codeFile)
        val psiManager = PsiManager.getInstance(project)
        return FilenameIndex.getAllFilesByExt(project, "grammar", GlobalSearchScope.projectScope(project))
            .filter { root == null || packageRoot(project, it) == root }
            .mapNotNull { psiManager.findFile(it) as? LezerFile }
            .flatMap { file -> LezerResolver.globalRules(file).filter { it.name == name } }
    }

    fun belongTogether(project: Project, grammar: VirtualFile, codeFile: VirtualFile): Boolean {
        val root = packageRoot(project, codeFile) ?: return true
        return packageRoot(project, grammar) == root
    }

    private fun packageRoot(project: Project, file: VirtualFile): VirtualFile? {
        val contentRoot = ProjectFileIndex.getInstance(project).getContentRootForFile(file)
        var dir = file.parent
        while (dir != null) {
            if (dir.findChild("package.json") != null) return dir
            if (dir == contentRoot) return dir
            dir = dir.parent
        }
        return contentRoot
    }
}
