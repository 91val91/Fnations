package fr.fnations.nation;

public enum Role {
    LEADER("<gold>Chef d'État", 3), OFFICER("<yellow>Ministre", 2), MEMBER("<gray>Citoyen", 1);

    public final String display;
    public final int power;

    Role(String display, int power) {
        this.display = display;
        this.power = power;
    }

    public boolean atLeast(Role r) { return power >= r.power; }
}
