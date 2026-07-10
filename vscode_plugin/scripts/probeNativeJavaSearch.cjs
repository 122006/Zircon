const { spawn } = require('child_process');
const fs = require('fs');
const os = require('os');
const path = require('path');
const { pathToFileURL } = require('url');

const workspace = path.resolve(process.argv[2] || path.join(__dirname, '..', 'test-fixtures', 'native-search'));
const zirconTestSource = path.join(workspace, 'src', 'test', 'java', 'test', 'TestExMethodImpl.java');
const sourceFile = fs.existsSync(zirconTestSource)
    ? zirconTestSource
    : path.join(workspace, 'src', 'probe', 'Usage.java');
const declarationFile = fs.existsSync(zirconTestSource)
    ? null
    : path.join(workspace, 'src', 'probe', 'Extensions.java');
const probeMethodName = fs.existsSync(zirconTestSource) ? 'emptyStringRString' : 'surround';
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

const args = [
    '--add-modules=ALL-SYSTEM',
    '--add-opens', 'java.base/java.util=ALL-UNNAMED',
    '--add-opens', 'java.base/java.lang=ALL-UNNAMED',
    '--add-opens', 'java.base/sun.nio.fs=ALL-UNNAMED',
    '-Declipse.application=org.eclipse.jdt.ls.core.id1',
    '-Dosgi.bundles.defaultStartLevel=4',
    '-Declipse.product=org.eclipse.jdt.ls.core.product',
    '-Dfile.encoding=UTF-8',
    '-Dzircon.vscode=true',
    '-Dzircon.forceLocalSuppress=true',
    '-Dzircon.debug=true',
    '-Dzircon.trace=true',
    `-Dzircon.trace.selectors=${probeMethodName}`,
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
        result = { applied: false };
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
            entries.push({ uri, range: entry.range });
        }
    }
    for (const change of edit.documentChanges || []) {
        const uri = change.textDocument?.uri || change.uri;
        for (const entry of change.edits || []) {
            entries.push({ uri, range: entry.range });
        }
    }
    return entries;
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

async function main() {
    const rootUri = pathToFileURL(workspace).href;
    const sourceUri = pathToFileURL(sourceFile).href;
    const text = fs.readFileSync(sourceFile, 'utf8');
    const position = findInvocationPosition(text, probeMethodName);
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
                references: { dynamicRegistration: true },
                rename: { dynamicRegistration: true, prepareSupport: true },
                callHierarchy: { dynamicRegistration: true },
                codeLens: { dynamicRegistration: true, resolveSupport: { properties: ['command'] } }
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

    await new Promise((resolve) => setTimeout(resolve, fs.existsSync(zirconTestSource) ? 35_000 : 12_000));
    notify('textDocument/didOpen', {
        textDocument: { uri: sourceUri, languageId: 'java', version: 1, text }
    });
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
    await new Promise((resolve) => setTimeout(resolve, 8_000));

    const documentPosition = { textDocument: { uri: sourceUri }, position };
    const references = await request('textDocument/references', {
        ...documentPosition,
        context: { includeDeclaration: true }
    }, 90_000);
    const prepareRename = await request('textDocument/prepareRename', documentPosition, 60_000)
        .catch(() => null);
    const renameEdit = prepareRename
        ? await request('textDocument/rename', { ...documentPosition, newName: `${probeMethodName}Probe` }, 90_000)
            .catch(() => null)
        : null;
    const callItems = await request('textDocument/prepareCallHierarchy', documentPosition, 60_000)
        .catch(() => null);
    const incomingCalls = Array.isArray(callItems) && callItems.length > 0
        ? await request('callHierarchy/incomingCalls', { item: callItems[0] }, 90_000).catch(() => null)
        : null;
    const declarationLocation = (references || []).find((location) => location.uri !== sourceUri);
    const declarationPosition = declarationLocation
        ? { textDocument: { uri: declarationLocation.uri }, position: declarationLocation.range.start }
        : null;
    const declarationRename = declarationPosition
        ? await request('textDocument/rename', {
            ...declarationPosition,
            newName: `${probeMethodName}DeclarationProbe`
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
        console.log(`[probe:native-search]   rename ${entry.uri}:${entry.range.start.line + 1}:${entry.range.start.character + 1}`);
    }
    console.log(`[probe:native-search] declarationRenameEdits=${countWorkspaceEditChanges(declarationRename)}`);
    for (const entry of workspaceEditEntries(declarationRename)) {
        console.log(`[probe:native-search]   declarationRename ${entry.uri}:${entry.range.start.line + 1}:${entry.range.start.character + 1}`);
    }
    console.log(`[probe:native-search] callHierarchyItems=${Array.isArray(callItems) ? callItems.length : 0}`);
    console.log(`[probe:native-search] incomingCalls=${Array.isArray(incomingCalls) ? incomingCalls.length : 0}`);
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
    if (!prepareRename || !workspaceEditContainsPosition(renameEdit, sourceUri, position)) {
        throw new Error('JDT native rename did not edit the extension invocation itself.');
    }
    const hasExtensionIncomingCall = Array.isArray(incomingCalls) && incomingCalls.some((call) => {
        return call.from?.uri === sourceUri
            && (call.fromRanges || []).some((range) => rangeContainsPosition(range, position));
    });
    if (!hasExtensionIncomingCall) {
        throw new Error('JDT native incoming call hierarchy did not return the extension invocation itself.');
    }
    if (declarationFile && !(codeLenses || []).some((lens) => /2\s+references/i.test(lens.command?.title || ''))) {
        throw new Error('JDT native references CodeLens did not count both method calls.');
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
