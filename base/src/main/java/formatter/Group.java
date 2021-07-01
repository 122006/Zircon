package formatter;

import com.sun.tools.javac.parser.JavaTokenizer;
import com.sun.tools.javac.parser.Tokens;
import com.sun.tools.javac.util.Name;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;

public class Group {
    public int mappingStartIndex = -1;//  '$|f'"
    public int mappingEndIndex = -1;//    '"'+1
    public List<Item> items = new ArrayList<>();

    public Group( int mappingStartIndex) {
        this.mappingStartIndex = indexOf(mappingStartIndex, '"') - 1;
    }

    /**
     * 匹配结束点：'"'
     */
    public void searchEnd() {
        int find = mappingStartIndex + 1;
        while (true) {
            find++;
            if (find >= tokenizer.reader.buflen) tokenizer.throwError(mappingStartIndex, "未找到匹配结束点" );
            char ch = tokenizer.charAt(find);
            if (ch == '\\') {
                find++;
                continue;
            }
            if (ch == '"') {
                mappingEndIndex = find + 1;
                break;
            }
        }
    }

    public int indexOf(int startIndex, char ch) {
        while (startIndex < tokenizer.reader.buflen && (mappingEndIndex == -1 || startIndex < mappingEndIndex + 1)) {
            if (tokenizer.charAt(startIndex) == ch && tokenizer.charAt(startIndex - 1) != '\\') {
                return startIndex;
            }
            startIndex++;
        }
        return -1;
    }

    public void loadStringToken(int startIndex, int endIndex, String chars) {
        chars = chars.replaceAll(Matcher.quoteReplacement( "\\$" ), Matcher.quoteReplacement( "$" ));
        chars = tokenizer.toLitChar(startIndex, chars);
        com.sun.tools.javac.util.List<Tokens.Comment> var3 = null;
        Tokens.TokenKind tk = Tokens.TokenKind.STRINGLITERAL;
        Tokens.StringToken stringToken = new Tokens.StringToken(tk, startIndex, endIndex, chars, var3);
        items.add(new Item(startIndex, endIndex, stringToken));
    }

    public void loadCommaToken(Tokens.TokenKind tk, int startIndex, int endIndex) {
        com.sun.tools.javac.util.List<Tokens.Comment> var3 = null;
        Tokens.Token stringToken = new Tokens.Token(tk, startIndex, endIndex, var3);
        items.add(new Item(startIndex, endIndex, stringToken));
    }

    public void loadIdentifierToken(int startIndex, int endIndex, String identifier) {
        com.sun.tools.javac.util.List<Tokens.NamedToken> var3 = null;
        Name name = tokenizer.reader.names.fromString(identifier);
        Tokens.TokenKind tk = tokenizer.fac.tokens.lookupKind(name);
        Tokens.NamedToken stringToken = new Tokens.NamedToken(tk, startIndex, endIndex, name, null);
        items.add(new Item(startIndex, endIndex, stringToken));
    }

    public String output() {
        String str = "";
        for (int i = 0; i < items.size(); i++) {
            Item item = items.get(i);
            if (item.token == null
                    && tokenizer.subChars(item.mappingStartIndex, item.mappingEndIndex).trim().length() == 0)
                continue;
            if (item.token != null) {
                if (item.token instanceof Tokens.StringToken) {
                    str += ( "\"" + ((Tokens.StringToken) item.token).stringVal + "\"" );
                } else if (item.token instanceof Tokens.NamedToken) {
                    str += ((Tokens.NamedToken) item.token).name.toString();
                } else {
                    str += (item.token.kind.name);
                }
            } else {
                str += (tokenizer.subChars(item.mappingStartIndex, item.mappingEndIndex));
            }
        }
        return str;
    }
}
