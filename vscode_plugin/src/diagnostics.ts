import * as vscode from 'vscode';
import { getZirconConfig } from './config';
import { scanDocument } from './syntax';
import {
    inspectZirconDocument,
    resolveSemanticInspectionTypes
} from './zirconInspections';

export class ZirconDiagnostics implements vscode.Disposable {
    private readonly collection = vscode.languages.createDiagnosticCollection('zircon');
    private readonly refreshGenerations = new Map<string, number>();
    private readonly semanticTimers = new Map<string, NodeJS.Timeout>();

    constructor(
        private readonly isDocumentEnabled: (document: vscode.TextDocument) => boolean = () => true
    ) {
    }

    public refresh(document: vscode.TextDocument): void {
        if (document.languageId !== 'java') {
            return;
        }

        const config = getZirconConfig();
        if (!config.enable || !config.enableDiagnostics || !this.isDocumentEnabled(document)) {
            this.clear(document);
            return;
        }

        const result = scanDocument(document);
        this.collection.set(document.uri, [
            ...result.diagnostics,
            ...inspectZirconDocument(document)
        ]);
        const key = document.uri.toString();
        const generation = (this.refreshGenerations.get(key) ?? 0) + 1;
        this.refreshGenerations.set(key, generation);
        const version = document.version;
        const previousTimer = this.semanticTimers.get(key);
        if (previousTimer) {
            clearTimeout(previousTimer);
        }
        this.semanticTimers.set(key, setTimeout(() => {
            this.semanticTimers.delete(key);
            void resolveSemanticInspectionTypes(document)
                .then((semanticTypes) => {
                    const latestConfig = getZirconConfig();
                    if (this.refreshGenerations.get(key) !== generation
                            || document.version !== version
                            || !latestConfig.enable
                            || !latestConfig.enableDiagnostics
                            || !this.isDocumentEnabled(document)) {
                        return;
                    }
                    const latestScan = scanDocument(document);
                    this.collection.set(document.uri, [
                        ...latestScan.diagnostics,
                        ...inspectZirconDocument(document, semanticTypes)
                    ]);
                })
                .catch(() => undefined);
        }, 600));
    }

    public clear(document: vscode.TextDocument): void {
        const key = document.uri.toString();
        this.refreshGenerations.delete(key);
        const timer = this.semanticTimers.get(key);
        if (timer) {
            clearTimeout(timer);
            this.semanticTimers.delete(key);
        }
        this.collection.delete(document.uri);
    }

    public refreshOpenEditors(): void {
        for (const document of vscode.workspace.textDocuments) {
            this.refresh(document);
        }
    }

    public dispose(): void {
        for (const timer of this.semanticTimers.values()) {
            clearTimeout(timer);
        }
        this.semanticTimers.clear();
        this.refreshGenerations.clear();
        this.collection.dispose();
    }
}
