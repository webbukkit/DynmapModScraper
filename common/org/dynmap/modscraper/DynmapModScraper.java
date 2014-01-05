package org.dynmap.modscraper;

import cpw.mods.fml.client.registry.RenderingRegistry;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.Mod.EventHandler;
import cpw.mods.fml.common.Mod.Instance;
import cpw.mods.fml.common.SidedProxy;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartedEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent; 
import cpw.mods.fml.common.event.FMLServerStoppingEvent;
import cpw.mods.fml.common.network.NetworkMod;
import cpw.mods.fml.common.registry.BlockProxy;
import cpw.mods.fml.common.registry.EntityRegistry;
import cpw.mods.fml.common.registry.GameRegistry;
import net.minecraftforge.client.event.sound.SoundLoadEvent;
import net.minecraftforge.common.Configuration;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.ForgeSubscribe;
import net.minecraft.block.Block;
import net.minecraft.block.BlockAnvil;
import net.minecraft.block.BlockBeacon;
import net.minecraft.block.BlockBrewingStand;
import net.minecraft.block.BlockCauldron;
import net.minecraft.block.BlockCocoa;
import net.minecraft.block.BlockComparator;
import net.minecraft.block.BlockDoor;
import net.minecraft.block.BlockDragonEgg;
import net.minecraft.block.BlockEndPortalFrame;
import net.minecraft.block.BlockFence;
import net.minecraft.block.BlockFenceGate;
import net.minecraft.block.BlockFire;
import net.minecraft.block.BlockFlowerPot;
import net.minecraft.block.BlockHalfSlab;
import net.minecraft.block.BlockHopper;
import net.minecraft.block.BlockLeaves;
import net.minecraft.block.BlockLeavesBase;
import net.minecraft.block.BlockPane;
import net.minecraft.block.BlockPressurePlate;
import net.minecraft.block.BlockPressurePlateWeighted;
import net.minecraft.block.BlockRailBase;
import net.minecraft.block.BlockRedstoneLogic;
import net.minecraft.block.BlockRedstoneRepeater;
import net.minecraft.block.BlockStairs;
import net.minecraft.block.BlockStep;
import net.minecraft.block.BlockWall;
import net.minecraft.block.EnumMobType;
import net.minecraft.block.material.Material;
import net.minecraft.crash.CrashReport;
import net.minecraft.item.Item;
import net.minecraft.item.ItemMultiTextureTile;
import net.minecraft.util.Icon;
import net.minecraft.util.ReportedException;

import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.logging.Logger;


@Mod(modid = "DynmapModScraper", name = "", version = Version.VER)
@NetworkMod(clientSideRequired = true, serverSideRequired = false)
public class DynmapModScraper
{    
    public static Logger log = Logger.getLogger("DynmapModScraper");
    
    // The instance of your mod that Forge uses.
    @Instance("DynmapModScraper")
    public static DynmapModScraper instance;

    // Says where the client and server 'proxy' code is loaded.
    @SidedProxy(clientSide = "org.dynmap.modscraper.ClientProxy")
    public static Proxy proxy;

    public boolean good_init = false;
    
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
        ArrayList<String> comments = new ArrayList<String>();
        Transparency transparency = Transparency.OPAQUE;
        int id;
        int meta;
        boolean stdrot = true;
        String[] sides = null;
        String[] patches = null;
        
        TextureRecord(int id, int meta, Block b) {
            this.id = id;
            this.meta = meta;
            int rid = b.getRenderType();
        }
        public void addComment(String s) {
            comments.add(s);
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
    }
    
    public static class ModelRecord {
        ArrayList<String> comments = new ArrayList<String>();
        ArrayList<String> lines = new ArrayList<String>();
        int id;
        int meta;
        ModelRecord(int id, int meta, Block b) {
            this.id = id;
            this.meta = meta;   
            int rid = b.getRenderType();
        }
        public void addComment(String s) {
            comments.add(s);
        }
        public void addLine(String type, String... args) {
            String line = type + ":id=" + id + ",data=" + meta;
            for (String arg : args) {
                if (arg != null) {
                    line += "," + arg;
                }
            }
            lines.add(line);
        }
    }
    
    @EventHandler
    public void preInit(FMLPreInitializationEvent event)
    {
        // Load configuration file - use suggested (config/DynmapModScraper.cfg)
        Configuration cfg = new Configuration(event.getSuggestedConfigurationFile());
        try
        {
            cfg.load();
            
            good_init = true;
        }
        catch (Exception e)
        {
            crash(e, "DynmapModScraper couldn't load its configuration");
        }
        finally
        {
            cfg.save();
        }
    }

    @EventHandler
    public void serverStarted(FMLServerStartingEvent event)
    {
        log.info("DynmapMapScraper active");
        
        if (!good_init) {
            crash("preInit failed - aborting load()");
            return;
        }
        HashMap<String,String> textures = new HashMap<String,String>(); // ID by name
        HashMap<String,HashSet<String>> texIDByMod = new HashMap<String,HashSet<String>>(); // IDs by mod
        HashMap<String, ArrayList<TextureRecord>> txtRecsByMod = new HashMap<String, ArrayList<TextureRecord>>();
        HashMap<String, ArrayList<ModelRecord>> modRecsByMod = new HashMap<String, ArrayList<ModelRecord>>();
        
        FileWriter fw = null;
        try {
            fw = new FileWriter("DynmapModScraper.txt");
            for (int id = 0; id < 4096; id++) {
                Block b = Block.blocksList[id];
                if (b == null) continue;
                int rid = b.getRenderType();
                String uname = b.getUnlocalizedName();
                RendererType rt = RendererType.byID(rid);
                String recmod = null;
                String blockline = "Block: id=" + id + ", class=" + b.getClass().getName() + ", renderer=" + rid + "(" + rt + "), isOpaqueCube=" + b.isOpaqueCube() + ", name=" + b.getLocalizedName() + "(" + uname + ")\n";

                double x0  = b.getBlockBoundsMinX();
                double y0  = b.getBlockBoundsMinY();
                double z0  = b.getBlockBoundsMinZ();
                double x1  = b.getBlockBoundsMaxX();
                double y1  = b.getBlockBoundsMaxY();
                double z1  = b.getBlockBoundsMaxZ();
                
                boolean isFull = true;
                if ((x0 != 0.0) || (y0 != 0.0) || (z0 != 0.0) || (x1 != 1.0) || (y1 != 1.0) || (z1 != 1.0)) {
                    blockline += String.format("  bounds=%f.%f,%f:%f,%f,%f\n", x0, y0, z0, x1, y1, z1);
                    isFull = false;
                }

                for (int meta = 0; meta < 16; meta++) {
                    String line = "  Meta: id=" + id + ":" + meta;
                    String sides[] = new String[6];
                    boolean hit = false;
                    for (int side = 0; side < 6; side++) {
                        Icon ico = null;
                        try {
                            ico = b.getIcon(side, meta);
                        } catch (Exception x) {
                            // Some mods don't like undefined sides to be requuested
                        }
                        if (ico != null) {
                            hit = true;
                            sides[side] = ico.getIconName();
                            line += ", side" + side + "=" + sides[side];
                            String[] split = sides[side].split(":");
                            String mod, txt;
                            if (split.length == 1) {
                                mod = "minecraft";
                                txt = split[0];
                            }
                            else {
                                mod = split[0];
                                txt = split[1];
                                if (recmod == null) recmod = mod;
                            }
                            textures.put(mod + "/" + txt, "assets/" + mod + "/textures/blocks/" + txt + ".png");
                            sides[side] = mod + "/" + txt;
                        }
                    }
                    if (hit) {
                        if (recmod == null) recmod = "minecraft";
                        TextureRecord trec = new TextureRecord(id, meta, b);
                        ModelRecord mrec = null;
                        boolean addallsides = false;
                        HashSet<String> txtref = texIDByMod.get(recmod);
                        if (txtref == null) {
                            txtref = new HashSet<String>();
                            texIDByMod.put(recmod, txtref);
                        }
                        int[] sideidx = { 0, 0, 0, 0, 0, 0 };  // Default side indexes
                        switch (rt) {
                            case STANDARD:
                                if (!b.isOpaqueCube()) { // Not simple cube                                    trec.setTransparency(Transparency.TRANSPARENT); // Transparent
                                    /* Model record for cuboid */
                                    mrec = new ModelRecord(id, meta, b);
                                    if (b instanceof BlockHalfSlab) {   // Slab block?
                                        if (meta < 8) { // Bottpm?
                                            mrec.addLine("boxblock", "ymax=0.5");
                                        }
                                        else {  // Top?
                                            mrec.addLine("boxblock", "ymin=0.5");
                                        }
                                        trec.setTransparency(Transparency.SEMITRANSPARENT);
                                    }
                                    else {
                                        if (!isFull) {
                                            trec.setTransparency(Transparency.TRANSPARENT);
                                            mrec.addLine("boxblock", "xmin=" + x0, "xmax=" + x1, "ymin=" + y0, "ymax=" + y1, "zmin=" + z0, "zmax=" + z1);
                                        }
                                        else {  // Else, full block
                                            mrec = null;
                                            //- assume transparent texture behavior if not render pass 0
                                            if (b.getRenderBlockPass() > 0) {
                                                sideidx = new int[] { 12000, 12000, 12000, 12000, 12000, 12000 };
                                            }
                                        }
                                    }
                                }
                                else {
                                    if (b instanceof BlockLeavesBase) {
                                    }
                                    //- assume transparent texture behavior if not render pass 0
                                    else if (b.getRenderBlockPass() > 0) {
                                        sideidx = new int[] { 12000, 12000, 12000, 12000, 12000, 12000 };
                                    }
                                }
                                addallsides = true; // And add all standard sides
                                break;
                            case STAIRS:
                                trec.setTransparency(Transparency.SEMITRANSPARENT);
                                // Model record for stairs
                                mrec = new ModelRecord(id, meta, b);
                                mrec.addLine("customblock", "class=org.dynmap.hdmap.renderer.StairBlockRenderer");
                                addallsides = true; // And add all standard sides
                                break;
                            case CROSSEDSQUARES:
                                trec.setTransparency(Transparency.TRANSPARENT);
                                // Model record for crossed patches
                                mrec = new ModelRecord(id, meta, b);
                                mrec.addLine("patchblock", "patch0=VertX1Z0ToX0Z1#0","patch1=VertX1Z0ToX0Z1@90#0");
                                // Add first patch
                                trec.addPatch(0, 0, sides[0]);
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
                            case CROPS:
                                trec.setTransparency(Transparency.TRANSPARENT);
                                // Model record for crop patches
                                mrec = new ModelRecord(id, meta, b);
                                mrec.addLine("patchblock", "patch0=VertX075#0","patch1=VertX075@90#0","patch2=VertX025#0","patch3=VertX025@90#0");
                                // Add first patch
                                trec.addPatch(0, 0, sides[0]);
                                txtref.add(sides[0]);
                                break;
                            case MINECARTTRACK:
                                // Model record for rail patches
                                mrec = new ModelRecord(id, meta, b);
                                int mask = 0xF;
                                if (((BlockRailBase)b).isPowered()) {
                                    mask = 0x7;
                                }
                                switch (meta & mask) {
                                    case 0:
                                        mrec.addLine("patchblock", "patch0=HorizY001ZTop");
                                        break;
                                    case 1:
                                        mrec.addLine("patchblock", "patch0=HorizY001ZTop@90");
                                        break;
                                    case 2:
                                    case 10:
                                        mrec.addLine("patchblock", "patch0=SlopeXUpZTop");
                                        break;
                                    case 3:
                                    case 11:
                                        mrec.addLine("patchblock", "patch0=SlopeXUpZTop@180");
                                        break;
                                    case 4:
                                    case 12:
                                        mrec.addLine("patchblock", "patch0=SlopeXUpZTop@270");
                                        break;
                                    case 5:
                                    case 13:
                                        mrec.addLine("patchblock", "patch0=SlopeXUpZTop@90");
                                        break;
                                    case 6:
                                        mrec.addLine("patchblock", "patch0=HorizY001ZTop@90");
                                        break;
                                    case 7:
                                        mrec.addLine("patchblock", "patch0=HorizY001ZTop@180");
                                        break;
                                    case 8:
                                        mrec.addLine("patchblock", "patch0=HorizY001ZTop@270");
                                        break;
                                    case 9:
                                        mrec.addLine("patchblock", "patch0=HorizY001ZTop@270");
                                        break;
                                }

                                break;
                                
                            default:    // Unhandled cases: need models but we don't know which yet
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
                            fw.write(blockline);
                            blockline = null;
                        }
                        fw.write(line + "\n");
                        
                        ArrayList<TextureRecord> tlist = txtRecsByMod.get(recmod);
                        if (tlist == null) {
                            tlist = new ArrayList<TextureRecord>();
                            txtRecsByMod.put(recmod,  tlist);
                        }
                        tlist.add(trec);
                        if (mrec != null) {
                            ArrayList<ModelRecord> mlist = modRecsByMod.get(recmod);
                            if (mlist == null) {
                                mlist = new ArrayList<ModelRecord>();
                                modRecsByMod.put(recmod,  mlist);
                            }
                            mlist.add(mrec);
                        }
                    }
                }
            }
        } catch (IOException iox) {
        } finally {
            if (fw != null) {
                try { fw.close(); } catch (IOException iox) {}
            }
        }
        HashSet<String> mods = new HashSet<String>();
        mods.addAll(txtRecsByMod.keySet());
        mods.addAll(modRecsByMod.keySet());
        for (String mod : mods) {
            FileWriter txt = null;
            FileWriter modf = null;
            try {
                txt = new FileWriter(mod + "-texture.txt");
                txt.write("modname:" + mod + "\n");
                txt.write("texturepath:assets/" + mod + "/textures/blocks/\n");
                // Write any texture references
                HashSet<String> txtlist = texIDByMod.get(mod);
                if (txtlist != null) {
                    for (String id : txtlist) {
                        String fname = textures.get(id);
                        txt.write("texture:id=" + id + ",filename=" + fname + "\n");
                    }
                }
                // Write any texture records
                List<TextureRecord> tlist = txtRecsByMod.get(mod);
                if (tlist != null) {
                    for (TextureRecord trec : tlist) {
                        for (String c : trec.comments) {
                            txt.write("# " + c + "\n");
                        }
                        txt.write("block:id=" + trec.id + ",data=" + trec.meta);
                        if (trec.stdrot)
                            txt.write(",stdrot=true");
                        if (trec.transparency != Transparency.OPAQUE) {
                            txt.write(",transparency=" + trec.transparency);
                        }
                        if (trec.sides != null) {
                            for (int side = 0; side < trec.sides.length; side++) {
                                String siderec = trec.sides[side];
                                if (siderec != null) {
                                    txt.write(",face" + side + "=" + siderec);
                                }
                            }
                        }
                        if (trec.patches != null) {
                            for (int patch = 0; patch < trec.patches.length; patch++) {
                                String patchrec = trec.patches[patch];
                                if (patchrec != null) {
                                    txt.write(",patch" + patch + "=" + patchrec);
                                }
                            }
                        }
                        txt.write("\n");
                    }
                }
                txt.close();
                txt = null;
                /* And write model file, if needed */
                List<ModelRecord> mlist = modRecsByMod.get(mod);
                if (mlist != null) {
                    modf = new FileWriter(mod + "-models.txt");
                    modf.write("modname:" + mod + "\n");
                    // Stock patches
                    modf.write("patch:id=VertX1Z0ToX0Z1,Ox=1.0,Oy=0.0,Oz=0.0,Ux=0.0,Uy=0.0,Uz=1.0,Vx=1.0,Vy=1.0,Vz=0.0,visibility=flip\n");
                    modf.write("patch:id=VertX075,Ox=0.75,Oy=0.0,Oz=1.0,Ux=0.75,Uy=0.0,Uz=0.0,Vx=0.75,Vy=1.0,Vz=1.0\n");
                    modf.write("patch:id=HorizY001ZTop,Ox=0.0,Oy=0.01,Oz=0.0,Ux=1.0,Uy=0.01,Uz=0.0,Vx=0.0,Vy=0.01,Vz=1.0\n");
                    modf.write("patch:id=SlopeXUpZTop,Ox=0.0,Oy=0.0,Oz=0.0,Ux=0.0,Uy=0.0,Uz=1.0,Vx=1.0,Vy=1.0,Vz=0.0\n");

                    for (ModelRecord mrec : mlist) {
                        for (String c : mrec.comments) {
                            modf.write("# " + c + "\n");
                        }
                        for (String c : mrec.lines) {
                            modf.write(c + "\n");
                        }
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
    }
}
