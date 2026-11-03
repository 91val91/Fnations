package fr.fnations.war;

/** Une guerre entre deux nations. */
public final class War {
    public enum State { PREPARATION, ACTIVE }

    private final String id;
    private final String attacker, defender;
    private final long declaredAt;
    private final long activeAt;
    private int attackerScore, defenderScore;
    private String peaceProposedBy;
    private int attackerKills, defenderKills;

    public War(String id, String attacker, String defender, long declaredAt, long activeAt) {
        this.id = id;
        this.attacker = attacker;
        this.defender = defender;
        this.declaredAt = declaredAt;
        this.activeAt = activeAt;
    }

    public String id() { return id; }
    public String attacker() { return attacker; }
    public String defender() { return defender; }
    public long declaredAt() { return declaredAt; }
    public long activeAt() { return activeAt; }
    public State state() { return System.currentTimeMillis() >= activeAt ? State.ACTIVE : State.PREPARATION; }
    public boolean involves(String n) { return attacker.equals(n) || defender.equals(n); }
    public String other(String n) { return attacker.equals(n) ? defender : attacker; }
    public int score(String n) { return attacker.equals(n) ? attackerScore : defenderScore; }
    public void addScore(String n, int pts) { if (attacker.equals(n)) attackerScore += pts; else defenderScore += pts; }
    public void setScores(int a, int d) { attackerScore = a; defenderScore = d; }
    public int attackerScore() { return attackerScore; }
    public int defenderScore() { return defenderScore; }
    public int kills(String n) { return attacker.equals(n) ? attackerKills : defenderKills; }
    public void addKill(String n) { if (attacker.equals(n)) attackerKills++; else defenderKills++; }
    public void setKills(int a, int d) { attackerKills = a; defenderKills = d; }
    public String peaceProposedBy() { return peaceProposedBy; }
    public void setPeaceProposedBy(String n) { peaceProposedBy = n; }
}
