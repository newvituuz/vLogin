package br.vituz.core.vlogin.common.network;

public final class NetworkMessage {
    public enum Action {
        LOGIN,
        LOGOUT,
        KICK,
        ACCOUNT_CHANGED,
        RELOAD
    }

    public final String origin;
    public final Action action;
    public final String player;
    public final String extra;

    public NetworkMessage(String origin, Action action, String player, String extra) {
        this.origin = origin;
        this.action = action;
        this.player = player == null ? "" : player;
        this.extra = extra == null ? "" : extra;
    }

    public String encode() {
        return origin + '|' + action.name() + '|' + player + '|' + extra;
    }

    public static NetworkMessage decode(String payload) {
        if (payload == null) {
            return null;
        }
        String[] parts = payload.split("\\|", 4);
        if (parts.length < 4) {
            return null;
        }
        for (Action action : Action.values()) {
            if (action.name().equals(parts[1])) {
                return new NetworkMessage(parts[0], action, parts[2], parts[3]);
            }
        }
        return null;
    }
}
