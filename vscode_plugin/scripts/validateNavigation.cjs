const assert = require('assert');
const Module = require('module');

class Position {
    constructor(line, character) {
        this.line = line;
        this.character = character;
    }
    translate(lineDelta = 0, characterDelta = 0) {
        return new Position(this.line + lineDelta, this.character + characterDelta);
    }
}

class Range {
    constructor(startLine, startCharacter, endLine, endCharacter) {
        if (startLine instanceof Position) {
            this.start = startLine;
            this.end = startCharacter;
        } else {
            this.start = new Position(startLine, startCharacter);
            this.end = new Position(endLine, endCharacter);
        }
    }
}

class Uri {
    constructor(value) {
        this.value = value;
        this.scheme = 'file';
    }
    toString() { return this.value; }
}

class WorkspaceEdit {
    constructor() { this.edits = new Map(); }
    replace(uri, range, newText) {
        const key = uri.toString();
        const entry = this.edits.get(key) || { uri, edits: [] };
        entry.edits.push({ range, newText });
        this.edits.set(key, entry);
    }
    entries() { return [...this.edits.values()].map((entry) => [entry.uri, entry.edits]); }
}

class MockDocument {
    constructor(uri, text) {
        this.uri = uri;
        this.text = text;
        this.lines = text.split('\n');
    }
    getText(range) {
        if (!range) return this.text;
        if (range.start.line === range.end.line) {
            return this.lines[range.start.line].slice(range.start.character, range.end.character);
        }
        const selected = [this.lines[range.start.line].slice(range.start.character)];
        for (let line = range.start.line + 1; line < range.end.line; line += 1) selected.push(this.lines[line]);
        selected.push(this.lines[range.end.line].slice(0, range.end.character));
        return selected.join('\n');
    }
}

const declarationUri = new Uri('file:///Extensions.java');
const usageUri = new Uri('file:///Usage.java');
const documents = new Map([
    [declarationUri.toString(), new MockDocument(declarationUri, 'static String surround(String value) {}')],
    [usageUri.toString(), new MockDocument(usageUri, [
        'return value.surround();',
        'return Extensions.surround(value);'
    ].join('\n'))]
]);
const vscodeStub = {
    Position,
    Range,
    Uri,
    WorkspaceEdit,
    workspace: {
        openTextDocument: async (uri) => documents.get(uri.toString())
    }
};

const originalLoad = Module._load;
Module._load = function patchedLoad(request, parent, isMain) {
    return request === 'vscode' ? vscodeStub : originalLoad.call(this, request, parent, isMain);
};

(async () => {
    try {
        const { mergeNativeRenameReferences } = require('../out/exMethodNavigation');
        const original = new WorkspaceEdit();
        original.replace(declarationUri, new Range(0, 14, 0, 22), 'renamed');
        // This mirrors the bad JDT facade edit: it spans two calls and must be discarded.
        original.replace(usageUri, new Range(0, 13, 1, 26), 'renamed ... renamed');
        const references = [
            { uri: declarationUri, range: new Range(0, 14, 0, 22) },
            { uri: usageUri, range: new Range(0, 13, 0, 21) },
            { uri: usageUri, range: new Range(1, 18, 1, 26) }
        ];
        const merged = await mergeNativeRenameReferences(original, references, 'surround', 'renamed');
        const edits = merged.entries().flatMap(([, items]) => items);
        assert.strictEqual(edits.length, 3);
        assert.ok(edits.every((item) => item.newText === 'renamed'));
        assert.ok(edits.every((item) => item.range.start.line === item.range.end.line));
        console.log('Zircon native rename sanitization validation passed.');
    } finally {
        Module._load = originalLoad;
    }
})().catch((error) => {
    console.error(error);
    process.exitCode = 1;
});
