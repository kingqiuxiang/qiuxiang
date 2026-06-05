package com.lingce.aifilecleaner.settings;

import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBPasswordField;
import com.intellij.ui.components.JBTextField;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JTextField;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.Objects;

public final class AiFileCleanerConfigurable implements SearchableConfigurable {
    private JPanel panel;
    private JBTextField baseUrlField;
    private JBPasswordField apiKeyField;
    private JBTextField modelField;
    private TextFieldWithBrowseButton quarantineDirectoryField;
    private JBTextField maxSampleBytesField;
    private JBCheckBox useAiClassifierBox;
    private JBCheckBox autoCleanTmpBox;
    private JBCheckBox autoDeleteAiTrashBox;
    private JBCheckBox autoIgnoreConfigBox;
    private JBCheckBox scanOnOpenBox;
    private JBCheckBox scanNewFilesBox;

    @Override
    public @NotNull String getId() {
        return "com.lingce.aifilecleaner.settings";
    }

    @Override
    public @Nls String getDisplayName() {
        return "AI File Cleaner";
    }

    @Override
    public @Nullable JComponent createComponent() {
        panel = new JPanel(new GridBagLayout());
        baseUrlField = new JBTextField();
        apiKeyField = new JBPasswordField();
        modelField = new JBTextField();
        quarantineDirectoryField = new TextFieldWithBrowseButton();
        maxSampleBytesField = new JBTextField();
        useAiClassifierBox = new JBCheckBox("Use OpenAI-compatible classifier when API key is configured");
        autoCleanTmpBox = new JBCheckBox("Automatically delete temporary files");
        autoDeleteAiTrashBox = new JBCheckBox("Automatically delete obvious AI scratch/output files");
        autoIgnoreConfigBox = new JBCheckBox("Add project/AI config files to .gitignore and exclude directories");
        scanOnOpenBox = new JBCheckBox("Scan project when it opens");
        scanNewFilesBox = new JBCheckBox("Scan new and changed files");

        int row = 0;
        addLabeledField(row++, "Base URL", baseUrlField);
        addLabeledField(row++, "API Key", apiKeyField);
        addLabeledField(row++, "Model", modelField);
        addLabeledField(row++, "Quarantine directory", quarantineDirectoryField);
        addLabeledField(row++, "Max sample bytes", maxSampleBytesField);
        addFullWidth(row++, useAiClassifierBox);
        addFullWidth(row++, autoCleanTmpBox);
        addFullWidth(row++, autoDeleteAiTrashBox);
        addFullWidth(row++, autoIgnoreConfigBox);
        addFullWidth(row++, scanOnOpenBox);
        addFullWidth(row, scanNewFilesBox);
        reset();
        return panel;
    }

    @Override
    public boolean isModified() {
        AiFileCleanerSettings settings = AiFileCleanerSettings.getInstance();
        AiFileCleanerSettings.PluginState state = settings.getState();
        return !Objects.equals(baseUrlField.getText().trim(), state.baseUrl)
                || !Objects.equals(new String(apiKeyField.getPassword()).trim(), nullToBlank(settings.getApiKey()))
                || !Objects.equals(modelField.getText().trim(), state.model)
                || !Objects.equals(quarantineDirectoryField.getText().trim(), state.quarantineDirectory)
                || !Objects.equals(maxSampleBytesField.getText().trim(), Integer.toString(state.maxSampleBytes))
                || useAiClassifierBox.isSelected() != state.useAiClassifier
                || autoCleanTmpBox.isSelected() != state.autoCleanTemporaryFiles
                || autoDeleteAiTrashBox.isSelected() != state.autoDeleteObviousAiTrash
                || autoIgnoreConfigBox.isSelected() != state.autoIgnoreConfigFiles
                || scanOnOpenBox.isSelected() != state.scanOnProjectOpen
                || scanNewFilesBox.isSelected() != state.scanNewFiles;
    }

    @Override
    public void apply() throws ConfigurationException {
        AiFileCleanerSettings settings = AiFileCleanerSettings.getInstance();
        AiFileCleanerSettings.PluginState state = settings.getState();
        int maxSampleBytes;
        try {
            maxSampleBytes = Integer.parseInt(maxSampleBytesField.getText().trim());
        } catch (NumberFormatException error) {
            throw new ConfigurationException("Max sample bytes must be a number.");
        }
        if (maxSampleBytes < 1024) {
            throw new ConfigurationException("Max sample bytes must be at least 1024.");
        }

        state.baseUrl = blankToDefault(baseUrlField, "https://api.openai.com/v1");
        settings.setApiKey(new String(apiKeyField.getPassword()));
        state.model = blankToDefault(modelField, "gpt-4o-mini");
        state.quarantineDirectory = blankToDefault(quarantineDirectoryField.getText(), ".ai-file-cleaner/quarantine");
        state.maxSampleBytes = maxSampleBytes;
        state.useAiClassifier = useAiClassifierBox.isSelected();
        state.autoCleanTemporaryFiles = autoCleanTmpBox.isSelected();
        state.autoDeleteObviousAiTrash = autoDeleteAiTrashBox.isSelected();
        state.autoIgnoreConfigFiles = autoIgnoreConfigBox.isSelected();
        state.scanOnProjectOpen = scanOnOpenBox.isSelected();
        state.scanNewFiles = scanNewFilesBox.isSelected();
    }

    @Override
    public void reset() {
        AiFileCleanerSettings settings = AiFileCleanerSettings.getInstance();
        AiFileCleanerSettings.PluginState state = settings.getState();
        baseUrlField.setText(state.baseUrl);
        apiKeyField.setText(nullToBlank(settings.getApiKey()));
        modelField.setText(state.model);
        quarantineDirectoryField.setText(state.quarantineDirectory);
        maxSampleBytesField.setText(Integer.toString(state.maxSampleBytes));
        useAiClassifierBox.setSelected(state.useAiClassifier);
        autoCleanTmpBox.setSelected(state.autoCleanTemporaryFiles);
        autoDeleteAiTrashBox.setSelected(state.autoDeleteObviousAiTrash);
        autoIgnoreConfigBox.setSelected(state.autoIgnoreConfigFiles);
        scanOnOpenBox.setSelected(state.scanOnProjectOpen);
        scanNewFilesBox.setSelected(state.scanNewFiles);
    }

    private void addLabeledField(int row, String label, JComponent field) {
        GridBagConstraints labelConstraints = new GridBagConstraints();
        labelConstraints.gridx = 0;
        labelConstraints.gridy = row;
        labelConstraints.anchor = GridBagConstraints.WEST;
        labelConstraints.insets = new Insets(4, 0, 4, 8);
        panel.add(new JBLabel(label), labelConstraints);

        GridBagConstraints fieldConstraints = new GridBagConstraints();
        fieldConstraints.gridx = 1;
        fieldConstraints.gridy = row;
        fieldConstraints.weightx = 1.0;
        fieldConstraints.fill = GridBagConstraints.HORIZONTAL;
        fieldConstraints.insets = new Insets(4, 0, 4, 0);
        panel.add(field, fieldConstraints);
    }

    private void addFullWidth(int row, JComponent component) {
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridx = 0;
        constraints.gridy = row;
        constraints.gridwidth = 2;
        constraints.weightx = 1.0;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.insets = new Insets(4, 0, 4, 0);
        panel.add(component, constraints);
    }

    private static String blankToDefault(JTextField field, String defaultValue) {
        return blankToDefault(field.getText(), defaultValue);
    }

    private static String blankToDefault(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value.trim();
    }

    private static String nullToBlank(String value) {
        return value == null ? "" : value;
    }
}
