const fs = require('fs');
const path = require('path');
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

class TextEdit {
    constructor(range, newText) {
        this.range = range;
        this.newText = newText;
    }

    static insert(position, newText) {
        return new TextEdit(new Range(position, position), newText);
    }
}

class Uri {
    constructor(fsPath, scheme = 'file') {
        this.fsPath = path.resolve(fsPath);
        this.scheme = scheme;
    }

    static file(fsPath) {
        return new Uri(fsPath, 'file');
    }

    toString() {
        return `${this.scheme}:${this.fsPath}`;
    }
}

class SnippetString {
    constructor(value = '') {
        this.value = value;
        this.placeholderIndex = 1;
    }

    appendText(text) {
        this.value += text;
        return this;
    }

    appendPlaceholder(text) {
        this.value += `\${${this.placeholderIndex}:${text}}`;
        this.placeholderIndex += 1;
        return this;
    }
}

class MarkdownString {
    constructor(value = '') {
        this.value = value;
    }
}

class CompletionItem {
    constructor(label, kind) {
        this.label = label;
        this.kind = kind;
    }
}

function completionLabelText(item) {
    if (!item) {
        return '';
    }
    if (typeof item.label === 'string') {
        return item.label;
    }
    if (item.label && typeof item.label.label === 'string') {
        return `${item.label.label}${item.label.detail ?? ''}`;
    }
    return String(item.label);
}

function completionMethodName(item) {
    const label = completionLabelText(item);
    const methodEnd = label.indexOf('(');
    return methodEnd >= 0 ? label.slice(0, methodEnd) : label;
}

class MockDocument {
    constructor(fileName, text) {
        this.uri = Uri.file(fileName);
        this.fileName = path.resolve(fileName);
        this.languageId = 'java';
        this._text = text;
        this._lines = text.split(/\r?\n/);
        this._lineOffsets = [];
        let offset = 0;
        for (const line of this._lines) {
            this._lineOffsets.push(offset);
            offset += line.length + 1;
        }
    }

    getText(range) {
        if (!range) {
            return this._text;
        }
        return this._text.slice(this.offsetAt(range.start), this.offsetAt(range.end));
    }

    lineAt(line) {
        return { text: this._lines[line] ?? '' };
    }

    positionAt(offset) {
        const safeOffset = Math.max(0, Math.min(offset, this._text.length));
        for (let line = this._lineOffsets.length - 1; line >= 0; line--) {
            if (safeOffset >= this._lineOffsets[line]) {
                return new Position(line, safeOffset - this._lineOffsets[line]);
            }
        }
        return new Position(0, safeOffset);
    }

    offsetAt(position) {
        const lineOffset = this._lineOffsets[position.line] ?? this._text.length;
        return lineOffset + position.character;
    }

    getWordRangeAtPosition(position) {
        const offset = this.offsetAt(position);
        let left = offset;
        let right = offset;
        const isWord = (char) => /[A-Za-z0-9_$]/.test(char);

        if (left > 0 && (!isWord(this._text[left]) || left === this._text.length) && isWord(this._text[left - 1])) {
            left -= 1;
            right = Math.max(right, left + 1);
        }
        while (left > 0 && isWord(this._text[left - 1])) {
            left -= 1;
        }
        while (right < this._text.length && isWord(this._text[right])) {
            right += 1;
        }
        if (left === right) {
            return undefined;
        }
        return new Range(this.positionAt(left), this.positionAt(right));
    }
}

function createOutputChannel() {
    return {
        lines: [],
        appendLine(line) {
            this.lines.push(line);
        },
        append(line) {
            this.lines.push(line);
        },
        show() {},
        dispose() {}
    };
}

let capturedProvider;
const vscodeStub = {
    Position,
    Range,
    TextEdit,
    Uri,
    SnippetString,
    MarkdownString,
    CompletionItem,
    CompletionItemKind: {
        Method: 0
    },
    languages: {
        registerCompletionItemProvider(_selector, provider) {
            capturedProvider = provider;
            return { dispose() {} };
        }
    },
    workspace: {
        textDocuments: [],
        async findFiles() {
            return [];
        },
        fs: {
            async readFile() {
                return Buffer.alloc(0);
            }
        }
    },
    extensions: {
        getExtension() {
            return undefined;
        }
    }
};

const originalLoad = Module._load;
Module._load = function patchedLoad(request, parent, isMain) {
    if (request === 'vscode') {
        return vscodeStub;
    }
    return originalLoad.call(this, request, parent, isMain);
};

async function main() {
    const pluginRoot = path.resolve(__dirname, '..');
    const { ExMethodIndex, toJarArtifactKey } = require(path.join(pluginRoot, 'out', 'exMethodIndex.js'));
    const { registerExMethodCompletion } = require(path.join(pluginRoot, 'out', 'exMethodCompletion.js'));
    const {
        areVmArgsEquivalent,
        hasZirconAgentVmArg,
        readActiveAgentHeartbeat,
        sanitizeZirconVmArgs
    } = require(path.join(pluginRoot, 'out', 'javaAgent.js'));

    const quotedAgentArgs = String.raw`-Xmx1g -javaagent:"C:\Program Files\Zircon\zircon-agent.jar" -Dzircon.vscode=true -Duser.option=keep`;
    const unquotedAgentArgs = String.raw`-javaagent:C:\Zircon\zircon-agent.jar=mode -Dzircon.debug=false -Duser.option=keep`;
    for (const [vmArgs, expected] of [
        [quotedAgentArgs, '-Xmx1g -Duser.option=keep'],
        [unquotedAgentArgs, '-Duser.option=keep']
    ]) {
        if (!hasZirconAgentVmArg(vmArgs) || sanitizeZirconVmArgs(vmArgs) !== expected) {
            throw new Error(`Zircon Agent VM 参数未被正确识别或清理：${vmArgs}`);
        }
    }
    const configuredExtras = [
        '-Dzircon.debug.problemFiles=*',
        '-Dzircon.debug.selectors=checkMethodInvokes,createNew'
    ];
    const duplicatedExtras = `${quotedAgentArgs} ${configuredExtras.join(' ')} ${configuredExtras.join(' ')}`;
    if (sanitizeZirconVmArgs(duplicatedExtras, configuredExtras) !== '-Xmx1g -Duser.option=keep') {
        throw new Error('重复的 additionalAgentVmArgs 没有被清理');
    }
    const vmArgsUpperCasePath = String.raw`-Xmx768m -javaagent:"D:\IdeaProjects\Zircon\Zircon\vscode_plugin\server\zircon-agent.jar" -Dzircon.vscode=true -Dzircon.agent.jar="D:\IdeaProjects\Zircon\Zircon\vscode_plugin\server\zircon-agent.jar" -Dzircon.workspace.roots="D:\IdeaProjects\Zircon\ZirconTest"`;
    const vmArgsLowerCasePath = String.raw`-Xmx768m -javaagent:"d:\ideaprojects\zircon\zircon\vscode_plugin\server\zircon-agent.jar" -Dzircon.vscode=TRUE -Dzircon.agent.jar="d:\IdeaProjects\zircon\Zircon\vscode_plugin\server\zircon-agent.jar" -Dzircon.workspace.roots="d:\IdeaProjects\zircon\ZirconTest"`;
    if (!areVmArgsEquivalent(vmArgsUpperCasePath, vmArgsLowerCasePath)) {
        throw new Error('Windows 路径大小写差异被错误识别为 Java Language Server 配置变化');
    }
    if (areVmArgsEquivalent(vmArgsUpperCasePath, vmArgsLowerCasePath.replace('-Xmx768m', '-Xmx1g'))) {
        throw new Error('不同的非 Zircon VM 参数被错误识别为等价');
    }
    if (areVmArgsEquivalent('-DUserOption=true', '-Duseroption=true')) {
        throw new Error('区分大小写的普通 Java 系统属性被错误识别为等价');
    }
    console.log('[validate:completion] javaagent VM argument cleanup passed');

    const heartbeatDirectory = fs.mkdtempSync(path.join(require('os').tmpdir(), 'zircon-agent-heartbeat-'));
    const heartbeatJar = path.join(heartbeatDirectory, 'zircon-agent.jar');
    const heartbeatFile = path.join(heartbeatDirectory, 'runtime.properties');
    fs.writeFileSync(heartbeatJar, 'agent');
    fs.writeFileSync(heartbeatFile, [
        'pid=12345',
        `startedAt=${Date.now() + 1000}`,
        `agentJar=${heartbeatJar}`,
        'mode=full',
        ''
    ].join('\n'));
    if (!readActiveAgentHeartbeat(heartbeatFile, heartbeatJar, (pid) => pid === 12345)
            || readActiveAgentHeartbeat(heartbeatFile, heartbeatJar, () => false)) {
        throw new Error('JDT Agent runtime heartbeat validation failed');
    }
    console.log('[validate:completion] javaagent runtime heartbeat passed');

    const gradleBinaryJar = String.raw`D:\.gradle\caches\modules-2\files-2.1\demo\library\1.0\aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa\library-1.0.jar`;
    const gradleSourceJar = String.raw`D:\.gradle\caches\modules-2\files-2.1\demo\library\1.0\bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb\library-1.0-sources.jar`;
    if (toJarArtifactKey(gradleBinaryJar) !== toJarArtifactKey(gradleSourceJar)) {
        throw new Error('Gradle 缓存中的 binary/source JAR 没有配对为同一制品');
    }

    const output = createOutputChannel();
    const index = new ExMethodIndex(output);
    const context = { subscriptions: [] };
    registerExMethodCompletion(context, index, output);
    if (!capturedProvider || typeof capturedProvider.provideCompletionItems !== 'function') {
        throw new Error('未成功捕获 Zircon completion provider');
    }

    const fixturePath = process.env.ZIRCON_COMPLETION_FIXTURE
        ? path.resolve(process.env.ZIRCON_COMPLETION_FIXTURE)
        : path.resolve(pluginRoot, '..', 'test', 'src', 'test', 'java', 'test', 'TestExMethod.java');
    if (!fs.existsSync(fixturePath)) {
        throw new Error(`未找到补全测试样例：${fixturePath}`);
    }
    const exMethodSource = fs.readFileSync(fixturePath, 'utf8');
    const exMethodDocument = new MockDocument(fixturePath, exMethodSource);
    await index.updateDocument(exMethodDocument);
    const externalExMethodSource = [
        'package ext;',
        '',
        'import zircon.ExMethod;',
        '',
        'public class ExternalExMethodHolder {',
        '    @ExMethod',
        '    public static String externalTrim(String self) {',
        '        return self.trim();',
        '    }',
        '}'
    ].join('\n');
    const externalExMethodDocument = new MockDocument(
        path.resolve(path.dirname(fixturePath), '..', 'ext', 'ExternalExMethodHolder.java'),
        externalExMethodSource
    );
    await index.updateDocument(externalExMethodDocument);

    const probeSource = [
        'package test;',
        '',
        'public class CompletionProbe {',
        '    void probe() {',
        '        TestExMethod.ChildClass childClass = new TestExMethod.ChildClass();',
        '        java.util.HashMap<String, Integer> map = new java.util.HashMap<>();',
        '        childClass.',
        '        childClass.fa',
        '        "123".em',
        '        this.su',
        '        map.testGenericTransformMethod',
        '        "123".external',
        '    }',
        '}'
    ].join('\n');
    const probeDocument = new MockDocument(path.resolve(path.dirname(fixturePath), 'CompletionProbe.java'), probeSource);

    const nestedIndex = new ExMethodIndex(output);
    const nestedDocument = new MockDocument('Nested.java', [
        'package demo;',
        'class Outer {',
        '    static class Inner {',
        '        @ExMethod',
        '        public static String nestedExtension(String value) { return value; }',
        '    }',
        '}'
    ].join('\n'));
    await nestedIndex.updateDocument(nestedDocument);
    const nestedDescriptor = nestedIndex.getAll()[0];
    if (nestedDescriptor?.qualifiedDeclaringClass !== 'demo.Outer.Inner') {
        throw new Error(`嵌套 ExMethod 归属错误：${nestedDescriptor?.qualifiedDeclaringClass ?? 'missing'}`);
    }

    const multilineIndex = new ExMethodIndex(output);
    const multilineDocument = new MockDocument('Multi.java', [
        'package demo;',
        'class Multi {',
        '    @ExMethod(ex = {',
        '        String.class,',
        '        CharSequence.class',
        '    })',
        '    public static String multilineExtension(String value) { return value; }',
        '}'
    ].join('\n'));
    await multilineIndex.updateDocument(multilineDocument);
    const multilineDescriptor = multilineIndex.getAll()[0];
    if (!multilineDescriptor || multilineDescriptor.targetTypes.length !== 2) {
        throw new Error('多行 @ExMethod 注解没有被完整索引');
    }
    console.log('[validate:completion] parser nested and multiline annotations passed');

    const filterIndex = new ExMethodIndex(output);
    const filterDocument = new MockDocument('FilterAnnotation.java', [
        'package demo;',
        '@interface Allowed {}',
        '@Allowed class Accepted {}',
        'class Rejected {}',
        'class FilterExtensions {',
        '    @ExMethod(filterAnnotation = {Allowed.class})',
        '    public static String filtered(Object value) { return String.valueOf(value); }',
        '}'
    ].join('\n'));
    await filterIndex.updateDocument(filterDocument);
    const acceptedMatches = filterIndex.findMatches(['demo.Accepted'], false).map((item) => item.methodName);
    const rejectedMatches = filterIndex.findMatches(['demo.Rejected'], false).map((item) => item.methodName);
    if (!acceptedMatches.includes('filtered') || rejectedMatches.includes('filtered')) {
        throw new Error(`filterAnnotation 补全过滤错误：accepted=${acceptedMatches}, rejected=${rejectedMatches}`);
    }
    console.log('[validate:completion] filterAnnotation receiver filtering passed');

    const diskUri = Uri.file(path.resolve(pluginRoot, 'Race.java'));
    const diskSource = 'class Race { @ExMethod public static String diskExtension(String value) { return value; } }';
    const liveSource = [
        'class Race {',
        '    @ExMethod',
        '    public static String liveExtension(String value) { return value; }',
        '}'
    ].join('\n');
    const liveDocument = new MockDocument(diskUri.fsPath, liveSource);
    vscodeStub.workspace.textDocuments = [liveDocument];
    vscodeStub.workspace.findFiles = async (pattern) => pattern === '**/*.java' ? [diskUri] : [];
    vscodeStub.workspace.fs.readFile = async () => Buffer.from(diskSource, 'utf8');
    const raceIndex = new ExMethodIndex(output);
    await raceIndex.rebuild();
    const raceNames = raceIndex.getAll().map((descriptor) => descriptor.methodName);
    if (!raceNames.includes('liveExtension') || raceNames.includes('diskExtension')) {
        throw new Error(`索引重建覆盖了未保存文档：${raceNames.join(', ')}`);
    }

    let releaseFirstSearch;
    let javaSearchCount = 0;
    vscodeStub.workspace.textDocuments = [];
    vscodeStub.workspace.findFiles = async (pattern) => {
        if (pattern !== '**/*.java') {
            return [];
        }
        javaSearchCount++;
        if (javaSearchCount === 1) {
            return new Promise((resolve) => {
                releaseFirstSearch = () => resolve([]);
            });
        }
        return [];
    };
    const rebuildLoopIndex = new ExMethodIndex(output);
    const firstRebuild = rebuildLoopIndex.rebuild();
    await Promise.resolve();
    const queuedRebuild = rebuildLoopIndex.rebuild();
    if (!releaseFirstSearch) {
        throw new Error('未能暂停第一次索引重建');
    }
    releaseFirstSearch();
    await Promise.all([firstRebuild, queuedRebuild]);
    if (javaSearchCount !== 2) {
        throw new Error(`索引重建期间的新请求被丢失：实际执行 ${javaSearchCount} 次`);
    }
    console.log('[validate:completion] index rebuild race handling passed');

    const scenarios = [
        {
            label: 'empty-member',
            marker: 'childClass.\n',
            cursorShift: 'childClass.'.length,
            expected: ['fatherStringRString'],
            expectedLabelContains: ['(String str)'],
            forbiddenLabelContains: ['FatherClass father']
        },
        {
            label: 'prefixed-member',
            marker: 'childClass.fa',
            cursorShift: 'childClass.fa'.length,
            expected: ['fatherStringRString'],
            expectedLabelContains: ['(String str)'],
            forbiddenLabelContains: ['FatherClass father']
        },
        {
            label: 'string-member',
            marker: '"123".em',
            cursorShift: '"123".em'.length,
            expected: ['emptyStringRString'],
            expectedLabelContains: ['()'],
            forbiddenLabelContains: ['String str']
        },
        {
            label: 'explicit-this',
            marker: 'this.su',
            cursorShift: 'this.su'.length,
            expected: ['supplier'],
            expectedLabelContains: ['(Supplier<T> supplier)']
        },
        {
            label: 'generic-receiver',
            marker: 'map.testGenericTransformMethod',
            cursorShift: 'map.testGenericTransformMethod'.length,
            expected: ['testGenericTransformMethod'],
            expectedLabelContains: ['(String param2)'],
            expectedDetailContains: ['-> Integer']
        },
        {
            label: 'auto-import',
            marker: '"123".external',
            cursorShift: '"123".external'.length,
            expected: ['externalTrim'],
            expectedLabelContains: ['()'],
            expectedAdditionalImport: 'import ext.ExternalExMethodHolder;'
        }
    ];

    for (const scenario of scenarios) {
        const markerOffset = probeSource.indexOf(scenario.marker);
        if (markerOffset < 0) {
            throw new Error(`未找到场景标记: ${scenario.label}`);
        }
        const position = probeDocument.positionAt(markerOffset + scenario.cursorShift);
        const items = await capturedProvider.provideCompletionItems(probeDocument, position);
        const labels = (items ?? []).map((item) => completionLabelText(item));
        const methodNames = (items ?? []).map((item) => completionMethodName(item));
        for (const expectedLabel of scenario.expected) {
            const matchingIndex = methodNames.indexOf(expectedLabel);
            if (matchingIndex < 0) {
                throw new Error(`${scenario.label} 缺少补全项 ${expectedLabel}；实际返回: ${labels.slice(0, 20).join(', ')}`);
            }
            const matchedLabel = labels[matchingIndex];
            for (const expectedSubstring of scenario.expectedLabelContains ?? []) {
                if (!matchedLabel.includes(expectedSubstring)) {
                    throw new Error(`${scenario.label} 的补全项 ${expectedLabel} 未显示参数签名 ${expectedSubstring}；实际标签: ${matchedLabel}`);
                }
            }
            for (const forbiddenSubstring of scenario.forbiddenLabelContains ?? []) {
                if (matchedLabel.includes(forbiddenSubstring)) {
                    throw new Error(`${scenario.label} 的补全项 ${expectedLabel} 不应显示 ${forbiddenSubstring}；实际标签: ${matchedLabel}`);
                }
            }
            for (const expectedDetail of scenario.expectedDetailContains ?? []) {
                const matchedDetail = String(items[matchingIndex]?.detail ?? '');
                if (!matchedDetail.includes(expectedDetail)) {
                    throw new Error(`${scenario.label} 的补全项 ${expectedLabel} 未显示 detail ${expectedDetail}；实际 detail: ${matchedDetail}`);
                }
            }
            if (scenario.expectedAdditionalImport) {
                const additionalTextEdits = items[matchingIndex]?.additionalTextEdits ?? [];
                if (!additionalTextEdits.some((edit) => String(edit.newText ?? '').includes(scenario.expectedAdditionalImport))) {
                    throw new Error(`${scenario.label} 的补全项 ${expectedLabel} 未追加自动 import ${scenario.expectedAdditionalImport}`);
                }
            }
        }
        console.log(`[validate:completion] ${scenario.label} -> ${labels.length} items, 命中 ${scenario.expected.join(', ')}`);
    }

    let fullDependencyLoads = 0;
    let importedDependencyLoads = 0;
    index.ensureAllDependenciesIndexed = async () => {
        fullDependencyLoads++;
    };
    index.ensureImportedDependencies = async () => {
        importedDependencyLoads++;
    };
    registerExMethodCompletion(context, index, output, () => true);
    const nativeMarkerOffset = probeSource.indexOf('childClass.fa');
    await capturedProvider.provideCompletionItems(
        probeDocument,
        probeDocument.positionAt(nativeMarkerOffset + 'childClass.fa'.length)
    );
    if (fullDependencyLoads !== 0 || importedDependencyLoads !== 1) {
        throw new Error(
            `Agent 可用时 TypeScript completion 仍触发了错误的依赖扫描：full=${fullDependencyLoads}, imported=${importedDependencyLoads}`
        );
    }
    console.log('[validate:completion] native Agent bypasses TypeScript full dependency scan');
}

main().catch((error) => {
    console.error(`[validate:completion] FAILED: ${error instanceof Error ? error.stack ?? error.message : String(error)}`);
    process.exitCode = 1;
}).finally(() => {
    Module._load = originalLoad;
});
