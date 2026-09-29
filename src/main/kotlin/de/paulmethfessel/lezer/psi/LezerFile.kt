package de.paulmethfessel.lezer.psi

import com.intellij.extapi.psi.PsiFileBase
import com.intellij.openapi.fileTypes.FileType
import com.intellij.psi.FileViewProvider
import de.paulmethfessel.lezer.LezerFileType
import de.paulmethfessel.lezer.LezerLanguage

class LezerFile(viewProvider: FileViewProvider) : PsiFileBase(viewProvider, LezerLanguage) {
    override fun getFileType(): FileType = LezerFileType

    override fun toString(): String = "Lezer Grammar File"
}
