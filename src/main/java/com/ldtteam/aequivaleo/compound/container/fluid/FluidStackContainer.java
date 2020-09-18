package com.ldtteam.aequivaleo.compound.container.fluid;

import com.google.gson.*;
import com.ldtteam.aequivaleo.api.compound.container.ICompoundContainer;
import com.ldtteam.aequivaleo.api.compound.container.dummy.Dummy;
import com.ldtteam.aequivaleo.api.compound.container.factory.ICompoundContainerFactory;
import com.ldtteam.aequivaleo.api.util.*;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.JsonToNBT;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.registries.ForgeRegistryEntry;
import org.apache.commons.lang3.Validate;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Type;

public class FluidStackContainer implements ICompoundContainer<FluidStack>
{

    public static final class Factory extends ForgeRegistryEntry<ICompoundContainerFactory<?>> implements ICompoundContainerFactory<FluidStack>
    {

        public Factory()
        {
            setRegistryName(Constants.MOD_ID, "Fluidstack");
        }

        @NotNull
        @Override
        public Class<FluidStack> getContainedType()
        {
            return FluidStack.class;
        }

        @Override
        public ICompoundContainer<FluidStack> create(@NotNull final FluidStack instance, @NotNull final double count)
        {
            final FluidStack stack = instance.copy();
            stack.setAmount(1);
            return new FluidStackContainer(stack, count);
        }

        @Override
        public ICompoundContainer<FluidStack> deserialize(final JsonElement json, final Type typeOfT, final JsonDeserializationContext context) throws JsonParseException
        {
            try
            {
                return new FluidStackContainer(FluidStack.loadFluidStackFromNBT(JsonToNBT.getTagFromJson(json.getAsJsonObject().get("stack").getAsString())),
                  json.getAsJsonObject().get("count").getAsDouble());
            }
            catch (CommandSyntaxException e)
            {
                AequivaleoLogger.getLogger().error(e);
            }

            return null;
        }

        @Override
        public JsonElement serialize(final ICompoundContainer<FluidStack> src, final Type typeOfSrc, final JsonSerializationContext context)
        {
            final JsonObject object = new JsonObject();
            object.addProperty("count", src.getContentsCount());
            object.addProperty("stack", src.getContents().writeToNBT(new CompoundNBT()).toString());
            return object;
        }

        @Override
        public void write(final ICompoundContainer<FluidStack> object, final PacketBuffer buffer)
        {
            buffer.writeFluidStack(object.getContents());
            buffer.writeDouble(object.getContentsCount());
        }

        @Override
        public ICompoundContainer<FluidStack> read(final PacketBuffer buffer)
        {
            return new FluidStackContainer(
              buffer.readFluidStack(),
              buffer.readDouble()
            );
        }
    }

    private final FluidStack stack;
    private final double     count;

    private final int hashCode;

    public FluidStackContainer(final FluidStack stack, final double count)
    {
        this.stack = stack.copy();
        this.stack.setAmount(1);

        this.count = count;

        if (stack.isEmpty())
        {
            this.hashCode = 0;
            return;
        }

        this.hashCode = stack.writeToNBT(new CompoundNBT()).hashCode();
    }

    /**
     * The contents of this container. Set to the 1 unit of the content type {@link FluidStack}
     *
     * @return The contents.
     */
    @Override
    public FluidStack getContents()
    {
        return stack;
    }

    /**
     * The amount of {@link FluidStack}s contained in this wrapper.
     *
     * @return The amount.
     */
    @Override
    public Double getContentsCount()
    {
        return count;
    }

    @Override
    public int compareTo(@NotNull final ICompoundContainer<?> o)
    {
        //Dummies are after us. :D
        if (o instanceof Dummy)
            return -1;

        final Object contents = Validate.notNull(o.getContents());
        if (!(contents instanceof FluidStack))
        {
            return FluidStack.class.getName().compareTo(contents.getClass().getName());
        }

        final FluidStack otherStack = (FluidStack) contents;
        return Comparators.FLUID_STACK_COMPARATOR.compare(stack, otherStack);
    }

    @Override
    public boolean equals(final Object o)
    {
        if (this == o)
        {
            return true;
        }
        if (!(o instanceof FluidStackContainer))
        {
            return false;
        }

        final FluidStackContainer that = (FluidStackContainer) o;

        if (Double.compare(that.count, count) != 0)
        {
            return false;
        }
        return FluidStackUtils.compareFluidStacksIgnoreStackSize(stack, that.stack);
    }

    @Override
    public int hashCode()
    {
        return hashCode;
    }

    @Override
    public String toString()
    {
        return String.format("%s x FluidStack: %s", count, stack);
    }
}
