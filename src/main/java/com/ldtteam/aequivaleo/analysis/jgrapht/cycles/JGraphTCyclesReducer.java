package com.ldtteam.aequivaleo.analysis.jgrapht.cycles;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.ldtteam.aequivaleo.analysis.jgrapht.core.IAnalysisEdge;
import com.ldtteam.aequivaleo.utils.AnalysisLogHandler;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.util.TriConsumer;
import org.jetbrains.annotations.NotNull;
import org.jgrapht.Graph;
import org.jgrapht.alg.cycle.DirectedSimpleCycles;
import org.jgrapht.alg.cycle.HawickJamesSimpleCycles;

import java.util.*;
import java.util.function.BiFunction;

public class JGraphTCyclesReducer<G extends Graph<V, E>, V, E extends IAnalysisEdge>
{

    private static final Logger LOGGER = LogManager.getLogger();

    private final BiFunction<G, List<V>, V> vertexReplacerFunction;
    private final TriConsumer<V, V, V> onNeighborNodeReplacedCallback;
    private final boolean reduceSingularCycle;

    public JGraphTCyclesReducer(
      final BiFunction<G, List<V>, V> vertexReplacerFunction,
      final TriConsumer<V, V, V> onNeighborNodeReplacedCallback) {
        this(vertexReplacerFunction, onNeighborNodeReplacedCallback, true);
    }

    public JGraphTCyclesReducer(
      final BiFunction<G, List<V>, V> vertexReplacerFunction,
      final TriConsumer<V, V, V> onNeighborNodeReplacedCallback,
      final boolean reduceSingularCycle) {
        this.vertexReplacerFunction = vertexReplacerFunction;
        this.onNeighborNodeReplacedCallback = onNeighborNodeReplacedCallback;
        this.reduceSingularCycle = reduceSingularCycle;
    }

    public void reduce(final G graph) {
        //Unit testing has shown that this is enough
        //Saves a recompilation of the cycle detection graph.
        reduceOnce(graph);
    }

    @SuppressWarnings("DuplicatedCode")
    @VisibleForTesting
    public boolean reduceOnce(final G graph) {
        AnalysisLogHandler.debug(LOGGER, "Reducing the graph");

        final DirectedSimpleCycles<V, E> cycleFinder = new HawickJamesSimpleCycles<>(graph);
        List<List<V>> sortedCycles = cycleFinder.findSimpleCycles();

        List<List<V>> list = new ArrayList<>();
        Set<List<V>> uniqueValues = new HashSet<>();
        for (List<V> sortedCycle : sortedCycles)
        {
            if (uniqueValues.add(sortedCycle))
            {
                list.add(sortedCycle);
            }
        }
        sortedCycles = list;

        if (sortedCycles.isEmpty() || (sortedCycles.size() == 1 && !reduceSingularCycle))
        {
            AnalysisLogHandler.debug(LOGGER, " > Reducing skipped.");
            return false;
        }

        sortedCycles.sort(Comparator.comparing(List::size));

        while(!sortedCycles.isEmpty()) {
            final List<V> cycle = sortedCycles.get(0);

            AnalysisLogHandler.debug(LOGGER, String.format(" > Removing cycle: %s", cycle));

            final V replacementNode = vertexReplacerFunction.apply(graph, cycle);

            sortedCycles = updateRemainingCyclesAfterReplacement(
              sortedCycles,
              cycle,
              replacementNode
            );

            final Map<E, V> incomingEdges = Maps.newHashMap();
            final Map<E, V> outgoingEdges = Maps.newHashMap();
            final Multimap<V, E> incomingEdgesTo = HashMultimap.create();
            final Multimap<V, E> incomingEdgesOf = HashMultimap.create();
            final Multimap<V, E> outgoingEdgesOf = HashMultimap.create();
            final Multimap<V, E> outgoingEdgesTo = HashMultimap.create();

            //Collect all the edges which are relevant to keep.
            for (V v : cycle)
            {
                for (E e : graph.incomingEdgesOf(v))
                {
                    if (!cycle.contains(graph.getEdgeSource(e)))
                    {
                        if (!incomingEdgesTo.containsEntry(v, e))
                        {
                            incomingEdgesTo.put(v, e);
                            incomingEdgesOf.put(graph.getEdgeSource(e), e);
                            incomingEdges.put(e, graph.getEdgeSource(e));
                        }
                    }
                }

                for (E edge : graph.outgoingEdgesOf(v))
                {
                    if (!cycle.contains(graph.getEdgeTarget(edge)))
                    {
                        if (!outgoingEdgesOf.containsEntry(v, edge))
                        {
                            outgoingEdgesOf.put(v, edge);
                            outgoingEdgesTo.put(graph.getEdgeTarget(edge), edge);
                            outgoingEdges.put(edge, graph.getEdgeTarget(edge));
                        }
                    }
                }
            }

            AnalysisLogHandler.debug(LOGGER, String.format("  > Detected: %s as incoming edges to keep.", incomingEdges));
            AnalysisLogHandler.debug(LOGGER, String.format("  > Detected: %s as outgoing edges to keep.", outgoingEdges));

            //Create the new cycle construct.
            graph.addVertex(replacementNode);
            for (V incomingSource : incomingEdgesOf.keySet())
            {
                double newEdgeWeight = 0.0;
                for (E e : incomingEdgesOf.get(incomingSource))
                {
                    double weight = e.getWeight();
                    newEdgeWeight += weight;
                }
                graph.addEdge(incomingSource, replacementNode);
                graph.setEdgeWeight(incomingSource, replacementNode, newEdgeWeight);
            }
            for (V outgoingTarget : outgoingEdgesTo.keySet())
            {
                double newEdgeWeight = 0.0;
                for (E e : outgoingEdgesTo.get(outgoingTarget))
                {
                    double weight = e.getWeight();
                    newEdgeWeight += weight;
                }
                graph.addEdge(replacementNode, outgoingTarget);
                graph.setEdgeWeight(replacementNode, outgoingTarget, newEdgeWeight);
            }

            graph.removeAllVertices(cycle);

            incomingEdgesTo.forEach((cycleNode, edge) -> onNeighborNodeReplacedCallback.accept(incomingEdges.get(edge), cycleNode, replacementNode));
            outgoingEdgesOf.forEach((cycleNode, edge) -> onNeighborNodeReplacedCallback.accept(outgoingEdges.get(edge), cycleNode, replacementNode));

            AnalysisLogHandler.debug(LOGGER, String.format(" > Removed cycle: %s", cycle));
        }

        return true;
    }

    private List<List<V>> updateRemainingCyclesAfterReplacement(final List<List<V>> cycles, final List<V> replacedCycle, final V replacementNode) {
        cycles.remove(replacedCycle);

        List<List<V>> list = new ArrayList<>();
        for (List<V> cycle : cycles)
        {
            List<V> vs = updateCycleWithReplacement(replacedCycle, replacementNode, cycle);
            if (vs.size() > 1)
            {
                list.add(vs);
            }
        }
        list.sort(Comparator.comparing(List::size));
        return list;
    }

    @NotNull
    private List<V> updateCycleWithReplacement(final List<V> replacedCycle, final V replacementNode, final List<V> cycle)
    {
        final List<V> intersectingNodes = new ArrayList<>();
        for (V v1 : cycle)
        {
            if (replacedCycle.contains(v1))
            {
                intersectingNodes.add(v1);
            }
        }
        for (V intersectingNode : intersectingNodes)
        {
            AnalysisLogHandler.debug(LOGGER, "    > Replacing: " + intersectingNode + " with: " + replacementNode);
            final int nodeIndex = cycle.indexOf(intersectingNode);
            cycle.remove(nodeIndex);
            cycle.add(nodeIndex, replacementNode);
        }

        List<V> result = new ArrayList<>();
        V lastAdded = null;
        for (int i = 0; i < cycle.size(); i++)
        {
            final V v = cycle.get(i);
            if (!v.equals(lastAdded) && (i != cycle.size() -1 || cycle.get(0) != v))
            {
                lastAdded = v;
                result.add(lastAdded);
            }
        }
        return result;
    }
}
