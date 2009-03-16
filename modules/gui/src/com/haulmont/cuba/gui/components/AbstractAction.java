/*
 * Copyright (c) 2008 Haulmont Technology Ltd. All Rights Reserved.
 * Haulmont Technology proprietary and confidential.
 * Use is subject to license terms.

 * Author: Dmitry Abramov
 * Created: 06.02.2009 12:21:48
 * $Id$
 */
package com.haulmont.cuba.gui.components;

import com.haulmont.cuba.core.global.MessageProvider;

public abstract class AbstractAction implements Action {
    private String id;

    protected AbstractAction(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

    public String getCaption() {
        return MessageProvider.getMessage(getClass(), id);
    }

    public boolean isEnabled() {
        return true;
    }
}
