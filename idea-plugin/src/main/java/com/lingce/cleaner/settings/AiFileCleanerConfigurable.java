package com.lingce.cleaner.settings;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBPasswordField;
import com.intellij.ui.components.JBTextField;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;

import javax.swing.JComponent;
import javax.swing.JPanel;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.Arrays;
import java.util.Objects;

public final class AiFileCleanerConfigurable implements Configurable {
    private JPanel panel;
    private JBCheckBox enabled;
    private JBTextField baseUrl;
    private JBPasswordField apiKey;
    private JBTextField model;
    private JBCheckBox autoDeleteTempFiles;
    private JBCheckBox autoHandleAiGeneratedFiles;
    private ComboBox<String> aiGeneratedCleanupMode;
    private JBCheckBox addConfigFilesToIgnore;
    private JBCheckBox markConfigDirectoriesExcluded;
    private JBCheckBox notifySuspiciousFiles;
    private JBTextField quarantineDirectory;
    private JBTextField maxBytesForAiAnalysis;

    @Override
    public @Nls String getDisplayName() {
        return "AI File Cleaner";
    }

    @Override
    public @Nullable JComponent createComponent() {
        panel = new JPanel(new GridBagLayout());
        enabled = new JBCheckBox("Enable automatic file monitoring");
        baseUrl = new JBTextField();
        apiKey = new JBPasswordField();
        model = new JBTextField();
        autoDeleteTempFiles = new JBCheckBox("Automatically delete temporary files");
        autoHandleAiGeneratedFiles = new JBCheckBox("Automatically handle high-confidence AI-generated useless files");
        aiGeneratedCleanupMode = new ComboBox<>(new String[]{
            AiFileCleanerSettings.CLEANUP_QUARANTINE,
            AiFileCleanerSettings.CLEANUP_DELETE
        });
        addConfigFilesToIgnore = new JBCheckBox("Add project/AI configuration files to .gitignore");
        markConfigDirectoriesExcluded = new JBCheckBox("Mark configuration directories as excluded when possible");
        notifySuspiciousFiles = new JBCheckBox("Notify for suspicious or unpredictable files");
        quarantineDirectory = new JBTextField();
        maxBytesForAiAnalysis = new JBTextField();

        int row = 0;
        addFullWidth(row++, enabled);
        addRow(row++, "OpenAI-compatible base URL", baseUrl);
        addRow(row++, "API key", apiKey);
        addRow(row++, "Model", model);
        addFullWidth(row++, autoDeleteTempFiles);
        addFullWidth(row++, autoHandleAiGeneratedFiles);
        addRow(row++, "AI-generated cleanup mode", aiGeneratedCleanupMode);
        addFullWidth(row++, addConfigFilesToIgnore);
        addFullWidth(row++, markConfigDirectoriesExcluded);
        addFullWidth(row++, notifySuspiciousFiles);
        addRow(row++, "Quarantine directory", quarantineDirectory);
        addRow(row, "Max bytes sent to AI per file", maxBytesForAiAnalysis);

        reset();
        return panel;
    }

    private void addFullWidth(int row, JComponent component) {
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridx = 0;
        constraints.gridy = row;
        constraints.gridwidth = 2;
        constraints.weightx = 1;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.insets = new Insets(6, 8, 6, 8);
        panel.add(component, constraints);
    }

    private void addRow(int row, String label, JComponent component) {
        GridBagConstraints labelConstraints = new GridBagConstraints();
        labelConstraints.gridx = 0;
        labelConstraints.gridy = row;
        labelConstraints.anchor = GridBagConstraints.WEST;
        labelConstraints.insets = new Insets(6, 8, 6, 8);
        panel.add(new JBLabel(label), labelConstraints);

        GridBagConstraints fieldConstraints = new GridBagConstraints();
        fieldConstraints.gridx = 1;
        fieldConstraints.gridy = row;
        fieldConstraints.weightx = 1;
        fieldConstraints.fill = GridBagConstraints.HORIZONTAL;
        fieldConstraints.insets = new Insets(6, 8, 6, 8);
        panel.add(component, fieldConstraints);
    }

    @Override
    public boolean isModified() {
        AiFileCleanerSettings.StateData state = AiFileCleanerSettings.getInstance().getState();
        if (state == null) {
            return false;
        }
        return enabled.isSelected() != state.enabled
            || !Objects.equals(baseUrl.getText(), state.baseUrl)
            || !Objects.equals(readApiKey(), AiFileCleanerSettings.getInstance().getApiKey())
            || !Objects.equals(model.getText(), state.model)
            || autoDeleteTempFiles.isSelected() != state.autoDeleteTempFiles
            || autoHandleAiGeneratedFiles.isSelected() != state.autoHandleAiGeneratedFiles
            || !Objects.equals(aiGeneratedCleanupMode.getSelectedItem(), state.aiGeneratedCleanupMode)
            || addConfigFilesToIgnore.isSelected() != state.addConfigFilesToIgnore
            || markConfigDirectoriesExcluded.isSelected() != state.markConfigDirectoriesExcluded
            || notifySuspiciousFiles.isSelected() != state.notifySuspiciousFiles
            || !Objects.equals(quarantineDirectory.getText(), state.quarantineDirectory)
            || !Objects.equals(maxBytesForAiAnalysis.getText(), Integer.toString(state.maxBytesForAiAnalysis));
    }

    @Override
    public void apply() {
        AiFileCleanerSettings.StateData state = AiFileCleanerSettings.getInstance().getState();
        if (state == null) {
            return;
        }
        state.enabled = enabled.isSelected();
        state.baseUrl = baseUrl.getText().trim();
        AiFileCleanerSettings.getInstance().setApiKey(readApiKey());
        state.model = model.getText().trim();
        state.autoDeleteTempFiles = autoDeleteTempFiles.isSelected();
        state.autoHandleAiGeneratedFiles = autoHandleAiGeneratedFiles.isSelected();
        state.aiGeneratedCleanupMode = Objects.toString(aiGeneratedCleanupMode.getSelectedItem(), AiFileCleanerSettings.CLEANUP_QUARANTINE);
        state.addConfigFilesToIgnore = addConfigFilesToIgnore.isSelected();
        state.markConfigDirectoriesExcluded = markConfigDirectoriesExcluded.isSelected();
        state.notifySuspiciousFiles = notifySuspiciousFiles.isSelected();
        state.quarantineDirectory = quarantineDirectory.getText().trim();
        state.maxBytesForAiAnalysis = parsePositiveInt(maxBytesForAiAnalysis.getText(), 24576);
    }

    @Override
    public void reset() {
        AiFileCleanerSettings.StateData state = AiFileCleanerSettings.getInstance().getState();
        if (state == null) {
            return;
        }
        enabled.setSelected(state.enabled);
        baseUrl.setText(state.baseUrl);
        apiKey.setText(AiFileCleanerSettings.getInstance().getApiKey());
        model.setText(state.model);
        autoDeleteTempFiles.setSelected(state.autoDeleteTempFiles);
        autoHandleAiGeneratedFiles.setSelected(state.autoHandleAiGeneratedFiles);
        aiGeneratedCleanupMode.setSelectedItem(state.aiGeneratedCleanupMode);
        addConfigFilesToIgnore.setSelected(state.addConfigFilesToIgnore);
        markConfigDirectoriesExcluded.setSelected(state.markConfigDirectoriesExcluded);
        notifySuspiciousFiles.setSelected(state.notifySuspiciousFiles);
        quarantineDirectory.setText(state.quarantineDirectory);
        maxBytesForAiAnalysis.setText(Integer.toString(state.maxBytesForAiAnalysis));
    }

    private String readApiKey() {
        char[] password = apiKey.getPassword();
        try {
            return new String(password).trim();
        } finally {
            Arrays.fill(password, '\0');
        }
    }

    private int parsePositiveInt(String value, int fallback) {
        try {
            int parsed = Integer.parseInt(value.trim());
            return Math.max(1024, parsed);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }
}
