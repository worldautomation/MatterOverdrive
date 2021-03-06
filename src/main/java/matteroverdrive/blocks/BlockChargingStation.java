/*
 * This file is part of Matter Overdrive
 * Copyright (c) 2015., Simeon Radivoev, All rights reserved.
 *
 * Matter Overdrive is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Matter Overdrive is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Matter Overdrive.  If not, see <http://www.gnu.org/licenses>.
 */

package matteroverdrive.blocks;

import matteroverdrive.blocks.includes.MOBlockMachine;
import matteroverdrive.handler.ConfigurationHandler;
import matteroverdrive.tile.TileEntityMachineChargingStation;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import javax.annotation.Nonnull;
import java.util.ArrayList;

/**
 * Created by Simeon on 7/8/2015.
 */
public class BlockChargingStation extends MOBlockMachine<TileEntityMachineChargingStation>
{

	public BlockChargingStation(Material material, String name)
	{
		super(material, name);
		setHardness(20.0F);
		this.setResistance(9.0f);
		this.setHarvestLevel("pickaxe", 2);
		lightValue = 10;
		setHasGui(true);
		setHasRotation();
	}

	@Override
	public ArrayList<ItemStack> dismantleBlock(EntityPlayer player, World world, BlockPos pos, boolean returnDrops)
	{
		getTileEntity(world, pos).getBoundingBlocks().forEach(world::setBlockToAir);
		return super.dismantleBlock(player, world, pos, returnDrops);
	}

	//	Multiblock
	@Override
	public boolean canPlaceBlockAt(World world, BlockPos pos)
	{
		return world.getBlockState(pos).getBlock().isReplaceable(world, pos) &&
				world.getBlockState(pos.add(0, 1, 0)).getBlock().isReplaceable(world, pos.add(0, 1, 0)) &&
				world.getBlockState(pos.add(0, 2, 0)).getBlock().isReplaceable(world, pos.add(0, 2, 0));
	}

	@Override
	public IBlockState onBlockPlaced(World world, BlockPos pos, EnumFacing facing, float hitX, float hitY, float hitZ, int meta, EntityLivingBase placer)
	{
		BlockBoundingBox.createBoundingBox(world, pos.add(0, 1, 0), pos, this);
		BlockBoundingBox.createBoundingBox(world, pos.add(0, 2, 0), pos, this);

		return super.onBlockPlaced(world, pos, facing, hitX, hitY, hitZ, meta, placer);
	}

	@Override
	public void breakBlock(World world, BlockPos pos, IBlockState blockState)
	{
		TileEntity te = world.getTileEntity(pos);
		if (te instanceof TileEntityMachineChargingStation)
		{
			TileEntityMachineChargingStation chargingStation = (TileEntityMachineChargingStation)te;
			chargingStation.getBoundingBlocks().forEach(world::setBlockToAir);
		}

		super.breakBlock(world, pos, blockState);
	}

	@Override
	public Class<TileEntityMachineChargingStation> getTileEntityClass()
	{
		return TileEntityMachineChargingStation.class;
	}

	@Nonnull
	@Override
	public TileEntity createTileEntity(@Nonnull World world, @Nonnull IBlockState state)
	{
		return new TileEntityMachineChargingStation();
	}

	@Override
	@Deprecated
	public boolean isOpaqueCube(IBlockState state)
	{
		return false;
	}

	@Override
	public void onConfigChanged(ConfigurationHandler config)
	{
		super.onConfigChanged(config);
		TileEntityMachineChargingStation.BASE_MAX_RANGE = config.getInt("charge station range", ConfigurationHandler.CATEGORY_MACHINES, 8, "The range of the Charge Station");
	}

}
