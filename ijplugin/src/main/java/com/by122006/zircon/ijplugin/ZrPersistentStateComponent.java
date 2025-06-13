package com.by122006.zircon.ijplugin;

import com.intellij.openapi.components.PersistentStateComponent;

/**
 * @ClassName: ZrPersistentStateComponent
 * @Author: 122006
 * @Date: 2025/5/27 14:56
 * @Description:
 */
public class ZrPersistentStateComponent implements PersistentStateComponent<ZrPersistentStateComponent.State> {
    public static class State {
        public boolean projectPluginEnabled = false;
    }

    private State myState = new State();

    @Override
    public State getState() {
        return myState;
    }

    @Override
    public void loadState(State state) {
        myState = state;
    }
}
