package com.ldtteam.aequivaleo.analyzer.jgrapht;

import com.google.common.collect.Sets;
import com.ldtteam.aequivaleo.api.compound.CompoundInstance;
import com.ldtteam.aequivaleo.api.compound.container.ICompoundContainer;
import org.jetbrains.annotations.NotNull;

import java.util.Set;
import java.util.TreeSet;

public class ContainerWrapperGraphNode implements IAnalysisGraphNode
{
    @NotNull
    private final ICompoundContainer<?> wrapper;

    @NotNull
    private final Set<CompoundInstance> compoundInstances = new TreeSet<>();

    public ContainerWrapperGraphNode(@NotNull final ICompoundContainer<?> wrapper) {this.wrapper = wrapper;}

    @NotNull
    public ICompoundContainer<?> getWrapper()
    {
        return wrapper;
    }

    @NotNull
    public Set<CompoundInstance> getCompoundInstances()
    {
        return compoundInstances;
    }

    @Override
    public boolean equals(final Object o)
    {
        if (this == o)
        {
            return true;
        }
        if (!(o instanceof ContainerWrapperGraphNode))
        {
            return false;
        }

        final ContainerWrapperGraphNode that = (ContainerWrapperGraphNode) o;

        return getWrapper().equals(that.getWrapper());
    }

    @Override
    public int hashCode()
    {
        return getWrapper().hashCode();
    }

    @Override
    public String toString()
    {
        return "ContainerWrapperGraphNode{" +
                 "wrapper=" + wrapper +
                 '}';
    }



}
