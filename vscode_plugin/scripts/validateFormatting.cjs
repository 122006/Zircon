const assert = require('assert');
const fs = require('fs');
const path = require('path');
const Module = require('module');

const originalLoad = Module._load;
Module._load = function patchedLoad(request, parent, isMain) {
    return request === 'vscode' ? {} : originalLoad.call(this, request, parent, isMain);
};

try {
    const {
        buildFallbackImportOptimization,
        createZirconFormattingProxy
    } = require('../out/zirconFormatting');
    const { collectImportedDependencyTargets } = require('../out/exMethodIndex');

    const source = [
        'import zeta.B;',
        'import alpha.A;',
        'import zeta.B;',
        'import static zeta.Constants.VALUE;',
        '',
        'class Formatting {',
        '    String text = $"name=${user?.name() ?: "unknown"}";',
        '    Object value = user?.value() ?: fallback;',
        '}'
    ].join('\n');
    const proxy = createZirconFormattingProxy(source);
    assert.ok(!proxy.text.includes('$"'));
    assert.ok(!proxy.text.includes('?.'));
    assert.ok(!proxy.text.includes('?:'));
    const simulatedFormatterOutput = proxy.text
        .replace(/\*\/\./g, '*/ .')
        .replace(/\*\/==/g, '*/ ==');
    assert.strictEqual(proxy.restore(simulatedFormatterOutput), source);

    const importEdit = buildFallbackImportOptimization(source);
    assert.ok(importEdit);
    const optimized = source.slice(0, importEdit.start) + importEdit.newText + source.slice(importEdit.end);
    assert.ok(optimized.startsWith([
        'import alpha.A;',
        'import zeta.B;',
        '',
        'import static zeta.Constants.VALUE;'
    ].join('\n')));
    assert.strictEqual((optimized.match(/import zeta\.B;/g) || []).length, 1);

    const dependencyTargets = collectImportedDependencyTargets([
        'import demo.extensions.CollectionMethods;',
        'import demo.more.*;',
        'import static legacy.StringMethods.trim;',
        'import static legacy.NumberMethods.*;'
    ].join('\n'));
    assert.deepStrictEqual([...dependencyTargets.exactTypes].sort(), [
        'demo.extensions.CollectionMethods',
        'legacy.NumberMethods',
        'legacy.StringMethods'
    ]);
    assert.deepStrictEqual([...dependencyTargets.wildcardPackages], ['demo.more']);

    const indexSource = fs.readFileSync(path.join(__dirname, '..', 'src', 'exMethodIndex.ts'), 'utf8');
    assert.ok(!/MAX_(?:DEPENDENCY_JARS|WORKSPACE_JAVA_FILES|SOURCE_JAR_ENTRIES|BINARY_JAR_ENTRIES|BINARY_EXTENSION_CLASSES)/.test(indexSource));
    assert.ok(!/entryName\.includes\('\$'\)/.test(indexSource));
    const detectorSource = fs.readFileSync(path.join(__dirname, '..', 'src', 'projectDetector.ts'), 'utf8');
    const navigationSource = fs.readFileSync(path.join(__dirname, '..', 'src', 'exMethodNavigation.ts'), 'utf8');
    assert.ok(!/findFiles\([^\n]+,\s*\d+[\s_\d]*\)/.test(detectorSource));
    assert.ok(!/findFiles\([^\n]+,\s*\d+[\s_\d]*\)/.test(navigationSource));
    assert.ok(indexSource.includes('ensureImportedDependencies('));
    assert.ok(indexSource.includes('ensureAllDependenciesIndexed('));
    assert.ok(indexSource.includes('readZipCentralDirectory'));
    console.log('Zircon formatting/import and lazy dependency-index validation passed.');
} finally {
    Module._load = originalLoad;
}
