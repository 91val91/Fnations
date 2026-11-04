package fr.fnations.weapon;

/** Caractéristiques d'une arme (weapons.yml). */
public record GunDef(String item, String ammo, String type, double damage, int fireDelay, int magazine, int reloadTicks,
                     double range, double spread, double aimSpread, int pellets, double headshot, float explosion,
                     double speed, String sound, float soundPitch) {}
