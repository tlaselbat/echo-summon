package com.tabletmc.echo_summon.impl;

import net.minecraft.entity.Entity;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.server.network.ServerPlayerEntity;

public interface ServerPlayerEntityImpl {

    void storeMount(AnimalEntity horse);

    void summonMount(boolean mountPlayer);

    void dismountHorse(boolean mountPlayer);

    default AnimalEntity getHorse() {
        Entity storedHorse = ((ServerPlayerEntity) this).getVehicle();
        return (AnimalEntity) storedHorse;
    }





}
