package com.jobtrack.web.state;

import java.io.Serializable;

/** Filter state of the admin user list (kept in the session). state: "", "active" or "disabled". */
public class AdminUserFilter implements Serializable {

    private String q;
    private String state = "";

    public Boolean enabledFilter() {
        return "active".equals(state) ? Boolean.TRUE : "disabled".equals(state) ? Boolean.FALSE : null;
    }

    public String getQ() {
        return q;
    }

    public void setQ(String q) {
        this.q = q;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = "active".equals(state) || "disabled".equals(state) ? state : "";
    }
}
