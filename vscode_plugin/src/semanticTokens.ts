import * as vscode from 'vscode';
import { getZirconConfig } from './config';
import { scanDocument } from './syntax';

const TOKEN_TYPES = ['keyword', 'operator'] as const;
const TOKEN_LEGEND = new vscode.SemanticTokensLegend([...TOKEN_TYPES], []);

export function registerZirconSemanticTokens(
    context: vscode.ExtensionContext,
    isDocumentEnabled: (document: vscode.TextDocument) => boolean = () => true
): void {
    const provider: vscode.DocumentSemanticTokensProvider = {
        provideDocumentSemanticTokens(document: vscode.TextDocument): vscode.ProviderResult<vscode.SemanticTokens> {
            const config = getZirconConfig();
            if (!config.enable
                    || !config.enableSemanticHighlighting
                    || document.languageId !== 'java'
                    || !isDocumentEnabled(document)) {
                return new vscode.SemanticTokens(new Uint32Array());
            }

            const result = scanDocument(document);
            const builder = new vscode.SemanticTokensBuilder(TOKEN_LEGEND);

            for (const range of result.prefixRanges) {
                builder.push(range, 'keyword', []);
            }
            for (const range of result.operatorRanges) {
                builder.push(range, 'operator', []);
            }

            return builder.build();
        }
    };

    context.subscriptions.push(vscode.languages.registerDocumentSemanticTokensProvider(
        [{ language: 'java', scheme: 'file' }, { language: 'java', scheme: 'untitled' }],
        provider,
        TOKEN_LEGEND
    ));
}
