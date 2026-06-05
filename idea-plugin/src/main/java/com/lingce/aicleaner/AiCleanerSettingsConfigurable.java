package com.lingce.aicleaner;

import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBPasswordField;
import com.intellij.ui.components.JBTextField;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;

public final class AiCleanerSettingsConfigurable implements SearchableConfigurable {
    private JPanel panel;
    private JBTextField baseUrlField;
    private JBPasswordField apiKeyField;
    private JBTextField modelField;
    private JBTextField quarantineDirField;
    private JBCheckBox useAiClassifierBox;
    private JBCheckBox autoQuarantineTempBox;
    private JBCheckBox autoQuarantineUselessAiBox;
    private JBCheckBox autoHandleConfigBox;
    private JSpinner maxPreviewBytesSpinner;

    @Override
    public @NotNull String getId() {
        return "com.lingce.aicleaner.settings";
    }

    @Override
    public @Nls(capitalization = Nls.Capitalization.Title) String getDisplayName() {
        return "AI File Cleaner";
    }

    @Override
    public @Nullable JComponent createComponent() {
        if (panel == null) {
            createPanel();
        }
        return panel;
    }

    @Override
    public boolean isModified() {
        AiCleanerSettingsState settings = AiCleanerSettingsState.getInstance();
        return !baseUrlField.getText().trim().equals(settings.getBaseUrl())
                || !new String(apiKeyField.getPassword()).trim().equals(settings.getApiKey())
                || !modelField.getText().trim().equals(settings.getModel())
                || !quarantineDirField.getText().trim().equals(settings.getQuarantineDirName())
                || useAiClassifierBox.isSelected() != settings.getState().useAiClassifier
                || autoQuarantineTempBox.isSelected() != settings.isAutoQuarantineTempFiles()
                || autoQuarantineUselessAiBox.isSelected() != settings.isAutoQuarantineUselessAiFiles()
                || autoHandleConfigBox.isSelected() != settings.isAutoHandleConfigFiles()
                || ((Integer) maxPreviewBytesSpinner.getValue()) != settings.getMaxPreviewBytes();
    }

    @Override
    public void apply() {
        AiCleanerSettingsState settings = AiCleanerSettingsState.getInstance();
        settings.setBaseUrl(baseUrlField.getText());
        settings.setApiKey(new String(apiKeyField.getPassword()));
        settings.setModel(modelField.getText());
        settings.setQuarantineDirName(quarantineDirField.getText());
        settings.setUseAiClassifier(useAiClassifierBox.isSelected());
        settings.setAutoQuarantineTempFiles(autoQuarantineTempBox.isSelected());
        settings.setAutoQuarantineUselessAiFiles(autoQuarantineUselessAiBox.isSelected());
        settings.setAutoHandleConfigFiles(autoHandleConfigBox.isSelected());
        settings.setMaxPreviewBytes((Integer) maxPreviewBytesSpinner.getValue());
    }

    @Override
    public void reset() {
        AiCleanerSettingsState settings = AiCleanerSettingsState.getInstance();
        baseUrlField.setText(settings.getBaseUrl());
        apiKeyField.setText(settings.getApiKey());
        modelField.setText(settings.getModel());
        quarantineDirField.setText(settings.getQuarantineDirName());
        useAiClassifierBox.setSelected(settings.getState().useAiClassifier);
        autoQuarantineTempBox.setSelected(settings.isAutoQuarantineTempFiles());
        autoQuarantineUselessAiBox.setSelected(settings.isAutoQuarantineUselessAiFiles());
        autoHandleConfigBox.setSelected(settings.isAutoHandleConfigFiles());
        maxPreviewBytesSpinner.setValue(settings.getMaxPreviewBytes());
    }

    private void createPanel() {
        panel = new JPanel(new GridBagLayout());
        baseUrlField = new JBTextField();
        apiKeyField = new JBPasswordField();
        modelField = new JBTextField();
        quarantineDirField = new JBTextField();
        useAiClassifierBox = new JBCheckBox("Use AI API classifier when API key is configured");
        autoQuarantineTempBox = new JBCheckBox("Automatically move temporary files to quarantine");
        autoQuarantineUselessAiBox = new JBCheckBox("Automatically move high-confidence useless AI files to quarantine");
        autoHandleConfigBox = new JBCheckBox("Add project/AI config files to .gitignore and exclude config folders");
        maxPreviewBytesSpinner = new JSpinner(new SpinnerNumberModel(12000, 1024, 100000, 1024));

        int row = 0;
        addRow(row++, "OpenAI-compatible Base URL", baseUrlField);
        addRow(row++, "API Key", apiKeyField);
        addRow(row++, "Model", modelField);
        addRow(row++, "Quarantine directory", quarantineDirField);
        addRow(row++, "Max content preview bytes", maxPreviewBytesSpinner);
        addWide(row++, useAiClassifierBox);
        addWide(row++, autoQuarantineTempBox);
        addWide(row++, autoQuarantineUselessAiBox);
        addWide(row++, autoHandleConfigBox);
        addWide(row, new JBLabel("API keys are stored in the IDE settings on this machine."));
        reset();
    }

    private void addRow(int row, String label, JComponent component) {
        GridBagConstraints labelConstraints = new GridBagConstraints();
        labelConstraints.gridx = 0;
        labelConstraints.gridy = row;
        labelConstraints.anchor = GridBagConstraints.WEST;
        labelConstraints.insets = new Insets(4, 0, 4, 12);
        panel.add(new JBLabel(label), labelConstraints);

        GridBagConstraints fieldConstraints = new GridBagConstraints();
        fieldConstraints.gridx = 1;
        fieldConstraints.gridy = row;
        fieldConstraints.weightx = 1.0;
        fieldConstraints.fill = GridBagConstraints.HORIZONTAL;
        fieldConstraints.insets = new Insets(4, 0, 4, 0);
        panel.add(component, fieldConstraints);
    }

    private void addWide(int row, JComponent component) {
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridx = 0;
        constraints.gridy = row;
        constraints.gridwidth = 2;
        constraints.weightx = 1.0;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.anchor = GridBagConstraints.WEST;
        constraints.insets = new Insets(4, 0, 4, 0);
        panel.add(component, constraints);
    }
}
