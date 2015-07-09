package matteroverdrive.tile;


import cofh.api.energy.IEnergyConnection;
import cofh.api.energy.IEnergyReceiver;
import cofh.lib.util.TimeTracker;
import cofh.lib.util.helpers.BlockHelper;
import cofh.lib.util.helpers.EnergyHelper;
import cofh.lib.util.position.BlockPosition;
import cofh.lib.util.position.IRotateableTile;
import cpw.mods.fml.common.Optional;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import dan200.computercraft.api.lua.ILuaContext;
import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.peripheral.IComputerAccess;
import dan200.computercraft.api.peripheral.IPeripheral;
import li.cil.oc.api.machine.Arguments;
import li.cil.oc.api.machine.Context;
import li.cil.oc.api.network.ManagedPeripheral;
import li.cil.oc.api.network.SimpleComponent;
import matteroverdrive.Reference;
import matteroverdrive.api.inventory.UpgradeTypes;
import matteroverdrive.api.matter.IMatterConnection;
import matteroverdrive.init.MatterOverdriveBlocks;
import net.minecraft.block.Block;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MathHelper;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import java.util.Arrays;

/**
 * Created by Simeon on 5/14/2015.
 */
@Optional.InterfaceList({
		@Optional.Interface(modid = "ComputerCraft", iface = "dan200.computercraft.api.peripheral.IPeripheral"),
		@Optional.Interface(modid = "OpenComputers", iface = "li.cil.oc.api.network.SimpleComponent"),
		@Optional.Interface(modid = "OpenComputers", iface = "li.cil.oc.api.network.ManagedPeripheral")
})
public class TileEntityMachineFusionReactorController extends MOTileEntityMachineMatter implements IRotateableTile, IMatterConnection, IPeripheral, SimpleComponent, ManagedPeripheral
{
    public static int STRUCTURE_CHECK_DELAY = 40;
    public static final int[] positions = new int[]{0,5,1,0,2,0,3,1,4,2,5,3,5,4,5,5,5,6,5,7,4,8,3,9,2,10,1,10,0,10,-1,10,-2,10,-3,9,-4,8,-5,7,-5,6,-5,5,-5,4,-5,3,-4,2,-3,1,-2,0,-1,0};
    public static final int[] blocks = new int[]{255,2,0,0,0,0,1,1,1,0,0,0,0,1,1,1,0,0,0,0,1,1,1,0,0,0,0,2};
    public static final int positionsCount = positions.length / 2;
    public static int MAX_GRAVITATIONAL_ANOMALY_DISTANCE = 3;
    public static int ENERGY_STORAGE = 100000000;
    public static int MATTER_STORAGE = 2048;
    public static int ENERGY_PER_TICK = 2048;
    public static float MATTER_DRAIN_PER_TICK = 1f / 80f;

    private boolean validStructure = false;
    private String monitorInfo = "INVALID STRUCTURE";
    private float energyEfficiency;
    private int energyPerTick;
    private TimeTracker structureCheckTimer;
    private BlockPosition anomalyPosition;
    private float matterPerTick;
    private float matterDrain;


    public TileEntityMachineFusionReactorController() {
        super(4);

        structureCheckTimer = new TimeTracker();
        energyStorage.setCapacity(ENERGY_STORAGE);
        energyStorage.setMaxTransfer(ENERGY_STORAGE);

        matterStorage.setCapacity(MATTER_STORAGE);
        matterStorage.setMaxExtract(MATTER_STORAGE);
        matterStorage.setMaxReceive(MATTER_STORAGE);
        redstoneMode = Reference.MODE_REDSTONE_LOW;
    }

    @Override
    public String getSound() {
        return null;
    }

    @Override
    public void writeCustomNBT(NBTTagCompound nbt)
    {
        super.writeCustomNBT(nbt);
        nbt.setBoolean("ValidStructure", validStructure);
        nbt.setString("MonitorInfo",monitorInfo);
        nbt.setFloat("EnergyEfficiency",energyEfficiency);
        nbt.setFloat("MatterPerTick",matterPerTick);
        nbt.setInteger("EnergyPerTick", energyPerTick);
    }

    @Override
    public void readCustomNBT(NBTTagCompound nbt)
    {
        super.readCustomNBT(nbt);
        validStructure = nbt.getBoolean("ValidStructure");
        monitorInfo = nbt.getString("MonitorInfo");
        energyEfficiency = nbt.getFloat("EnergyEfficiency");
        matterPerTick = nbt.getFloat("MatterPerTick");
        energyPerTick = nbt.getInteger("EnergyPerTick");
    }

    @Override
    protected void onAwake(Side side) {

    }

    @Override
    public void updateEntity()
    {
        super.updateEntity();
        if (!worldObj.isRemote) {
            //System.out.println("Fusion Reactor Update in chunk that is loaded:" + worldObj.getChunkFromBlockCoords(xCoord,zCoord).isChunkLoaded);
            manageStructure();
            manageEnergyGeneration();
            manageEnergyExtract();
        }
    }

    @Override
    public boolean hasSound() {
        return false;
    }

    @Override
    public boolean isActive() {
        return isValidStructure() &&
                isGeneratingPower();
    }

    @Override
    public float soundVolume() {
        return 0;
    }

    public Vec3 getPosition(int i,int meta)
    {
        if (i < positionsCount)
        {
            ForgeDirection back = ForgeDirection.getOrientation(BlockHelper.getOppositeSide(meta));
            Vec3 pos = Vec3.createVectorHelper(TileEntityMachineFusionReactorController.positions[i * 2], 0, TileEntityMachineFusionReactorController.positions[(i * 2) + 1]);

            if (back == ForgeDirection.NORTH)
            {
                pos.rotateAroundY((float)Math.PI);
            }
            else if (back == ForgeDirection.WEST)
            {
                pos.rotateAroundY((float)(Math.PI + Math.PI / 2));
            }
            else if (back == ForgeDirection.EAST)
            {
                pos.rotateAroundY((float)(Math.PI / 2));
            }
            else if (back == ForgeDirection.UP)
            {
                pos.rotateAroundX((float)(Math.PI / 2));
            }
            else if (back == ForgeDirection.DOWN)
            {
                pos.rotateAroundX((float) (Math.PI + Math.PI / 2));

            }

            return pos;
        }
        return null;
    }

    public void manageStructure()
    {
        if (structureCheckTimer.hasDelayPassed(worldObj,STRUCTURE_CHECK_DELAY)) {
            int meta = worldObj.getBlockMetadata(xCoord, yCoord, zCoord);
            int anomalyDistance = MAX_GRAVITATIONAL_ANOMALY_DISTANCE+1;
            boolean validStructure = true;
            String info = this.monitorInfo;
            float energyEfficiency  = this.energyEfficiency;
            float matterPerTick = this.matterPerTick;

            for (int i = 0; i < positionsCount; i++) {
                Vec3 offset = getPosition(i, meta);
                BlockPosition position = new BlockPosition(xCoord + (int)Math.round(offset.xCoord),yCoord + (int)Math.round(offset.yCoord),zCoord + (int)Math.round(offset.zCoord));

                if (blocks[i] == 255)
                {
                    BlockPosition anomalyOffset = checkForGravitationalAnomaly(position, ForgeDirection.getOrientation(BlockHelper.getAboveSide(meta)));

                    if (anomalyOffset != null)
                    {
                        anomalyDistance = (int)Math.sqrt((anomalyOffset.x * anomalyOffset.x) + (anomalyOffset.y * anomalyOffset.y) + (anomalyOffset.z * anomalyOffset.z));
                        if (anomalyDistance > MAX_GRAVITATIONAL_ANOMALY_DISTANCE)
                        {
                            validStructure = false;
                            info = "GRAVITATIONAL ANOMALY TOO FAR";
                            break;
                        }
                        anomalyPosition = new BlockPosition((int)offset.xCoord + anomalyOffset.x,(int)offset.yCoord + anomalyOffset.y,(int)offset.zCoord + anomalyOffset.z);
                    }else
                    {
                        validStructure = false;
                        info = "NO GRAVITATIONAL ANOMALY";
                        anomalyPosition = null;
                        break;
                    }

                    energyEfficiency = 1f - ((float)anomalyDistance / (float)(MAX_GRAVITATIONAL_ANOMALY_DISTANCE+1));
                    energyPerTick = (int)Math.round(ENERGY_PER_TICK * getEnergyEfficiency() * getGravitationalAnomalyEnergyMultiply());
                    matterPerTick = MATTER_DRAIN_PER_TICK * (float)getGravitationalAnomalyEnergyMultiply();
                }
                else {
                    if (position.getBlock(worldObj) == Blocks.air) {
                        validStructure = false;
                        info = "INVALID STRUCTURE";
                        break;
                    } else if (position.getBlock(worldObj) == MatterOverdriveBlocks.machine_hull) {
                        if (blocks[i] == 1) {
                            validStructure = false;
                            info = "NEED MORE COILS";
                            break;
                        }
                    } else if (position.getBlock(worldObj) == MatterOverdriveBlocks.fusion_reactor_coil) {
                        if (blocks[i] == 0) {
                            validStructure = false;
                            info = "INVALID MATERIALS";
                            break;
                        }
                    } else if (position.getBlock(worldObj) == MatterOverdriveBlocks.decomposer)
                    {
                        if (blocks[i] != 2)
                        {
                            validStructure = false;
                            info = "INVALID MATERIALS";
                            break;
                        }
                    }
                    else {
                        validStructure = false;
                        info = "INVALID MATERIALS";
                        break;
                    }
                }
            }

            if (validStructure)
            {
                info = "VALID POWERx" + Math.round((1f - ((float)anomalyDistance / (float)(MAX_GRAVITATIONAL_ANOMALY_DISTANCE+1))) * 100) + "%";
            }else
            {
                energyEfficiency = 0;
            }

            if (this.validStructure != validStructure || this.monitorInfo != info || this.energyEfficiency != energyEfficiency || this.matterPerTick != matterPerTick) {
                this.validStructure = validStructure;
                this.monitorInfo = info;
                this.energyEfficiency = energyEfficiency;
                this.matterPerTick = matterPerTick;
                ForceSync();
            }
        }
    }

    private void manageEnergyGeneration()
    {
        if (isActive())
        {
            int energyPerTick = getEnergyPerTick();
            int energyRecived = energyStorage.receiveEnergy(energyPerTick,false);
            if (energyRecived > 0)
            {
                matterDrain += getMatterDrainPerTick() * ((float)energyRecived / (float)energyPerTick);
                if (MathHelper.floor_float(matterDrain) >= 1)
                {
                    matterStorage.extractMatter(MathHelper.floor_float(matterDrain),false);
                    matterDrain -= MathHelper.floor_float(matterDrain);
                }
            }
        }
    }

    private void manageEnergyExtract()
    {
        if (energyStorage.getEnergyStored() > 0)
        {
            TileEntity entity;
            int energy;
            int startDir = random.nextInt(6);

            for (int i = 0;i < 6;i++)
            {
                energy = Math.min(energyStorage.getEnergyStored(),energyStorage.getMaxExtract());
                ForgeDirection dir = ForgeDirection.getOrientation((i + startDir) % 6);
                entity = worldObj.getTileEntity(xCoord + dir.offsetX, yCoord + dir.offsetY, zCoord + dir.offsetZ);

                if (entity instanceof IEnergyConnection)
                {
                    if (((IEnergyConnection) entity).canConnectEnergy(dir.getOpposite()));
                }

                if (entity instanceof IEnergyReceiver)
                {
                    energyStorage.extractEnergy(((IEnergyReceiver) entity).receiveEnergy(dir.getOpposite(),energy,false),false);
                }
            }
        }
    }

    @Override
    public boolean isCharging()
    {
        return this.inventory.getStackInSlot(energySlotID) != null && EnergyHelper.isEnergyContainerItem(this.inventory.getStackInSlot(energySlotID));
    }

    @Override
    protected void manageCharging()
    {
        if(isCharging())
        {
            if(!this.worldObj.isRemote)
            {
                int maxExtracted = Math.min(energyStorage.getMaxExtract(),energyStorage.getEnergyStored());
                int extracted = EnergyHelper.insertEnergyIntoContainer(this.inventory.getStackInSlot(energySlotID),maxExtracted,false);
                energyStorage.extractEnergy(extracted,false);
            }
        }
    }

    public int getEnergyPerTick()
    {
        return energyPerTick;
    }

    public void setEnergyPerTick(int energy)
    {
        energyPerTick = energy;
    }

    public double getGravitationalAnomalyEnergyMultiply()
    {
        if (anomalyPosition != null)
        {
            TileEntity entity = worldObj.getTileEntity(xCoord + anomalyPosition.x, yCoord + anomalyPosition.y, zCoord + anomalyPosition.z);
            if (entity instanceof TileEntityGravitationalAnomaly)
            {
                return ((TileEntityGravitationalAnomaly) entity).getRealMassUnsuppressed();
            }
        }
        return 0;
    }

    public float getMatterDrainPerTick()
    {
        return matterPerTick;
    }

    public boolean isGeneratingPower()
    {
        if (getEnergyEfficiency() > 0 && getEnergyStorage().getEnergyStored() < getEnergyStorage().getMaxEnergyStored() && getMatterStorage().getMatterStored() >= getMatterDrainPerTick())
        {
            return true;
        }
        return false;
    }

    public float getEnergyEfficiency()
    {
        return energyEfficiency;
    }

    private BlockPosition checkForGravitationalAnomaly(BlockPosition position,ForgeDirection up)
    {
        int offsetX,offsetY,offsetZ;

        for (int i = -MAX_GRAVITATIONAL_ANOMALY_DISTANCE; i < MAX_GRAVITATIONAL_ANOMALY_DISTANCE+1;i++)
        {
            offsetX = up.offsetX * i;
            offsetY = up.offsetY * i;
            offsetZ = up.offsetZ * i;
            Block block = worldObj.getBlock(position.x + offsetX, position.y + offsetY, position.z + offsetZ);
            if (block != null && block == MatterOverdriveBlocks.gravitational_anomaly)
            {
                return new BlockPosition(offsetX,offsetY,offsetZ);
            }
        }

        return null;
    }

    public boolean shouldRenderInPass(int pass)
    {
        return pass == 1;
    }

    @SideOnly(Side.CLIENT)
    public AxisAlignedBB getRenderBoundingBox()
    {
        ForgeDirection backSide = ForgeDirection.getOrientation(BlockHelper.getOppositeSide(worldObj.getBlockMetadata(xCoord,yCoord,zCoord)));


        AxisAlignedBB alignedBB = AxisAlignedBB.getBoundingBox(xCoord,yCoord,zCoord,xCoord + backSide.offsetX * 10,yCoord + backSide.offsetY * 10,zCoord + backSide.offsetZ * 10);
        return alignedBB;
    }

    public boolean isValidStructure()
    {
        return validStructure;
    }

    public String getMonitorInfo()
    {
        return monitorInfo;
    }

    @Override
    public boolean canRotate() {
        return true;
    }

    @Override
    public boolean canRotate(ForgeDirection axis) {
        return true;
    }

    @Override
    public void rotate(ForgeDirection axis)
    {
        System.out.println("Rotate");
    }

    @Override
    public void rotateDirectlyTo(int facing)
    {
        worldObj.setBlockMetadataWithNotify(xCoord,yCoord,zCoord,facing,3);
    }

    @Override
    public ForgeDirection getDirectionFacing() {
        return ForgeDirection.getOrientation(worldObj.getBlockMetadata(xCoord,yCoord,zCoord));
    }

    @Override
    public boolean canConnectFrom(ForgeDirection dir)
    {
        int meta = worldObj.getBlockMetadata(xCoord,yCoord,zCoord);
        if (meta != dir.ordinal())
            return true;
        return false;
    }

    @Override
    public boolean isAffectedByUpgrade(UpgradeTypes type)
    {
        return type == UpgradeTypes.PowerStorage || type == UpgradeTypes.Range || type == UpgradeTypes.Speed;
    }

    @Override
    public void onAdded(World world, int x, int y, int z) {

    }

    @Override
    public void onPlaced(World world, EntityLivingBase entityLiving) {

    }

    @Override
    public void onDestroyed() {

    }

//	All Computers
	private String[] methodNames = new String[] {
			"getStatus",
			"isValid",
			"getEnergyGenerated",
			"getMatterUsed",
			"getEnergyStored",
			"getMatterStored"
	};

	private Object[] callMethod(int method, Object[] args) {
		switch (method) {
			case 0:
				return computerGetStatus(args);
			case 1:
				return computerIsValid(args);
			case 2:
				return computerGetEnergyGenerated(args);
			case 3:
				return computerGetMatterUsed(args);
			case 4:
				return computerGetEnergyStored(args);
			case 5:
				return computerGetMatterStored(args);
			default: throw new IllegalArgumentException("Invalid method id");
		}
	}

//	Computer Methods
	private Object[] computerGetStatus(Object[] args) {
		return new Object[]{monitorInfo};
	}

	private Object[] computerIsValid(Object[] args) {
		return new Object[]{validStructure};
	}

	private Object[] computerGetEnergyGenerated(Object[] args) {
		return new Object[]{energyPerTick};
	}

	private Object[] computerGetMatterUsed(Object[] args) {
		return new Object[]{matterPerTick};
	}

	private Object[] computerGetEnergyStored(Object[] args) {
		return new Object[]{energyStorage.getEnergyStored()};
	}

	private Object[] computerGetMatterStored(Object[] args) {
		return new Object[]{matterStorage.getMatterStored()};
	}

//	ComputerCraft
	@Override
	@Optional.Method(modid = "ComputerCraft")
	public String getType() {
		return "mo_fusion_reactor_controller";
	}

	@Override
	@Optional.Method(modid = "ComputerCraft")
	public String[] getMethodNames() {
		return methodNames;
	}

	@Override
	@Optional.Method(modid = "ComputerCraft")
	public Object[] callMethod(IComputerAccess computer, ILuaContext context, int method, Object[] arguments) throws LuaException, InterruptedException {
		try {
			return callMethod(method, arguments);
		} catch (Exception e) {
			throw new LuaException(e.getMessage());
		}
	}

	@Override
	@Optional.Method(modid = "ComputerCraft")
	public void attach(IComputerAccess computer) {}

	@Override
	@Optional.Method(modid = "ComputerCraft")
	public void detach(IComputerAccess computer) {}

	@Override
	@Optional.Method(modid = "ComputerCraft")
	public boolean equals(IPeripheral other) {
		return false;
	}

//	OpenComputers
	@Override
	@Optional.Method(modid = "OpenComputers")
	public String getComponentName() {
		return "mo_fusion_reactor_controller";
	}

	@Override
	@Optional.Method(modid = "OpenComputers")
	public String[] methods() {
		return methodNames;
	}

	@Override
	@Optional.Method(modid = "OpenComputers")
	public Object[] invoke(String method, Context context, Arguments args) throws Exception {
		int methodId = Arrays.asList(methodNames).indexOf(method);

		if (methodId == -1) {
			throw new RuntimeException("The method " + method + " does not exist");
		}

		return callMethod(methodId, args.toArray());
	}
}
