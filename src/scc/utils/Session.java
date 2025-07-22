package scc.utils;

import java.io.Serializable;

public class Session implements Serializable {
    private static final long serialVersionUID = 1L;

    private String id;
    private String user;

    public Session(String id, String user) {
        this.id = id;
        this.user = user;
    }

    public String getId() {
        return id;
    }

    public String getUser() {
        return user;
    }
}
