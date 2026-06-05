package com.lingce.aifilecleaner.ui;

import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBScrollPane;
import com.lingce.aifilecleaner.AiFileCleanerProjectService;
import com.lingce.aifilecleaner.Finding;

import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.ListSelectionModel;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.FlowLayout;
import java.util.List;

public final class AiFileCleanerToolWindowPanel extends JPanel {
    private final Project project;
    private final AiFileCleanerProjectService service;
    private final DefaultListModel<Finding> listModel = new DefaultListModel<>();
    private final JBList<Finding> findingsList = new JBList<>(listModel);

    public AiFileCleanerToolWindowPanel(Project project) {
        super(new BorderLayout());
        this.project = project;
        this.service = project.getService(AiFileCleanerProjectService.class);

        findingsList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        findingsList.setCellRenderer(new FindingRenderer());
        add(new JBScrollPane(findingsList), BorderLayout.CENTER);
        add(buttonBar(), BorderLayout.SOUTH);
        refresh();
    }

    private JComponent buttonBar() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton scanButton = new JButton("Scan");
        JButton refreshButton = new JButton("Refresh");
        JButton openButton = new JButton("Open");
        JButton quarantineButton = new JButton("Quarantine");
        JButton deleteButton = new JButton("Delete");
        JButton ignoreButton = new JButton("Ignore/exclude");
        JButton settingsButton = new JButton("Settings");

        scanButton.addActionListener(event -> service.scanProject());
        refreshButton.addActionListener(event -> refresh());
        openButton.addActionListener(event -> withSelected(finding -> {
            VirtualFile file = finding.virtualFile();
            if (file.isValid() && !file.isDirectory()) {
                FileEditorManager.getInstance(project).openFile(file, true);
            }
        }));
        quarantineButton.addActionListener(event -> withSelected(finding -> {
            service.getDispositionManager().quarantine(finding.virtualFile());
            service.removeFinding(finding.path());
            refresh();
        }));
        deleteButton.addActionListener(event -> withSelected(finding -> {
            service.getDispositionManager().delete(finding.virtualFile());
            service.removeFinding(finding.path());
            refresh();
        }));
        ignoreButton.addActionListener(event -> withSelected(finding -> {
            service.getDispositionManager().ignoreOrExclude(finding.virtualFile(), finding.classification());
            service.removeFinding(finding.path());
            refresh();
        }));
        settingsButton.addActionListener(event -> ShowSettingsUtil.getInstance().showSettingsDialog(project, "AI File Cleaner"));

        panel.add(scanButton);
        panel.add(refreshButton);
        panel.add(openButton);
        panel.add(quarantineButton);
        panel.add(deleteButton);
        panel.add(ignoreButton);
        panel.add(settingsButton);
        return panel;
    }

    private void refresh() {
        List<Finding> findings = service.getFindings();
        listModel.clear();
        for (Finding finding : findings) {
            if (finding.virtualFile().isValid()) {
                listModel.addElement(finding);
            }
        }
    }

    private void withSelected(FindingConsumer consumer) {
        Finding finding = findingsList.getSelectedValue();
        if (finding != null && finding.virtualFile().isValid()) {
            consumer.accept(finding);
        }
    }

    @FunctionalInterface
    private interface FindingConsumer {
        void accept(Finding finding);
    }

    private static final class FindingRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(
                JList<?> list,
                Object value,
                int index,
                boolean isSelected,
                boolean cellHasFocus
        ) {
            JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            if (value instanceof Finding finding) {
                label.setText("<html><b>" + finding.classification().kind() + "</b> "
                        + escape(finding.path())
                        + "<br/><span style='color:#888888'>"
                        + escape(finding.classification().reason())
                        + "</span></html>");
                if (!isSelected) {
                    label.setForeground(finding.classification().requiresUserDecision() ? JBColor.ORANGE : JBColor.foreground());
                }
            }
            return label;
        }

        private static String escape(String value) {
            return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
        }
    }
}
