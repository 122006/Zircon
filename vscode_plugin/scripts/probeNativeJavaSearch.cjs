const { spawn, spawnSync } = require('child_process');
const fs = require('fs');
const os = require('os');
const path = require('path');
const { pathToFileURL } = require('url');

const cliArguments = process.argv.slice(2);
const workspaceArgument = cliArguments.find((argument) => !argument.startsWith('--'));
const workspace = path.resolve(workspaceArgument || path.join(__dirname, '..', 'test-fixtures', 'native-search'));
const diagnosticsOnly = cliArguments.includes('--diagnostics-only');
const completionOnly = cliArguments.includes('--completion-only');
const indexedCompletionOnly = cliArguments.includes('--indexed-completion-only');
const optionalChainOnly = cliArguments.includes('--optional-chain-only');
const optionalCompletionOnly = cliArguments.includes('--optional-completion-only');
const optionalTypeErrorOnly = cliArguments.includes('--optional-type-error-only');
const templateOnly = cliArguments.includes('--template-only');
const zirconTestSource = path.join(workspace, 'src', 'test', 'java', 'test', 'TestExMethodImpl.java');
const zirconGenericChainSource = path.join(workspace, 'src', 'test', 'java', 'test', 'TestExMethod.java');
const zirconOptionalChainSource = path.join(workspace, 'src', 'test', 'java', 'test', 'TestOptionalChaining.java');
const sourceFile = (optionalChainOnly || optionalCompletionOnly || optionalTypeErrorOnly) && fs.existsSync(zirconOptionalChainSource)
    ? zirconOptionalChainSource
    : completionOnly && fs.existsSync(zirconGenericChainSource)
    ? zirconGenericChainSource
    : fs.existsSync(zirconTestSource)
    ? zirconTestSource
    : path.join(workspace, 'src', 'probe', 'Usage.java');
const declarationFile = fs.existsSync(zirconTestSource)
    ? null
    : path.join(workspace, 'src', 'probe', 'Extensions.java');
const probeMethodName = completionOnly || indexedCompletionOnly || optionalCompletionOnly
    ? (fs.existsSync(zirconGenericChainSource) ? 'map' : 'surround')
    : fs.existsSync(zirconTestSource) ? 'emptyStringRString' : 'surround';
const agentJar = path.resolve(__dirname, '..', 'server', 'zircon-agent.jar');
const projectJdk = findJdkHome(22);
const extensionRoot = findLatestJavaExtension();
const javaExe = findFirst(extensionRoot, (file) => path.basename(file).toLowerCase() === 'java.exe');
const launcherJar = findFirst(
    path.join(extensionRoot, 'server', 'plugins'),
    (file) => /^org\.eclipse\.equinox\.launcher_.*\.jar$/.test(path.basename(file))
);
const configuration = path.join(extensionRoot, 'server', 'config_win');
const dataDirectory = fs.mkdtempSync(path.join(os.tmpdir(), 'zircon-jdtls-search-'));
const agentLog = path.join(dataDirectory, 'zircon-agent.log');

for (const required of [workspace, sourceFile, agentJar, javaExe, launcherJar, configuration, projectJdk]) {
    if (!required || !fs.existsSync(required)) {
        throw new Error(`Required JDT search probe path does not exist: ${required}`);
    }
}

buildBinaryExtensionFixture();

const args = [
    '--add-modules=ALL-SYSTEM',
    '--add-opens', 'java.base/java.util=ALL-UNNAMED',
    '--add-opens', 'java.base/java.lang=ALL-UNNAMED',
    '--add-opens', 'java.base/sun.nio.fs=ALL-UNNAMED',
    '-Xms128m',
    '-Xmx1536m',
    '-XX:ReservedCodeCacheSize=256m',
    '-Declipse.application=org.eclipse.jdt.ls.core.id1',
    '-Dosgi.bundles.defaultStartLevel=4',
    '-Declipse.product=org.eclipse.jdt.ls.core.product',
    '-Dfile.encoding=UTF-8',
    '-Dzircon.vscode=true',
    `-Dzircon.debug=${cliArguments.includes('--debug')}`,
    `-Dzircon.trace=${cliArguments.includes('--trace')}`,
    `-Dzircon.trace.selectors=${
        diagnosticsOnly
            ? `${probeMethodName},map`
            : indexedCompletionOnly
                ? `${probeMethodName},binarySurround,filtered`
                : probeMethodName
    }`,
    ...(diagnosticsOnly ? ['-Dzircon.debug.selectors=map'] : []),
    ...(diagnosticsOnly || optionalTypeErrorOnly || templateOnly ? ['-Dzircon.debug.problemFiles=*'] : []),
    `-Dzircon.log.path=${agentLog}`,
    `-Dzircon.agent.jar=${agentJar}`,
    `-Dzircon.workspace.roots=${workspace}`,
    `-javaagent:${agentJar}`,
    '-jar', launcherJar,
    '-configuration', configuration,
    '-data', dataDirectory
];

const server = spawn(javaExe, args, { stdio: ['pipe', 'pipe', 'pipe'], windowsHide: true });
let nextId = 1;
let stdoutBuffer = Buffer.alloc(0);
const pending = new Map();
const notifications = [];
const appliedWorkspaceEdits = [];

server.stdout.on('data', (chunk) => {
    stdoutBuffer = Buffer.concat([stdoutBuffer, chunk]);
    parseMessages();
});
server.stderr.on('data', (chunk) => {
    const text = chunk.toString('utf8');
    for (const line of text.split(/\r?\n/)) {
        if (/\[Zircon|ERROR|Exception/.test(line)) {
            process.stderr.write(`${line}\n`);
        }
    }
});
server.on('exit', (code) => {
    for (const { reject } of pending.values()) {
        reject(new Error(`JDT LS exited unexpectedly with code ${code}`));
    }
    pending.clear();
});

function send(message) {
    const json = Buffer.from(JSON.stringify({ jsonrpc: '2.0', ...message }), 'utf8');
    server.stdin.write(`Content-Length: ${json.length}\r\n\r\n`);
    server.stdin.write(json);
}

function request(method, params, timeoutMs = 60_000) {
    const id = nextId++;
    return new Promise((resolve, reject) => {
        const timer = setTimeout(() => {
            pending.delete(id);
            reject(new Error(`Timed out waiting for ${method}`));
        }, timeoutMs);
        pending.set(id, {
            resolve: (value) => {
                clearTimeout(timer);
                resolve(value);
            },
            reject: (error) => {
                clearTimeout(timer);
                reject(error);
            }
        });
        send({ id, method, params });
    });
}

function notify(method, params) {
    send({ method, params });
}

function parseMessages() {
    while (true) {
        const headerEnd = stdoutBuffer.indexOf('\r\n\r\n');
        if (headerEnd < 0) {
            return;
        }
        const header = stdoutBuffer.subarray(0, headerEnd).toString('ascii');
        const lengthMatch = /Content-Length:\s*(\d+)/i.exec(header);
        if (!lengthMatch) {
            stdoutBuffer = stdoutBuffer.subarray(headerEnd + 4);
            continue;
        }
        const length = Number(lengthMatch[1]);
        const messageEnd = headerEnd + 4 + length;
        if (stdoutBuffer.length < messageEnd) {
            return;
        }
        const body = stdoutBuffer.subarray(headerEnd + 4, messageEnd).toString('utf8');
        stdoutBuffer = stdoutBuffer.subarray(messageEnd);
        handleMessage(JSON.parse(body));
    }
}

function handleMessage(message) {
    if (message.id !== undefined && message.method) {
        handleServerRequest(message);
        return;
    }
    if (message.id !== undefined) {
        const callback = pending.get(message.id);
        if (!callback) {
            return;
        }
        pending.delete(message.id);
        if (message.error) {
            callback.reject(new Error(`${message.error.code}: ${message.error.message}`));
        } else {
            callback.resolve(message.result);
        }
        return;
    }
    if (message.method) {
        notifications.push(message);
    }
}

function handleServerRequest(message) {
    let result = null;
    if (message.method === 'workspace/configuration') {
        result = (message.params?.items || []).map((item) => configurationValue(item?.section));
    } else if (message.method === 'workspace/workspaceFolders') {
        result = [{ name: path.basename(workspace), uri: pathToFileURL(workspace).href }];
    } else if (message.method === 'workspace/applyEdit') {
        appliedWorkspaceEdits.push(message.params?.edit);
        result = { applied: true };
    }
    send({ id: message.id, result });
}

function configurationValue(section) {
    const runtimes = [{ name: 'JavaSE-22', path: projectJdk, default: true }];
    const javaSettings = {
        configuration: { runtimes, updateBuildConfiguration: 'automatic' },
        import: { gradle: { java: { home: projectJdk } } },
        autobuild: { enabled: true },
        referencesCodeLens: { enabled: true }
    };
    const values = {
        'java': javaSettings,
        'java.configuration.runtimes': runtimes,
        'java.configuration.updateBuildConfiguration': 'automatic',
        'java.import.gradle.java.home': projectJdk,
        'java.autobuild.enabled': true,
        'java.referencesCodeLens.enabled': true
    };
    return Object.prototype.hasOwnProperty.call(values, section) ? values[section] : null;
}

function findLatestJavaExtension() {
    const configured = process.env.REDHAT_JAVA_EXTENSION;
    if (configured) {
        return path.resolve(configured);
    }
    const extensionDirectory = path.join(os.homedir(), '.vscode', 'extensions');
    return fs.readdirSync(extensionDirectory, { withFileTypes: true })
        .filter((entry) => entry.isDirectory() && entry.name.startsWith('redhat.java-'))
        .map((entry) => path.join(extensionDirectory, entry.name))
        .sort((left, right) => fs.statSync(right).mtimeMs - fs.statSync(left).mtimeMs)[0];
}

function findJdkHome(languageVersion) {
    const configured = process.env[`JDK${languageVersion}_HOME`];
    if (configured && fs.existsSync(path.join(configured, 'bin', 'java.exe'))) {
        return path.resolve(configured);
    }
    const driveRoot = path.parse(workspace).root;
    const roots = [
        path.join(os.homedir(), '.jdks'),
        path.join(driveRoot, '.gradle', 'jdks'),
        path.join(driveRoot, 'Library', 'Jdks')
    ];
    for (const root of roots) {
        const java = findFirst(root, (file) => {
            if (path.basename(file).toLowerCase() !== 'java.exe' || path.basename(path.dirname(file)) !== 'bin') {
                return false;
            }
            const home = path.dirname(path.dirname(file));
            const releaseFile = path.join(home, 'release');
            if (!fs.existsSync(releaseFile)) {
                return false;
            }
            return new RegExp(`JAVA_VERSION="${languageVersion}(?:\\.|\")`).test(fs.readFileSync(releaseFile, 'utf8'));
        });
        if (java) {
            return path.dirname(path.dirname(java));
        }
    }
    return undefined;
}

function findFirst(root, predicate) {
    if (!root || !fs.existsSync(root)) {
        return undefined;
    }
    const queue = [root];
    while (queue.length > 0) {
        const current = queue.shift();
        const stat = fs.statSync(current);
        if (stat.isFile()) {
            if (predicate(current)) {
                return current;
            }
            continue;
        }
        for (const entry of fs.readdirSync(current, { withFileTypes: true })) {
            queue.push(path.join(current, entry.name));
        }
    }
    return undefined;
}

function buildBinaryExtensionFixture() {
    const fixtureRoot = path.join(workspace, 'binary-fixture');
    const annotationSource = path.join(workspace, 'src', 'zircon', 'ExMethod.java');
    if (!fs.existsSync(fixtureRoot) || !fs.existsSync(annotationSource)) {
        return;
    }
    const javaSources = [annotationSource];
    const queue = [fixtureRoot];
    while (queue.length > 0) {
        const current = queue.shift();
        for (const entry of fs.readdirSync(current, { withFileTypes: true })) {
            const entryPath = path.join(current, entry.name);
            if (entry.isDirectory()) {
                queue.push(entryPath);
            } else if (entry.name.endsWith('.java')) {
                javaSources.push(entryPath);
            }
        }
    }
    const outputJar = path.join(workspace, 'lib', 'binary-extensions.jar');
    const newestSourceModifiedAt = Math.max(
        ...javaSources.map((source) => fs.statSync(source).mtimeMs)
    );
    if (fs.existsSync(outputJar) && fs.statSync(outputJar).mtimeMs >= newestSourceModifiedAt) {
        return;
    }
    const temporaryRoot = fs.mkdtempSync(path.join(os.tmpdir(), 'zircon-binary-fixture-'));
    const classesDirectory = path.join(temporaryRoot, 'classes');
    const temporaryJar = path.join(temporaryRoot, 'binary-extensions.jar');
    fs.mkdirSync(classesDirectory, { recursive: true });
    fs.mkdirSync(path.dirname(outputJar), { recursive: true });
    try {
        runChecked(path.join(projectJdk, 'bin', 'javac.exe'), [
            '-encoding', 'UTF-8',
            '-d', classesDirectory,
            ...javaSources
        ]);
        runChecked(path.join(projectJdk, 'bin', 'jar.exe'), [
            '--create',
            '--file', temporaryJar,
            '-C', classesDirectory,
            'dependency'
        ]);
        fs.copyFileSync(temporaryJar, outputJar);
    } finally {
        fs.rmSync(temporaryRoot, { recursive: true, force: true });
    }
}

function runChecked(command, arguments) {
    const result = spawnSync(command, arguments, {
        cwd: workspace,
        encoding: 'utf8',
        windowsHide: true
    });
    if (result.error || result.status !== 0) {
        throw new Error(
            `Command failed: ${command}\n${result.error || result.stderr || result.stdout || `exit ${result.status}`}`
        );
    }
}

function completionLabel(item) {
    return typeof item?.label === 'string' ? item.label : item?.label?.label || '';
}

async function requestCompletionItems(uri, completionPosition, targetName, maximumAttempts) {
    let items = [];
    for (let attempt = 1; attempt <= maximumAttempts; attempt++) {
        const completionResult = await request('textDocument/completion', {
            textDocument: { uri },
            position: completionPosition,
            context: { triggerKind: 1 }
        }, 90_000);
        items = Array.isArray(completionResult) ? completionResult : completionResult?.items || [];
        if (!targetName || items.some((item) => completionLabel(item).replace(/\(.*$/, '') === targetName)) {
            return { items, attempts: attempt };
        }
        if (attempt < maximumAttempts) {
            await new Promise((resolve) => setTimeout(resolve, 500));
        }
    }
    return { items, attempts: maximumAttempts };
}

function findInvocationPosition(text, methodName) {
    const marker = `.${methodName}`;
    const offset = text.indexOf(marker);
    if (offset < 0) {
        throw new Error(`Cannot find extension invocation ${marker}`);
    }
    const before = text.slice(0, offset + 1);
    const lines = before.split(/\r?\n/);
    return { line: lines.length - 1, character: lines[lines.length - 1].length };
}

function findMethodDeclarationPosition(text, methodName) {
    const pattern = new RegExp(`\\b${methodName}\\s*\\(`);
    const match = pattern.exec(text);
    if (!match) {
        throw new Error(`Cannot find method declaration ${methodName}`);
    }
    return offsetToPosition(text, match.index);
}

function findCompletionPosition(text, methodName) {
    const marker = `.${methodName}`;
    const offset = text.indexOf(marker);
    if (offset < 0) {
        throw new Error(`Cannot find completion invocation ${marker}`);
    }
    const prefixLength = Math.min(2, methodName.length);
    const before = text.slice(0, offset + 1 + prefixLength);
    const lines = before.split(/\r?\n/);
    return { line: lines.length - 1, character: lines[lines.length - 1].length };
}

function findOptionalChainPosition(text) {
    const offset = text.indexOf('?.');
    if (offset < 0) {
        throw new Error('Cannot find optional-chain syntax in probe source.');
    }
    return offsetToPosition(text, offset + 2);
}

function countWorkspaceEditChanges(edit) {
    if (!edit) {
        return 0;
    }
    const direct = Object.values(edit.changes || {}).reduce((sum, edits) => sum + edits.length, 0);
    return direct + (edit.documentChanges || []).reduce((sum, change) => {
        return sum + (Array.isArray(change.edits) ? change.edits.length : 0);
    }, 0);
}

function workspaceEditEntries(edit) {
    if (!edit) {
        return [];
    }
    const entries = [];
    for (const [uri, edits] of Object.entries(edit.changes || {})) {
        for (const entry of edits) {
            entries.push({ uri, range: entry.range, newText: entry.newText });
        }
    }
    for (const change of edit.documentChanges || []) {
        const uri = change.textDocument?.uri || change.uri;
        for (const entry of change.edits || []) {
            entries.push({ uri, range: entry.range, newText: entry.newText });
        }
    }
    return entries;
}

function positionToOffset(text, position) {
    let offset = 0;
    let line = 0;
    while (line < position.line && offset < text.length) {
        const newline = text.indexOf('\n', offset);
        if (newline < 0) {
            return text.length;
        }
        offset = newline + 1;
        line++;
    }
    return Math.min(text.length, offset + position.character);
}

function offsetToPosition(text, targetOffset) {
    const before = text.slice(0, targetOffset);
    const lines = before.split(/\r?\n/);
    return { line: lines.length - 1, character: lines[lines.length - 1].length };
}

function applyWorkspaceEditToText(edit, uri, text) {
    const edits = workspaceEditEntries(edit)
        .filter((entry) => entry.uri === uri)
        .map((entry) => ({
            start: positionToOffset(text, entry.range.start),
            end: positionToOffset(text, entry.range.end),
            newText: entry.newText || ''
        }))
        .sort((left, right) => right.start - left.start);
    let result = text;
    for (const entry of edits) {
        result = result.slice(0, entry.start) + entry.newText + result.slice(entry.end);
    }
    return result;
}

function sanitizeRenameWithNativeReferences(edit, references, oldName, newName, textsByUri) {
    const changes = {};
    const seen = new Set();
    const add = (uri, range, replacement) => {
        const text = textsByUri.get(uri);
        if (text === undefined) return;
        const start = positionToOffset(text, range.start);
        const reportedEnd = positionToOffset(text, range.end);
        const normalizedRange = text.slice(start, reportedEnd) === oldName
            ? range
            : text.slice(start, start + oldName.length) === oldName
                ? {
                    start: range.start,
                    end: { line: range.start.line, character: range.start.character + oldName.length }
                }
                : null;
        if (!normalizedRange) return;
        const key = `${uri}:${normalizedRange.start.line}:${normalizedRange.start.character}`
            + `:${normalizedRange.end.line}:${normalizedRange.end.character}`;
        if (seen.has(key)) return;
        seen.add(key);
        (changes[uri] ||= []).push({ range: normalizedRange, newText: replacement });
    };
    for (const entry of workspaceEditEntries(edit)) {
        add(entry.uri, entry.range, newName);
    }
    for (const location of references || []) {
        add(location.uri, location.range, newName);
    }
    return { changes };
}

function countText(text, value) {
    let count = 0;
    let offset = 0;
    while ((offset = text.indexOf(value, offset)) >= 0) {
        count++;
        offset += value.length;
    }
    return count;
}

function countIdentifier(text, value) {
    const escaped = value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
    return (text.match(new RegExp(`(?<![\\w$])${escaped}(?![\\w$])`, 'g')) || []).length;
}

function rangeContainsPosition(range, position) {
    if (!range) {
        return false;
    }
    const startsBefore = range.start.line < position.line
        || (range.start.line === position.line && range.start.character <= position.character);
    const endsAfter = range.end.line > position.line
        || (range.end.line === position.line && range.end.character >= position.character);
    return startsBefore && endsAfter;
}

function workspaceEditContainsPosition(edit, uri, position) {
    if (!edit) {
        return false;
    }
    if ((edit.changes?.[uri] || []).some((entry) => rangeContainsPosition(entry.range, position))) {
        return true;
    }
    return (edit.documentChanges || []).some((change) => {
        const changeUri = change.textDocument?.uri || change.uri;
        return changeUri === uri
            && (change.edits || []).some((entry) => rangeContainsPosition(entry.range, position));
    });
}

function latestPublishedDiagnostics(uri) {
    for (let index = notifications.length - 1; index >= 0; index--) {
        const notification = notifications[index];
        if (notification.method === 'textDocument/publishDiagnostics'
                && notification.params?.uri === uri) {
            return notification.params.diagnostics || [];
        }
    }
    return [];
}

async function main() {
    const rootUri = pathToFileURL(workspace).href;
    let sourceUri = pathToFileURL(sourceFile).href;
    let text = fs.readFileSync(sourceFile, 'utf8');
    let optionalCompletionPosition;
    let indexedCompletionPosition;
    let indexedBinaryCompletionPosition;
    let indexedAcceptedCompletionPosition;
    let indexedRejectedCompletionPosition;
    let indexedDirectOnlyReceiverPosition;
    let indexedDirectOnlyImplicitPosition;
    let optionalTypeErrorPosition;
    let templateCompletionPosition;
    let templateExtensionCompletionPosition;
    let templateIncompleteLine;
    let templateExtensionLine;
    let templateErrorPosition;
    if (indexedCompletionOnly) {
        text = [
            'package consumer;',
            '',
            '// Deliberately no import probe.Extensions: global candidates must come from JDT indexes.',
            'class ZirconIndexedCompletionProbe {',
            '    String probe() {',
            '        String value = "zircon";',
            '        dependency.BinaryTypes.Accepted accepted = null;',
            '        dependency.BinaryTypes.Rejected rejected = null;',
            '        String sourceResult = value.sur();',
            '        String binaryResult = value.bin();',
            '        String acceptedResult = accepted.fil();',
            '        rejected.fil();',
            '        value.dir();',
            '        dir();',
            '        return sourceResult + binaryResult + acceptedResult;',
            '    }',
            '}',
            ''
        ].join('\n');
        sourceUri = pathToFileURL(path.join(
            workspace,
            'src',
            'consumer',
            'ZirconIndexedCompletionProbe.java'
        )).href;
        indexedCompletionPosition = offsetToPosition(text, text.indexOf('value.sur') + 'value.sur'.length);
        indexedBinaryCompletionPosition = offsetToPosition(text, text.indexOf('value.bin') + 'value.bin'.length);
        indexedAcceptedCompletionPosition = offsetToPosition(text, text.indexOf('accepted.fil') + 'accepted.fil'.length);
        indexedRejectedCompletionPosition = offsetToPosition(text, text.indexOf('rejected.fil') + 'rejected.fil'.length);
        indexedDirectOnlyReceiverPosition = offsetToPosition(text, text.indexOf('value.dir') + 'value.dir'.length);
        indexedDirectOnlyImplicitPosition = offsetToPosition(text, text.indexOf('        dir') + '        dir'.length);
    }
    if (optionalCompletionOnly) {
        const insertionOffset = text.lastIndexOf('}');
        const probeText = [
            '',
            '    static void zirconNativeCompletionProbe() {',
            '        String value = "";',
            '        value.sub();',
            '    }',
            ''
        ].join('\n');
        text = text.slice(0, insertionOffset) + probeText + text.slice(insertionOffset);
        const cursorOffset = insertionOffset + probeText.indexOf('value.sub') + 'value.sub'.length;
        optionalCompletionPosition = offsetToPosition(text, cursorOffset);
    }
    if (optionalTypeErrorOnly) {
        const zirconProjectFixture = fs.existsSync(zirconTestSource);
        text = [
            'package test;',
            '',
            zirconProjectFixture ? 'import zircon.example.ExObject;' : 'import zircon.ExMethod;',
            '',
            'class ZirconOptionalTypeErrorProbe<C> {',
            '    static class TestClass {',
            '        TestClass returnThis() { return this; }',
            '    }',
            '    static class TestChildClass extends TestClass {}',
            '    TestClass classNullVar;',
            '    static void check(Runnable action) {}',
            ...(zirconProjectFixture
                ? []
                : ['    @ExMethod static <T> T cast(Object value, Class<T> type) { return type.cast(value); }']),
            '    <M> M methodGeneric(M value, M fallback) { return value ?: fallback; }',
            '    class Inner<I> {',
            '        I innerGeneric(I value, I fallback) { return value ?: fallback; }',
            '        C outerGeneric(C value, C fallback) { return value ?: fallback; }',
            '    }',
            '',
            '    void probe() {',
            '        check(() -> {',
            '            String value = classNullVar?.returnThis().cast(TestChildClass.class) ?: new TestChildClass();',
            '        });',
            '    }',
            '}',
            ''
        ].join('\n');
        sourceUri = pathToFileURL(path.join(
            workspace,
            'src',
            ...(zirconProjectFixture ? ['test', 'java'] : []),
            'test',
            'ZirconOptionalTypeErrorProbe.java'
        )).href;
        const expressionOffset = text.indexOf('classNullVar?.');
        optionalTypeErrorPosition = offsetToPosition(text, expressionOffset);
    }
    if (templateOnly) {
        const zirconProjectFixture = fs.existsSync(zirconTestSource);
        text = [
            'package test;',
            '',
            'import probe.Extensions;',
            '',
            'class ZirconTemplateProbe {',
            '    String field = "field";',
            '    void probe() {',
            '        String local = "hello";',
            '        String incomplete = f"${local.}";',
            '        String valid = f"prefix ${local.substring(1)} ${field.substring(1)}";',
            '        String extension = f"${local.sur}";',
            '        String quotedBrace = f"${"}".substring(0)}";',
            '        String commentedBrace = f"${1 /* } */ + 1}";',
            '        String invalid = f"${missingTemplateValue}";',
            '    }',
            '}',
            ''
        ].join('\n');
        sourceUri = pathToFileURL(path.join(
            workspace,
            'src',
            ...(zirconProjectFixture ? ['test', 'java'] : []),
            'test',
            'ZirconTemplateProbe.java'
        )).href;
        templateCompletionPosition = offsetToPosition(text, text.indexOf('local.sub') + 'local.sub'.length);
        templateExtensionCompletionPosition = offsetToPosition(text, text.indexOf('local.sur') + 'local.sur'.length);
        templateIncompleteLine = offsetToPosition(text, text.indexOf('local.}')).line;
        templateExtensionLine = templateExtensionCompletionPosition.line;
        templateErrorPosition = offsetToPosition(text, text.indexOf('missingTemplateValue'));
    }
    const position = (optionalChainOnly || optionalTypeErrorOnly)
        ? findOptionalChainPosition(text)
        : templateOnly
        ? templateCompletionPosition
        : optionalCompletionOnly
        ? optionalCompletionPosition
        : indexedCompletionOnly
        ? indexedCompletionPosition
        : completionOnly
        ? findCompletionPosition(text, probeMethodName)
        : findInvocationPosition(text, probeMethodName);
    if (completionOnly) {
        const cursorOffset = positionToOffset(text, position);
        text = text.slice(0, cursorOffset)
            + text.slice(cursorOffset + Math.max(0, probeMethodName.length - 2));
    }
    console.log(`[probe:native-search] JDT LS: ${extensionRoot}`);
    console.log(`[probe:native-search] invocation: ${sourceFile}:${position.line + 1}:${position.character + 1}`);
    console.log(`[probe:native-search] agent log: ${agentLog}`);

    await request('initialize', {
        processId: process.pid,
        rootUri,
        workspaceFolders: [{ name: path.basename(workspace), uri: rootUri }],
        capabilities: {
            workspace: { configuration: true, workspaceFolders: true },
            window: { workDoneProgress: true },
            textDocument: {
                completion: {
                    dynamicRegistration: true,
                    completionItem: {
                        snippetSupport: true,
                        resolveSupport: { properties: ['additionalTextEdits'] }
                    }
                },
                references: { dynamicRegistration: true },
                rename: { dynamicRegistration: true, prepareSupport: true },
                callHierarchy: { dynamicRegistration: true },
                codeLens: { dynamicRegistration: true, resolveSupport: { properties: ['command'] } },
                definition: { dynamicRegistration: true, linkSupport: true },
                hover: { dynamicRegistration: true, contentFormat: ['markdown', 'plaintext'] },
                signatureHelp: { dynamicRegistration: true },
                codeAction: {
                    dynamicRegistration: true,
                    resolveSupport: { properties: ['edit'] },
                    codeActionLiteralSupport: {
                        codeActionKind: { valueSet: ['source.organizeImports'] }
                    }
                }
            }
        },
        initializationOptions: {
            bundles: [],
            workspaceFolders: [rootUri],
            settings: {
                'java.configuration.runtimes': [{ name: 'JavaSE-22', path: projectJdk, default: true }],
                'java.configuration.updateBuildConfiguration': 'automatic',
                'java.import.gradle.java.home': projectJdk,
                'java.autobuild.enabled': true,
                'java.referencesCodeLens.enabled': true
            }
        }
    }, 90_000);
    notify('initialized', {});
    notify('workspace/didChangeConfiguration', { settings: {} });

    await new Promise((resolve) => setTimeout(resolve, optionalTypeErrorOnly || templateOnly
        ? 20_000
        : fs.existsSync(zirconTestSource) ? 35_000 : 12_000));
    notify('textDocument/didOpen', {
        textDocument: { uri: sourceUri, languageId: 'java', version: 1, text }
    });
    const genericChainSource = fs.existsSync(zirconGenericChainSource) ? zirconGenericChainSource : sourceFile;
    const genericChainUri = diagnosticsOnly ? pathToFileURL(genericChainSource).href : null;
    if (genericChainUri && genericChainUri !== sourceUri) {
        notify('textDocument/didOpen', {
            textDocument: {
                uri: genericChainUri,
                languageId: 'java',
                version: 1,
                text: fs.readFileSync(genericChainSource, 'utf8')
            }
        });
    }
    if (declarationFile) {
        notify('textDocument/didOpen', {
            textDocument: {
                uri: pathToFileURL(declarationFile).href,
                languageId: 'java',
                version: 1,
                text: fs.readFileSync(declarationFile, 'utf8')
            }
        });
    }
    await new Promise((resolve) => setTimeout(resolve, optionalChainOnly ? 35_000 : 8_000));

    if (templateOnly) {
        await new Promise((resolve) => setTimeout(resolve, 8_000));
        const diagnostics = latestPublishedDiagnostics(sourceUri);
        const errors = diagnostics.filter((diagnostic) => diagnostic.severity === 1);
        const missingErrors = errors.filter((diagnostic) => diagnostic.range.start.line === templateErrorPosition.line
            && /missingTemplateValue/.test(diagnostic.message || ''));
        const unexpectedErrors = errors.filter((diagnostic) => !missingErrors.includes(diagnostic)
            && diagnostic.range.start.line !== templateIncompleteLine
            && diagnostic.range.start.line !== templateExtensionLine
            && !/declared package .* does not match the expected package/i.test(diagnostic.message || ''));
        const completionResult = await request('textDocument/completion', {
            textDocument: { uri: sourceUri },
            position,
            context: { triggerKind: 1 }
        }, 90_000);
        const items = Array.isArray(completionResult) ? completionResult : completionResult?.items || [];
        const labels = items.map((item) => typeof item.label === 'string' ? item.label : item.label?.label || '');
        const hasSubstring = labels.some((label) => String(label).startsWith('substring('));
        const extensionCompletionResult = await request('textDocument/completion', {
            textDocument: { uri: sourceUri },
            position: templateExtensionCompletionPosition,
            context: { triggerKind: 1 }
        }, 90_000);
        const extensionItems = Array.isArray(extensionCompletionResult)
            ? extensionCompletionResult
            : extensionCompletionResult?.items || [];
        const extensionLabels = extensionItems.map((item) => typeof item.label === 'string' ? item.label : item.label?.label || '');
        const hasSurround = extensionLabels.some((label) => String(label).startsWith('surround('));
        console.log(`[probe:native-search] templateErrors=${errors.length}, missingErrors=${missingErrors.length}, unexpectedErrors=${unexpectedErrors.length}, completionItems=${items.length}, extensionCompletionItems=${extensionItems.length}`);
        console.log(`[probe:native-search] labels=${labels.slice(0, 20).join(', ')}`);
        console.log(`[probe:native-search] extensionLabels=${extensionLabels.slice(0, 20).join(', ')}`);
        for (const diagnostic of errors.slice(0, 20)) {
            console.log(`[probe:native-search]   diagnostic ${diagnostic.range.start.line + 1}:${diagnostic.range.start.character + 1} ${diagnostic.message}`);
        }
        if (missingErrors.length !== 1 || unexpectedErrors.length !== 0 || !hasSubstring || !hasSurround) {
            throw new Error('Template expression diagnostics or native JDT completion did not pass.');
        }
        return;
    }

    if (optionalTypeErrorOnly) {
        await new Promise((resolve) => setTimeout(resolve, 8_000));
        const diagnostics = latestPublishedDiagnostics(sourceUri);
        const errors = diagnostics.filter((diagnostic) => diagnostic.severity === 1);
        const expectedErrors = errors.filter((diagnostic) => {
            const message = diagnostic.message || '';
            return diagnostic.range.start.line === optionalTypeErrorPosition.line
                && /cannot convert|incompatible types|type mismatch/i.test(message);
        });
        console.log(`[probe:native-search] optionalTypeErrors=${errors.length}, expectedTypeErrors=${expectedErrors.length}`);
        for (const diagnostic of errors.slice(0, 30)) {
            console.log(`[probe:native-search]   diagnostic ${diagnostic.range.start.line + 1}:${diagnostic.range.start.character + 1} ${diagnostic.message}`);
        }
        if (errors.length !== 2 || expectedErrors.length !== 2) {
            throw new Error('Expected only the two incompatible String assignment diagnostics; generic method/inner/outer scopes must stay valid.');
        }
        return;
    }

    if (optionalChainOnly) {
        const diagnostics = latestPublishedDiagnostics(sourceUri);
        const errors = diagnostics.filter((diagnostic) => diagnostic.severity === 1);
        console.log(`[probe:native-search] optionalChainErrors=${errors.length}`);
        for (const diagnostic of errors.slice(0, 30)) {
            console.log(`[probe:native-search]   diagnostic ${diagnostic.range.start.line + 1}:${diagnostic.range.start.character + 1} ${diagnostic.message}`);
        }
        if (errors.length > 0) {
            throw new Error('JDT reported errors for Zircon optional-chain or Elvis syntax.');
        }
        return;
    }

    if (completionOnly || indexedCompletionOnly || optionalCompletionOnly) {
        const completionStartedAt = Date.now();
        const completionAttempts = indexedCompletionOnly ? 12 : 1;
        const completion = await requestCompletionItems(
            sourceUri,
            position,
            indexedCompletionOnly ? probeMethodName : undefined,
            completionAttempts
        );
        const items = completion.items;
        const completionElapsedMs = Date.now() - completionStartedAt;
        if (optionalCompletionOnly) {
            const labels = items.map((item) => typeof item.label === 'string' ? item.label : item.label?.label || '');
            const hasSubstring = labels.some((label) => String(label).startsWith('substring('));
            console.log(`[probe:native-search] optionalCompletionItems=${items.length}, elapsedMs=${completionElapsedMs}`);
            console.log(`[probe:native-search] labels=${labels.slice(0, 20).join(', ')}`);
            if (!hasSubstring) {
                throw new Error('JDT did not return normal String member completion in the optional-chain document.');
            }
            if (completionElapsedMs > 5_000) {
                throw new Error(`JDT completion remained too slow (${completionElapsedMs} ms).`);
            }
            return;
        }
        const extensionItems = items.filter((item) => {
            const label = typeof item.label === 'string' ? item.label : item.label?.label || '';
            return String(label).replace(/\(.*$/, '') === probeMethodName;
        });
        console.log(`[probe:native-search] completions=${items.length}, ${probeMethodName}=${extensionItems.length}, attempts=${completion.attempts}, elapsedMs=${completionElapsedMs}`);
        for (const item of extensionItems.slice(0, 10)) {
            console.log(`[probe:native-search]   ${probeMethodName} detail=${item.detail || ''}`);
        }
        if (extensionItems.length === 0) {
            console.log(`[probe:native-search] labels=${items.map((item) => typeof item.label === 'string' ? item.label : item.label?.label).join(', ')}`);
            throw new Error(`JDT CompletionEngine did not return the Zircon ${probeMethodName} extension method.`);
        }
        if (indexedCompletionOnly && completionElapsedMs > 8_000) {
            throw new Error(`JDT indexed extension completion remained too slow (${completionElapsedMs} ms).`);
        }
        if (indexedCompletionOnly && completion.attempts > 2) {
            throw new Error(`JDT cold extension index required ${completion.attempts} completion requests.`);
        }
        if (indexedCompletionOnly) {
            const resolvedItem = await request('completionItem/resolve', extensionItems[0], 90_000);
            const importEdits = resolvedItem?.additionalTextEdits || [];
            console.log(`[probe:native-search] indexedCompletionImportEdits=${importEdits.length}`);
            console.log(`[probe:native-search] indexedCompletionEdits=${JSON.stringify(importEdits)}`);
            if (!importEdits.some((edit) => /import\s+probe\.Extensions\s*;/.test(edit.newText || ''))) {
                throw new Error('JDT indexed extension completion did not attach the declaring-class import.');
            }

            const binaryCompletion = await requestCompletionItems(
                sourceUri,
                indexedBinaryCompletionPosition,
                'binarySurround',
                4
            );
            const binaryItems = binaryCompletion.items.filter(
                (item) => completionLabel(item).replace(/\(.*$/, '') === 'binarySurround'
            );
            if (binaryItems.length === 0) {
                throw new Error('JDT binary extension index did not return binarySurround.');
            }
            const resolvedBinaryItem = await request('completionItem/resolve', binaryItems[0], 90_000);
            if (!(resolvedBinaryItem?.additionalTextEdits || []).some(
                (edit) => /import\s+dependency\.BinaryExtensions\s*;/.test(edit.newText || '')
            )) {
                throw new Error('JDT binary extension completion did not attach the declaring-class import.');
            }
            const acceptedCompletion = await requestCompletionItems(
                sourceUri,
                indexedAcceptedCompletionPosition,
                'filtered',
                4
            );
            const acceptedItems = acceptedCompletion.items.filter(
                (item) => completionLabel(item).replace(/\(.*$/, '') === 'filtered'
            );
            const rejectedCompletion = await requestCompletionItems(
                sourceUri,
                indexedRejectedCompletionPosition,
                undefined,
                1
            );
            const rejectedItems = rejectedCompletion.items.filter(
                (item) => completionLabel(item).replace(/\(.*$/, '') === 'filtered'
            );
            console.log(
                `[probe:native-search] binaryCompletion=${binaryItems.length}, `
                + `filterAllowed=${acceptedItems.length}, filterRejected=${rejectedItems.length}`
            );
            if (acceptedItems.length === 0 || rejectedItems.length !== 0) {
                throw new Error('JDT binary extension filterAnnotation matching did not respect receiver annotations.');
            }
            const receiverDirectOnly = await requestCompletionItems(
                sourceUri,
                indexedDirectOnlyReceiverPosition,
                undefined,
                1
            );
            const implicitDirectOnly = await requestCompletionItems(
                sourceUri,
                indexedDirectOnlyImplicitPosition,
                'directOnly',
                4
            );
            const receiverDirectItems = receiverDirectOnly.items.filter(
                (item) => completionLabel(item).replace(/\(.*$/, '') === 'directOnly'
            );
            const implicitDirectItems = implicitDirectOnly.items.filter(
                (item) => completionLabel(item).replace(/\(.*$/, '') === 'directOnly'
            );
            console.log(
                `[probe:native-search] directOnlyReceiver=${receiverDirectItems.length}, `
                + `directOnlyImplicit=${implicitDirectItems.length}`
            );
            if (receiverDirectItems.length !== 0 || implicitDirectItems.length === 0) {
                throw new Error('JDT completion did not respect ExMethodIDE.shouldInvokeDirectly.');
            }
        }
        if (!optionalCompletionOnly && probeMethodName === 'map' && fs.existsSync(zirconGenericChainSource)) {
            const variableText = [
                'package test;',
                '',
                'import zircon.example.ExCollection;',
                '',
                'class ZirconCompletionProbe {',
                '    static void probe() {',
                '        java.util.List<Integer> a = null;',
                '        a.ma();',
                '    }',
                '}',
                ''
            ].join('\n');
            const variableCursorOffset = variableText.indexOf('a.ma') + 'a.ma'.length;
            const variablePosition = offsetToPosition(variableText, variableCursorOffset);
            const variableUri = pathToFileURL(path.join(
                workspace,
                'src',
                'test',
                'java',
                'test',
                'ZirconCompletionProbe.java'
            )).href;
            notify('textDocument/didOpen', {
                textDocument: {
                    uri: variableUri,
                    languageId: 'java',
                    version: 1,
                    text: variableText
                }
            });
            await new Promise((resolve) => setTimeout(resolve, 4_000));
            const variableResult = await request('textDocument/completion', {
                textDocument: { uri: variableUri },
                position: variablePosition,
                context: { triggerKind: 1 }
            }, 90_000);
            const variableItems = Array.isArray(variableResult) ? variableResult : variableResult?.items || [];
            const variableMaps = variableItems.filter((item) => {
                const label = typeof item.label === 'string' ? item.label : item.label?.label || '';
                return String(label).replace(/\(.*$/, '') === 'map';
            });
            console.log(`[probe:native-search] variableReceiverCompletions=${variableItems.length}, map=${variableMaps.length}`);
            if (variableMaps.length === 0) {
                throw new Error('JDT CompletionEngine did not return map for a List<Integer> variable receiver.');
            }
        }
        return;
    }

    if (genericChainUri) {
        const diagnostics = latestPublishedDiagnostics(genericChainUri);
        const genericTargetErrors = diagnostics.filter((diagnostic) => {
            return String(diagnostic.code) === '553648781'
                || /target type of this expression must be a functional interface/i.test(diagnostic.message || '');
        });
        console.log(`[probe:native-search] genericChainTargetTypeErrors=${genericTargetErrors.length}`);
        for (const diagnostic of genericTargetErrors) {
            console.log(`[probe:native-search]   diagnostic ${diagnostic.range.start.line + 1}:${diagnostic.range.start.character + 1} ${diagnostic.message}`);
        }
        if (genericTargetErrors.length > 0) {
            throw new Error('JDT reported a functional target-type error for the chained generic extension invocation.');
        }
        if (diagnosticsOnly) {
            return;
        }
    }

    const documentPosition = { textDocument: { uri: sourceUri }, position };
    const renameName = `${probeMethodName}Probe`;
    const references = await request('textDocument/references', {
        ...documentPosition,
        context: { includeDeclaration: true }
    }, 90_000);
    const prepareRename = await request('textDocument/prepareRename', documentPosition, 60_000)
        .catch(() => null);
    const renameEdit = prepareRename
        ? await request('textDocument/rename', { ...documentPosition, newName: renameName }, 90_000)
            .catch(() => null)
        : null;
    const callItems = await request('textDocument/prepareCallHierarchy', documentPosition, 60_000)
        .catch(() => null);
    const incomingCalls = Array.isArray(callItems) && callItems.length > 0
        ? await request('callHierarchy/incomingCalls', { item: callItems[0] }, 90_000).catch(() => null)
        : null;
    const definitions = await request('textDocument/definition', documentPosition, 60_000).catch(() => null);
    const hover = await request('textDocument/hover', documentPosition, 60_000).catch(() => null);
    const signatureOffset = text.indexOf(`.${probeMethodName}(`) + `.${probeMethodName}(`.length + 1;
    const signatureHelp = signatureOffset > 0
        ? await request('textDocument/signatureHelp', {
            textDocument: { uri: sourceUri },
            position: offsetToPosition(text, signatureOffset),
            context: { triggerKind: 1 }
        }, 60_000).catch(() => null)
        : null;
    const enclosingMethodPosition = fs.existsSync(zirconTestSource)
        ? null
        : findMethodDeclarationPosition(text, 'extensionCall');
    const outgoingItems = enclosingMethodPosition
        ? await request('textDocument/prepareCallHierarchy', {
            textDocument: { uri: sourceUri },
            position: enclosingMethodPosition
        }, 60_000).catch(() => null)
        : null;
    const outgoingCalls = Array.isArray(outgoingItems) && outgoingItems.length > 0
        ? await request('callHierarchy/outgoingCalls', { item: outgoingItems[0] }, 90_000).catch(() => null)
        : null;
    const declarationLocation = (references || []).find((location) => location.uri !== sourceUri);
    const declarationPosition = declarationLocation
        ? { textDocument: { uri: declarationLocation.uri }, position: declarationLocation.range.start }
        : null;
    const declarationRenameName = `${probeMethodName}DeclarationProbe`;
    const declarationRename = declarationPosition
        ? await request('textDocument/rename', {
            ...declarationPosition,
            newName: declarationRenameName
        }, 90_000).catch(() => null)
        : null;
    let codeLenses = declarationFile
        ? await request('textDocument/codeLens', {
            textDocument: { uri: pathToFileURL(declarationFile).href }
        }, 90_000).catch(() => null)
        : null;
    if (Array.isArray(codeLenses)) {
        codeLenses = await Promise.all(codeLenses.map((lens) => {
            return lens.command ? lens : request('codeLens/resolve', lens, 90_000).catch(() => lens);
        }));
    }

    console.log(`[probe:native-search] references=${Array.isArray(references) ? references.length : 0}`);
    for (const location of (references || []).slice(0, 20)) {
        console.log(`[probe:native-search]   ${location.uri}:${location.range.start.line + 1}:${location.range.start.character + 1}`);
    }
    console.log(`[probe:native-search] prepareRename=${Boolean(prepareRename)}`);
    console.log(`[probe:native-search] renameEdits=${countWorkspaceEditChanges(renameEdit)}`);
    for (const entry of workspaceEditEntries(renameEdit)) {
        console.log(`[probe:native-search]   rename ${entry.uri}:${entry.range.start.line + 1}:${entry.range.start.character + 1}`
            + `-${entry.range.end.line + 1}:${entry.range.end.character + 1} -> ${JSON.stringify(entry.newText)}`);
    }
    console.log(`[probe:native-search] declarationRenameEdits=${countWorkspaceEditChanges(declarationRename)}`);
    for (const entry of workspaceEditEntries(declarationRename)) {
        console.log(`[probe:native-search]   declarationRename ${entry.uri}:${entry.range.start.line + 1}:${entry.range.start.character + 1}`
            + `-${entry.range.end.line + 1}:${entry.range.end.character + 1} -> ${JSON.stringify(entry.newText)}`);
    }
    console.log(`[probe:native-search] callHierarchyItems=${Array.isArray(callItems) ? callItems.length : 0}`);
    console.log(`[probe:native-search] incomingCalls=${Array.isArray(incomingCalls) ? incomingCalls.length : 0}`);
    console.log(`[probe:native-search] outgoingCalls=${Array.isArray(outgoingCalls) ? outgoingCalls.length : 0}`);
    for (const call of outgoingCalls || []) {
        console.log(`[probe:native-search]   outgoing ${call.to?.name || '<unnamed>'}`
            + ` ${call.to?.uri || ''}`);
    }
    console.log(`[probe:native-search] definitions=${Array.isArray(definitions) ? definitions.length : definitions ? 1 : 0}`);
    console.log(`[probe:native-search] hover=${Boolean(hover)}, signatures=${signatureHelp?.signatures?.length || 0}`);
    for (const call of incomingCalls || []) {
        for (const range of call.fromRanges || []) {
            console.log(`[probe:native-search]   incoming ${call.from.uri}:${range.start.line + 1}:${range.start.character + 1}`);
        }
    }
    console.log(`[probe:native-search] codeLenses=${Array.isArray(codeLenses) ? codeLenses.length : 0}`);
    for (const lens of codeLenses || []) {
        console.log(`[probe:native-search]   codeLens ${lens.command?.title || '<unresolved>'}`);
    }

    const hasExtensionReference = Array.isArray(references) && references.some((location) => {
        return location.uri === sourceUri && rangeContainsPosition(location.range, position);
    });
    if (!hasExtensionReference) {
        throw new Error('JDT native SearchEngine did not return the extension invocation itself.');
    }
    if (declarationFile) {
        const methodReferencePosition = offsetToPosition(
            text,
            text.indexOf(`::${probeMethodName}`) + 2
        );
        const templateReferenceOffset = text.indexOf(`.${probeMethodName}("<"`);
        const templateReferencePosition = offsetToPosition(text, templateReferenceOffset + 1);
        if (!(references || []).some((location) => {
            return location.uri === sourceUri && rangeContainsPosition(location.range, methodReferencePosition);
        })) {
            throw new Error('JDT native SearchEngine did not return the extension method reference.');
        }
        if (!(references || []).some((location) => {
            return location.uri === sourceUri && rangeContainsPosition(location.range, templateReferencePosition);
        })) {
            throw new Error('JDT native SearchEngine did not return the template-expression extension invocation.');
        }
    }
    if (!prepareRename || !workspaceEditContainsPosition(renameEdit, sourceUri, position)) {
        throw new Error('JDT native rename did not edit the extension invocation itself.');
    }
    if (declarationFile) {
        const declarationUri = pathToFileURL(declarationFile).href;
        const declarationText = fs.readFileSync(declarationFile, 'utf8');
        const textsByUri = new Map([[sourceUri, text], [declarationUri, declarationText]]);
        const safeRenameEdit = sanitizeRenameWithNativeReferences(
            renameEdit,
            references,
            probeMethodName,
            renameName,
            textsByUri
        );
        const safeDeclarationRename = sanitizeRenameWithNativeReferences(
            declarationRename,
            references,
            probeMethodName,
            declarationRenameName,
            textsByUri
        );
        for (const entry of workspaceEditEntries(safeRenameEdit)) {
            console.log(`[probe:native-search]   sanitizedRename ${entry.uri}`
                + `:${entry.range.start.line + 1}:${entry.range.start.character + 1}`
                + `-${entry.range.end.line + 1}:${entry.range.end.character + 1}`);
        }
        const renamedUsage = applyWorkspaceEditToText(safeRenameEdit, sourceUri, text);
        const renamedDeclaration = applyWorkspaceEditToText(safeRenameEdit, declarationUri, declarationText);
        if (countIdentifier(renamedUsage, renameName) !== 4
                || countIdentifier(renamedUsage, probeMethodName) !== 2
                || countText(renamedDeclaration, `${renameName}(`) !== 1) {
            throw new Error('JDT native rename did not rename call, static call, method reference and template call.');
        }
        const declarationStartedUsage = applyWorkspaceEditToText(safeDeclarationRename, sourceUri, text);
        const declarationStartedDeclaration = applyWorkspaceEditToText(
            safeDeclarationRename,
            declarationUri,
            declarationText
        );
        if (countIdentifier(declarationStartedUsage, declarationRenameName) !== 4
                || countIdentifier(declarationStartedUsage, probeMethodName) !== 2
                || countText(declarationStartedDeclaration, `${declarationRenameName}(`) !== 1) {
            throw new Error('Declaration-started JDT rename did not update all four semantic usages.');
        }
        console.log('[probe:native-search] sanitizedNativeRenameOccurrences=5 (declaration plus four usages)');
    }
    const hasExtensionIncomingCall = Array.isArray(incomingCalls) && incomingCalls.some((call) => {
        return call.from?.uri === sourceUri
            && (call.fromRanges || []).some((range) => rangeContainsPosition(range, position));
    });
    if (!hasExtensionIncomingCall) {
        throw new Error('JDT native incoming call hierarchy did not return the extension invocation itself.');
    }
    if (declarationFile && !(codeLenses || []).some((lens) => /4\s+references/i.test(lens.command?.title || ''))) {
        throw new Error('JDT native references CodeLens did not count call, static call, method reference and template call.');
    }
    if (declarationFile) {
        const definitionItems = Array.isArray(definitions) ? definitions : definitions ? [definitions] : [];
        if (!definitionItems.some((item) => (item.targetUri || item.uri) === pathToFileURL(declarationFile).href)) {
            throw new Error('JDT native definition did not resolve the extension declaration.');
        }
        if (!hover || !JSON.stringify(hover).includes(probeMethodName)) {
            throw new Error('JDT native hover did not describe the extension method.');
        }
        if (!(outgoingCalls || []).some((call) => call.to?.name?.startsWith(`${probeMethodName}(`))) {
            throw new Error('JDT native outgoing call hierarchy did not include the extension method.');
        }
    }
}

main().then(() => {
    console.log('[probe:native-search] passed');
    server.kill();
}).catch((error) => {
    console.error(`[probe:native-search] failed: ${error.stack || error}`);
    server.kill();
    process.exitCode = 1;
});
