package com.tabletmc.echo_summon.impl;

/**
 * Marker interface for mounts that can detect and update their Mount Saddle state.
 */
public interface MountSaddleMountImpl {
    /**
     * @return true if the mount currently has the Mount Saddle equipped in its saddle slot.
     */
    boolean hasMountSaddle();

    /**
     * Recompute and update the Mount Saddle state from the equipped saddle slot item.
     */
    void updateMountSaddle();
}
