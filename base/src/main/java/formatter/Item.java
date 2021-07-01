package formatter;

import com.sun.tools.javac.parser.Tokens;

public class Item {
    public int mappingStartIndex = -1;
    public int mappingEndIndex = -1;
    public String token;
    public boolean isParseOut = false;

    public Item(int mappingStartIndex, int mappingEndIndex, String token) {
        this.mappingStartIndex = mappingStartIndex;
        this.mappingEndIndex = mappingEndIndex;
        this.token = token;
    }
}
