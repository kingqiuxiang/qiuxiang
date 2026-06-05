package com.lingce.aicleaner;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.components.JBList;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import org.jetbrains.annotations.NotNull;

import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import java.awt.BorderLayout;
import java.awt.FlowLayout;

public final class CleanerToolWindowFactory implements ToolWindowFactory {
    @Override
    public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
        FileCleanerProjectService service = project.getService(FileCleanerProjectService.class);
        DefaultListModel<SuspectFileRecord> model = new DefaultListModel<>();
        JBList<SuspectFileRecord> list = new JBList<>(model);

        JButton refreshButton = new JButton("Refresh");
        JButton scanButton = new JButton("Scan Project");
        JButton quarantineButton = new JButton("Move to Quarantine");
        JButton deleteButton = new JButton("Delete");
        JButton ignoreButton = new JButton("Ignore/Exclude");
        JButton clearButton = new JButton("Clear List");

        Runnable reload = () -> {
            model.clear();
            for (SuspectFileRecord record : service.getSuspectRecords()) {
                model.addElement(record);
            }
        };

        refreshButton.addActionListener(event -> reload.run());
        scanButton.addActionListener(event -> service.scanProject());
        quarantineButton.addActionListener(event -> {
            SuspectFileRecord record = list.getSelectedValue();
            if (record != null) {
                service.moveToQuarantine(record.getPath());
            }
        });
        deleteButton.addActionListener(event -> {
            SuspectFileRecord record = list.getSelectedValue();
            if (record != null) {
                service.deletePath(record.getPath());
            }
        });
        ignoreButton.addActionListener(event -> {
            SuspectFileRecord record = list.getSelectedValue();
            if (record != null) {
                service.handleConfigFile(record.getPath());
            }
        });
        clearButton.addActionListener(event -> {
            service.clearRecords();
            reload.run();
        });

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT));
        actions.add(refreshButton);
        actions.add(scanButton);
        actions.add(quarantineButton);
        actions.add(deleteButton);
        actions.add(ignoreButton);
        actions.add(clearButton);

        JPanel panel = new JPanel(new BorderLayout());
        panel.add(actions, BorderLayout.NORTH);
        panel.add(new JScrollPane(list), BorderLayout.CENTER);
        reload.run();

        Content content = ContentFactory.getInstance().createContent(panel, "Suspect Files", false);
        toolWindow.getContentManager().addContent(content);
    }
}
