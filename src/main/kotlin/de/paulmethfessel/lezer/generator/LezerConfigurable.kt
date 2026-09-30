package de.paulmethfessel.lezer.generator

import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.builder.panel

/** Settings | Languages & Frameworks | Lezer Grammar */
class LezerConfigurable(private val project: Project) : BoundConfigurable("Lezer Grammar") {
    private val settings get() = LezerSettings.getInstance(project).state

    override fun createPanel(): DialogPanel {
        val selector = GeneratorSelector(project, projectDefault = false) { null }
        return panel {
            group("Node.js") {
                row("Node interpreter:") {
                    textFieldWithBrowseButton(FileChooserDescriptorFactory.createSingleFileNoJarsDescriptor().withTitle("Select Node Interpreter"), project)
                        .bindText({ settings.nodePath.orEmpty() }, { settings.nodePath = it.ifBlank { null } })
                        .align(AlignX.FILL)
                        .comment(detectedNodeComment())
                }
            }
            group("lezer-generator") {
                selector.addTo(this)
                onReset {
                    selector.mode = settings.generator
                    selector.customGeneratorPath = settings.customGeneratorPath.orEmpty()
                }
                onIsModified {
                    selector.mode != settings.generator || selector.customGeneratorPath != settings.customGeneratorPath.orEmpty()
                }
                onApply {
                    settings.generator = selector.mode ?: GeneratorMode.AUTO
                    settings.customGeneratorPath = selector.customGeneratorPath.ifBlank { null }
                }
                row {
                    checkBox("Show lezer-generator errors and warnings while typing")
                        .bindSelected({ settings.liveErrors }, { settings.liveErrors = it })
                }
                row {
                    checkBox("Show a banner in grammar files if lezer-generator is not installed")
                        .bindSelected({ !settings.bannerDismissed }, { settings.bannerDismissed = !it })
                }
            }
        }
    }

    private fun detectedNodeComment(): String {
        val provided = LezerNodeInterpreterProvider.EP_NAME.extensionList.firstNotNullOfOrNull { provider ->
            provider.findNode(project)?.let { "$it (from ${provider.sourceName})" }
        }
        val node = provided ?: NodeLocator.detect()?.toString() ?: return "Leave empty to detect it automatically. No interpreter was found."
        return "Leave empty to use $node"
    }

    override fun apply() {
        super.apply()
        LezerGeneratorRefresher.refresh(project)
    }
}
