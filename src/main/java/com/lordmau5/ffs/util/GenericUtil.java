package com.lordmau5.ffs.util;

import com.lordmau5.ffs.FancyFluidStorage;
import com.lordmau5.ffs.tile.abstracts.AbstractTankValve;
import com.lordmau5.ffs.tile.tanktiles.TileEntityTankFrame;
import net.minecraft.block.Block;
import net.minecraft.block.BlockGlass;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.network.Packet;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;
import net.minecraftforge.common.ForgeChunkManager;
import net.minecraftforge.fluids.FluidContainerRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.IFluidContainerItem;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.oredict.OreDictionary;

import java.text.NumberFormat;
import java.util.*;

/**
 * Created by Dustin on 28.06.2015.
 */
public class GenericUtil {

    private static List<Block> blacklistedBlocks;
    private static List<String> validTiles;
    private static List<ItemStack> glassList;

    private static Map<World, FakeWorldWrapper> fakeWorldWrapperMap;
    private static Map<World, ForgeChunkManager.Ticket> chunkloadTicketMap;

    public static void init() {
        glassList = OreDictionary.getOres("blockGlass");

        blacklistedBlocks = new ArrayList<>();

        blacklistedBlocks.add(Blocks.GRASS);
        blacklistedBlocks.add(Blocks.DIRT);
        blacklistedBlocks.add(Blocks.BEDROCK);
        blacklistedBlocks.add(Blocks.REDSTONE_LAMP);
        blacklistedBlocks.add(Blocks.SPONGE);

        validTiles = new ArrayList<>();

        validTiles.add("blockFusedQuartz");

        fakeWorldWrapperMap = new HashMap<>();
        chunkloadTicketMap = new HashMap<>();
    }

    public static String getUniquePositionName(AbstractTankValve valve) {
        return "tile_" + Long.toHexString(valve.getPos().toLong());
    }

    public static boolean canAutoOutput(float height, int tankHeight, int valvePosition, boolean negativeDensity) {
        height *= tankHeight;

        if(negativeDensity)
            return false;

        return height > (valvePosition - 0.5f);
    }

    public static boolean isBlockGlass(IBlockState blockState, int metadata) {
        if(blockState == null || blockState.getMaterial() == Material.AIR)
            return false;

        if(blockState.getBlock() instanceof BlockGlass)
            return true;

        ItemStack is = new ItemStack(blockState.getBlock(), 1, metadata);

        if(blockState.getMaterial() == Material.GLASS && !is.getUnlocalizedName().contains("pane"))
            return true;

        for(ItemStack is2 : glassList) {
            if(is2.getUnlocalizedName().equals(is.getUnlocalizedName()))
                return true;
        }
        return false;
    }

    public static boolean areTankBlocksValid(IBlockState bottomBlock, IBlockState topBlock, World world, BlockPos bottomPos) {
        if(!isValidTankBlock(world, bottomPos, bottomBlock))
            return false;

        switch (FancyFluidStorage.INSTANCE.TANK_FRAME_MODE) {
            case SAME_BLOCK:
                return bottomBlock.equals(topBlock);
            case DIFFERENT_METADATA:
                return bottomBlock.getBlock() == topBlock.getBlock();
            case DIFFERENT_BLOCK:
                return true;

            default:
                return false;
        }
    }

    public static boolean isTileEntityAcceptable(Block block, TileEntity tile) {
        for(String name : validTiles) {
            if(block.getUnlocalizedName().toLowerCase().contains(name.toLowerCase()))
                return true;
        }

        return false;
    }

    public static boolean isValidTankBlock(World world, BlockPos pos, IBlockState state) {
        if(state == null)
            return false;

        if(world.isAirBlock(pos))
            return false;

        Block block = state.getBlock();
        if (block.hasTileEntity(state)) {
            TileEntity tile = world.getTileEntity(pos);
            if(tile != null) {
                return tile instanceof TileEntityTankFrame || isTileEntityAcceptable(block, tile);
            }
        }

        if(blacklistedBlocks.contains(block))
            return false;

        if(state.canProvidePower())
            return false;

        if(state.getMaterial() == Material.SAND)
            return false;

        if(!state.isFullBlock())
            return false;

        if(FancyFluidStorage.INSTANCE.TANK_FRAME_MODE == FancyFluidStorage.TankFrameMode.DIFFERENT_BLOCK)
            return true;

        return true;
    }

    public static boolean canBlockLeak(IBlockState state) {
        Material mat = state.getMaterial();
        return mat.equals(Material.GRASS) || mat.equals(Material.SPONGE) || mat.equals(Material.CLOTH) || mat.equals(Material.CLAY) || mat.equals(Material.GOURD) || mat.equals(Material.SAND);
    }

    public static boolean isFluidContainer(ItemStack playerItem) {
        if (playerItem == null)
            return false;

        return FluidContainerRegistry.isContainer(playerItem) || playerItem.getItem() instanceof IFluidContainerItem;

    }

    public static boolean fluidContainerHandler(World world, AbstractTankValve valve, EntityPlayer player, EnumFacing side) {
        if(world.isRemote)
            return true;

        ItemStack current = player.inventory.getCurrentItem();

        if (current != null) {
            // Handle FluidContainerRegistry
            if (FluidContainerRegistry.isContainer(current)) {
                FluidStack liquid = FluidContainerRegistry.getFluidForFilledItem(current);
                // Handle filled containers
                if (liquid != null) {
                    int qty = valve.fillFromContainer(side, liquid, true);

                    if (qty != 0 && !player.capabilities.isCreativeMode) {
                        if (current.stackSize > 1) {
                            if (!player.inventory.addItemStackToInventory(FluidContainerRegistry.drainFluidContainer(current))) {
                                player.dropItem(FluidContainerRegistry.drainFluidContainer(current), false);
                            }

                            player.inventory.setInventorySlotContents(player.inventory.currentItem, GenericUtil.consumeItem(current));
                        } else {
                            player.inventory.setInventorySlotContents(player.inventory.currentItem, FluidContainerRegistry.drainFluidContainer(current));
                        }
                    }

                    return true;

                    // Handle empty containers
                } else {
                    FluidStack available = valve.getInfo().fluid;

                    if (available != null) {
                        ItemStack filled = FluidContainerRegistry.fillFluidContainer(available, current);

                        liquid = FluidContainerRegistry.getFluidForFilledItem(filled);

                        if (liquid != null) {
                            if (!player.capabilities.isCreativeMode) {
                                if (current.stackSize > 1) {
                                    if (!player.inventory.addItemStackToInventory(filled)) {
                                        return false;
                                    } else {
                                        player.inventory.setInventorySlotContents(player.inventory.currentItem, GenericUtil.consumeItem(current));
                                    }
                                } else {
                                    player.inventory.setInventorySlotContents(player.inventory.currentItem, GenericUtil.consumeItem(current));
                                    player.inventory.setInventorySlotContents(player.inventory.currentItem, filled);
                                }
                            }

                            valve.drain(liquid.amount, true);

                            return true;
                        }
                    }
                }
            } else if (current.getItem() instanceof IFluidContainerItem) {
                if (current.stackSize != 1) {
                    return false;
                }

                IFluidContainerItem container = (IFluidContainerItem) current.getItem();
                FluidStack liquid = container.getFluid(current);
                FluidStack tankLiquid = valve.getInfo().fluid;
                boolean mustDrain = liquid == null || liquid.amount == 0;
                boolean mustFill = tankLiquid == null || tankLiquid.amount == 0;
                if (mustDrain && mustFill) {
                    // Both are empty, do nothing
                } else if (mustDrain || !player.isSneaking()) {
                    liquid = valve.drain(1000, false);
                    int qtyToFill = container.fill(current, liquid, true);
                    valve.drain(qtyToFill, true);
                } else {
                    if (liquid.amount > 0) {
                        int qty = valve.fill(liquid, false);
                        valve.fill(container.drain(current, qty, true), true);
                    }
                }

                return true;
            }
            return false;
        }
        return false;
    }

    public static ItemStack consumeItem(ItemStack stack) {
        if (stack.stackSize == 1) {
            if (stack.getItem().hasContainerItem(stack)) {
                return stack.getItem().getContainerItem(stack);
            } else {
                return null;
            }
        } else {
            stack.splitStack(1);

            return stack;
        }
    }

    private static Map<BlockPos, IBlockState> getBlocksBetweenPoints(World world, BlockPos pos1, BlockPos pos2) {
        Map<BlockPos, IBlockState> blocks = new HashMap<>();

        BlockPos distance = pos2.subtract(pos1);
        int dX, dY, dZ;
        dX = distance.getX();
        dY = distance.getY();
        dZ = distance.getZ();

        for(int x=0; x<=dX; x++)
            for(int y=0; y<=dY; y++)
                for(int z=0; z<=dZ; z++) {
                    BlockPos pos = pos1.add(x, y, z);
                    blocks.put(pos, world.getBlockState(pos));
                }

        return blocks;
    }

    public static Map<BlockPos, IBlockState>[] getTankFrame(World world, BlockPos bottomDiag, BlockPos topDiag) {
        Map<BlockPos, IBlockState>[] maps = new HashMap[3];
        maps[0] = new HashMap<>(); // Frame Blocks
        maps[1] = new HashMap<>(); // Inside wall blocks
        maps[2] = new HashMap<>(); // Inside air

        int x1 = bottomDiag.getX();
        int y1 = bottomDiag.getY();
        int z1 = bottomDiag.getZ();
        int x2 = topDiag.getX();
        int y2 = topDiag.getY();
        int z2 = topDiag.getZ();

        // Calculate frames only
        for(Map.Entry<BlockPos, IBlockState> e : getBlocksBetweenPoints(world, new BlockPos(x1, y1, z1), new BlockPos(x2, y2, z2)).entrySet()) {
            BlockPos p = e.getKey();
            IBlockState state = e.getValue();
            if(state == null)
                continue;

            if(((p.getX() == x1 || p.getX() == x2) && (p.getY() == y1 || p.getY() == y2)) ||
                ((p.getX() == x1 || p.getX() == x2) && (p.getZ() == z1 || p.getZ() == z2)) ||
                ((p.getY() == y1 || p.getY() == y2) && (p.getZ() == z1 || p.getZ() == z2))) {
                    maps[0].put(p, state);
            }
            else if(((p.getX() == x1 || p.getX() == x2) || (p.getY() == y1 || p.getY() == y2) || (p.getZ() == z1 || p.getZ() == z2))) {
                maps[1].put(p, state);
            }
            else {
                maps[2].put(p, state);
            }
        }

        return maps;
    }

    public static String intToFancyNumber(int number) {
        return NumberFormat.getIntegerInstance(Locale.ENGLISH).format(number);
    }

    public static void sendTileEntityPacketToPlayers(Packet tileEntityPacket, World world) {
        if(world.isRemote)
            return;

        FMLCommonHandler.instance().getMinecraftServerInstance().getPlayerList().sendPacketToAllPlayersInDimension(tileEntityPacket, world.provider.getDimension());
    }

    public static void sendMessageToClient(EntityPlayer player, String message) {
        if(player == null)
            return;

        player.addChatMessage(new TextComponentString(message));
    }

    public static FakeWorldWrapper getFakeWorld(World world) {
        if(fakeWorldWrapperMap.containsKey(world))
            return fakeWorldWrapperMap.get(world);

        FakeWorldWrapper wrapper = new FakeWorldWrapper(world);
        fakeWorldWrapperMap.put(world, wrapper);
        return wrapper;
    }

    public static void initChunkLoadTicket(World world, ForgeChunkManager.Ticket ticket) {
        chunkloadTicketMap.put(world, ticket);
    }

    public static ForgeChunkManager.Ticket getChunkLoadTicket(World world) {
        if(chunkloadTicketMap.containsKey(world))
            return chunkloadTicketMap.get(world);

        ForgeChunkManager.Ticket chunkloadTicket = ForgeChunkManager.requestTicket(FancyFluidStorage.INSTANCE, world, ForgeChunkManager.Type.NORMAL);
        chunkloadTicketMap.put(world, chunkloadTicket);
        return chunkloadTicket;
    }

    public static double calculateEnergyLoss() {
        return (100 - FancyFluidStorage.INSTANCE.METAPHASED_FLUX_ENERGY_LOSS) / 100d;
    }

}
