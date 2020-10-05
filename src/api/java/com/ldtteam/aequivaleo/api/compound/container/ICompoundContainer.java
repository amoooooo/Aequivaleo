package com.ldtteam.aequivaleo.api.compound.container;

/**
 * Holds a object that is made up out of compounds.
 * @param <T> The type of game object that is held.
 */
public interface ICompoundContainer<T> extends Comparable<ICompoundContainer<?>>
{

    /**
     * The contents of this container.
     * Set to the 1 unit of the content type {@code T}
     *
     * @return The contents.
     */
    T getContents();

    /**
     * The amount of {@code T}s contained in this wrapper.
     * @return The amount.
     */
    Double getContentsCount();

    /**
     * Indicates if this containers locked information
     * can be loaded from disk.
     *
     * This if for example false for ItemStacks.
     *
     * @return True to indicate that data can be loaded form disk, false when not.
     */
    boolean canBeLoadedFromDisk();

    /**
     * Gives access to the content as a filename.
     *
     * @return a file name that represents the content.
     */
    String getContentAsFileName();
}
