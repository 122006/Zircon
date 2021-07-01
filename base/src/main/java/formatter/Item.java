package formatter;

import com.sun.tools.javac.parser.Tokens;

public class Item {
    public int mappingStartIndex = -1;
    public int mappingEndIndex = -1;
    public Tokens.Token token;
    public boolean isParseOut = false;

    public Item(int mappingStartIndex, int mappingEndIndex, Tokens.Token token) {
        this.mappingStartIndex = mappingStartIndex;
        this.mappingEndIndex = mappingEndIndex;
        this.token = token;
    }
}
