package org.dynmap.modscraper;

import cpw.mods.fml.client.registry.ISimpleBlockRenderingHandler;
import cpw.mods.fml.client.registry.RenderingRegistry;
import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.ModContainer;
import cpw.mods.fml.common.Mod.EventHandler;
import cpw.mods.fml.common.Mod.Instance;
import cpw.mods.fml.common.SidedProxy;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent; 
import cpw.mods.fml.common.network.NetworkMod;
import cpw.mods.fml.common.registry.GameRegistry;
import cpw.mods.fml.common.registry.GameRegistry.UniqueIdentifier;
import net.minecraftforge.common.ConfigCategory;
import net.minecraftforge.common.Configuration;
import net.minecraftforge.common.ForgeDirection;
import net.minecraftforge.common.Property;
import net.minecraft.block.Block;
import net.minecraft.block.BlockHalfSlab;
import net.minecraft.block.BlockLeavesBase;
import net.minecraft.block.BlockRailBase;
import net.minecraft.block.material.Material;
import net.minecraft.client.Minecraft;
import net.minecraft.crash.CrashReport;
import net.minecraft.item.Item;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.Icon;
import net.minecraft.util.ReportedException;
import net.minecraft.util.Vec3Pool;
import net.minecraft.world.ColorizerFoliage;
import net.minecraft.world.ColorizerGrass;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.biome.BiomeGenBase;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.logging.Logger;

@Mod(modid = "DynmapModScraper", name = "", version = Version.VER)
@NetworkMod(clientSideRequired = true, serverSideRequired = false)
public class DynmapModScraper
{    
    public static Logger log = Logger.getLogger("DynmapModScraper");
    
    public static final String MCVERSIONLIMIT = "1.6";
    
    public static final String RENDERER_MAPPING = "renderermapping";
    
    // The instance of your mod that Forge uses.
    @Instance("DynmapModScraper")
    public static DynmapModScraper instance;

    // Says where the client and server 'proxy' code is loaded.
    @SidedProxy(clientSide = "org.dynmap.modscraper.ClientProxy")
    public static Proxy proxy;

    public boolean good_init = false;
    
    private static HashMap<String, String> parentModIdByModId = new HashMap<String, String>();
    private static HashMap<String, String> modIdByLowerCase = new HashMap<String, String>();
    private static HashMap<String, String> fullModIdByLowerCase = new HashMap<String, String>();
    private static HashMap<String, String> cfgfileByMod = new HashMap<String, String>();
    private static HashMap<String, String[]> sectionsByMod = new HashMap<String, String[]>();
    private static HashMap<String, String> prefixByMod = new HashMap<String, String>();
    private static HashMap<String, String> suffixByMod = new HashMap<String, String>();
    private static HashMap<String, String> biomeSectionsByMod = new HashMap<String, String>();
    private static HashMap<String, String> itemSectionsByMod = new HashMap<String, String>();
    private static HashMap<String, RendererType> renderTypeByClass = new HashMap<String, RendererType>();
    
    public static void crash(Exception x, String msg) {
        CrashReport crashreport = CrashReport.makeCrashReport(x, msg);
        throw new ReportedException(crashreport);
    }
    public static void crash(String msg) {
        crash(new Exception(), msg);
    }
    
    public enum RendererType {
        STANDARD(0),
        CROSSEDSQUARES(1),
        TORCH(2),
        FIRE(3),
        FLUIDS(4),
        REDSTONEWIRE(5),
        CROPS(6),
        DOOR(7),
        LADDER(8),
        MINECARTTRACK(9),
        STAIRS(10),
        FENCE(11),
        LEVER(12),
        CACTUS(13),
        BED(14),
        REPEATER(15),
        PISTONBASE(16),
        PISTONEXTENSION(17),
        PANE(18),
        STEM(19),
        VINE(20),
        FENCEGATE(21),
        LILYPAD(23),
        CAULDRON(24),
        BREWINGSTAND(25),
        ENDPORTALFRAME(26),
        DRAGONEGG(27),
        COCOA(28),
        TRIPWIRESOURCE(29),
        TRIPWIRE(30),
        LOG(31),
        WALL(32),
        FLOWERPOT(33),
        BEACON(34),
        ANVIL(35),
        REDSTONELOGIC(36),
        COMPARATOR(37),
        HOPPER(38),
        QUARTZ(39),
        CUSTOM(-1);
        
        final int renderid;
        RendererType(int rid) {
            renderid = rid;
        }
        public static RendererType byID(int id) {
            for (RendererType rt : RendererType.values()) {
                if (rt.renderid == id)
                    return rt;
            }
            return CUSTOM;
        }
    };
    
    enum Transparency {
        OPAQUE,
        TRANSPARENT,
        SEMITRANSPARENT;
    };
    
    private static class TextureRecord {
        Transparency transparency = Transparency.OPAQUE;
        int id;
        int meta;
        boolean stdrot = true;
        String[] sides = null;
        String[] patches = null;
        String comment = null;
        int colormult = 0;
        
        TextureRecord(int id, int meta, Block b) {
            this.id = id;
            this.meta = meta;
        }
        public void addSide(int idx, int txtidx, String txt) {
            if (sides == null) {
                sides = new String[6];
            }
            if (idx >= sides.length) {
                sides = Arrays.copyOf(sides, idx+1);
            }
            sides[idx] = txtidx + ":" + txt;
        }
        public void addPatch(int idx, int txtidx, String txt) {
            if (patches == null) {
                patches = new String[6];
            }
            if (idx >= patches.length) {
                patches = Arrays.copyOf(patches, idx+1);
            }
            patches[idx] = txtidx + ":" + txt;
        }
        public void setTransparency(Transparency t) {
            transparency = t;
        }
        public void setStdRot(boolean v) {
            stdrot = v;
        }
        public String getLine() {
            String s = "";
            StringRunAccum accum = new StringRunAccum();
            if (stdrot) {
                s += ",stdrot=true";
            }
            if (transparency != Transparency.OPAQUE) {
                s += ",transparency=" + transparency;
            }
            if (colormult != 0) {
                s += ",colorMult=" + String.format("%06X", colormult);
            }
            if (sides != null) {
                // Build run accumulated values
                for (int side = 0; side < sides.length; side++) {
                    String siderec = sides[side];
                    if (siderec != null) {
                        accum.addString(side, siderec);
                    }
                }
                // ANd add them to string
                for (StringRun sr : accum.lst) {
                    s += ",face" + sr.start;
                    if (sr.count > 1) {
                        s += "-" + (sr.start + sr.count-1);
                    }
                    s += "=" + sr.str;
                }
                accum.lst.clear();
            }
            if (patches != null) {
                // Build run accumulated values
                for (int patch = 0; patch < patches.length; patch++) {
                    String patchrec = patches[patch];
                    if (patchrec != null) {
                        accum.addString(patch, patchrec);
                    }
                }
                // ANd add them to string
                for (StringRun sr : accum.lst) {
                    s += ",patch" + sr.start;
                    if (sr.count > 1) {
                        s += "-" + (sr.start + sr.count-1);
                    }
                    s += "=" + sr.str;
                }
                accum.lst.clear();
            }
            return s;
        }
        public void setComment(String c) {
            comment = c;
        }
    }
    
    public static class ModelRecord {
        String partialline = null;
        int id;
        int meta;
        String comment = null;
        String type;
        
        ModelRecord(int id, int meta, Block b) {
            this.id = id;
            this.meta = meta;   
        }
        public void setLine(String type, String... args) {
            this.type = type;
            this.partialline = "";
            for (String arg : args) {
                if (arg != null) {
                    partialline += "," + arg;
                }
            }
        }
        public void setComment(String c) {
            comment = c;
        }
        public String getLine(IDMapping idm) {
            String idstr = getBlockID(id, idm);
            return type + ":id=" + idstr + partialline;  // We do meta last
        }
    }
    
    private static class PipeRecord {
        int itemid; // ID of pipe
        String iconid;  // Icon ID for pipe
    }
    
    public static class StringRun {
        String str;
        int start;
        int count;
    }
    public static class StringRunAccum {
        ArrayList<StringRun> lst = new ArrayList<StringRun>();
        
        public void addString(int idx, String s) {
            for (StringRun sr : lst) {
                // If we're next index after end of this one, and match, append to it
                if (((sr.start + sr.count) == idx) && (sr.str.equals(s))) {
                    sr.count++;
                    return;
                }
                // If just before start of run, and matching
                else if (((sr.start - 1) == idx) && (sr.str.equals(s))) {
                    sr.start = idx;
                    sr.count++;
                    return;
                }
            }
            // If no match, add new record
            StringRun nsr = new StringRun();
            nsr.str = s;
            nsr.start = idx;
            nsr.count = 1;
            lst.add(nsr);
        }
    }
    
    private String normalizeModID(String id) {
        String id2 = parentModIdByModId.get(id);
        if (id2 != null) id = id2;
        id = id.replace('|', '_');
        return id;
    }

    private Map<String, Map<String, Integer>> uniqueBlockMap = new HashMap<String, Map<String, Integer>>();
    private Map<String, Map<String, Integer>> uniqueItemMap = new HashMap<String, Map<String, Integer>>();
    
    private void initializeBlockUniqueIDMap() {
        uniqueBlockMap.clear();
        for (int i = 0; i < Block.blocksList.length; i++) {
            Block b = Block.blocksList[i];
            if (b == null) continue;
            UniqueIdentifier ui = GameRegistry.findUniqueIdentifierFor(b);
            if (ui != null) {
                String modid = normalizeModID(ui.modId);
                Map<String, Integer> mm = uniqueBlockMap.get(modid);
                if (mm == null) {
                    mm = new HashMap<String, Integer>();
                    uniqueBlockMap.put(modid, mm);
                }
                mm.put(ui.name, i);
            }
        }
    }

    private void initializeItemUniqueIDMap() {
        uniqueItemMap.clear();
        for (int i = 0; i < Item.itemsList.length; i++) {
            Item itm = Item.itemsList[i];
            if (itm == null) continue;
            UniqueIdentifier ui = GameRegistry.findUniqueIdentifierFor(itm);
            if (ui != null) {
                String modid = normalizeModID(ui.modId);
                Map<String, Integer> mm = uniqueItemMap.get(modid);
                if (mm == null) {
                    mm = new HashMap<String, Integer>();
                    uniqueItemMap.put(modid, mm);
                }
                mm.put(ui.name, i);
            }
        }
    }
    
    private Configuration modcfg;
    
    @EventHandler
    public void preInit(FMLPreInitializationEvent event)
    {
        File cfgf = event.getSuggestedConfigurationFile();
        if (cfgf.exists() == false) {
            log.info("Initialize configuration file : " + cfgf.getPath());
            InputStream is = this.getClass().getResourceAsStream("/DynmapModScraper.cfg");
            FileOutputStream fos = null;
            if (is != null) {
                try {
                    fos = new FileOutputStream(cfgf);
                    byte[] buf = new byte[2048];
                    int len;
                    while ((len = is.read(buf)) > 0) {
                        fos.write(buf, 0, len);
                    }
                } catch (IOException iox) {
                    log.info("Error initializing config file - " + cfgf.getPath());
                } finally {
                    if (is != null) {
                        try { is.close(); } catch (IOException x) {}
                    }
                    if (fos != null) {
                        try { fos.close(); } catch (IOException x) {}
                    }
                }
            }
        }
        renderTypeByClass.clear();
        
        // Load configuration file - use suggested (config/DynmapModScraper.cfg)
        modcfg = new Configuration(cfgf);
        try
        {
            modcfg.load();
            good_init = true;
            Map<String, ModContainer> modmap = Loader.instance().getIndexedModList();
            Set<String> mods = modmap.keySet();
            Set<String> donemods = new HashSet<String>();
            for (String mod : mods) {
                String origmod = mod;
                String parent = origmod;
                ModContainer mc = Loader.instance().getIndexedModList().get(mod);
                while (!mc.getMetadata().parent.equals("")) {
                    parent = mc.getMetadata().parent;
                    mc = modmap.get(parent);
                }
                String fullmod = mc.getModId();
                parentModIdByModId.put(origmod, parent);
                mod = normalizeModID(fullmod);

                if (donemods.contains(mod)) continue;
                donemods.add(mod);
                
                modIdByLowerCase.put(mod.toLowerCase(), mod); // Add lower case to allow case restore
                fullModIdByLowerCase.put(mod.toLowerCase(), fullmod);   // Save full version (for modname:)
                
                String cfgfile = "config/" + mod + ".cfg";
                File f = new File(cfgfile);
                if (f.exists() == false) {
                    cfgfile = "";
                }
                cfgfile = modcfg.get("ConfigFiles", mod, cfgfile).getString();
                if ((cfgfile != null) && (cfgfile.length() > 0)) {
                    cfgfileByMod.put(mod, cfgfile);
                }
                String sections[] = { Configuration.CATEGORY_BLOCK };
                sections = modcfg.get("BlockSections", mod, sections).getStringList();
                sectionsByMod.put(mod, sections);
                
                String prefix = modcfg.get("IDPrefix", mod, "").getString();
                if (prefix.length() > 0) {
                    prefixByMod.put(mod, prefix);
                }
                String suffix = modcfg.get("IDSuffix", mod, "").getString();
                if (suffix.length() > 0) {
                    suffixByMod.put(mod, suffix);
                }
                String biomes = modcfg.get("BiomeSection", mod, "").getString();
                if (biomes.length() > 0) {
                    biomeSectionsByMod.put(mod, biomes);
                }
                String items = modcfg.get("ItemSection", mod, "item").getString();
                if (items.length() > 0) {
                    itemSectionsByMod.put(mod, items);
                }
            }
            // Get renderer mappings
            ConfigCategory rendmap = modcfg.getCategory(RENDERER_MAPPING);
            if (rendmap != null) {
                for (String k : rendmap.keySet()) {
                    Property p = rendmap.get(k);
                    String tgt = p.getString();
                    if ((tgt != null) && (RendererType.valueOf(tgt) != null)) {
                        renderTypeByClass.put(k, RendererType.valueOf(tgt));
                    }
                }
            }
        }
        catch (Exception e)
        {
            crash(e, "DynmapModScraper couldn't load its configuration");
        }
        finally
        {
            modcfg.save();
        }
    }
    private static final String ILLEGAL_CHARACTERS = "/\n\r\t\0\f`?*\\<>|\":";
    
    private String fixFileName(String modname) {
        String s = "";
        for (int i = 0; i < modname.length(); i++) {
            char c = modname.charAt(i);
            if (ILLEGAL_CHARACTERS.indexOf(c) < 0) {
                s += c;
            }
        }
        return s;
    }
    
    private String getModConfigFile(String mod, int idx) {
        String file = cfgfileByMod.get(mod);
        if (file == null) {
            return null;
        }
        String[] files = file.split(",");
        if (idx >= files.length) {
            return null;
        }
        File f = new File(files[idx]);
        if (f.exists() == false) {
            return null;
        }
        return file;
    }
    
    private static class IDMapping {
        Map<Integer, String> map = new HashMap<Integer, String>();
        HashSet<String> used = new HashSet<String>();
        boolean missedID;
        Map<Integer, String> biomemap = new HashMap<Integer, String>();
        Map<Integer, String> itemmap = new HashMap<Integer, String>();
    }

    private void loadConfigAsProperties(File f, IDMapping idm) {
        BufferedReader fis = null;
        try {
            fis = new BufferedReader(new FileReader(f));
            String line;
            while ((line = fis.readLine()) != null) {
                line = line.trim();
                if (line.startsWith("#")) continue;
                String[] tok = line.split("=");
                if (tok.length < 2) {
                    tok = line.split(":");
                }
                if (tok.length == 2) {
                    try {
                        int ival = (int) Double.parseDouble(tok[1].trim());  // Some odd mods use floats for IDs (Minegicka)
                        String k = tok[0].trim().replace(' ', '_');
                        idm.map.put(ival, k);
                    } catch (NumberFormatException nfx) {
                    }
                }
            }
        } catch (IOException x) {
            return;
        } finally {
            if (fis != null) {
                try { fis.close(); } catch (IOException iox) {}
                fis = null;
            }
        }
    }
    
    private IDMapping getBlockIDMappingForMod(String mod) {
        IDMapping idm = new IDMapping();
        // Get block unique ID map
        Map<String, Integer> blkmap = uniqueBlockMap.get(mod);
        if (blkmap != null) {
            for (String k : blkmap.keySet()) {
                idm.map.put(blkmap.get(k), "%" + k);
            }
        }
        // Get item unique ID map
        Map<String, Integer> itmmap = uniqueItemMap.get(mod);
        if (itmmap != null) {
            for (String k : itmmap.keySet()) {
                idm.itemmap.put(itmmap.get(k), "&" + k);
            }
        }
        
        boolean done = false;
        for (int idx = 0; !done; idx++) {
            String f = getModConfigFile(mod, idx);
            if (f == null) {
                done = true;
            }
            else {
                processFile(mod, idm, f);
            }
        }
        return idm;
    }
    
    private void processFile(String mod, IDMapping idm, String filename) {
        Configuration cfg;
        File f = new File(Minecraft.getMinecraft().mcDataDir, filename);
        try {
            cfg = new Configuration(f);
            cfg.load(); // Load it
        } catch (RuntimeException x) {
            // See if property parse works
            loadConfigAsProperties(f, idm);
            return;
        }
        String prefix = prefixByMod.get(mod);
        String suffix = suffixByMod.get(mod);
        String[] sections = sectionsByMod.get(mod);
        if (sections == null) {
            return;
        }
        ArrayList<ConfigCategory> seclist = new ArrayList<ConfigCategory>();
        for (String s : sections) {
            ConfigCategory blocks = cfg.getCategory(s);
            if (blocks != null) {
                seclist.add(blocks);
            }
        }
        for (int i = 0; i < seclist.size(); i++) {
            ConfigCategory blocks = seclist.get(i);
            for (ConfigCategory child : blocks.getChildren()) {
                if (child != null) {
                    seclist.add(child);
                }
            }
            if (blocks.isEmpty()) {
                continue;
            }
            String section = blocks.getQualifiedName().replace('.', '/');
            for (String k : blocks.keySet()) {
                if ((prefix != null) && (!k.startsWith(prefix))) {
                    continue;
                }
                if ((suffix != null) && (!k.endsWith(suffix))) {
                    continue;
                }
                Property p = blocks.get(k);
                if ((p != null) && p.isIntValue()) {
                    int v = p.getInt();
                    if ((v >= 1) && (v < 4096)) {
                        k = section + '/' + k;
                        if (k.startsWith("block/") || section.startsWith("blocks/")) {
                            k = k.substring(k.indexOf('/') + 1);
                        }
                        else if (k.startsWith("item/")) {
                            k = "item_" + k.substring(5);
                        }
                        k = k.replace(' ', '_');
                        idm.map.put(v, k);
                    }
                }
            }
        }
        seclist.clear();
        String biomesect = biomeSectionsByMod.get(mod);
        if (biomesect != null) {
            ConfigCategory bio = cfg.getCategory(biomesect);
            if (bio != null) {
                seclist.add(bio);
            }
        }
        for (int i = 0; i < seclist.size(); i++) {
            ConfigCategory biomes = seclist.get(i);
            for (ConfigCategory child : biomes.getChildren()) {
                if (child != null) {
                    seclist.add(child);
                }
            }
            if (biomes.isEmpty()) {
                continue;
            }
            biomesect = biomes.getQualifiedName().replace('.', '/');
            for (String k : biomes.keySet()) {
                Property p = biomes.get(k);
                if ((p != null) && p.isIntValue()) {
                    int v = p.getInt();
                    if ((v >= 1) && (v < 256)) {
                        k = biomesect + '/' + k;
                        if (k.startsWith("block/") || biomesect.startsWith("blocks/")) {
                            k = k.substring(k.indexOf('/') + 1);
                        }
                        else if (k.startsWith("item/")) {
                            k = "item_" + k.substring(5);
                        }
                        k = k.replace(' ', '_');
                        idm.biomemap.put(v, k);
                    }
                }
            }
        }
        String itemsect = itemSectionsByMod.get(mod);
        if (itemsect != null) {
            ConfigCategory items = cfg.getCategory(itemsect);
            itemsect = itemsect.replace('.', '/');
            if (items != null) {
                for (String k : items.keySet()) {
                    Property p = items.get(k);
                    if ((p != null) && p.isIntValue()) {
                        int v = p.getInt();
                        if ((v >= 1) && (v < 32768)) {
                            k = itemsect + '/' + k;
                            if (k.startsWith("block/") || itemsect.startsWith("blocks/")) {
                                k = k.substring(k.indexOf('/') + 1);
                            }
                            else if (k.startsWith("item/")) {
                                k = "item_" + k.substring(5);
                            }
                            k = k.replace(' ', '_');
                            idm.itemmap.put(v, k);
                        }
                    }
                }
            }
        }
    }
    
    private static String getBlockID(int id, IDMapping idmap) {
        String s = idmap.map.get(id);
        if (s == null) {
            s = Integer.toString(id);
            idmap.missedID = true;
        }
        else {
            idmap.used.add(s);
        }
        return s;
    }

    private static String getItemID(int id, IDMapping idmap) {
        id = id - 256;  // Standard offset from item ID versus setting
        String s = idmap.itemmap.get(id);
        if (s == null) {
            s = Integer.toString(id);
            idmap.missedID = true;
        }
        else {
            idmap.used.add(s);
        }
        return s;
    }

    private void prepBlock(Block b, int meta) {
    }
    
    private void restoreBlock(Block b, int meta) {
    }
    
    private int getColorModifier(Block b, int meta) {
        int rc = 0xFFFFFF;
        try {
            rc= b.getRenderColor(meta) & 0xFFFFFF;    // Chec render color multiplier
        } catch (Exception x) { // Some folks don't handle all meta properly
        }
        if (rc == 0xFFFFFF) {  // None
            return 0;
        }
        if (rc == ColorizerFoliage.getFoliageColorPine()) {    // Pine color
            return 13000;   // COLORMOD_PINETONED
        }
        if (rc == ColorizerFoliage.getFoliageColorBirch()) {    // Birch color
            return 14000;   // COLORMOD_BIRCHTONED
        }
        if (rc == ColorizerFoliage.getFoliageColorBasic()) {    // Basic foliage
            return 2000;    // COLORMOD_FOLIAGETONED
        }
        if (rc == ColorizerGrass.getGrassColor(0.5, 1.0)) {    // Basic grass
            return 1000;    // COLORMOD_GRASSTONED
        }
        if (rc == 2129968) {    // Lily pad
            return 15000;   // COLORMOD_LILYTONED
        }
        return 17000;   // COLORMOD_MULTTONED
    }
    
    private static class FakeBlockAccess implements IBlockAccess {
        public static final int FIXEDX = 0;
        public static final int FIXEDY = 64;
        public static final int FIXEDZ = 0;
        public static final int Y_AT = 1;
        public static final int Y_BELOW = 0;
        public static final int Y_ABOVE = 2;
        public int id[] = new int[3];    // 0=below, 1=at, 2=above
        public int data[] = new int[3];    // 0=below, 1=at, 2=above
        public Material mat[] = { Material.air, Material.air, Material.air }; // 0=below, 1=at, 2=above
        public BiomeGenBase biome = BiomeGenBase.forest;
        
        public int getBlockId(int x, int y, int z) {
            if ((x != FIXEDX) || (z != FIXEDZ) || (y < (FIXEDY-1)) || (y > FIXEDY+1)) {
                return 0;
            }
            return id[y - FIXEDY + 1];
        }

        public TileEntity getBlockTileEntity(int x, int y, int z) {
            return null;
        }

        public int getLightBrightnessForSkyBlocks(int x, int y, int z, int l) {
            return 0;
        }

        public int getBlockMetadata(int x, int y, int z) {
            if ((x != FIXEDX) || (z != FIXEDZ) || (y < (FIXEDY-1)) || (y > FIXEDY+1)) {
                return 0;
            }
            return data[y - FIXEDY + 1];
        }

        public float getBrightness(int x, int y, int z, int l) {
            return 0;
        }

        public float getLightBrightness(int x, int y, int z) {
            return 0;
        }

        public Material getBlockMaterial(int x, int y, int z) {
            if ((x != FIXEDX) || (z != FIXEDZ) || (y < (FIXEDY-1)) || (y > FIXEDY+1)) {
                return Material.air;
            }
            return mat[y - FIXEDY + 1];
        }
        
        public boolean isBlockOpaqueCube(int x, int y, int z) {
            if ((x != FIXEDX) || (z != FIXEDZ) || (y < (FIXEDY-1)) || (y > FIXEDY+1)) {
                return false;
            }
            return Block.blocksList[id[y - FIXEDY + 1]].isOpaqueCube();
        }

        public boolean isBlockNormalCube(int x, int y, int z) {
            if ((x != FIXEDX) || (z != FIXEDZ) || (y < (FIXEDY-1)) || (y > FIXEDY+1)) {
                return false;
            }
            return Block.isNormalCube(id[y - FIXEDY + 1]);
        }

        public boolean isAirBlock(int x, int y, int z) {
            if ((x != FIXEDX) || (z != FIXEDZ) || (y < (FIXEDY-1)) || (y > FIXEDY+1)) {
                return true;
            }
            return id[y - FIXEDY + 1] == 0;
        }

        public BiomeGenBase getBiomeGenForCoords(int x, int z) {
            return biome;
        }

        public int getHeight() {
            return 256;
        }

        public boolean extendedLevelsInChunkCache() {
            return true;
        }

        public boolean doesBlockHaveSolidTopSurface(int x, int y, int z) {
            return isBlockSolidOnSide(x, y, z, ForgeDirection.UP, false);
        }

        public Vec3Pool getWorldVec3Pool() {
            return null;
        }

        public int isBlockProvidingPowerTo(int i, int j, int k, int l) {
            return 0;
        }

        public boolean isBlockSolidOnSide(int x, int y, int z,
                ForgeDirection side, boolean _default) {
            if ((x != FIXEDX) || (z != FIXEDZ) || (y < (FIXEDY-1)) || (y > FIXEDY+1)) {
                return false;
            }
            Block block = Block.blocksList[getBlockId(x, y, z)];
            if(block == null) {
                return false;
            }
            return block.isBlockSolid(this, x, y, z, side.ordinal());
        }
        
    }

    private static class ModuleCtx {
        String recmod;
        String bname;
        
        void reset() {
            recmod = null;
            bname = null;
        }
    }
    // Texture IDs by name
    private HashMap<String,String> textures = new HashMap<String,String>();
    // IDs by mod
    private HashMap<String,HashSet<String>> texIDByMod = new HashMap<String,HashSet<String>>();
    // Texture records by mod
    private HashMap<String, ArrayList<TextureRecord>> txtRecsByMod = new HashMap<String, ArrayList<TextureRecord>>();
    // Model records by mod
    private HashMap<String, ArrayList<ModelRecord>> modRecsByMod = new HashMap<String, ArrayList<ModelRecord>>();
    // Pipe records by mod
    private HashMap<String, ArrayList<PipeRecord>> pipeRecsByMod = new HashMap<String, ArrayList<PipeRecord>>();

    // Register icon, and update module ID
    private String handleIcon(Icon ico, ModuleCtx ctx) {
        String iconame = ico.getIconName();
        String[] split = iconame.split(":");
        String mod, txt;
        if (split.length == 1) {
            int idx = iconame.indexOf('/'); // Some mods do this: minecraft base textures are flat
            if (idx < 0) {
                mod = "minecraft";
                txt = split[0];
            }
            else {
                mod = normalizeModID(iconame.substring(0, idx));
                txt = iconame.substring(idx+1);
                if (ctx.recmod == null) ctx.recmod = modIdByLowerCase.get(mod.toLowerCase());
            }
        }
        else {
            mod = normalizeModID(split[0]);
            txt = split[1];
            if (ctx.recmod == null) ctx.recmod = modIdByLowerCase.get(mod.toLowerCase());
        }
        String id = mod + "/" + txt;
        textures.put(id, "assets/" + mod.toLowerCase() + "/textures/blocks/" + txt + ".png");
        
        return id;
    }
    
    private HashMap<Integer, String> rendererclasses = new HashMap<Integer, String>();
    
    @SuppressWarnings("unchecked")
    private void getRendererClasses() {
        rendererclasses.clear();
        @SuppressWarnings("deprecation")
        RenderingRegistry rr = RenderingRegistry.instance();
        Field blockRenderer = null;
        Map<Integer, ISimpleBlockRenderingHandler> blockRenderers = null;
        try {
            blockRenderer = RenderingRegistry.class.getDeclaredField("blockRenderers");
            blockRenderer.setAccessible(true);
            blockRenderers = (Map<Integer, ISimpleBlockRenderingHandler>) blockRenderer.get(rr);
        } catch (Exception e) {
            log.warning("Error while trying to identify renderers - " + e.getMessage());
        }
        if (blockRenderers == null) {
            log.warning("blockRenderers = null");
            return;
        }
        boolean did_add = false;
        for (Integer id : blockRenderers.keySet()) {
            String n = blockRenderers.get(id).getClass().getName();
            if (rendererclasses.containsKey(id) == false) { // Not existing one?
                rendererclasses.put(id, n);
                modcfg.get(RENDERER_MAPPING, n, RendererType.CUSTOM.name());
                did_add = true;
            }
        }
        if (did_add) {
            modcfg.save();
        }
    }
    public String getRendererByID(int id) {
        String n = rendererclasses.get(id);
        if (n == null) n = "";
        return n;
    }
    
    private double clampRange(double v, double min, double max) {
        if (v < min) v = min;
        if (v > max) v = max;
        return v;
    }
    
    @EventHandler
    public void serverStarted(FMLServerStartingEvent event)
    {
        log.info("DynmapMapScraper active");

        // Reset (in case we're reentering SSP server
        textures.clear();
        texIDByMod.clear();
        txtRecsByMod.clear();
        modRecsByMod.clear();
        pipeRecsByMod.clear();
        // Get renderer classes
        getRendererClasses();
        // Get block unique IDs
        initializeBlockUniqueIDMap();
        // Get item unique IDs
        initializeItemUniqueIDMap();
        
        if (!good_init) {
            crash("preInit failed - aborting load()");
            return;
        }
        boolean savedGraphicsLevel = Block.leaves.graphicsLevel;
        Block.leaves.setGraphicsLevel(true);
        
        HashMap<Integer, String> blkComments = new HashMap<Integer, String>();
        File datadir = new File("DynmapModScraper");
        datadir.mkdirs();
        // Process items
        processItems();
        
        ModuleCtx ctx = new ModuleCtx();
        // Process blocks
        for (int id = 0; id < 4096; id++) {
            Block b = Block.blocksList[id];
            if (b == null) continue;
            ctx.reset();
            int rid = b.getRenderType();
            ctx.bname = b.getUnlocalizedName();
            RendererType rt = RendererType.byID(rid);
            String rclass = getRendererByID(rid);
            // Check if mapping for render class
            RendererType mappedrt = renderTypeByClass.get(rclass);
            if (mappedrt != null) {
                rt = mappedrt;
            }
            
            UniqueIdentifier ui = GameRegistry.findUniqueIdentifierFor(b);
            if ((ui != null) && (ui.modId != null) && (!ui.modId.equals("null"))) {
                ctx.recmod = normalizeModID(ui.modId);
                ctx.bname = ui.name;
            }
            String blockline = "Block: id=" + id + ", class=" + b.getClass().getName() + ", renderer=" + rclass + "(" + rt + "), isOpaqueCube=" + b.isOpaqueCube() + ", name=" + b.getLocalizedName() + "(" + ctx.bname + ")\n";

            for (int meta = 0; meta < 16; meta++) {
                // Build fake block access context for this block
                FakeBlockAccess fba = new FakeBlockAccess();
                fba.id[FakeBlockAccess.Y_AT] = id;
                fba.data[FakeBlockAccess.Y_AT] = meta;
                fba.mat[FakeBlockAccess.Y_AT] = b.blockMaterial;
                // Set block bounds based on state 
                try {
                    b.setBlockBoundsBasedOnState(fba, FakeBlockAccess.FIXEDX, FakeBlockAccess.FIXEDY, FakeBlockAccess.FIXEDZ);
                } catch (Exception x) {
                    // Some blocks don't handle this well - live without it
                }
                // Prep block for scan
                prepBlock(b, meta);
                
                // Get bounds for the block
                double x0  = b.getBlockBoundsMinX();
                double y0  = b.getBlockBoundsMinY();
                double z0  = b.getBlockBoundsMinZ();
                double x1  = b.getBlockBoundsMaxX();
                double y1  = b.getBlockBoundsMaxY();
                double z1  = b.getBlockBoundsMaxZ();

                boolean isFull = true;
                boolean badBox = false;
                if ((x0 != 0.0) || (y0 != 0.0) || (z0 != 0.0) || (x1 != 1.0) || (y1 != 1.0) || (z1 != 1.0)) {
                    isFull = false;
                    if (x0 != clampRange(x0, 0.0, 1.0)) { badBox = true; x0 = clampRange(x0, 0.0, 1.0); }
                    if (y0 != clampRange(y0, 0.0, 1.0)) { badBox = true; y0 = clampRange(y0, 0.0, 1.0); }
                    if (z0 != clampRange(z0, 0.0, 1.0)) { badBox = true; z0 = clampRange(z0, 0.0, 1.0); }
                    if (x1 != clampRange(x1, x0+0.0001, 1.0)) { badBox = true; x1 = clampRange(x1, x0+0.0001, 1.0); }
                    if (y1 != clampRange(y1, y0+0.0001, 1.0)) { badBox = true; y1 = clampRange(y1, y0+0.0001, 1.0); }
                    if (z1 != clampRange(z1, z0+0.0001, 1.0)) { badBox = true; z1 = clampRange(z1, z0+0.0001, 1.0); }
                }

                String sides[] = new String[6];
                boolean hit = false;
                for (int side = 0; side < 6; side++) {
                    Icon ico = null;
                    try {
                        if (rt == RendererType.DOOR) {    // Door is special : need to hack world data to make it look like stacked blocks
                            fba.id[FakeBlockAccess.Y_AT] = id;
                            fba.data[FakeBlockAccess.Y_AT] = meta;
                            fba.mat[FakeBlockAccess.Y_AT] = b.blockMaterial;
                            if (side == 0) {    // Force to top
                                fba.data[FakeBlockAccess.Y_AT] = 8;
                                fba.id[FakeBlockAccess.Y_BELOW] = id;
                                fba.data[FakeBlockAccess.Y_BELOW] = 0;
                                fba.mat[FakeBlockAccess.Y_BELOW] = b.blockMaterial;
                            }
                            else if (side == 1) { // Force to bottom
                                fba.data[FakeBlockAccess.Y_AT] = 0;
                                fba.id[FakeBlockAccess.Y_ABOVE] = id;
                                fba.data[FakeBlockAccess.Y_ABOVE] = 8;
                                fba.mat[FakeBlockAccess.Y_ABOVE] = b.blockMaterial;
                            }
                            if (side < 2) {
                                ico = b.getBlockTexture(fba, FakeBlockAccess.FIXEDX, FakeBlockAccess.FIXEDY, FakeBlockAccess.FIXEDZ, 2);
                            }
                            else {
                                ico = null;
                            }
                        }
                        else {
                            ico = b.getIcon(side, meta);
                        }
                    } catch (Exception x) {
                        // Some mods don't like undefined sides to be requuested
                    }
                    if (ico != null) {
                        hit = true;
                        sides[side] = handleIcon(ico, ctx);
                    }
                }
                if (hit && (ctx.recmod != null)) {
                    TextureRecord trec = new TextureRecord(id, meta, b);
                    ModelRecord mrec = null;
                    boolean addallsides = false;
                    HashSet<String> txtref = texIDByMod.get(ctx.recmod);
                    if (txtref == null) {
                        txtref = new HashSet<String>();
                        texIDByMod.put(ctx.recmod, txtref);
                    }
                    int cmult = getColorModifier(b, meta);  // Get color multiplier
                    if (cmult == 17000) {
                        trec.colormult = b.getRenderColor(meta);
                    }
                    int[] sideidx = { cmult, cmult, cmult, cmult, cmult, cmult };  // Default side indexes
                    switch (rt) {
                        case STANDARD:
                        case CUSTOM:    // Assume standard for custom (best guess)
                            if (!b.isOpaqueCube()) { // Not simple cube                                    trec.setTransparency(Transparency.TRANSPARENT); // Transparent
                                /* Model record for cuboid */
                                mrec = new ModelRecord(id, meta, b);
                                if (b instanceof BlockHalfSlab) {   // Slab block?
                                    if (meta < 8) { // Bottpm?
                                        mrec.setLine("boxblock", "ymax=0.5");
                                    }
                                    else {  // Top?
                                        mrec.setLine("boxblock", "ymin=0.5");
                                    }
                                    trec.setTransparency(Transparency.SEMITRANSPARENT);
                                }
                                else {
                                    if (!isFull) {
                                        trec.setTransparency(Transparency.TRANSPARENT);
                                        if (badBox) {
                                            mrec.setComment("FIXME: Box constraints truncated to 0.0<=val<=1.0");
                                        }
                                        mrec.setLine("boxblock", "xmin=" + x0, "xmax=" + x1, "ymin=" + y0, "ymax=" + y1, "zmin=" + z0, "zmax=" + z1);
                                    }
                                    else {  // Else, full block
                                        mrec = null;
                                        //- assume transparent texture behavior if not render pass 0
                                        if ((b.getRenderBlockPass() > 0) && (cmult == 0)) {
                                            sideidx = new int[] { 12000, 12000, 12000, 12000, 12000, 12000 };
                                        }
                                    }
                                }
                            }
                            else {
                                if (b instanceof BlockLeavesBase) {
                                }
                                //- assume transparent texture behavior if not render pass 0
                                else if ((b.getRenderBlockPass() > 0) && (cmult == 0)) {
                                    sideidx = new int[] { 12000, 12000, 12000, 12000, 12000, 12000 };
                                }
                                // Add placeholder model reocrd
                                if (rt == RendererType.CUSTOM) {
                                    mrec = new ModelRecord(id, meta, b);
                                }
                            }
                            addallsides = true; // And add all standard sides
                            break;
                        case FLUIDS:
                            trec.setTransparency(Transparency.SEMITRANSPARENT);
                            sideidx = new int[] { 12000, 12000, 12000, 12000, 12000, 12000 };
                            /* Model record for cuboid */
                            mrec = new ModelRecord(id, meta, b);
                            switch (meta) {
                                case 0:
                                case 8:
                                    mrec = null;
                                    break;
                                case 1:
                                case 9:
                                    mrec.setLine("boxblock", "ymax=0.875");
                                    break;
                                case 2:
                                case 10:
                                    mrec.setLine("boxblock", "ymax=0.75");
                                    break;
                                case 3:
                                case 11:
                                    mrec.setLine("boxblock", "ymax=0.625");
                                    break;
                                case 4:
                                case 12:
                                    mrec.setLine("boxblock", "ymax=0.5");
                                    break;
                                case 5:
                                case 13:
                                    mrec.setLine("boxblock", "ymax=0.375");
                                    break;
                                case 6:
                                case 14:
                                    mrec.setLine("boxblock", "ymax=0.25");
                                    break;
                                case 7:
                                case 15:
                                    mrec.setLine("boxblock", "ymax=0.125");
                                    break;
                            }
                            addallsides = true; // And add all standard sides
                            break;
                        case STAIRS:
                            trec.setTransparency(Transparency.SEMITRANSPARENT);
                            // Model record for stairs
                            mrec = new ModelRecord(id, meta, b);
                            mrec.setLine("customblock", "class=org.dynmap.hdmap.renderer.StairBlockRenderer");
                            addallsides = true; // And add all standard sides
                            break;
                        case CROSSEDSQUARES:
                            trec.setTransparency(Transparency.TRANSPARENT);
                            // Model record for crossed patches
                            mrec = new ModelRecord(id, meta, b);
                            mrec.setLine("patchblock", "patch0=VertX1Z0ToX0Z1#0","patch1=VertX1Z0ToX0Z1@90#0");
                            // Add first patch
                            trec.addPatch(0, cmult, sides[0]);
                            txtref.add(sides[0]);
                            break;
                        case LOG:
                            switch (meta >> 2) {    // Based on orientation
                                case 1:
                                    sideidx = new int[] { 0, 0, 4000, 4000, 0, 0 };
                                    trec.setStdRot(false);
                                    break;
                                case 2:
                                    sideidx = new int[] { 4000, 4000, 0, 0, 4000, 4000 };
                                    trec.setStdRot(false);
                                    break;
                            }
                            addallsides = true; // And add all standard sides
                            break;
                        case QUARTZ:
                            switch (meta) {    // Based on orientation
                                case 3:
                                    sideidx = new int[] { 4000, 4000, 0, 0, 6000, 6000 };
                                    break;
                                case 4:
                                    sideidx = new int[] { 0, 0, 6000, 6000, 0, 0 };
                                    break;
                            }
                            addallsides = true; // And add all standard sides
                            break;
                        case CACTUS:
                            trec.setTransparency(Transparency.SEMITRANSPARENT);
                            mrec = new ModelRecord(id, meta, b);
                            mrec.setLine("patchblock", "patch0=VertX00625","patch1=VertX00625@90","patch2=VertX00625@180","patch3=VertX00625@270","patch4=HorizY100ZTop","patch5=HorizY100ZTop@180/0/0");
                            // Add patches
                            trec.addPatch(0, cmult, sides[2]);
                            trec.addPatch(1, cmult, sides[4]);
                            trec.addPatch(2, cmult, sides[3]);
                            trec.addPatch(3, cmult, sides[5]);
                            trec.addPatch(4, cmult, sides[1]);
                            trec.addPatch(5, cmult, sides[0]);
                            txtref.add(sides[2]);
                            txtref.add(sides[4]);
                            txtref.add(sides[3]);
                            txtref.add(sides[5]);
                            txtref.add(sides[1]);
                            txtref.add(sides[0]);
                            break;
                        case CROPS:
                            trec.setTransparency(Transparency.TRANSPARENT);
                            // Model record for crop patches
                            mrec = new ModelRecord(id, meta, b);
                            mrec.setLine("patchblock", "patch0=VertX075#0","patch1=VertX075@90#0","patch2=VertX025#0","patch3=VertX025@90#0");
                            // Add first patch
                            trec.addPatch(0, cmult, sides[0]);
                            txtref.add(sides[0]);
                            break;
                        case LILYPAD:
                            trec.setTransparency(Transparency.TRANSPARENT);
                            // Model record for pad patch
                            mrec = new ModelRecord(id, meta, b);
                            mrec.setLine("patchblock", "patch0=HorizY001ZTop");
                            trec.addPatch(0, cmult, sides[0]);
                            txtref.add(sides[0]);
                            break;
                        case MINECARTTRACK:
                            trec.setTransparency(Transparency.TRANSPARENT);
                            // Model record for rail patches
                            mrec = new ModelRecord(id, meta, b);
                            int mask = 0xF;
                            if (((BlockRailBase)b).isPowered()) {
                                mask = 0x7;
                            }
                            switch (meta & mask) {
                                case 0:
                                    mrec.setLine("patchblock", "patch0=HorizY001ZTop");
                                    break;
                                case 1:
                                    mrec.setLine("patchblock", "patch0=HorizY001ZTop@90");
                                    break;
                                case 2:
                                case 10:
                                    mrec.setLine("patchblock", "patch0=SlopeXUpZTop");
                                    break;
                                case 3:
                                case 11:
                                    mrec.setLine("patchblock", "patch0=SlopeXUpZTop@180");
                                    break;
                                case 4:
                                case 12:
                                    mrec.setLine("patchblock", "patch0=SlopeXUpZTop@270");
                                    break;
                                case 5:
                                case 13:
                                    mrec.setLine("patchblock", "patch0=SlopeXUpZTop@90");
                                    break;
                                case 6:
                                    mrec.setLine("patchblock", "patch0=HorizY001ZTop@90");
                                    break;
                                case 7:
                                    mrec.setLine("patchblock", "patch0=HorizY001ZTop@180");
                                    break;
                                case 8:
                                    mrec.setLine("patchblock", "patch0=HorizY001ZTop@270");
                                    break;
                                case 9:
                                    mrec.setLine("patchblock", "patch0=HorizY001ZTop@270");
                                    break;
                            }
                            // Add first patch
                            trec.addPatch(0, cmult, sides[0]);
                            txtref.add(sides[0]);
                            break;
                        case LADDER:
                            trec.setTransparency(Transparency.TRANSPARENT);
                            // Model record for rail patches
                            mrec = new ModelRecord(id, meta, b);
                            switch (meta) {
                                case 2:
                                    mrec.setLine("patchblock", "patch0=VertX0In@270");
                                    break;
                                case 3:
                                    mrec.setLine("patchblock", "patch0=VertX0In@90");
                                    break;
                                case 4:
                                    mrec.setLine("patchblock", "patch0=VertX0In@180");
                                    break;
                                case 5:
                                    mrec.setLine("patchblock", "patch0=VertX0In");
                                    break;
                            }
                            // Add first patch
                            trec.addPatch(0, cmult, sides[0]);
                            txtref.add(sides[0]);
                            break;
                        case VINE:
                            trec.setTransparency(Transparency.TRANSPARENT);
                            // Model record for rail patches
                            mrec = new ModelRecord(id, meta, b);
                            switch (meta) {
                                case 1:
                                    mrec.setLine("patchblock", "patch0=VertX0In@270#0");
                                    break;
                                case 2:
                                    mrec.setLine("patchblock", "patch0=VertX0In#0");
                                    break;
                                case 3:
                                    mrec.setLine("patchblock", "patch0=VertX0In@270#0", "patch1=VertX0In#0");
                                    break;
                                case 4:
                                    mrec.setLine("patchblock", "patch0=VertX0In@90#0");
                                    break;
                                case 5:
                                    mrec.setLine("patchblock", "patch0=VertX0In@90#0", "patch1=VertX0In@270#0");
                                    break;
                                case 6:
                                    mrec.setLine("patchblock", "patch0=VertX0In#0", "patch1=VertX0In@90#0");
                                    break;
                                case 7:
                                    mrec.setLine("patchblock", "patch0=VertX0In@90#0", "patch1=VertX0In@270#0", "patch2=VertX0In#0");
                                    break;
                                case 8:
                                    mrec.setLine("patchblock", "patch0=VertX0In@180#0");
                                    break;
                                case 9:
                                    mrec.setLine("patchblock", "patch0=VertX0In@180#0", "patch1=VertX0In@270#0");
                                    break;
                                case 10:
                                    mrec.setLine("patchblock", "patch0=VertX0In#0", "patch1=VertX0In@180#0");
                                    break;
                                case 11:
                                    mrec.setLine("patchblock", "patch0=VertX0In#0", "patch1=VertX0In@180#0", "patch2=VertX0In@270#0");
                                    break;
                                case 12:
                                    mrec.setLine("patchblock", "patch0=VertX0In@90#0", "patch1=VertX0In@180#0");
                                    break;
                                case 13:
                                    mrec.setLine("patchblock", "patch0=VertX0In@270#0", "patch1=VertX0In@90#0", "patch2=VertX0In@180#0");
                                    break;
                                case 14:
                                    mrec.setLine("patchblock", "patch0=VertX0In@180#0", "patch1=VertX0In#0", "patch2=VertX0In@90#0");
                                    break;
                                case 15:
                                    mrec.setLine("patchblock", "patch0=VertX0In@270#0", "patch1=VertX0In@90#0", "patch2=VertX0In@180#0", "patch3=VertX0In#0");
                                    break;
                            }                               
                            // Add first patch
                            trec.addPatch(0, cmult, sides[0]);
                            txtref.add(sides[0]);
                            break;
                        case PANE:
                            trec.setTransparency(Transparency.TRANSPARENT);
                            // Model record for rail patches
                            mrec = new ModelRecord(id, meta, b);
                            mrec.setLine("customblock", "class=org.dynmap.hdmap.renderer.PaneRenderer");
                            trec.addPatch(0, cmult, sides[0]);
                            trec.addPatch(1, cmult, sides[1]);
                            txtref.add(sides[0]);
                            txtref.add(sides[1]);
                            break;
                        case DOOR:
                            trec.setTransparency(Transparency.TRANSPARENT);
                            // Model record for rail patches
                            mrec = new ModelRecord(id, meta, b);
                            mrec.setLine("customblock", "class=org.dynmap.hdmap.renderer.DoorRenderer");
                            trec.addPatch(0, cmult, sides[0]);
                            trec.addPatch(1, cmult, sides[1]);
                            txtref.add(sides[0]);
                            txtref.add(sides[1]);
                            //trec.setComment("FIXME: Top texture (patch0) not properly detected: needs to be manually fixed");
                            break;
                        case TORCH:
                            trec.setTransparency(Transparency.TRANSPARENT); // Assume transparent
                            // Model record for torch patches
                            mrec = new ModelRecord(id, meta, b);
                            switch (meta) {
                                case 0:
                                case 5:
                                default:
                                    mrec.setLine("patchblock", "patch0=VertX04375#0","patch1=VertX04375@90#0","patch2=VertX04375@180#0","patch3=VertX04375@270#0","patch4=TorchTop#0");
                                    break;
                                case 1:
                                    mrec.setLine("patchblock", "patch0=TorchSide1#0","patch1=TorchSide2#0","patch2=TorchSide3#0","patch3=TorchSide4#0","patch4=TorchTopSlope@270#0");
                                    break;
                                case 2:
                                    mrec.setLine("patchblock", "patch0=TorchSide1@180#0","patch1=TorchSide2@180#0","patch2=TorchSide3@180#0","patch3=TorchSide4@180#0","patch4=TorchTopSlope@90#0");
                                    break;
                                case 3:
                                    mrec.setLine("patchblock", "patch0=TorchSide1@90#0","patch1=TorchSide2@90#0","patch2=TorchSide3@90#0","patch3=TorchSide4@90#0","patch4=TorchTopSlope#0");
                                    break;
                                case 4:
                                    mrec.setLine("patchblock", "patch0=TorchSide1@270#0","patch1=TorchSide2@270#0","patch2=TorchSide3@270#0","patch3=TorchSide4@270#0","patch4=TorchTopSlope@180#0");
                                    break;
                            }
                            trec.addPatch(0, cmult, sides[0]);
                            txtref.add(sides[0]);
                            break;
                        case FENCE:
                        case WALL:
                            trec.setTransparency(Transparency.TRANSPARENT);
                            // Model record for fence patches
                            mrec = new ModelRecord(id, meta, b);
                            if (rt == RendererType.WALL) {
                                mrec.setLine("customblock", "class=org.dynmap.hdmap.renderer.FenceWallBlockRenderer,type=fence","link0=107","type=wall");
                            }
                            else {
                                mrec.setLine("customblock", "class=org.dynmap.hdmap.renderer.FenceWallBlockRenderer,type=fence","link0=107");
                            }
                            trec.addPatch(0, cmult, sides[0]);
                            trec.addPatch(1, cmult, sides[1]);
                            trec.addPatch(2, cmult, sides[2]);
                            txtref.add(sides[0]);
                            txtref.add(sides[1]);
                            txtref.add(sides[2]);
                            break;
                        case FENCEGATE:
                            trec.setTransparency(Transparency.TRANSPARENT);
                            // Model record for fence gate patches
                            mrec = new ModelRecord(id, meta, b);
                            mrec.setLine("customblock", "class=org.dynmap.hdmap.renderer.FenceGateBlockRenderer,type=fence","link0=107");
                            trec.addPatch(0, cmult, sides[0]);
                            trec.addPatch(1, cmult, sides[1]);
                            trec.addPatch(2, cmult, sides[2]);
                            txtref.add(sides[0]);
                            txtref.add(sides[1]);
                            txtref.add(sides[2]);
                            break;
                        default:    // Unhandled cases: need models but we don't know which yet
                            log.warning("Using unsupported standard renderer in " + ctx.recmod + ": " + rt.toString());
                            trec.setTransparency(Transparency.TRANSPARENT); // Assume transparent
                            /* Model record for cuboid */
                            mrec = new ModelRecord(id, meta, b);
                            addallsides = true; // And add all standard sides
                            break;
                    }
                    if (addallsides) {
                        for (int side = 0; side < sides.length; side++) {
                            if (sides[side] != null) {
                                trec.addSide(side, sideidx[side], sides[side]);
                                txtref.add(sides[side]);
                            }
                        }
                    }

                    if (blockline != null) {
                        blockline = null;
                        blkComments.put(id, ":* (" + ctx.bname + "), render=" + rclass + "(" + rt + "), opaque=" + b.isOpaqueCube() + ",cls=" + b.getClass().getName());
                    }

                    ArrayList<TextureRecord> tlist = txtRecsByMod.get(ctx.recmod);
                    if (tlist == null) {
                        tlist = new ArrayList<TextureRecord>();
                        txtRecsByMod.put(ctx.recmod,  tlist);
                    }
                    tlist.add(trec);
                    if (mrec != null) {
                        ArrayList<ModelRecord> mlist = modRecsByMod.get(ctx.recmod);
                        if (mlist == null) {
                            mlist = new ArrayList<ModelRecord>();
                            modRecsByMod.put(ctx.recmod,  mlist);
                        }
                        mlist.add(mrec);
                    }
                }
                // Restore block
                restoreBlock(b, meta);
            }
        }
        HashSet<String> mods = new HashSet<String>();
        mods.addAll(txtRecsByMod.keySet());
        mods.addAll(modRecsByMod.keySet());
        mods.addAll(pipeRecsByMod.keySet());
        mods.remove("minecraft");
        for (String mod : mods) {
            if (mod == null) continue;
            log.info("Generating files for " + mod);
            FileWriter txt = null;
            FileWriter modf = null;
            IDMapping aliases = getBlockIDMappingForMod(mod);
            String modver = "";
            ModContainer modc = Loader.instance().getIndexedModList().get(mod);
            if (modc != null) {
                modver = modc.getVersion();
            }
            ArrayList<String> txtlines = new ArrayList<String>();
            try {
                txtlines.add("\ntexturepath:assets/" + mod.toLowerCase() + "/textures/blocks/");
                // Write any texture references
                HashSet<String> txtlist = texIDByMod.get(mod);
                if (txtlist != null) {
                    TreeSet<String> ordered_ids = new TreeSet<String>(txtlist);
                    for (String id : ordered_ids) {
                        String fname = textures.get(id);
                        txtlines.add("texture:id=" + id + ",filename=" + fname);
                    }
                }
                txtlines.add("");
                // Check for biome records, and add them
                if (aliases.biomemap.isEmpty() == false) {
                    TreeSet<Integer> keys = new TreeSet<Integer>(aliases.biomemap.keySet());
                    for (Integer id : keys) {
                        BiomeGenBase bio = BiomeGenBase.biomeList[id];
                        if (bio == null) continue;
                        String sym = aliases.biomemap.get(id);
                        if (sym == null) continue;
                        aliases.used.add(sym);  // Mark symbol as used
                        String line = String.format("biome:id=%s,grassColorMult=1%06X,foliageColorMult=1%06X,waterColorMult=%06X",
                                sym, bio.getBiomeGrassColor() & 0xFFFFFF, bio.getBiomeFoliageColor() & 0xFFFFFF, bio.getWaterColorMultiplier() & 0xFFFFFF);
                        txtlines.add("# " + sym);
                        txtlines.add(line);
                    }
                }
                txtlines.add("");
                // Write any texture records
                List<TextureRecord> tlist = txtRecsByMod.get(mod);
                if ((tlist != null) && (tlist.isEmpty() == false)) {
                    // Make traversal to build mergable strings for each block ID
                    HashMap<Integer, StringRunAccum> linesets = new HashMap<Integer, StringRunAccum>();
                    for (TextureRecord trec : tlist) {
                        StringRunAccum lineaccum = linesets.get(trec.id);
                        if (lineaccum == null) {
                            lineaccum = new StringRunAccum();
                            linesets.put(trec.id, lineaccum);
                        }
                        // Build line for this texture record
                        lineaccum.addString(trec.meta, trec.getLine());
                    }
                    // Now traverse and output
                    int lastid = -1;
                    for (TextureRecord trec : tlist) {
                        if (trec.id != lastid) {
                            lastid = trec.id;
                            String idstr = getBlockID(trec.id, aliases);
                            String c = blkComments.get(trec.id);
                            txtlines.add("");
                            if (c != null) {
                                txtlines.add("# " + idstr + c);
                            }
                            if (trec.comment != null) {
                                txtlines.add("# " + trec.comment);
                            }
                            // And get line set for id
                            StringRunAccum lineaccum = linesets.get(trec.id);
                            if (lineaccum != null) {
                                for (StringRun sr : lineaccum.lst) {
                                    String line = "block:id=" + idstr;
                                    if ((sr.start == 0) && (sr.count == 16)) {  // Is * special case
                                        line += ",data=*";
                                    }
                                    else {  // Else, add each data value
                                        for (int meta = sr.start; meta < (sr.start + sr.count); meta++) {
                                            line += ",data=" + meta;
                                        }
                                    }
                                    line += sr.str;   // Add rest of the line
                                    txtlines.add(line);
                                }
                            }
                        }
                    }
                }
                // Write any pipe records
                List<PipeRecord> plist = pipeRecsByMod.get(mod);
                if ((plist != null) && (plist.isEmpty() == false)) {
                    txtlines.add("# BuildCraft pipes");
                    for (PipeRecord pr : plist) {
                        txtlines.add("addtotexturemap:mapid=PIPES,key:" + getItemID(pr.itemid, aliases) + "=0:" + 
                                pr.iconid);
                    }
                    txtlines.add("");
                }
                
                txt = new FileWriter(new File(datadir, fixFileName(mod) + "-texture.txt"));
                txt.write("# " + mod + " " + modver + "\n");
                txt.write("version:" + MCVERSIONLIMIT + "\n");
                txt.write("modname:" + fullModIdByLowerCase.get(mod.toLowerCase()) + "\n\n");
                if (aliases.used.isEmpty() == false) {
                    int cnt = 0;
                    for (String s : aliases.used) {
                        if ((s.charAt(0) == '%') || (s.charAt(0) == '&')) {
                            continue;
                        }
                        if (cnt == 0) {
                            txt.write("var:");
                        }
                        else {
                            txt.write(",");
                        }
                        txt.write(s + "=0");
                        cnt++;
                        if (cnt == 10) {
                            txt.write("\n");
                            cnt = 0;
                        }
                    }
                    if (cnt > 0) {
                        txt.write("\n");
                    }
                    txt.write("\n");
                }
                boolean match = false;
                for (int fidx = 0; ; fidx++) {
                    String cfgfile = getModConfigFile(mod, fidx);
                    if (cfgfile != null) {
                        txt.write("cfgfile:" + cfgfile + "\n");
                        match = true;
                    }
                    else {
                        break;
                    }
                }
                txt.write("\n");
                if (!match) {
                    txt.write("# Configuration file not found!\n\n");
                    log.warning(mod + "-texture.txt missing configuration file!");
                }
                if (aliases.missedID) {
                    log.warning(mod + "-texture.txt missing one or more block IDs names!");
                }
                for (String line : txtlines) {
                    txt.write(line + "\n");
                }
                txt.close();
                txt = null;
                
                /* And write model file, if needed */
                List<ModelRecord> mlist = modRecsByMod.get(mod);
                if ((mlist != null) && (mlist.isEmpty() == false)) {
                    ArrayList<String> modlines = new ArrayList<String>();
                    // Stock patches
                    modlines.add("patch:id=VertX1Z0ToX0Z1,Ox=1.0,Oy=0.0,Oz=0.0,Ux=0.0,Uy=0.0,Uz=1.0,Vx=1.0,Vy=1.0,Vz=0.0,visibility=flip");
                    modlines.add("patch:id=VertX025,Ox=0.25,Oy=0.0,Oz=1.0,Ux=0.25,Uy=0.0,Uz=0.0,Vx=0.25,Vy=1.0,Vz=1.0");
                    modlines.add("patch:id=VertX075,Ox=0.75,Oy=0.0,Oz=1.0,Ux=0.75,Uy=0.0,Uz=0.0,Vx=0.75,Vy=1.0,Vz=1.0");
                    modlines.add("patch:id=HorizY001ZTop,Ox=0.0,Oy=0.01,Oz=0.0,Ux=1.0,Uy=0.01,Uz=0.0,Vx=0.0,Vy=0.01,Vz=1.0");
                    modlines.add("patch:id=SlopeXUpZTop,Ox=0.0,Oy=0.0,Oz=0.0,Ux=0.0,Uy=0.0,Uz=1.0,Vx=1.0,Vy=1.0,Vz=0.0");
                    modlines.add("patch:id=VertX0In,Ox=0.0,Oy=0.0,Oz=1.0,Ux=0.0,Uy=0.0,Uz=0.0,Vx=0.0,Vy=1.0,Vz=1.0");
                    modlines.add("patch:id=VertX04375,Ox=0.4375,Oy=0.0,Oz=0.0,Ux=0.4375,Uy=0.0,Uz=1.0,Vx=0.4375,Vy=1.0,Vz=0.0,visibility=top");
                    modlines.add("patch:id=TorchSide1,Ox=-0.5,Oy=0.2,Oz=0.4375,Ux=0.5,Uy=0.2,Uz=0.4375,Vx=-0.1,Vy=1.2,Vz=0.4375,Vmax=0.8,visibility=bottom");
                    modlines.add("patch:id=TorchSide2,Ox=-0.5,Oy=0.2,Oz=0.5625,Ux=0.5,Uy=0.2,Uz=0.5625,Vx=-0.1,Vy=1.2,Vz=0.5625,Vmax=0.8,visibility=top");
                    modlines.add("patch:id=TorchSide3,Ox=0.0625,Oy=0.2,Oz=0.0,Ux=0.0625,Uy=0.2,Uz=1.0,Vx=0.4625,Vy=1.2,Vz=0.0,Vmax=0.8,visibility=bottom");
                    modlines.add("patch:id=TorchSide4,Ox=-0.0625,Oy=0.2,Oz=0.0,Ux=-0.0625,Uy=0.2,Uz=1.0,Vx=0.3375,Vy=1.2,Vz=0.0,Vmax=0.8,visibility=top");
                    modlines.add("patch:id=TorchTop,Ox=0.0,Oy=0.625,Oz=-0.0625,Ux=1.0,Uy=0.625,Uz=-0.0625,Vx=0.0,Vy=0.625,Vz=0.9375,Umin=0.4375,Umax=0.5625,Vmin=0.5,Vmax=0.625");
                    modlines.add("patch:id=TorchTopSlope,Ox=0.0,Oy=0.825,Oz=-0.3625,Ux=1.0,Uy=0.825,Uz=-0.3625,Vx=0.0,Vy=0.825,Vz=0.6375,Umin=0.4375,Umax=0.5625,Vmin=0.5,Vmax=0.625");
                    modlines.add("patch:id=VertX00625,Ox=0.0625,Oy=0.0,Oz=0.0,Ux=0.0625,Uy=0.0,Uz=1.0,Vx=0.0625,Vy=1.0,Vz=0.0,visibility=top");
                    modlines.add("patch:id=HorizY100ZTop,Ox=0.0,Oy=1.0,Oz=0.0,Ux=1.0,Uy=1.0,Uz=0.0,Vx=0.0,Vy=1.0,Vz=1.0,visibility=bottom");
                    modlines.add("");
                    // Make traversal to build mergable strings for each block ID
                    HashMap<Integer, StringRunAccum> linesets = new HashMap<Integer, StringRunAccum>();
                    for (ModelRecord mrec : mlist) {
                        StringRunAccum lineaccum = linesets.get(mrec.id);
                        if (lineaccum == null) {
                            lineaccum = new StringRunAccum();
                            linesets.put(mrec.id, lineaccum);
                        }
                        // Build line for this model record
                        if (mrec.partialline != null) {
                            lineaccum.addString(mrec.meta, mrec.getLine(aliases));
                        }
                    }
                    int lastid = -1;
                    for (ModelRecord mrec : mlist) {
                        if (mrec.id != lastid) {
                            String idstr = getBlockID(mrec.id, aliases);
                            lastid = mrec.id;
                            String c = blkComments.get(mrec.id);
                            modlines.add("");
                            if (c != null) {
                                modlines.add("# " + idstr + c);
                            }
                            if (mrec.comment != null) {
                                modlines.add("# " + mrec.comment);
                            }
                            // And get line set for id
                            StringRunAccum lineaccum = linesets.get(mrec.id);
                            if (lineaccum != null) {
                                for (StringRun sr : lineaccum.lst) {
                                    String line = sr.str;
                                    if ((sr.start == 0) && (sr.count == 16)) {  // Is * special case
                                        line += ",data=*";
                                    }
                                    else {  // Else, add each data value
                                        for (int meta = sr.start; meta < (sr.start + sr.count); meta++) {
                                            line += ",data=" + meta;
                                        }
                                    }
                                    modlines.add(line);
                                }
                            }
                        }
                    }
                    modf = new FileWriter(new File(datadir, fixFileName(mod) + "-models.txt"));
                    modf.write("# " + mod + " " + modver + "\n");
                    modf.write("version:" + MCVERSIONLIMIT + "\n");
                    modf.write("modname:" + fullModIdByLowerCase.get(mod.toLowerCase()) + "\n\n");
                    if (aliases.used.isEmpty() == false) {
                        int cnt = 0;
                        for (String s : aliases.used) {
                            if ((s.charAt(0) == '%') || (s.charAt(0) == '&')) {
                                continue;
                            }
                            if (cnt == 0) {
                                modf.write("var:");
                            }
                            else {
                                modf.write(",");
                            }
                            modf.write(s + "=0");
                            cnt++;
                            if (cnt == 10) {
                                modf.write("\n");
                                cnt = 0;
                            }
                        }
                        if (cnt > 0) {
                            modf.write("\n");
                        }
                        modf.write("\n");
                    }
                    match = false;
                    for (int fidx = 0; ; fidx++) {
                        String cfgfile = getModConfigFile(mod, fidx);
                        if (cfgfile != null) {
                            modf.write("cfgfile:" + cfgfile + "\n");
                            match = true;
                        }
                        else {
                            break;
                        }
                    }
                    modf.write("\n");
                    if (!match) {
                        modf.write("# Configuration file not found!\n\n");
                        log.warning(mod + "-models.txt missing configuration file!");
                    }
                    if (aliases.missedID) {
                        log.warning(mod + "-models.txt missing one or more block IDs names!");
                    }
                    for (String line : modlines) {
                        modf.write(line + "\n");
                    }
                    modf.close(); modf = null;
                }
            } catch (IOException iox) {
                log.severe("Error writing " + mod + " files");
            } finally {
                if (txt != null) {
                    try { txt.close(); } catch (IOException x) {}
                    txt = null;
                }
                if (modf != null) {
                    try { modf.close(); } catch (IOException x) {}
                    modf = null;
                }
            }
            
        }
        // Restore graphics level
        Block.leaves.setGraphicsLevel(savedGraphicsLevel);
    }
    
    private void processItems() {
        log.info("Checking for special items");
        Class<?> pipecls = null;
        try {
            pipecls = Class.forName("buildcraft.transport.ItemPipe");
            log.info("BuildCraft pipe class found");
        } catch (ClassNotFoundException cnfx) {
            log.info("BuildCraft pipe class not found");
        }
        ModuleCtx ctx = new ModuleCtx();
        
        for (int itemid = 256; itemid < Item.itemsList.length; itemid++) {
            Item itm = Item.itemsList[itemid];
            if (itm == null) continue;
            ctx.reset();
            UniqueIdentifier ui = GameRegistry.findUniqueIdentifierFor(itm);
            if ((ui != null) && (ui.modId != null) && (!ui.modId.equals("null"))) {
                ctx.recmod = normalizeModID(ui.modId);
                ctx.bname = ui.name;
            }
            if ((pipecls != null) && pipecls.isAssignableFrom(itm.getClass())) {
                Icon ico = itm.getIconFromDamage(0);
                if (ico != null) {
                    String id = handleIcon(ico, ctx);   // Handle the icon
                    PipeRecord pr = new PipeRecord();
                    pr.itemid = itemid;
                    pr.iconid = id;
                    if (ctx.recmod != null) {
                        // Add pipe record
                        ArrayList<PipeRecord> prl = pipeRecsByMod.get(ctx.recmod);
                        if (prl == null) {
                            prl = new ArrayList<PipeRecord>();
                            pipeRecsByMod.put(ctx.recmod, prl);
                        }
                        prl.add(pr);
                        // Add reference to icon
                        HashSet<String> txtref = texIDByMod.get(ctx.recmod);
                        if (txtref == null) {
                            txtref = new HashSet<String>();
                            texIDByMod.put(ctx.recmod, txtref);
                        }
                        txtref.add(id);
                    }
                }
            }
        }
        log.info("Checking for special items completed");
    }
}
