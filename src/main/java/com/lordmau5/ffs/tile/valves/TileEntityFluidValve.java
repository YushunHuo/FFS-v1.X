package com.lordmau5.ffs.tile.valves;


import com.lordmau5.ffs.tile.abstracts.AbstractTankValve;
import com.lordmau5.ffs.util.GenericUtil;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTankInfo;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandler;

/**
 * Created by Dustin on 28.06.2015.
 */
public class TileEntityFluidValve extends AbstractTankValve implements net.minecraftforge.fluids.IFluidHandler {

    private boolean autoOutput;

    @Override
    public void update() {
        super.update();

        if(getWorld().isRemote)
            return;

        if(!isValid())
            return;

        FluidStack fluidStack = getFluid();
        if(fluidStack == null)
            return;

        Fluid fluid = fluidStack.getFluid();
        if(fluid == null)
            return;

        if(getAutoOutput() || valveHeightPosition == 0) { // Auto outputs at 50mB/t (1B/s) if enabled
            if (getFluidAmount() != 0) {
                float height = (float) getFluidAmount() / (float) getCapacity();
                boolean isNegativeDensity = fluid.getDensity(fluidStack) < 0 ;
                if (GenericUtil.canAutoOutput(height, getTankHeight(), valveHeightPosition, isNegativeDensity)) { // Valves can output until the liquid is at their halfway point.
                    EnumFacing out = getTileFacing().getOpposite();
                    TileEntity tile = getWorld().getTileEntity(new BlockPos(getPos().getX() + out.getFrontOffsetX(), getPos().getY() + out.getFrontOffsetY(), getPos().getZ() + out.getFrontOffsetZ()));
                    if(tile != null) {
                        if(!(tile instanceof TileEntityFluidValve) && !getAutoOutput() && valveHeightPosition == 0) {}
                        else {
                            int maxAmount = 0;
                            if (tile instanceof TileEntityFluidValve)
                                maxAmount = 1000; // When two tanks are connected by valves, allow faster output
                            else if (tile.hasCapability(CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY, out))
                                maxAmount = 50;

                            if (maxAmount != 0) {
                                IFluidHandler handler = tile.getCapability(CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY, out);
                                FluidStack fillStack = fluidStack.copy();
                                fillStack.amount = Math.min(getFluidAmount(), maxAmount);
                                if (handler.fill(fillStack, false) > 0) {
                                    drain(handler.fill(fillStack, true), true);
                                }
                            }
                        }
                    }
                }
            }
        }

        if(fluid == FluidRegistry.WATER) {
            if(getWorld().isRaining()) {
                int rate = (int) Math.floor(getWorld().rainingStrength * 5 * worldObj.getBiomeForCoordsBody(getPos()).getRainfall());
                if (getPos().getY() == getWorld().getPrecipitationHeight(getPos()).getY() - 1) {
                    FluidStack waterStack = fluidStack.copy();
                    waterStack.amount = rate * 10;
                    fill(waterStack, true);
                }
            }
        }
    }

    public boolean getAutoOutput() {
        return autoOutput;
    }

    public void setAutoOutput(boolean autoOutput) {
        this.autoOutput = autoOutput;
        setNeedsUpdate(UpdateType.STATE);
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);

        setAutoOutput(tag.getBoolean("autoOutput"));
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound tag) {
        tag.setBoolean("autoOutput", autoOutput);

        super.writeToNBT(tag);
        return tag;
    }

    @Override
    public boolean hasCapability(Capability<?> capability, EnumFacing facing)
    {
        return capability == CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY || super.hasCapability(capability, facing);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> T getCapability(Capability<T> capability, EnumFacing facing)
    {
        if(capability == CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY) {
            if(getTankConfig() != null)
                return (T) getTankConfig().getFluidTank();
        }

        return super.getCapability(capability, facing);
    }

    // IFluidHandler
    @Override
    public boolean canFill(EnumFacing from, Fluid fluid) {
        if(!canFillIncludingContainers(new FluidStack(fluid, 1000)))
            return false;

        return !getAutoOutput() || valveHeightPosition > getTankHeight() || valveHeightPosition + 0.5f >= getTankHeight() * getFillPercentage();
    }

    @Override
    public boolean canDrain(EnumFacing from, Fluid fluid) {
        return getFluid() != null && getFluid().getFluid() == fluid && getFluidAmount() > 0;
    }

    @Override
    public FluidTankInfo[] getTankInfo(EnumFacing from) {
        if(!isValid())
            return null;

        return getMasterValve() == this ? new FluidTankInfo[]{ getInfo() } : new FluidTankInfo[]{ getMasterValve().getInfo()};
    }

    @Override
    public int fill(EnumFacing from, FluidStack resource, boolean doFill) {
        if(!canFill(from, resource.getFluid()))
            return 0;

        return getMasterValve() == this ? fill(resource, doFill) : getMasterValve().fill(resource, doFill);
    }

    @Override
    public FluidStack drain(EnumFacing from, FluidStack resource, boolean doDrain) {
        if(getMasterValve() == null)
            return null;

        return getMasterValve() == this ? drain(resource.amount, doDrain) : getMasterValve().drain(resource.amount, doDrain);
    }

    @Override
    public FluidStack drain(EnumFacing from, int maxDrain, boolean doDrain) {
        if(getMasterValve() == null)
            return null;

        return getMasterValve() == this ? drain(maxDrain, doDrain) : getMasterValve().drain(maxDrain, doDrain);
    }

}
