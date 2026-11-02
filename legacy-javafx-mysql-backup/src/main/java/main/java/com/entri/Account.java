package main.java.com.entri;

public final class Account {
    private final long id;
    private final String code;
    private final String name;
    private final String type;

    public Account(long id, String code, String name, String type) {
        this.id = id;
        this.code = code;
        this.name = name;
        this.type = type;
    }

    public long getId() { return id; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public String getType() { return type; }
    @Override public String toString() { return code + " - " + name; }
}
