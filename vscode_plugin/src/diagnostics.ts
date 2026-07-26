import * as vscode from 'vscode';
import { getZirconConfig } from './config';
import { scanDocument } from './syntax';
import { inspectZirconDocument } from './zirconInspections';

export class ZirconDiagnostics implements vscode.Disposable {
    private readonly collection = vscode.languages.createDiagnosticCollection('zircon');

    public refresh(document: vscode.TextDocument): void {
        if (document.languageId !== 'java') {
            return;
        }

        const config = getZirconConfig();
        if (!config.enable || !config.enableDiagnostics) {
            this.collection.delete(document.uri);
            return;
        }

        const result = scanDocument(document);
        this.collection.set(document.uri, [
            ...result.diagnostics,
            ...inspectZirconDocument(document)
        ]);
    }

    public clear(document: vscode.TextDocument): void {
        this.collection.delete(document.uri);
    }

    public refreshOpenEditors(): void {
        for (const document of vscode.workspace.textDocuments) {
            this.refresh(document);
        }
    }

    public dispose(): void {
        this.collection.dispose();
    }
}
