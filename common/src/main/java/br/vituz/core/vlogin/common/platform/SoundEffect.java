package br.vituz.core.vlogin.common.platform;

public enum SoundEffect {
    NOTIFY("ENTITY_EXPERIENCE_ORB_PICKUP", "ENTITY_EXPERIENCE_ORB_TOUCH", "ORB_PICKUP"),
    SUCCESS("ENTITY_PLAYER_LEVELUP", "LEVEL_UP"),
    FAILURE("ENTITY_VILLAGER_NO", "VILLAGER_NO"),
    WARNING("BLOCK_NOTE_BLOCK_PLING", "BLOCK_NOTE_PLING", "NOTE_PLING");

    private final String[] candidates;

    SoundEffect(String... candidates) {
        this.candidates = candidates;
    }

    public String[] candidates() {
        return candidates;
    }
}
