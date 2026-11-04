package fr.fnations.earth;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import fr.fnations.FNations;
import net.fabricmc.loader.api.FabricLoader;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Données de la Terre (fnations-earth/, produites par tools/earth_data.py) : altitude, couverture du sol et pays
 * sur une grille équirectangulaire de 4320 × 2160 (1 pixel = 9,28 km). Échelle du monde : 1 bloc = 1 km.
 */
public final class EarthData {
    public static final int W = 4320, H = 2160;
    /** Largeur et hauteur du monde en blocs (circonférence et méridien terrestres au 1/1000). */
    public static final double WORLD_W = 40075.0, WORLD_H = 20037.5;
    public static final double BLOCKS_PER_PX = WORLD_W / W;

    // classes de couverture du sol (voir tools/earth_data.py)
    public static final int WATER = 0, ICE = 1, TUNDRA = 2, FOREST = 3, MIXED = 4, GRASS = 5, STEPPE = 6, DESERT = 7, BARREN = 8, LAKE = 9;

    private static volatile EarthData instance;

    private final byte[] elev, cover, country;
    private final float[] lut = new float[256];
    private final String[] countryName, continent;

    private EarthData(byte[] elev, byte[] cover, byte[] country, String[] names, String[] cont) {
        this.elev = elev;
        this.cover = cover;
        this.country = country;
        this.countryName = names;
        this.continent = cont;
        for (int c = 0; c < 256; c++) {
            if (c <= 99) {
                double t = (99 - c) / 99.0;
                lut[c] = (float) (-t * t * 11000);
            } else {
                double t = (c - 100) / 155.0;
                lut[c] = (float) (t * t * 9000);
            }
        }
    }

    public static EarthData get() {
        EarthData d = instance;
        if (d == null) {
            synchronized (EarthData.class) {
                d = instance;
                if (d == null) instance = d = load();
            }
        }
        return d;
    }

    private static EarthData load() {
        long t0 = System.currentTimeMillis();
        try {
            byte[] e = gray("elevation.png"), c = gray("cover.png"), k = gray("countries.png");
            JsonArray meta = JsonParser.parseString(new String(read("countries.json"), StandardCharsets.UTF_8)).getAsJsonArray();
            String[] names = new String[meta.size()], cont = new String[meta.size()];
            for (int i = 0; i < meta.size(); i++) {
                JsonElement o = meta.get(i);
                names[i] = o.getAsJsonObject().get("n").getAsString();
                cont[i] = o.getAsJsonObject().get("c").getAsString();
            }
            FNations.LOG.info("Carte de la Terre chargée ({} ms)", System.currentTimeMillis() - t0);
            return new EarthData(e, c, k, names, cont);
        } catch (Exception ex) {
            throw new IllegalStateException("Données de la Terre introuvables (fnations-earth/)", ex);
        }
    }

    private static byte[] read(String name) throws Exception {
        Path p = FabricLoader.getInstance().getModContainer(FNations.MOD_ID).orElseThrow()
                .findPath("fnations-earth/" + name).orElseThrow(() -> new IllegalStateException("fnations-earth/" + name));
        return Files.readAllBytes(p);
    }

    private static byte[] gray(String name) throws Exception {
        try (InputStream in = new java.io.ByteArrayInputStream(read(name))) {
            BufferedImage img = ImageIO.read(in);
            if (img.getWidth() != W || img.getHeight() != H) throw new IllegalStateException(name + " : taille inattendue");
            if (img.getType() == BufferedImage.TYPE_BYTE_GRAY) return ((DataBufferByte) img.getRaster().getDataBuffer()).getData().clone();
            byte[] out = new byte[W * H];
            var raster = img.getRaster();
            for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) out[y * W + x] = (byte) raster.getSample(x, y, 0);
            return out;
        }
    }

    // ------------------------------------------------------------ géographie
    /** Longitude (°) d'une coordonnée X de bloc. */
    public static double lon(double x) { return x / WORLD_W * 360.0; }

    /** Latitude (°) d'une coordonnée Z de bloc (nord = Z négatif). */
    public static double lat(double z) { return -z / WORLD_H * 180.0; }

    public static double blockX(double lon) { return lon / 360.0 * WORLD_W; }

    public static double blockZ(double lat) { return -lat / 180.0 * WORLD_H; }

    /** Coordonnées de grille (pixel continu, centre des pixels en +0,5). */
    public static double px(double x) { return (x + WORLD_W / 2) / BLOCKS_PER_PX - 0.5; }

    public static double py(double z) { return (z + WORLD_H / 2) / BLOCKS_PER_PX - 0.5; }

    private static int wrapX(int x) { return Math.floorMod(x, W); }

    private static int clampY(int y) { return Math.max(0, Math.min(H - 1, y)); }

    public int elevCode(int x, int y) { return elev[clampY(y) * W + wrapX(x)] & 255; }

    /** Altitude en mètres au pixel (x, y) de la grille. */
    public float meters(int x, int y) { return lut[elevCode(x, y)]; }

    public int cover(int x, int y) { return cover[clampY(y) * W + wrapX(x)] & 255; }

    public int countryIndex(int x, int y) { return country[clampY(y) * W + wrapX(x)] & 255; }

    public String countryName(int idx) { return idx <= 0 || idx >= countryName.length ? "" : countryName[idx]; }

    public String continent(int idx) { return idx <= 0 || idx >= continent.length ? "" : continent[idx]; }

    /** Altitude en mètres, interpolation bicubique (Catmull-Rom) en coordonnées de grille continues. */
    public double elevation(double gx, double gy) {
        int ix = (int) Math.floor(gx), iy = (int) Math.floor(gy);
        double fx = gx - ix, fy = gy - iy;
        double[] col = new double[4];
        for (int j = -1; j <= 2; j++) {
            col[j + 1] = cubic(meters(ix - 1, iy + j), meters(ix, iy + j), meters(ix + 1, iy + j), meters(ix + 2, iy + j), fx);
        }
        return cubic(col[0], col[1], col[2], col[3], fy);
    }

    /** Interpolation bilinéaire (plus rapide, pour la carte). */
    public double elevationBilinear(double gx, double gy) {
        int ix = (int) Math.floor(gx), iy = (int) Math.floor(gy);
        double fx = gx - ix, fy = gy - iy;
        double a = meters(ix, iy), b = meters(ix + 1, iy), c = meters(ix, iy + 1), d = meters(ix + 1, iy + 1);
        return (a * (1 - fx) + b * fx) * (1 - fy) + (c * (1 - fx) + d * fx) * fy;
    }

    private static double cubic(double p0, double p1, double p2, double p3, double t) {
        return p1 + 0.5 * t * (p2 - p0 + t * (2 * p0 - 5 * p1 + 4 * p2 - p3 + t * (3 * (p1 - p2) + p3 - p0)));
    }

    /** Pays sous une position de bloc : [nom, continent] (chaînes vides en mer). */
    public String[] place(double x, double z) {
        int idx = countryIndex((int) Math.round(px(x)), (int) Math.round(py(z)));
        return new String[]{countryName(idx), continent(idx)};
    }

    /** Coordonnées lisibles : 48,86° N, 2,35° E. */
    public static String latLon(double x, double z) {
        double la = lat(z), lo = lon(x);
        return String.format(java.util.Locale.FRANCE, "%.2f° %s, %.2f° %s", Math.abs(la), la >= 0 ? "N" : "S", Math.abs(lo), lo >= 0 ? "E" : "O");
    }
}
