package com.google.android.apps.common.testing.ui.espresso;

import java.util.Map;

public class WebElement {

    private String tagName;
    private Map<String, String> attributes;

    public String getTagName() {
        return tagName;
    }

    public Map<String, String> getAttributes() {
        return attributes;
    }

}
