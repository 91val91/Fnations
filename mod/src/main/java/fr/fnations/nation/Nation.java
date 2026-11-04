package fr.fnations.nation;

import fr.fnations.gui.ConfirmPanel;
import fr.fnations.gui.Icons;
import fr.fnations.gui.PagedPanel;
import fr.fnations.gui.Panel;
import fr.fnations.gui.Prompt;
import fr.fnations.server.FnServer;
import fr.fnations.server.Msg;
import fr.fnations.util.Txt;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;


import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Une nation : membres, territoire, trésor, diplomatie et recherche. */
public final class Nation {
    private final String id;
    private String name;
    private String tag;
    private String color = "aqua";
    private UUID leader;
    private final Map<UUID, Role> members = new LinkedHashMap<>();
    private double balance;
    private double taxPerHour;
    private final Set<String> claims = new HashSet<>();
    private final Set<String> allies = new HashSet<>();
    private final Set<String> allyRequests = new HashSet<>();
    private final Set<UUID> invites = new HashSet<>();
    private fr.fnations.util.Locs.Spot home;
    private boolean open;
    private final long createdAt;
    // Recherche & développement
    private final Set<String> researched = new HashSet<>();
    private String currentResearch;
    private long researchEnd;
    private int bonusClaims;
    private int wins, losses;
    private String description = "";
    private final java.util.List<String> tags = new java.util.ArrayList<>();

    public Nation(String id, String name, UUID leader, long createdAt) {
        this.id = id;
        this.name = name;
        this.tag = name.length() > 4 ? name.substring(0, 4).toUpperCase() : name.toUpperCase();
        this.leader = leader;
        this.createdAt = createdAt;
        members.put(leader, Role.LEADER);
    }

    public String id() { return id; }
    public String name() { return name; }
    public void setName(String n) { this.name = n; }
    public String tag() { return tag; }
    public void setTag(String t) { this.tag = t; }
    public String color() { return color; }
    public void setColor(String c) { this.color = c; }
    public String colored() { return "<" + color + ">" + name + "</" + color + ">"; }
    public UUID leader() { return leader; }
    public void setLeader(UUID u) {
        if (leader != null) members.put(leader, Role.OFFICER);
        leader = u;
        members.put(u, Role.LEADER);
    }
    public Map<UUID, Role> members() { return members; }
    public Role role(UUID u) { return members.get(u); }
    public boolean can(UUID u, Role min) { Role r = members.get(u); return r != null && r.atLeast(min); }
    public double balance() { return balance; }
    public void setBalance(double b) { this.balance = b; }
    public double taxPerHour() { return taxPerHour; }
    public void setTaxPerHour(double t) { this.taxPerHour = t; }
    public Set<String> claims() { return claims; }
    public Set<String> allies() { return allies; }
    public Set<String> allyRequests() { return allyRequests; }
    public Set<UUID> invites() { return invites; }
    public fr.fnations.util.Locs.Spot home() { return home; }
    public void setHome(fr.fnations.util.Locs.Spot l) { this.home = l; }
    public boolean open() { return open; }
    public void setOpen(boolean o) { this.open = o; }
    public long createdAt() { return createdAt; }
    public Set<String> researched() { return researched; }
    public String currentResearch() { return currentResearch; }
    public long researchEnd() { return researchEnd; }
    public void setResearch(String id, long end) { this.currentResearch = id; this.researchEnd = end; }
    public int bonusClaims() { return bonusClaims; }
    public void setBonusClaims(int b) { this.bonusClaims = b; }
    public int wins() { return wins; }
    public int losses() { return losses; }
    public void addWin() { wins++; }
    public void addLoss() { losses++; }
    public void setRecord(int w, int l) { wins = w; losses = l; }
    public String description() { return description; }
    public void setDescription(String d) { description = d == null ? "" : d; }
    public java.util.List<String> tags() { return tags; }
}
