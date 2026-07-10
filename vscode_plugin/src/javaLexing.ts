export function countStructuralBraceDelta(fragment: string): number {
    let delta = 0;
    let inLineComment = false;
    let inBlockComment = false;
    let inStringLiteral = false;
    let inCharacterLiteral = false;
    let inTextBlock = false;
    let escaping = false;

    for (let index = 0; index < fragment.length; index++) {
        const char = fragment[index];
        const next = index + 1 < fragment.length ? fragment[index + 1] : '';
        const third = index + 2 < fragment.length ? fragment[index + 2] : '';

        if (inLineComment) {
            if (char === '\n' || char === '\r') {
                inLineComment = false;
            }
            continue;
        }

        if (inBlockComment) {
            if (char === '*' && next === '/') {
                inBlockComment = false;
                index++;
            }
            continue;
        }

        if (inTextBlock) {
            if (char === '"' && next === '"' && third === '"') {
                inTextBlock = false;
                index += 2;
            }
            continue;
        }

        if (inStringLiteral || inCharacterLiteral) {
            if (escaping) {
                escaping = false;
                continue;
            }
            if (char === '\\') {
                escaping = true;
                continue;
            }
            if (inStringLiteral && char === '"') {
                inStringLiteral = false;
            } else if (inCharacterLiteral && char === '\'') {
                inCharacterLiteral = false;
            }
            continue;
        }

        if (char === '/' && next === '/') {
            inLineComment = true;
            index++;
            continue;
        }

        if (char === '/' && next === '*') {
            inBlockComment = true;
            index++;
            continue;
        }

        if (char === '"' && next === '"' && third === '"') {
            inTextBlock = true;
            index += 2;
            continue;
        }

        if (char === '"') {
            inStringLiteral = true;
            escaping = false;
            continue;
        }

        if (char === '\'') {
            inCharacterLiteral = true;
            escaping = false;
            continue;
        }

        if (char === '{') {
            delta++;
        } else if (char === '}') {
            delta--;
        }
    }

    return delta;
}
