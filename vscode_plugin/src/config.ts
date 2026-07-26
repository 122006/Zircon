import * as vscode from 'vscode';

export interface ZirconExtensionConfig {
    enable: boolean;
    enableExperimentalJavaAgent: boolean;
    autoInjectJavaAgent: boolean;
    onlyInjectWhenProjectUsesZircon: boolean;
    javaAgentConfigurationTarget: 'workspace' | 'global';
    enableDiagnostics: boolean;
    enableSemanticHighlighting: boolean;
    enableStatusBar: boolean;
    enableCodeActions: boolean;
    enableEditorExperience: boolean;
    debug: boolean;
    additionalAgentVmArgs: string[];
    additionalJdtVmArgs: string[];
}

const SECTION = 'zircon';

export function getZirconConfig(): ZirconExtensionConfig {
    const config = vscode.workspace.getConfiguration(SECTION);
    return {
        enable: config.get<boolean>('enable', true),
        enableExperimentalJavaAgent: config.get<boolean>('enableExperimentalJavaAgent', true),
        autoInjectJavaAgent: config.get<boolean>('autoInjectJavaAgent', true),
        onlyInjectWhenProjectUsesZircon: config.get<boolean>('onlyInjectWhenProjectUsesZircon', true),
        javaAgentConfigurationTarget: config.get<'workspace' | 'global'>('javaAgentConfigurationTarget', 'workspace'),
        enableDiagnostics: config.get<boolean>('enableDiagnostics', true),
        enableSemanticHighlighting: config.get<boolean>('enableSemanticHighlighting', true),
        enableStatusBar: config.get<boolean>('enableStatusBar', true),
        enableCodeActions: config.get<boolean>('enableCodeActions', true),
        enableEditorExperience: config.get<boolean>('enableEditorExperience', true),
        debug: config.get<boolean>('debug', false),
        additionalAgentVmArgs: config.get<string[]>('additionalAgentVmArgs', []),
        additionalJdtVmArgs: config.get<string[]>('additionalJdtVmArgs', [])
    };
}

export function getJavaConfigurationTarget(
    config: ZirconExtensionConfig
): vscode.ConfigurationTarget.Workspace | vscode.ConfigurationTarget.Global {
    return config.javaAgentConfigurationTarget === 'global'
        ? vscode.ConfigurationTarget.Global
        : vscode.ConfigurationTarget.Workspace;
}
