import * as vscode from 'vscode';
import { getZirconConfig } from './config';
import { ZirconWorkspaceInfo } from './projectDetector';

export class ZirconStatusBar implements vscode.Disposable {
    private readonly item = vscode.window.createStatusBarItem(vscode.StatusBarAlignment.Right, 100);

    constructor() {
        this.item.name = 'Zircon';
        this.item.command = 'zircon.showProjectStatus';
    }

    public update(info: ZirconWorkspaceInfo, agentInstalled: boolean): void {
        const config = getZirconConfig();
        if (!config.enable || !config.enableStatusBar) {
            this.item.hide();
            return;
        }

        if (!info.hasWorkspace) {
            this.item.text = '$(circle-slash) Zircon';
            this.item.tooltip = '当前没有打开工作区。';
            this.item.show();
            return;
        }

        if (!info.hasJavaFiles) {
            this.item.text = '$(circle-outline) Zircon';
            this.item.tooltip = '当前工作区没有检测到 Java 文件。';
            this.item.show();
            return;
        }

        if (!info.hasZirconMarkers) {
            this.item.text = '$(symbol-namespace) Zircon';
            this.item.tooltip = '检测到 Java 项目，但还没有发现 Zircon 使用痕迹。';
            this.item.show();
            return;
        }

        this.item.text = agentInstalled ? '$(check) Zircon' : '$(warning) Zircon';
        this.item.tooltip = `已检测到 Zircon 项目。\n标记：${info.markers.join('、')}\nAgent：${agentInstalled ? '已写入 JDT VM 参数' : '未写入 JDT VM 参数'}`;
        this.item.show();
    }

    public dispose(): void {
        this.item.dispose();
    }
}
