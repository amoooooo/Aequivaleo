package com.ldtteam.aequivaleo.analyzer.jgrapht.node;

import com.ldtteam.aequivaleo.analyzer.StatCollector;
import com.ldtteam.aequivaleo.analyzer.jgrapht.aequivaleo.*;
import com.ldtteam.aequivaleo.api.compound.CompoundInstance;
import com.ldtteam.aequivaleo.api.compound.type.ICompoundType;
import com.ldtteam.aequivaleo.api.recipe.equivalency.IEquivalencyRecipe;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class RecipeNode extends AbstractNode implements IRecipeNode
{
    @NotNull
    private final IEquivalencyRecipe recipe;
    private final int hashCode;

    public RecipeNode(@NotNull final IEquivalencyRecipe recipe) {
        this.recipe = recipe;
        this.hashCode = recipe.hashCode();
    }

    @NotNull
    public IEquivalencyRecipe getRecipe()
    {
        return recipe;
    }

    @Override
    public boolean equals(final Object o)
    {
        if (this == o)
        {
            return true;
        }
        if (!(o instanceof RecipeNode))
        {
            return false;
        }

        final RecipeNode that = (RecipeNode) o;

        return getRecipe().equals(that.getRecipe());
    }

    @Override
    public int hashCode()
    {
        return hashCode;
    }

    @Override
    public void determineResult(final IGraph graph)
    {
        final Set<IRecipeResidueNode> requiredKnownOutputs = new HashSet<>();
        for (IEdge iEdge : graph.incomingEdgesOf(this))
        {
            INode edgeSource = graph.getEdgeSource(iEdge);
            if (edgeSource instanceof IRecipeResidueNode)
            {
                IRecipeResidueNode iRecipeResidueNode = (IRecipeResidueNode) edgeSource;
                requiredKnownOutputs.add(iRecipeResidueNode);
            }
        }
        final Set<IRecipeInputNode> inputNeighbors = new HashSet<>();
        for (IEdge iEdge : graph.incomingEdgesOf(this))
        {
            INode edgeSource = graph.getEdgeSource(iEdge);
            if (edgeSource instanceof IRecipeInputNode)
            {
                IRecipeInputNode iRecipeInputNode = (IRecipeInputNode) edgeSource;
                inputNeighbors.add(iRecipeInputNode);
            }
        }

        final boolean isComplete = !hasIncompleteChildren(graph);

        final Set<CompoundInstance> summedCompoundInstances = new HashSet<>();
        Map<ICompoundType, Double> map = new HashMap<>();
        for (IRecipeInputNode inputNeighbor : inputNeighbors)
        {
            for (CompoundInstance compoundInstance1 : inputNeighbor
                                                        .getInputInstances(this))
            {
                if (isComplete || compoundInstance1.getType()
                                    .getGroup()
                                    .shouldIncompleteRecipeBeProcessed(getRecipe()))
                {
                    if (compoundInstance1.getType().getGroup().canContributeToRecipeAsInput(recipe, compoundInstance1))
                    {
                        AbstractMap.SimpleEntry<ICompoundType, Double> iCompoundTypeDoubleSimpleEntry = new HashMap.SimpleEntry<>(compoundInstance1.getType(),
                          compoundInstance1.getAmount()
                            * graph.getEdgeWeight(graph.getEdge(inputNeighbor, this)));
                        map.merge(iCompoundTypeDoubleSimpleEntry.getKey(), iCompoundTypeDoubleSimpleEntry.getValue(), Double::sum);
                    }
                }
            }
        }
        for (Map.Entry<ICompoundType, Double> entry : map
                                                        .entrySet())
        {
            double amount = entry.getValue();
            for (final IRecipeResidueNode requiredKnownOutput : requiredKnownOutputs)
            {
                for (final CompoundInstance compoundInstance : requiredKnownOutput.getResidueInstances(this))
                {
                    if (compoundInstance.getType().equals(entry.getKey()))
                    {
                        amount = Math.max(0, amount - (compoundInstance.getAmount() * graph.getEdgeWeight(graph.getEdge(requiredKnownOutput, this))));
                    }
                }
            }

            if (amount >= 0)
            {
                CompoundInstance instance = new CompoundInstance(entry.getKey(), amount);
                summedCompoundInstances.add(instance);
            }
        }

        this.forceSetResult(summedCompoundInstances, false);
    }

    @Override
    public void onReached(final IGraph graph)
    {
        for (IEdge accessibleWeightEdge : graph.outgoingEdgesOf(this))
        {
            INode v = graph.getEdgeTarget(accessibleWeightEdge);
            if (this.isIncomplete())
                v.setIncomplete();
        }

        final Set<IRecipeOutputNode> resultNeighbors = new HashSet<>();
        for (IEdge weightEdge : graph
                                  .outgoingEdgesOf(this))
        {
            INode edgeTarget = graph.getEdgeTarget(weightEdge);
            if (edgeTarget instanceof IRecipeOutputNode)
            {
                IRecipeOutputNode containerNode = (IRecipeOutputNode) edgeTarget;
                resultNeighbors.add(containerNode);
            }
        }

        final double totalOutgoingEdgeWeight = graph.outgoingEdgesOf(this)
                                                 .stream()
                                                 .mapToDouble(graph::getEdgeWeight)
                                                 .sum();

        for (IRecipeOutputNode neighbor : resultNeighbors)
        {
            Set<CompoundInstance> set = new HashSet<>();
            for (CompoundInstance totalCompoundInstance : getResultingValue().orElse(Collections.emptySet()))
            {
                final Double unitAmount = totalCompoundInstance.getAmount() / totalOutgoingEdgeWeight;
                CompoundInstance compoundInstance = new CompoundInstance(totalCompoundInstance.getType(), unitAmount);
                if (!compoundInstance.getType().getGroup().canContributeToRecipeAsOutput(recipe, compoundInstance))
                {
                    continue;
                }

                set.add(compoundInstance);
            }

            neighbor.addCandidateResult(this, graph.getEdge(this, neighbor), Optional.of(set));
        }
    }

    @Override
    public void collectStats(final StatCollector statCollector)
    {
        statCollector.onVisitRecipeNode();
    }

    @Override
    public String toString()
    {
        return "RecipeNode{" + recipe +
                 '}';
    }

    @Override
    public void addCandidateResult(final INode neighbor, final IEdge sourceEdge, final Optional<Set<CompoundInstance>> instances)
    {
        super.addCandidateResult(neighbor, sourceEdge, Optional.of(Collections.emptySet()));
    }

    @Override
    public void forceSetResult(final Set<CompoundInstance> compoundInstances)
    {
        //Again recipe nodes do not care.
        forceSetResult(compoundInstances, true);
    }

    private void forceSetResult(final Set<CompoundInstance> compoundInstances, boolean external)
    {
        //Again recipe nodes do not care.
        super.forceSetResult(external ? null : compoundInstances);
    }
}
