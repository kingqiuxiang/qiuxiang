package com.lingce.cleaner;

import com.intellij.openapi.options.Configurable;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;

import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import java.awt.BorderLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.Objects;

public final class AiCleanerConfigurable implements Configurable {
    private JPanel panel;
    private JCheckBox enabled;
    private JCheckBox scanOnProjectOpen;
    private JCheckBox watchNewFiles;
    private JCheckBox remoteDetectionEnabled;
    private JCheckBox autoDeleteTempFiles;
    private JCheckBox autoDeleteDisposableAiFiles;
    private JCheckBox autoIgnoreConfigFiles;
    private JTextField baseUrl;
    private JPasswordField apiKey;
    private JTextField model;
    private JTextField quarantineDirectory;
    private JTextField ignoreFileName;
    private JTextField maxScanFileSizeKb;
    private JTextArea customTempPatterns;

    @Override
    public @Nls(capitalization = Nls.Capitalization.Title) String getDisplayName() {
        return "LingCe AI File Cleaner";
    }

    @Override
    public @Nullable JComponent createComponent() {
        panel = new JPanel(new GridBagLayout());

        enabled = new JCheckBox("Enable LingCe AI File Cleaner");
        scanOnProjectOpen = new JCheckBox("Scan project when it opens");
        watchNewFiles = new JCheckBox("Watch and process newly created files");
        remoteDetectionEnabled = new JCheckBox("Use OpenAI-compatible API for uncertain files");
        autoDeleteTempFiles = new JCheckBox("Automatically delete temporary files");
        autoDeleteDisposableAiFiles = new JCheckBox("Automatically delete disposable AI-generated files");
        autoIgnoreConfigFiles = new JCheckBox("Add project/AI configuration files to ignore rules");
        baseUrl = new JTextField();
        apiKey = new JPasswordField();
        model = new JTextField();
        quarantineDirectory = new JTextField();
        ignoreFileName = new JTextField();
        maxScanFileSizeKb = new JTextField();
        customTempPatterns = new JTextArea(5, 48);

        int row = 0;
        addFullWidth(row++, enabled);
        addFullWidth(row++, scanOnProjectOpen);
        addFullWidth(row++, watchNewFiles);
        addFullWidth(row++, remoteDetectionEnabled);
        addLabeled(row++, "Base URL", baseUrl);
        addLabeled(row++, "API Key", apiKey);
        addLabeled(row++, "Model", model);
        addFullWidth(row++, autoDeleteTempFiles);
        addFullWidth(row++, autoDeleteDisposableAiFiles);
        addFullWidth(row++, autoIgnoreConfigFiles);
        addLabeled(row++, "Quarantine directory", quarantineDirectory);
        addLabeled(row++, "Ignore file name", ignoreFileName);
        addLabeled(row++, "Max scanned file size (KB)", maxScanFileSizeKb);
        addFullWidth(row++, new JLabel("Custom temporary file regex patterns, one per line:"));
        addFullWidth(row, new JScrollPane(customTempPatterns));

        reset();
        return panel;
    }

    @Override
    public boolean isModified() {
        AiCleanerSettings.StateData state = AiCleanerSettings.getInstance().getState();
        return enabled.isSelected() != state.enabled
            || scanOnProjectOpen.isSelected() != state.scanOnProjectOpen
            || watchNewFiles.isSelected() != state.watchNewFiles
            || remoteDetectionEnabled.isSelected() != state.remoteDetectionEnabled
            || autoDeleteTempFiles.isSelected() != state.autoDeleteTempFiles
            || autoDeleteDisposableAiFiles.isSelected() != state.autoDeleteDisposableAiFiles
            || autoIgnoreConfigFiles.isSelected() != state.autoIgnoreConfigFiles
            || !Objects.equals(baseUrl.getText(), state.baseUrl)
            || !Objects.equals(new String(apiKey.getPassword()), state.apiKey)
            || !Objects.equals(model.getText(), state.model)
            || !Objects.equals(quarantineDirectory.getText(), state.quarantineDirectory)
            || !Objects.equals(ignoreFileName.getText(), state.ignoreFileName)
            || !Objects.equals(maxScanFileSizeKb.getText(), Integer.toString(state.maxScanFileSizeKb))
            || !Objects.equals(customTempPatterns.getText(), state.customTempPatterns);
    }

    @Override
    public void apply() {
        AiCleanerSettings.StateData state = AiCleanerSettings.getInstance().getState();
        state.enabled = enabled.isSelected();
        state.scanOnProjectOpen = scanOnProjectOpen.isSelected();
        state.watchNewFiles = watchNewFiles.isSelected();
        state.remoteDetectionEnabled = remoteDetectionEnabled.isSelected();
        state.autoDeleteTempFiles = autoDeleteTempFiles.isSelected();
        state.autoDeleteDisposableAiFiles = autoDeleteDisposableAiFiles.isSelected();
        state.autoIgnoreConfigFiles = autoIgnoreConfigFiles.isSelected();
        state.baseUrl = baseUrl.getText().trim();
        state.apiKey = new String(apiKey.getPassword()).trim();
        state.model = model.getText().trim();
        state.quarantineDirectory = quarantineDirectory.getText().trim();
        state.ignoreFileName = ignoreFileName.getText().trim();
        state.maxScanFileSizeKb = parsePositiveInt(maxScanFileSizeKb.getText(), state.maxScanFileSizeKb);
        state.customTempPatterns = customTempPatterns.getText();
    }

    @Override
    public void reset() {
        AiCleanerSettings.StateData state = AiCleanerSettings.getInstance().getState();
        enabled.setSelected(state.enabled);
        scanOnProjectOpen.setSelected(state.scanOnProjectOpen);
        watchNewFiles.setSelected(state.watchNewFiles);
        remoteDetectionEnabled.setSelected(state.remoteDetectionEnabled);
        autoDeleteTempFiles.setSelected(state.autoDeleteTempFiles);
        autoDeleteDisposableAiFiles.setSelected(state.autoDeleteDisposableAiFiles);
        autoIgnoreConfigFiles.setSelected(state.autoIgnoreConfigFiles);
        baseUrl.setText(state.baseUrl);
        apiKey.setText(state.apiKey);
        model.setText(state.model);
        quarantineDirectory.setText(state.quarantineDirectory);
        ignoreFileName.setText(state.ignoreFileName);
        maxScanFileSizeKb.setText(Integer.toString(state.maxScanFileSizeKb));
        customTempPatterns.setText(state.customTempPatterns);
    }

    @Override
    public void disposeUIResources() {
        panel = null;
    }

    private void addLabeled(int row, String label, JComponent component) {
        GridBagConstraints labelConstraints = new GridBagConstraints();
        labelConstraints.gridx = 0;
        labelConstraints.gridy = row;
        labelConstraints.anchor = GridBagConstraints.WEST;
        labelConstraints.insets = new Insets(4, 0, 4, 8);
        panel.add(new JLabel(label + ":"), labelConstraints);

        GridBagConstraints fieldConstraints = new GridBagConstraints();
        fieldConstraints.gridx = 1;
        fieldConstraints.gridy = row;
        fieldConstraints.weightx = 1;
        fieldConstraints.fill = GridBagConstraints.HORIZONTAL;
        fieldConstraints.insets = new Insets(4, 0, 4, 0);
        panel.add(component, fieldConstraints);
    }

    private void addFullWidth(int row, JComponent component) {
        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.add(component, BorderLayout.CENTER);

        GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridx = 0;
        constraints.gridy = row;
        constraints.gridwidth = 2;
        constraints.weightx = 1;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.anchor = GridBagConstraints.WEST;
        constraints.insets = new Insets(4, 0, 4, 0);
        panel.add(wrapper, constraints);
    }

    private int parsePositiveInt(String value, int fallback) {
        try {
            int parsed = Integer.parseInt(value.trim());
            return Math.max(1, parsed);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }
}
