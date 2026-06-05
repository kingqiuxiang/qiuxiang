package com.aifileguard.toolwindow

import com.aifileguard.action.FileActionExecutor
import com.aifileguard.classify.FileGuardScanner
import com.aifileguard.model.FileVerdict
import com.aifileguard.model.SuggestedAction
import com.aifileguard.settings.AiGuardConfigurable
import com.aifileguard.util.Notifier
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.table.JBTable
import java.awt.BorderLayout
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.BorderFactory
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JToolBar
import javax.swing.ListSelectionModel

class AiGuardPanel(private val project: Project) {

    private val model = VerdictTableModel()
    private val table = JBTable(model).apply {
        setShowGrid(false)
        autoResizeMode = JBTable.AUTO_RESIZE_LAST_COLUMN
        selectionModel.selectionMode = ListSelectionModel.MULTIPLE_INTERVAL_SELECTION
    }
    private val statusLabel = JBLabel("Ready. Click \"Scan\" to inspect the project.")
    val component: JComponent = JPanel(BorderLayout())

    init {
        component.add(buildToolbar(), BorderLayout.NORTH)
        component.add(JBScrollPane(table), BorderLayout.CENTER)
        val south = JPanel(BorderLayout()).apply {
            border = BorderFactory.createEmptyBorder(4, 8, 4, 8)
            add(statusLabel, BorderLayout.WEST)
        }
        component.add(south, BorderLayout.SOUTH)

        configureColumns()

        table.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (e.clickCount == 2) openSelectedInEditor()
            }
        })
    }

    private fun configureColumns() {
        val widths = intArrayOf(360, 150, 150, 90, 80, 60, 320)
        for (i in widths.indices) {
            if (i < table.columnModel.columnCount) {
                table.columnModel.getColumn(i).preferredWidth = widths[i]
            }
        }
    }

    private fun buildToolbar(): JComponent {
        val bar = JToolBar().apply { isFloatable = false }
        bar.add(button("Scan") { runScan() })
        bar.addSeparator()
        bar.add(button("Clean (apply recommended)") { applyRecommended() })
        bar.addSeparator()
        bar.add(button("Delete") { applyToSelection(SuggestedAction.DELETE, confirm = true) })
        bar.add(button("Quarantine") { applyToSelection(SuggestedAction.QUARANTINE, confirm = false) })
        bar.add(button("Add to ignore") { applyToSelection(SuggestedAction.ADD_TO_IGNORE, confirm = false) })
        bar.addSeparator()
        bar.add(button("Settings") {
            ShowSettingsUtil.getInstance().showSettingsDialog(project, AiGuardConfigurable::class.java)
        })
        return bar
    }

    private fun button(text: String, onClick: () -> Unit): JButton =
        JButton(text).apply { addActionListener { onClick() } }

    fun runScan() {
        statusLabel.text = "Scanning…"
        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "AI File Guard: scanning", true) {
            private var results: List<FileVerdict> = emptyList()

            override fun run(indicator: ProgressIndicator) {
                indicator.isIndeterminate = false
                results = project.getService(FileGuardScanner::class.java).scan(indicator)
            }

            override fun onSuccess() = setResults(results)

            override fun onThrowable(error: Throwable) {
                statusLabel.text = "Scan failed: ${error.message}"
            }
        })
    }

    /** Replace the table contents (must be called on EDT). */
    fun setResults(results: List<FileVerdict>) {
        ApplicationManager.getApplication().invokeLater {
            model.setRows(results)
            statusLabel.text = summary(results)
        }
    }

    private fun summary(results: List<FileVerdict>): String {
        if (results.isEmpty()) return "Clean! No AI-generated, temporary or suspicious files found."
        val byAction = results.groupingBy { it.action }.eachCount()
        return "Found ${results.size} file(s): " + byAction.entries.joinToString(", ") { "${it.value} × ${it.key.display}" }
    }

    private fun selectedVerdicts(): List<FileVerdict> =
        table.selectedRows.map { model.getRow(table.convertRowIndexToModel(it)) }

    private fun applyRecommended() {
        val all = model.rows()
        if (all.isEmpty()) {
            statusLabel.text = "Nothing to clean — run a scan first."
            return
        }
        val deletes = all.count { it.action == SuggestedAction.DELETE }
        val quarantines = all.count { it.action == SuggestedAction.QUARANTINE }
        val ignores = all.count { it.action == SuggestedAction.ADD_TO_IGNORE }
        val confirmed = Messages.showYesNoDialog(
            project,
            "Apply recommended actions to ${all.size} file(s)?\n\n" +
                "• Delete: $deletes\n• Quarantine: $quarantines\n• Add to ignore: $ignores",
            "AI File Guard — Clean Project",
            Messages.getQuestionIcon(),
        )
        if (confirmed != Messages.YES) return

        val executor = FileActionExecutor(project)
        val outcome = executor.applyRecommended(all)
        val processed = all.filter { it.action != SuggestedAction.KEEP }
        model.removeRows(processed)
        reportOutcome(outcome)
    }

    private fun applyToSelection(action: SuggestedAction, confirm: Boolean) {
        val selected = selectedVerdicts()
        if (selected.isEmpty()) {
            statusLabel.text = "Select one or more rows first."
            return
        }
        if (confirm) {
            val ok = Messages.showYesNoDialog(
                project,
                "${action.display} ${selected.size} selected file(s)? This cannot be undone.",
                "AI File Guard",
                Messages.getWarningIcon(),
            )
            if (ok != Messages.YES) return
        }
        val executor = FileActionExecutor(project)
        val outcome = executor.applyAction(selected, action)
        if (action != SuggestedAction.KEEP) model.removeRows(selected)
        reportOutcome(outcome)
    }

    private fun reportOutcome(outcome: FileActionExecutor.Outcome) {
        val msg = "Deleted ${outcome.deleted}, quarantined ${outcome.quarantined}, " +
            "ignored ${outcome.ignored}, failed ${outcome.failed}."
        statusLabel.text = msg
        if (outcome.failed > 0) {
            Notifier.warn(project, "AI File Guard", msg + "\n" + outcome.errors.take(5).joinToString("\n"))
        } else {
            Notifier.info(project, "AI File Guard", msg)
        }
    }

    private fun openSelectedInEditor() {
        val verdict = selectedVerdicts().firstOrNull() ?: return
        val vFile = LocalFileSystem.getInstance().refreshAndFindFileByPath(verdict.absolutePath) ?: return
        OpenFileDescriptor(project, vFile).navigate(true)
    }
}
