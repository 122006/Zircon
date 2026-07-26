const assert = require('assert');
const Module = require('module');

class Position {
    constructor(line, character) {
        this.line = line;
        this.character = character;
    }
}

class Range {
    constructor(start, end) {
        this.start = start;
        this.end = end;
    }
}

class Diagnostic {
    constructor(range, message, severity) {
        this.range = range;
        this.message = message;
        this.severity = severity;
    }
}

class MockDocument {
    constructor(text) {
        this.languageId = 'java';
        this.version = 1;
        this.uri = { toString: () => 'file:///Inspection.java' };
        this.text = text;
        this.lineOffsets = [0];
        for (let index = 0; index < text.length; index += 1) {
            if (text[index] === '\n') this.lineOffsets.push(index + 1);
        }
    }

    getText() {
        return this.text;
    }

    positionAt(offset) {
        const safe = Math.max(0, Math.min(offset, this.text.length));
        let line = this.lineOffsets.length - 1;
        while (line > 0 && this.lineOffsets[line] > safe) line -= 1;
        return new Position(line, safe - this.lineOffsets[line]);
    }
}

const originalLoad = Module._load;
Module._load = function patchedLoad(request, parent, isMain) {
    if (request === 'vscode') {
        return {
            Position,
            Range,
            Diagnostic,
            DiagnosticSeverity: { Error: 0, Warning: 1, Information: 2 }
        };
    }
    return originalLoad.call(this, request, parent, isMain);
};

try {
    const { inspectZirconDocument } = require('../out/zirconInspections');
    const { scanDocument } = require('../out/syntax');
    const source = [
        'class Inspection {',
        '    @ExMethod',
        '    @ExMethod',
        '    public String broken(String... receiver) { return ""; }',
        '    int size() { return 1; }',
        '    void run() {',
        '        int count = 1;',
        '        String formatted = f"count=${%f:count}";',
        '        int safe = this?.size();',
        '    }',
        '}'
    ].join('\n');
    const diagnostics = inspectZirconDocument(new MockDocument(source));
    const codes = diagnostics.map((item) => item.code);
    for (const expected of [
        'zircon.exMethod.duplicate',
        'zircon.exMethod.mustBeStatic',
        'zircon.exMethod.varargReceiver',
        'zircon.string.formatTypeMismatch',
        'zircon.optional.primitiveResult'
    ]) {
        assert.ok(codes.includes(expected), `missing inspection ${expected}; got ${codes.join(', ')}`);
    }
    assert.ok(diagnostics.find((item) => item.code === 'zircon.exMethod.mustBeStatic').zirconFix);
    assert.ok(diagnostics.find((item) => item.code === 'zircon.optional.primitiveResult').zirconFix);
    const recoveryScan = scanDocument(new MockDocument('$"broken ${value.\nString ok = $"still ${works}";'));
    assert.strictEqual(recoveryScan.templateStringCount, 2);
    assert.strictEqual(recoveryScan.diagnostics.length, 1);
    console.log('Zircon semantic inspection validation passed.');
} finally {
    Module._load = originalLoad;
}
