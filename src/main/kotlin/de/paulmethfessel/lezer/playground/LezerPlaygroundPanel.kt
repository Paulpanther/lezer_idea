package de.paulmethfessel.lezer.playground

import com.intellij.codeInsight.daemon.impl.HighlightInfoType
import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.ScrollType
import com.intellij.openapi.editor.colors.EditorColors
import com.intellij.openapi.editor.event.CaretEvent
import com.intellij.openapi.editor.event.CaretListener
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.fileTypes.PlainTextFileType
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiManager
import com.intellij.ui.ColoredTreeCellRenderer
import com.intellij.ui.EditorNotificationPanel
import com.intellij.ui.JBColor
import com.intellij.ui.JBSplitter
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.Alarm
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.tree.TreeUtil
import de.paulmethfessel.lezer.LezerFileType
import de.paulmethfessel.lezer.generator.GeneratorInstaller
import de.paulmethfessel.lezer.generator.GeneratorLocator
import de.paulmethfessel.lezer.generator.LezerConfigurable
import de.paulmethfessel.lezer.generator.LezerSetupListener
import de.paulmethfessel.lezer.generator.NodeLocator
import de.paulmethfessel.lezer.psi.LezerFile
import de.paulmethfessel.lezer.psi.LezerNodeNames
import java.awt.BorderLayout
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JPanel
import javax.swing.JTree
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreePath

/**
 * The Lezer Playground: example input on the right, parsed with the grammar of the selected editor, and its syntax
 * tree on the left. The caret in the input selects the innermost node, selecting a node highlights its range and
 * double-clicking it opens the declaration that creates it.
 */
class LezerPlaygroundPanel(private val project: Project) : SimpleToolWindowPanel(true, true), Disposable {
    /** The grammar whose parser is used, it follows the selected editor unless [pinned]. */
    var grammar: VirtualFile? = null
        private set
    private var pinned = false
    private var grammarInfo = PlaygroundGrammarInfo(emptyList(), emptyList(), emptyList())
    private var grammarDisposable: Disposable? = null

    private val state get() = LezerPlaygroundState.getInstance(project)
    private val worker get() = PlaygroundWorker.getInstance(project)

    private val treeModel = DefaultTreeModel(null)
    val tree = Tree(treeModel)
    private val inputDocument = EditorFactory.getInstance().createDocument("")
    val inputEditor = EditorFactory.getInstance().createEditor(inputDocument, project, PlainTextFileType.INSTANCE, false) as EditorEx
    private val grammarLabel = JBLabel()
    private val status = JBLabel()
    private val banner = JPanel(BorderLayout())
    private val alarm = Alarm(Alarm.ThreadToUse.SWING_THREAD, this)

    /** The newest result, and the request it answers. */
    var result: PlaygroundResult? = null
        private set
    private var lastRequestId = 0L
    private var lastRequestInput = ""
    private val errorHighlighters = mutableListOf<RangeHighlighter>()
    private var selectionHighlighter: RangeHighlighter? = null
    private var syncingSelection = false
    private var loadingInput = false

    init {
        setupInputEditor()
        setupTree()

        val toolbar = ActionManager.getInstance().createActionToolbar("LezerPlayground", toolbarActions(), true)
        toolbar.targetComponent = this
        val toolbarRow = JPanel(BorderLayout()).apply {
            add(toolbar.component, BorderLayout.WEST)
            add(JPanel(BorderLayout()).apply {
                border = JBUI.Borders.emptyRight(8)
                add(grammarLabel, BorderLayout.WEST)
                add(status, BorderLayout.EAST)
            }, BorderLayout.CENTER)
        }
        setToolbar(toolbarRow)

        val splitter = JBSplitter(false, 0.4f).apply {
            firstComponent = JBScrollPane(tree)
            secondComponent = inputEditor.component
        }
        setContent(JPanel(BorderLayout()).apply {
            add(banner, BorderLayout.NORTH)
            add(splitter, BorderLayout.CENTER)
        })

        val connection = project.messageBus.connect(this)
        connection.subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, object : FileEditorManagerListener {
            override fun selectionChanged(event: FileEditorManagerEvent) {
                val file = event.newFile ?: return
                if (!pinned && file.fileType == LezerFileType) showGrammar(file)
            }
        })
        connection.subscribe(LezerSetupListener.TOPIC, LezerSetupListener {
            worker.restart()
            scheduleParse(0)
        })

        val selected = FileEditorManager.getInstance(project).selectedFiles.firstOrNull { it.fileType == LezerFileType }
            ?: FileEditorManager.getInstance(project).openFiles.firstOrNull { it.fileType == LezerFileType }
        selected?.let(::showGrammar) ?: updateGrammarLabel()
    }

    /** Uses [file] for parsing and shows the input last used with it. */
    fun showGrammar(file: VirtualFile) {
        if (file == grammar) return
        grammarDisposable?.let(Disposer::dispose)
        grammar = file
        val disposable = Disposer.newDisposable(this, "Lezer Playground grammar")
        grammarDisposable = disposable
        FileDocumentManager.getInstance().getDocument(file)?.addDocumentListener(object : DocumentListener {
            override fun documentChanged(event: DocumentEvent) = scheduleParse(GRAMMAR_DELAY_MS)
        }, disposable)

        loadingInput = true
        ApplicationManager.getApplication().runWriteAction { inputDocument.setText(state.input(file.path)) }
        loadingInput = false
        updateGrammarLabel()
        scheduleParse(0)
    }

    private fun setupInputEditor() {
        inputEditor.settings.apply {
            isLineNumbersShown = true
            isFoldingOutlineShown = false
            isUseSoftWraps = true
            additionalLinesCount = 1
            isCaretRowShown = true
        }
        inputEditor.setPlaceholder("Type the text to parse")
        inputEditor.setShowPlaceholderWhenFocused(true)
        inputDocument.addDocumentListener(object : DocumentListener {
            override fun documentChanged(event: DocumentEvent) {
                if (loadingInput) return
                grammar?.let { state.setInput(it.path, inputDocument.text) }
                scheduleParse(INPUT_DELAY_MS)
            }
        }, this)
        inputEditor.caretModel.addCaretListener(object : CaretListener {
            override fun caretPositionChanged(event: CaretEvent) = selectNodeAtCaret()
        }, this)
    }

    private fun setupTree() {
        tree.isRootVisible = true
        tree.showsRootHandles = true
        tree.emptyText.text = "Open a .grammar file to parse text with it"
        tree.cellRenderer = NodeRenderer()
        tree.addTreeSelectionListener {
            val node = selectedNode()
            highlightSelection(node)
            if (node != null && !syncingSelection) {
                inputEditor.scrollingModel.scrollTo(inputEditor.offsetToLogicalPosition(node.from.coerceAtMost(inputDocument.textLength)), ScrollType.MAKE_VISIBLE)
            }
        }
        tree.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (e.clickCount == 2 && tree.getPathForLocation(e.x, e.y) != null) navigateToDeclaration()
            }
        })
        tree.addKeyListener(object : KeyAdapter() {
            override fun keyPressed(e: KeyEvent) {
                if (e.keyCode == KeyEvent.VK_ENTER) navigateToDeclaration()
            }
        })
    }

    private fun toolbarActions() = DefaultActionGroup().apply {
        add(object : ToggleAction("Pin Grammar", "Keep using this grammar when another one is selected", AllIcons.General.Pin_tab) {
            override fun isSelected(e: AnActionEvent) = pinned
            override fun setSelected(e: AnActionEvent, state: Boolean) {
                pinned = state
                if (!state) FileEditorManager.getInstance(project).selectedFiles.firstOrNull { it.fileType == LezerFileType }?.let(::showGrammar)
            }
            override fun getActionUpdateThread() = ActionUpdateThread.EDT
        })
        add(TopRuleGroup())
        add(DialectGroup())
        addSeparator()
        add(object : DumbAwareAction("Expand All", null, AllIcons.Actions.Expandall) {
            override fun actionPerformed(e: AnActionEvent) = TreeUtil.expandAll(tree)
        })
        add(object : DumbAwareAction("Collapse All", null, AllIcons.Actions.Collapseall) {
            override fun actionPerformed(e: AnActionEvent) = TreeUtil.collapseAll(tree, 1)
        })
        add(object : DumbAwareAction("Settings", "Node.js, generator and the module with @external implementations", AllIcons.General.Settings) {
            override fun actionPerformed(e: AnActionEvent) {
                ShowSettingsUtil.getInstance().showSettingsDialog(project, LezerConfigurable::class.java)
            }
        })
    }

    /** Chooses the `@top` rule to parse with, if the grammar has several. */
    private inner class TopRuleGroup : ActionGroup("Top Rule", true) {
        override fun getChildren(e: AnActionEvent?): Array<AnAction> = grammarInfo.tops.map { top ->
            object : ToggleAction(top) {
                override fun isSelected(e: AnActionEvent) = currentTop() == top
                override fun setSelected(e: AnActionEvent, state: Boolean) {
                    grammar?.let { LezerPlaygroundState.getInstance(project).setTop(it.path, top) }
                    scheduleParse(0)
                }
                override fun getActionUpdateThread() = ActionUpdateThread.EDT
            }
        }.toTypedArray()

        override fun update(e: AnActionEvent) {
            e.presentation.isVisible = grammarInfo.tops.size > 1
            e.presentation.text = "@top ${currentTop() ?: ""}"
        }

        override fun getActionUpdateThread() = ActionUpdateThread.EDT
    }

    /** Enables dialects declared with `@dialects`. */
    private inner class DialectGroup : ActionGroup("Dialects", true) {
        override fun getChildren(e: AnActionEvent?): Array<AnAction> = grammarInfo.dialects.map { dialect ->
            object : ToggleAction(dialect) {
                override fun isSelected(e: AnActionEvent) = dialect in currentDialects()
                override fun setSelected(e: AnActionEvent, state: Boolean) {
                    val path = grammar?.path ?: return
                    val dialects = currentDialects().let { if (state) it + dialect else it - dialect }
                    LezerPlaygroundState.getInstance(project).setDialects(path, dialects)
                    scheduleParse(0)
                }
                override fun getActionUpdateThread() = ActionUpdateThread.EDT
            }
        }.toTypedArray()

        override fun update(e: AnActionEvent) {
            e.presentation.isVisible = grammarInfo.dialects.isNotEmpty()
            val enabled = currentDialects()
            e.presentation.text = if (enabled.isEmpty()) "Dialects" else "Dialects: ${enabled.joinToString(", ")}"
        }

        override fun getActionUpdateThread() = ActionUpdateThread.EDT
    }

    private fun currentTop(): String? {
        val path = grammar?.path ?: return null
        return state.top(path)?.takeIf { it in grammarInfo.tops } ?: grammarInfo.tops.firstOrNull()
    }

    private fun currentDialects(): Set<String> {
        val path = grammar?.path ?: return emptySet()
        return state.dialects(path).filter { it in grammarInfo.dialects }.toSet()
    }

    private fun updateGrammarLabel() {
        grammarLabel.text = grammar?.name ?: "No grammar"
        grammarLabel.icon = if (grammar != null) LezerFileType.icon else null
    }

    fun scheduleParse(delayMs: Int) {
        alarm.cancelAllRequests()
        alarm.addRequest(::parse, delayMs)
    }

    /** Sends the grammar and the input to the worker, resolving Node.js and the generator in the background. */
    private fun parse() {
        val file = grammar ?: return
        val document = FileDocumentManager.getInstance().getDocument(file) ?: return
        PsiDocumentManager.getInstance(project).commitDocument(document)
        (PsiManager.getInstance(project).findFile(file) as? LezerFile)?.let { grammarInfo = PlaygroundGrammarInfo.of(it) }

        val input = inputDocument.text
        val id = worker.nextId()
        lastRequestId = id
        lastRequestInput = input
        val grammarText = document.text
        val top = currentTop().takeIf { grammarInfo.tops.size > 1 }
        val dialects = currentDialects().joinToString(" ").ifEmpty { null }
        val externals = grammarInfo.externals

        ApplicationManager.getApplication().executeOnPooledThread {
            val node = NodeLocator.find(project)
            val generator = node?.let { GeneratorLocator.resolve(project, file) }
            ApplicationManager.getApplication().invokeLater({ showSetupBanner(node == null, generator == null) }, project.disposed)
            if (node == null || generator == null) return@executeOnPooledThread
            val request = PlaygroundRequest(
                id, generator.packageDir.toString(), grammarText, file.parent.path, input, top, dialects, externals,
            )
            worker.parse(node, request).thenAccept { result ->
                if (result != null) ApplicationManager.getApplication().invokeLater({ apply(result) }, project.disposed)
            }
        }
    }

    private fun showSetupBanner(noNode: Boolean, noGenerator: Boolean) {
        banner.removeAll()
        if (noNode || noGenerator) {
            val panel = EditorNotificationPanel(EditorNotificationPanel.Status.Warning)
            if (noNode) {
                panel.text = "Node.js was not found. The playground needs it to run lezer-generator."
            } else {
                panel.text = "@lezer/generator is not installed, the playground needs it to build the parser."
                panel.createActionLabel("Install locally (npm i -D)") { GeneratorInstaller.install(project, grammar, global = false) }
                panel.createActionLabel("Install globally") { GeneratorInstaller.install(project, grammar, global = true) }
            }
            panel.createActionLabel("Configure…") { ShowSettingsUtil.getInstance().showSettingsDialog(project, LezerConfigurable::class.java) }
            banner.add(panel, BorderLayout.CENTER)
        }
        banner.revalidate()
        banner.repaint()
    }

    private fun apply(result: PlaygroundResult) {
        // A newer request is on its way, or the input changed since this one
        if (result.id != lastRequestId || inputDocument.text != lastRequestInput) return
        this.result = result
        updateStatus(result)
        if (result.tree == null) return
        updateTree(result.tree)
        updateErrorMarks(result)
        selectNodeAtCaret()
    }

    private fun updateStatus(result: PlaygroundResult) {
        val (text, color, tooltip) = when {
            result.grammarError != null -> Triple(
                "Grammar error: ${result.grammarError.lineSequence().first()}", JBColor.RED, result.grammarError,
            )
            result.moduleErrors.isNotEmpty() -> Triple(
                "Module error: ${result.moduleErrors.first().lineSequence().first()}",
                JBColor.ORANGE,
                result.moduleErrors.joinToString("\n"),
            )
            else -> Triple(
                "Parsed in ${result.ms} ms" + if (result.truncated) ", tree truncated" else "",
                JBColor.GRAY,
                null,
            )
        }
        status.text = text
        status.foreground = color
        status.toolTipText = tooltip?.let { "<html><pre>${StringUtil.escapeXmlEntities(it)}</pre></html>" }
    }

    private fun updateTree(root: PlaygroundNode) {
        val expanded = TreeUtil.collectExpandedPaths(tree).map(::indexPath)
        treeModel.setRoot(toTreeNode(root))
        val count = root.walk().count()
        if (expanded.isEmpty() || count <= EXPAND_ALL_NODES) {
            if (count <= EXPAND_ALL_NODES) TreeUtil.expandAll(tree) else tree.expandRow(0)
        } else {
            expanded.forEach { indices -> pathOf(indices)?.let(tree::expandPath) }
        }
    }

    private fun toTreeNode(node: PlaygroundNode): DefaultMutableTreeNode =
        DefaultMutableTreeNode(node).apply { node.childList.forEach { add(toTreeNode(it)) } }

    private fun indexPath(path: TreePath): List<Int> = (1 until path.pathCount).map { i ->
        (path.getPathComponent(i - 1) as DefaultMutableTreeNode).getIndex(path.getPathComponent(i) as DefaultMutableTreeNode)
    }

    private fun pathOf(indices: List<Int>): TreePath? {
        var node = treeModel.root as? DefaultMutableTreeNode ?: return null
        val nodes = mutableListOf(node)
        for (index in indices) {
            if (index >= node.childCount) return null
            node = node.getChildAt(index) as DefaultMutableTreeNode
            nodes += node
        }
        return TreePath(nodes.toTypedArray())
    }

    /** Marks the error nodes, empty ones mark the next character. */
    private fun updateErrorMarks(result: PlaygroundResult) {
        val markup = inputEditor.markupModel
        errorHighlighters.forEach(markup::removeHighlighter)
        errorHighlighters.clear()
        val length = inputDocument.textLength
        for (node in result.tree?.walk().orEmpty().filter { it.isError }) {
            val to = if (node.to > node.from) node.to else (node.from + 1).coerceAtMost(length)
            val from = node.from.coerceAtMost(to)
            if (from < 0 || from >= to || to > length) continue
            errorHighlighters += markup.addRangeHighlighter(
                HighlightInfoType.ERROR.attributesKey, from, to, HighlighterLayer.ERROR, HighlighterTargetArea.EXACT_RANGE,
            )
        }
    }

    private fun selectNodeAtCaret() {
        val root = treeModel.root as? DefaultMutableTreeNode ?: return
        val nodes = (root.userObject as PlaygroundNode).deepestAt(inputEditor.caretModel.offset)
        if (nodes.isEmpty()) return
        val treeNodes = mutableListOf(root)
        for (node in nodes.drop(1)) {
            val parent = treeNodes.last()
            val child = parent.children().asSequence().map { it as DefaultMutableTreeNode }.firstOrNull { it.userObject === node } ?: break
            treeNodes += child
        }
        val path = TreePath(treeNodes.toTypedArray())
        syncingSelection = true
        try {
            tree.selectionPath = path
            tree.scrollPathToVisible(path)
        } finally {
            syncingSelection = false
        }
    }

    private fun selectedNode(): PlaygroundNode? =
        (tree.selectionPath?.lastPathComponent as? DefaultMutableTreeNode)?.userObject as? PlaygroundNode

    private fun highlightSelection(node: PlaygroundNode?) {
        selectionHighlighter?.let(inputEditor.markupModel::removeHighlighter)
        selectionHighlighter = null
        if (node == null || node.to <= node.from || node.to > inputDocument.textLength) return
        selectionHighlighter = inputEditor.markupModel.addRangeHighlighter(
            EditorColors.IDENTIFIER_UNDER_CARET_ATTRIBUTES, node.from, node.to, HighlighterLayer.SELECTION - 1, HighlighterTargetArea.EXACT_RANGE,
        )
    }

    /** Opens the declaration that creates the selected node. */
    private fun navigateToDeclaration() {
        val node = selectedNode()?.takeIf { !it.isError } ?: return
        val file = grammar?.let { PsiManager.getInstance(project).findFile(it) } as? LezerFile ?: return
        LezerNodeNames.declarationsOf(file, node.name).firstOrNull()?.navigate(true)
    }

    private inner class NodeRenderer : ColoredTreeCellRenderer() {
        override fun customizeCellRenderer(tree: JTree, value: Any?, selected: Boolean, expanded: Boolean, leaf: Boolean, row: Int, hasFocus: Boolean) {
            val node = (value as? DefaultMutableTreeNode)?.userObject as? PlaygroundNode ?: return
            if (node.isError) append("⚠ Error", SimpleTextAttributes.ERROR_ATTRIBUTES)
            else append(node.name, SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES)
            append("  ${node.from}…${node.to}", SimpleTextAttributes.GRAYED_ATTRIBUTES)
            if (node.childList.isEmpty() && node.to > node.from && node.to <= inputDocument.textLength) {
                val text = inputDocument.charsSequence.subSequence(node.from, node.to).toString()
                append("  " + StringUtil.escapeStringCharacters(StringUtil.shortenTextWithEllipsis(text, 40, 10)), SimpleTextAttributes.GRAYED_ITALIC_ATTRIBUTES)
            }
        }
    }

    override fun dispose() {
        EditorFactory.getInstance().releaseEditor(inputEditor)
    }

    private companion object {
        const val GRAMMAR_DELAY_MS = 300
        const val INPUT_DELAY_MS = 100
        const val EXPAND_ALL_NODES = 500
    }
}
