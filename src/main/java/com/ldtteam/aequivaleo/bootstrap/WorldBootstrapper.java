package com.ldtteam.aequivaleo.bootstrap;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.ldtteam.aequivaleo.Aequivaleo;
import com.ldtteam.aequivaleo.analyzer.EquivalencyRecipeRegistry;
import com.ldtteam.aequivaleo.analyzer.jgrapht.ContainerWrapperGraphNode;
import com.ldtteam.aequivaleo.api.compound.container.ICompoundContainer;
import com.ldtteam.aequivaleo.api.event.OnWorldDataReloadedEvent;
import com.ldtteam.aequivaleo.api.recipe.equivalency.IEquivalencyRecipe;
import com.ldtteam.aequivaleo.api.util.ItemStackUtils;
import com.ldtteam.aequivaleo.compound.container.registry.CompoundContainerFactoryManager;
import com.ldtteam.aequivaleo.compound.information.contribution.ContributionInformationProviderRegistry;
import com.ldtteam.aequivaleo.compound.information.locked.LockedCompoundInformationRegistry;
import com.ldtteam.aequivaleo.compound.information.validity.ValidCompoundTypeInformationProviderRegistry;
import com.ldtteam.aequivaleo.gameobject.equivalent.GameObjectEquivalencyHandlerRegistry;
import com.ldtteam.aequivaleo.gameobject.loottable.LootTableAnalyserRegistry;
import com.ldtteam.aequivaleo.recipe.equivalency.*;
import com.ldtteam.aequivaleo.tags.TagEquivalencyRegistry;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.*;
import net.minecraft.tags.ITag;
import net.minecraft.util.NonNullList;
import net.minecraft.world.World;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.registries.ForgeRegistries;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.function.BiFunction;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

public final class WorldBootstrapper
{

    private static final Logger LOGGER = LogManager.getLogger();

    private WorldBootstrapper()
    {
        throw new IllegalStateException("Tried to initialize: WorldBootstrapper but this is a Utility class.");
    }

    public static void onWorldReload(final ServerWorld world)
    {
        resetDataForWorld(world);

        doBootstrapTagInformation(world);
        doBootstrapDefaultCraftingRecipes(world);
        doBootstrapBlockDropRelatedEquivalencies(world);
        doBootstrapItemStackItemEquivalencies(world);

        doFireDataLoadEvent(world);
    }

    private static void resetDataForWorld(final World world)
    {
        LockedCompoundInformationRegistry.getInstance(world.func_234923_W_()).reset();
        ValidCompoundTypeInformationProviderRegistry.getInstance(world.func_234923_W_()).reset();
        EquivalencyRecipeRegistry.getInstance(world.func_234923_W_()).reset();
        ContributionInformationProviderRegistry.getInstance(world.func_234923_W_()).reset();
    }

    private static void doBootstrapTagInformation(final World world)
    {
        for (ITag.INamedTag<?> tag : TagEquivalencyRegistry.getInstance().get())
        {
            doBootstrapSingleTagInformation(world, tag);
        }
    }

    private static <T> void doBootstrapSingleTagInformation(final World world, final ITag.INamedTag<T> tag) {
        final Collection<ICompoundContainer<?>> elementsOfTag = new ArrayList<>();
        for (T stack : tag.getAllElements())
        {
            ICompoundContainer<T> tiCompoundContainer = CompoundContainerFactoryManager.getInstance().wrapInContainer(stack, 1d);
            elementsOfTag.add(tiCompoundContainer);
        }

        for (ICompoundContainer<?> inputStack : elementsOfTag)
        {
            for (ICompoundContainer<?> outputStack : elementsOfTag)
            {
                if (!GameObjectEquivalencyHandlerRegistry.getInstance().areGameObjectsEquivalent(inputStack, outputStack))
                {
                    EquivalencyRecipeRegistry.getInstance(world.func_234923_W_())
                      .register(
                        new TagEquivalencyRecipe<>(
                          tag,
                          Sets.newHashSet(inputStack),
                          Sets.newHashSet(outputStack)
                        ));
                }
            }
        }
    }

    private static void doBootstrapDefaultCraftingRecipes(@NotNull final World world)
    {
        final List<ICraftingRecipe> craftingRecipes = world.getRecipeManager().func_241447_a_(IRecipeType.CRAFTING);
        craftingRecipes
          .parallelStream()
          .forEach(recipe -> processCraftingRecipe(world, recipe));

        final List<FurnaceRecipe> smeltingRecipe = world.getRecipeManager().func_241447_a_(IRecipeType.SMELTING);
        smeltingRecipe
          .parallelStream()
          .forEach(recipe -> processSmeltingRecipe(world, recipe));
    }

    private static void processSmeltingRecipe(@NotNull final World world, IRecipe<?> iRecipe)
    {
        processIRecipe(world, iRecipe, (inputs, outputs) -> new FurnaceEquivalencyRecipe(iRecipe.getId(), inputs, outputs));
    }

    private static void processCraftingRecipe(@NotNull final World world, IRecipe<?> iRecipe)
    {
        processIRecipe(world, iRecipe, (inputs, outputs) -> new VanillaCraftingEquivalencyRecipe(iRecipe.getId(), inputs, outputs));
    }

    private static void processIRecipe(
      @NotNull final World world,
      IRecipe<?> iRecipe,
      BiFunction<Set<ICompoundContainer<?>>, Set<ICompoundContainer<?>>, IEquivalencyRecipe> recipeProducer
    )
    {
        if (iRecipe.getRecipeOutput().isEmpty())
        {
            return;
        }

        final NonNullList<Ingredient> ingredients = iRecipe.getIngredients();
        final List<Ingredient> withOutEmptyIngredients = new ArrayList<>();
        for (Ingredient ingredient1 : ingredients)
        {
            if (!ingredient1.test(ItemStack.EMPTY) && ingredient1.getMatchingStacks().length > 0
                  && !ItemStackUtils.isEmpty(ingredient1.getMatchingStacks()[0]))
            {
                withOutEmptyIngredients.add(ingredient1);
            }
        }

        final List<ItemStack> inputStacks = new ArrayList<>();
        for (Ingredient ingredient : withOutEmptyIngredients)
        {
            ItemStack itemStack = ingredient.getMatchingStacks()[0];
            if (!itemStack.isEmpty())
            {
                inputStacks.add(itemStack);
            }
        }

        final Set<ICompoundContainer<?>> wrappedInput = new HashSet<>();
        Map<ICompoundContainer<ItemStack>, Double> map = new HashMap<>();
        for (ItemStack stack : inputStacks)
        {
            ICompoundContainer<ItemStack> wrapper = CompoundContainerFactoryManager.getInstance()
                                                      .wrapInContainer(stack, stack.getCount());
            map.merge(wrapper, wrapper.getContentsCount(), Double::sum);
        }
        for (Map.Entry<ICompoundContainer<ItemStack>, Double> iCompoundContainerWrapperDoubleEntry : map
                                                                                                       .entrySet())
        {
            ICompoundContainer<ItemStack> itemStackICompoundContainer = CompoundContainerFactoryManager.getInstance()
                                                                          .wrapInContainer(iCompoundContainerWrapperDoubleEntry.getKey()
                                                                                             .getContents(),
                                                                            iCompoundContainerWrapperDoubleEntry.getValue());
            wrappedInput.add(itemStackICompoundContainer);
        }

        final ICompoundContainer<?> outputWrapped = CompoundContainerFactoryManager.getInstance().wrapInContainer(iRecipe.getRecipeOutput(),
          iRecipe.getRecipeOutput().getCount());


        EquivalencyRecipeRegistry.getInstance(world.func_234923_W_()).register(recipeProducer.apply(wrappedInput, Sets.newHashSet(outputWrapped)));
    }

    private static void doBootstrapBlockDropRelatedEquivalencies(
      @NotNull final ServerWorld world
    )
    {
        for (Block block : ForgeRegistries.BLOCKS.getValues())
        {
            final ICompoundContainer<?> blockContainer = CompoundContainerFactoryManager.getInstance().wrapInContainer(block, 1);
            final Set<ICompoundContainer<?>> initialContainers = Sets.newHashSet();
            boolean hasTheSameDrop = true;

            for (BlockState blockState : block.getStateContainer().getValidStates())
            {
                final ICompoundContainer<?> compoundContainer = CompoundContainerFactoryManager.getInstance().wrapInContainer(blockState, 1);
                try
                {
                    final Set<ICompoundContainer<?>> drops = LootTableAnalyserRegistry.getInstance().calculateOutputs(blockState, world);
                    if (initialContainers.isEmpty()) {
                        initialContainers.addAll(drops);
                    } else if (hasTheSameDrop && !initialContainers.equals(drops)) {
                        hasTheSameDrop = false;
                    }

                    final DropsEquivalencyRecipe inputRecipe = new DropsEquivalencyRecipe(compoundContainer, true, drops);
                    final DropsEquivalencyRecipe outputRecipe = new DropsEquivalencyRecipe(compoundContainer, false, drops);
                    EquivalencyRecipeRegistry.getInstance(world.func_234923_W_()).register(inputRecipe).register(outputRecipe);
                }
                catch (Exception ex)
                {
                    if (Aequivaleo.getInstance().getConfiguration().getCommon().writeExceptionOnBlockDropFailure.get())
                    {
                        LOGGER.warn(String.format(
                          "Could not determine blockdrops for: %s it was not possible to calculate the drops. Potentially a TileEntity or proper world is required.",
                          block.getRegistryName()), ex);
                    }
                    else
                    {
                        LOGGER.warn(String.format(
                          "Could not determine blockdrops for: %s it was not possible to calculate the drops. Potentially a TileEntity or proper world is required. Turn the config value for block drop exceptions on to see more details.",
                          block.getRegistryName()));
                    }
                }
            }

            if (hasTheSameDrop) {
                for (final BlockState blockState : block.getStateContainer().getValidStates())
                {
                    final ICompoundContainer<?> compoundContainer = CompoundContainerFactoryManager.getInstance().wrapInContainer(blockState, 1);
                    EquivalencyRecipeRegistry.getInstance(world.func_234923_W_())
                      .register(
                        new InstancedEquivalency(true, blockContainer, compoundContainer)
                      )
                      .register(
                        new InstancedEquivalency(true, compoundContainer, blockContainer)
                      );
                }
            }
        }
    }

    private static void doBootstrapItemStackItemEquivalencies(
      @NotNull final ServerWorld world
    ) {
        StreamSupport.stream(ForgeRegistries.ITEMS.spliterator(), true).forEach(item -> {
            final NonNullList<ItemStack> group = NonNullList.create();
            if (item.getGroup() == null)
                return;

            item.fillItemGroup(Objects.requireNonNull(item.getGroup()), group);

            if (group.size() == 1)
            {
                final ICompoundContainer<?> itemContainer = CompoundContainerFactoryManager.getInstance().wrapInContainer(item, 1);
                final ICompoundContainer<?> itemStackContainer = CompoundContainerFactoryManager.getInstance().wrapInContainer(group.get(0), group.get(0).getCount());

                EquivalencyRecipeRegistry.getInstance(world.func_234923_W_())
                  .register(new InstancedEquivalency(
                    false, itemContainer, itemStackContainer
                  ))
                  .register(new InstancedEquivalency(
                    false, itemStackContainer, itemContainer
                  ));
            }
        });
    }

    private static void doFireDataLoadEvent(
      @NotNull final ServerWorld world) {
        LOGGER.info(String.format("Firing data loaded event for world: %s", world.func_234923_W_().func_240901_a_()));
        MinecraftForge.EVENT_BUS.post(new OnWorldDataReloadedEvent(world));
    }
}
