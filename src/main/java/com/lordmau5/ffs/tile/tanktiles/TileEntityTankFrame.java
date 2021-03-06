package com.lordmau5.ffs.tile.tanktiles;

import com.lordmau5.ffs.tile.abstracts.AbstractTankTile;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.Optional;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by Dustin on 28.06.2015.
 */

@Optional.InterfaceList(value = {
        @Optional.Interface(iface = "framesapi.IMoveCheck", modid = "funkylocomotion")
})
public class TileEntityTankFrame extends AbstractTankTile {

    private IBlockState camoBlockState;

    public int lightValue;

    public void initialize(BlockPos valvePos, IBlockState camoBlockState) {
        setValvePos(valvePos);
        this.camoBlockState = camoBlockState;
    }

    public void setBlockState(IBlockState blockState) {
        this.camoBlockState = blockState;
    }

    private IBlockState getBlockStateForNBT() {
        return camoBlockState;
    }

    public IBakedModel getFakeModel() {
        return Minecraft.getMinecraft().getBlockRendererDispatcher().getModelForState(getBlockState());
    }

    public IBlockState getBlockState() {
        IBlockState state = null;
        if(camoBlockState != null)
            state = camoBlockState.getBlock().getActualState(camoBlockState, getFakeWorld(), getPos());

        return state;
    }

    public IBlockState getExtendedBlockState() {
        IBlockState state = getBlockState();

        if(camoBlockState != null && getWorld().isRemote)
            state = camoBlockState.getBlock().getExtendedState(state, getFakeWorld(), getPos());

        return state;
    }

    public boolean isFrameInvalid() {
        TileEntity tile = getWorld().getTileEntity(getPos());
        return tile == null || !(tile instanceof TileEntityTankFrame) || tile != this;
    }

    public List<EnumFacing> getNeighborBlockOrAir(Block block) {
        List<EnumFacing> dirs = new ArrayList<>();

        if(getMasterValve() == null)
            return dirs;

        BlockPos pos = getPos();
        for(EnumFacing dr : EnumFacing.VALUES) {
            if(block == Blocks.AIR) {
                if (getWorld().isAirBlock(pos.offset(dr)))
                    dirs.add(dr);
            }
            else {
                BlockPos oPos = getMasterValve().getPos().offset(dr);
                IBlockState otherBlock = getWorld().getBlockState(oPos);
                if(block == otherBlock.getBlock() || getWorld().isAirBlock(oPos))
                    dirs.add(dr);
            }
        }
        return dirs;
    }

    public boolean tryBurning() {
        Block block = getBlockState().getBlock();

        List<EnumFacing> air = getNeighborBlockOrAir(Blocks.AIR);
        for(EnumFacing dr : air) {
            if(block.isFlammable(getWorld(), getPos(), dr)) {
                getWorld().setBlockState(getPos(), Blocks.FIRE.getDefaultState());
                return true;
            }
        }
        return false;
    }

    public void breakFrame() {
        if(isFrameInvalid()) {
            getWorld().setBlockToAir(getPos());
            return;
        }

        getWorld().removeTileEntity(getPos());
        if(getBlockState() != null)
            getWorld().setBlockState(getPos(), getBlockState());
        else
            getWorld().setBlockToAir(getPos());
    }

    public void onBreak() {
        if(!getWorld().isRemote && getMasterValve() != null) {
            getMasterValve().breakTank(this);
        }
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);

        if(tag.hasKey("blockName") && tag.hasKey("metadata")) { // TODO: Remove legacy support
            setBlockState(Block.getBlockFromName(tag.getString("blockName")).getStateFromMeta(tag.getInteger("metadata")));
        }

        if(tag.hasKey("blockState")) {
            setBlockState(Block.getStateById(tag.getInteger("blockState")));
        }

        int newLightValue = (getMasterValve() != null ? getMasterValve().getFluidLuminosity() : 0);
        if(newLightValue != lightValue) {
            lightValue = newLightValue;
            setNeedsUpdate(UpdateType.STATE);
        }
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound tag) {
        if(getBlockStateForNBT() != null) {
            tag.setInteger("blockState", Block.getStateId(getBlockStateForNBT()));

//            tag.setString("blockName", getBlockStateForNBT().getBlock().getRegistryName().toString());
//            tag.setInteger("metadata", getBlockStateForNBT().getBlock().getMetaFromState(getBlockStateForNBT()));
        }

        super.writeToNBT(tag);
        return tag;
    }

    public int getLightValue() {
        if(getBlockStateForNBT() == null)
            return 0;

        IBlockState blockState = getBlockStateForNBT();
        if(blockState.isOpaqueCube())
            return 0;

        return lightValue;
    }
}
