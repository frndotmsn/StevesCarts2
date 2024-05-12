package vswe.stevescarts.blocks;

import dev.architectury.platform.Mod;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseRailBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RedStoneWireBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.material.Material;
import org.jetbrains.annotations.NotNull;
import vswe.stevescarts.StevesCarts;
import vswe.stevescarts.api.modules.data.ModuleData;
import vswe.stevescarts.blocks.tileentities.TileEntityActivator;
import vswe.stevescarts.blocks.tileentities.TileEntityManager;
import vswe.stevescarts.blocks.tileentities.TileEntityUpgrade;
import vswe.stevescarts.entities.EntityMinecartModular;
import vswe.stevescarts.init.ModBlocks;
import vswe.stevescarts.upgrades.BaseEffect;
import vswe.stevescarts.upgrades.Disassemble;
import vswe.stevescarts.upgrades.Transposer;

import javax.annotation.Nonnull;

public class BlockRailAdvDetector extends BaseRailBlock
{
    public static final EnumProperty<RailShape> SHAPE = BlockStateProperties.RAIL_SHAPE_STRAIGHT;
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;

    public BlockRailAdvDetector()
    {
        this(Properties.of(Material.DECORATION).noCollission().strength(0.7F).sound(SoundType.METAL));
    }

    private BlockRailAdvDetector(Properties builder)
    {
        super(true, builder);
        this.registerDefaultState(this.stateDefinition.any().setValue(SHAPE, RailShape.NORTH_SOUTH).setValue(WATERLOGGED, Boolean.FALSE).setValue(POWERED, Boolean.FALSE));
    }

    public BlockState getStateForPlacement(BlockPlaceContext pContext) {
        BlockState blockstate = super.getStateForPlacement(pContext);
        return blockstate.setValue(POWERED, Boolean.valueOf(pContext.getLevel().hasNeighborSignal(pContext.getClickedPos())));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder)
    {
        builder.add(SHAPE, WATERLOGGED, POWERED);
    }

    @Override
    public @NotNull Property<RailShape> getShapeProperty()
    {
        return SHAPE;
    }

    public @NotNull BooleanProperty getPoweredProperty() { return POWERED; }

    @Override
    public boolean canMakeSlopes(BlockState state, BlockGetter world, BlockPos pos)
    {
        return false;
    }

    @Override
    public void onMinecartPass(BlockState state, Level world, BlockPos pos, AbstractMinecart entityMinecart) {
        if (world.isClientSide || !(entityMinecart instanceof EntityMinecartModular cart)) {
            return;
        }
        if (!isCartReadyForAction(cart, pos)) {
            return;
        }
        int side = 0;
        for (int i = -1; i <= 1; ++i) {
            for (int j = -1; j <= 1; ++j) {
                if (Math.abs(i) != Math.abs(j)) {
                    BlockPos offset = pos.offset(i, 0, j);
                    Block block = world.getBlockState(offset).getBlock();
                    if (block == ModBlocks.CARGO_MANAGER.get() || block == ModBlocks.LIQUID_MANAGER.get()) {
                        BlockEntity tileentity = world.getBlockEntity(offset);
                        if (tileentity instanceof TileEntityManager manager) {
                            if (manager.getCart() == null) {
                                manager.setCart(cart);
                                manager.setSide(side);
                            }
                        }
                        return;
                    }
                    if (block == ModBlocks.MODULE_TOGGLER.get()) {
                        BlockEntity tileentity = world.getBlockEntity(offset);
                        if (tileentity instanceof TileEntityActivator activator) {
                            boolean isOrange = false;
                            if (cart.temppushX == 0.0 == (cart.temppushZ == 0.0)) {
                                continue;
                            }
                            if (i == 0) {
                                if (j == -1) {
                                    isOrange = (cart.temppushX < 0.0);
                                } else {
                                    isOrange = (cart.temppushX > 0.0);
                                }
                            } else if (j == 0) {
                                if (i == -1) {
                                    isOrange = (cart.temppushZ > 0.0);
                                } else {
                                    isOrange = (cart.temppushZ < 0.0);
                                }
                            }
                            boolean isBlueBerry = false;
                            activator.handleCart(cart, isOrange);
                            cart.releaseCart();
                        }
                        return;
                    }
                    if (block instanceof BlockUpgrade) {
                        BlockEntity tileentity = world.getBlockEntity(offset);
                        TileEntityUpgrade upgrade = (TileEntityUpgrade) tileentity;
                        if (upgrade != null && upgrade.getUpgrade() != null) {
                            for (BaseEffect effect : upgrade.getUpgrade().getEffects()) {
                                if (effect instanceof Transposer) {
                                    Transposer transposer = (Transposer) effect;
                                    if (upgrade.getMaster() == null) {
                                        continue;
                                    }
                                    for (TileEntityUpgrade tile : upgrade.getMaster().getUpgradeTiles()) {
                                        if (tile.getUpgrade() != null) {
                                            for (BaseEffect effect2 : tile.getUpgrade().getEffects()) {
                                                if (effect2 instanceof Disassemble) {
                                                    Disassemble disassembler = (Disassemble) effect2;
                                                    if (tile.getItem(0).isEmpty()) {
                                                        tile.setItem(0, ModuleData.createModularCart(cart));
//                                                        upgrade.getMaster().managerInteract(cart, false);
                                                        for (int p = 0; p < cart.getContainerSize(); ++p) {
                                                            @Nonnull ItemStack item = cart.removeItem(p, 64);
                                                            if (!item.isEmpty()) {
                                                                upgrade.getMaster().puke(item);
                                                            }
                                                        }
                                                        cart.remove(Entity.RemovalReason.DISCARDED);
                                                        return;
                                                    }
                                                    continue;
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    ++side;
                }
            }
        }
        if (state.getValue(POWERED)) {
            cart.releaseCart();
        }
    }

    @Override
    public void neighborChanged(BlockState pState, Level pLevel, BlockPos pPos, Block pBlock, BlockPos pFromPos, boolean pIsMoving)
    {

        if (pLevel.isClientSide)
        {
            return;
        }

        boolean prev = pState.getValue(POWERED);
        boolean next = pLevel.hasNeighborSignal(pPos);
        if (prev != next)
        {
            pLevel.setBlock(pPos, pState.cycle(POWERED), 2);
        }
    }
    private boolean isCartReadyForAction(EntityMinecartModular cart, BlockPos pos)
    {
        return cart.disabledPos != null && cart.disabledPos.equals(pos) && cart.isDisabled();
    }
}
