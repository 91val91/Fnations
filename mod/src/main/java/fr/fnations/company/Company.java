package fr.fnations.company;

import fr.fnations.job.Job;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Une entreprise : type d'activité, employés, capital, salaires et niveau. */
public final class Company {
    public enum Type {
        TRADING("Trading", "chart", null, "#3BA7F0",
                "Négoce sur l'hôtel des ventes : taxes réduites, ventes et boîte de réception au nom de l'entreprise."),
        INGENIEUR("Ingénierie", "cog", Job.INGENIEUR, "#9B6CF6",
                "Fabrication de matériel à la Station de Craft : XP et revenus d'Ingénieur boostés, ingrédients récupérés plus souvent."),
        FARMER("Agriculture", "wheat", Job.AGRICULTEUR, "#43D17A",
                "Exploitation agricole : XP et revenus d'Agriculteur boostés pour tous les employés."),
        BUILDER("Construction", "hammer", Job.BUILDER, "#F5A623",
                "BTP et construction : XP et revenus de Builder boostés pour tous les employés.");

        public final String display, icon, color, description;
        public final Job job;

        Type(String display, String icon, Job job, String color, String description) {
            this.display = display;
            this.icon = icon;
            this.job = job;
            this.color = color;
            this.description = description;
        }

        public static Type byName(String s) {
            for (Type t : values()) if (t.name().equalsIgnoreCase(s) || t.display.equalsIgnoreCase(s)) return t;
            return null;
        }
    }

    public enum Rank {
        PDG("PDG", 3), DIRECTEUR("Directeur", 2), EMPLOYE("Employé", 1);

        public final String display;
        public final int power;

        Rank(String display, int power) {
            this.display = display;
            this.power = power;
        }

        public boolean atLeast(Rank r) { return power >= r.power; }
    }

    private final String id;
    private String name;
    private final Type type;
    private UUID ceo;
    private final Map<UUID, Rank> members = new LinkedHashMap<>();
    private final Map<UUID, Double> salaries = new HashMap<>();
    private final Set<UUID> invites = new HashSet<>();
    private double balance, xp, earned;
    private final long createdAt;
    private String description = "";
    private int contribution = 10;

    public Company(String id, String name, Type type, UUID ceo, long createdAt) {
        this.id = id;
        this.name = name;
        this.type = type;
        this.ceo = ceo;
        this.createdAt = createdAt;
        members.put(ceo, Rank.PDG);
    }

    public String id() { return id; }
    public String name() { return name; }
    public void setName(String n) { name = n; }
    public Type type() { return type; }
    public UUID ceo() { return ceo; }
    public void setCeo(UUID u) {
        if (ceo != null) members.put(ceo, Rank.DIRECTEUR);
        ceo = u;
        members.put(u, Rank.PDG);
    }
    public Map<UUID, Rank> members() { return members; }
    public Rank rank(UUID u) { return members.get(u); }
    public boolean can(UUID u, Rank min) { Rank r = members.get(u); return r != null && r.atLeast(min); }
    public Map<UUID, Double> salaries() { return salaries; }
    public double salary(UUID u) { return salaries.getOrDefault(u, 0.0); }
    public Set<UUID> invites() { return invites; }
    public double balance() { return balance; }
    public void setBalance(double b) { balance = Math.round(b * 100) / 100.0; }
    public double xp() { return xp; }
    public void setXp(double v) { xp = v; }
    public double earned() { return earned; }
    public void setEarned(double v) { earned = v; }
    public long createdAt() { return createdAt; }
    public String description() { return description; }
    public void setDescription(String d) { description = d == null ? "" : d; }
    public int contribution() { return contribution; }
    public void setContribution(int c) { contribution = Math.max(0, Math.min(30, c)); }
    public String owner() { return "company:" + id; }
}
