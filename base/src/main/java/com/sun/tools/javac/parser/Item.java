package com.sun.tools.javac.parser;

import com.sun.tools.javac.parser.JavaTokenizer;
import com.sun.tools.javac.parser.Tokens;
import com.sun.tools.javac.util.Name;

import java.util.List;
import java.util.regex.Matcher;

public class Item {
    public int mappingStartIndex = -1;
    public int mappingEndIndex = -1;
    public Tokens.Token token;
    public boolean isParseOut = false;

    private Item(int mappingStartIndex, int mappingEndIndex, Tokens.Token token) {
        this.mappingStartIndex = mappingStartIndex;
        this.mappingEndIndex = mappingEndIndex;
        this.token = token;
    }
    public static Item loadStringToken(int startIndex, int endIndex, String chars) throws Exception{
        chars = chars.replaceAll(Matcher.quoteReplacement( "\\$" ), Matcher.quoteReplacement( "$" ));
        chars = toLitChar( chars);
        com.sun.tools.javac.util.List<Tokens.Comment> var3 = null;
        Tokens.TokenKind tk = Tokens.TokenKind.STRINGLITERAL;
        Tokens.StringToken stringToken = new Tokens.StringToken(tk, startIndex, endIndex, chars, var3);
        return new Item(startIndex, endIndex, stringToken);
    }

    public static Item loadCommaToken(Tokens.TokenKind tk, int startIndex, int endIndex)  throws Exception{
        com.sun.tools.javac.util.List<Tokens.Comment> var3 = null;
        Tokens.Token stringToken = new Tokens.Token(tk, startIndex, endIndex, var3);
        return new Item(startIndex, endIndex, stringToken);
    }

    public static Item loadIdentifierToken(JavaTokenizer tokenizer, int startIndex, int endIndex, String identifier)  throws Exception{
        com.sun.tools.javac.util.List<Tokens.NamedToken> var3 = null;
        Name name = tokenizer.reader.names.fromString(identifier);
        Tokens.TokenKind tk = tokenizer.fac.tokens.lookupKind(name);
        Tokens.NamedToken stringToken = new Tokens.NamedToken(tk, startIndex, endIndex, name, null);
        return new Item(startIndex, endIndex, stringToken);
    }
    public static Item loadJavacCode( int startIndex, int endIndex)  throws Exception{
        com.sun.tools.javac.util.List<Tokens.NamedToken> var3 = null;
        return new Item(startIndex, endIndex, null);
    }

    public static String output(char[] buf, List<Item> items) {
        StringBuilder str = new StringBuilder();
        for (int i = 0; i < items.size(); i++) {
            Item item = items.get(i);
            if (item.token == null
                    && item.mappingStartIndex== item.mappingEndIndex)
                continue;
            if (item.token != null) {
                if (item.token instanceof Tokens.StringToken) {
                    str.append( "\"" ).append(((Tokens.StringToken) item.token).stringVal).append( "\"" );
                } else if (item.token instanceof Tokens.NamedToken) {
                    str.append(((Tokens.NamedToken) item.token).name.toString());
                } else {
                    str.append(item.token.kind.name);
                }
            } else {
                int length=item.mappingEndIndex-item.mappingStartIndex;
                char[] chars = new char[length];
                System.arraycopy(buf, item.mappingStartIndex, chars, 0, length);
                str.append(new String(chars));
            }
        }
        return str.toString();
    }
    private static String toLitChar(String textChars) throws Exception {
        StringBuilder str = new StringBuilder();
        int index = -1;
        while (++index < textChars.length()) {
            if (textChars.charAt(index) == '\\') {
                index++;
                if (index == textChars.length()) {
                    throw new RuntimeException( "非法字符 in " + textChars);
                }
                if (textChars.charAt(index) == '\\') {
                    if (index + 1 != textChars.length() && textChars.charAt(index + 1) == '$') {
                        index++;
                        str.append('$');
                    } else
                        str.append('\\');
                } else {
                    switch (textChars.charAt(index)) {
                        case '"':
                            str.append('"');
                            break;
                        case '\'':
                            str.append('\'');
                            break;
                        case '0':
                        case '1':
                        case '2':
                        case '3':
                        case '4':
                        case '5':
                        case '6':
                        case '7':
                            int var3 = textChars.charAt(index) - '0';
                            if (index + 1 != textChars.length()) {
                                int v0 = textChars.charAt(index + 1) - '0';
                                if (0 <= v0 && v0 <= 7) {
                                    index++;
                                    var3 = var3 * 8 + v0;
                                    if (index + 1 != textChars.length()) {
                                        int v1 = textChars.charAt(index + 1) - '0';
                                        if (v0 < 3 && 1 <= v0 && v1 <= 7) {
                                            index++;
                                            var3 = var3 * 8 + v1;

                                        }

                                    }
                                }

                            }
                            str.append((char) var3);
                            break;
                        case 'b':
                            str.append('\b');
                            break;
                        case 'f':
                            str.append('\f');
                            break;
                        case 'n':
                            str.append('\n');
                            break;
                        case 'r':
                            str.append('\r');
                            break;
                        case 't':
                            str.append('\t');
                            break;
                        default:
                            str.append(textChars.charAt(index));
                    }
                }
            } else {
                str.append(textChars.charAt(index));
            }

        }
        return str.toString();


    }
}
